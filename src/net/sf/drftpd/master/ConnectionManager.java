package net.sf.drftpd.master;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.GlftpdLog;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.queues.NukeLog;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.permission.GlobRMISocketFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveImpl;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import se.mog.io.File;

public class ConnectionManager {
	public static final int idleTimeout = 600;
	private Vector connections = new Vector();
	private UserManager usermanager;
	private NukeLog nukelog;
	private SlaveManagerImpl slavemanager;
	private Timer timer;

	private static Logger logger =
		Logger.getLogger(ConnectionManager.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}

	public ConnectionManager(Properties cfg) {
		
		
		/** END: load XML file database **/
		
		nukelog = new NukeLog();
		try {
			addFtpListener(new GlftpdLog(new File("glftpd.log")));
		} catch (IOException e1) {
			logger.log(Level.SEVERE, "Error writing to glftpd.log", e1);
		}
		
		try {
			Document doc =
				new SAXBuilder().build(new FileReader("nukelog.xml"));
			List nukes = doc.getRootElement().getChildren("nukes");
			for (Iterator iter = nukes.iterator(); iter.hasNext();) {
				Element nukeElement = (Element) iter.next();
				
				User user = usermanager.getUserByName(nukeElement.getChildText("user"));
				String command = nukeElement.getChildText("command");
				String directory = nukeElement.getChildText("directory");
				long time = Long.parseLong(nukeElement.getChildText("time"));
				int multiplier = Integer.parseInt(nukeElement.getChildText("multiplier"));
				String reason = nukeElement.getChildText("reason");

				StaticRemoteFile directoryFile = new StaticRemoteFile(Collections.EMPTY_LIST, directory, null, 0, time);

				Map nukees = new Hashtable();
				List nukeesElement = nukeElement.getChild("nukees").getChildren("nukee");
				for (Iterator iterator = nukeesElement.iterator();
					iterator.hasNext();
					) {
					Element nukeeElement = (Element) iterator.next();
					String nukeeUsername = nukeeElement.getChildText("username");
					Long nukeeAmount = new Long(nukeeElement.getChildText("amount"));
					nukees.put(nukeeUsername, nukeeAmount);
				}
				
				nukelog.add(new NukeEvent(user, command, directoryFile.getPath(), time, multiplier, reason, nukees));
			}
		} catch(FileNotFoundException ex) {
			logger.log(Level.FINE, "Couldn't open nukelog.xml - will create it later", ex);
		} catch (Exception ex) {
			logger.log(
				Level.INFO,
				"Error loading nukelog from nukelog.xml",
				ex);
		}
		Collection rslaves = SlaveManagerImpl.loadRSlaves();
		GlobRMISocketFactory ssf = new GlobRMISocketFactory(rslaves);
		/** register slavemanager **/
		try {
			slavemanager =
				new SlaveManagerImpl(
					cfg, rslaves, ssf);
		} catch (Throwable e) {
			throw new FatalException(e);
		}

		String localslave = cfg.getProperty("master.localslave", "false");
		if (localslave.equalsIgnoreCase("true")) {
			Slave slave;
			try {
				slave = new SlaveImpl(cfg);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
				//the compiler doesn't know that execution stops at System.exit(),
			}
			
			try {
				LinkedRemoteFile slaveroot =
					SlaveImpl.getDefaultRoot(
						cfg.getProperty("slave.roots"));
				slavemanager.addSlave(cfg.getProperty("slave.name"), slave, slaveroot);
			} catch (RemoteException ex) {
				ex.printStackTrace();
				return;
			} catch (IOException ex) {
				ex.printStackTrace();
				System.exit(0);
				return;
			}
		}
		
		try {
			usermanager = (UserManager) Class.forName(cfg.getProperty("master.usermanager")).newInstance();
		} catch (Exception e) {
			throw new FatalException("Cannot create instance of usermanager, check master.usermanager in drftpd.conf", e);
		}

		timer = new Timer();
		TimerTask timerLogoutIdle = new TimerTask() {
			public void run() {
				timerLogoutIdle();
			}
		};
		//run every 10 seconds
		timer.schedule(timerLogoutIdle, 0, 10 * 1000);

		TimerTask timerSave = new TimerTask() {
			public void run() {
				slavemanager.saveFilesXML();
			}
		};
		//run every 5 minutes
		timer.schedule(timerSave, 0, 600 * 1000);
		

		try {
			addFtpListener(new IRCListener(this, cfg));
		} catch (Exception e2) {
			logger.log(Level.WARNING, "Error starting IRC bot", e2);
		}
	}

	public void timerLogoutIdle() {
		long currTime = System.currentTimeMillis();
		synchronized (connections) {
			//for(Iterator i = ((Vector)connections.clone()).iterator(); i.hasNext(); ) {
			for (Iterator i = connections.iterator(); i.hasNext();) {
				FtpConnection conn = (FtpConnection) i.next();

				int idle = (int) ((currTime - conn.getLastActive()) / 1000);
				if (conn.getUser() == null) {
					logger.finer(conn + " not logged in");
					continue;
				}
				int maxIdleTime = conn.getUser().getMaxIdleTime();
				if (maxIdleTime == 0)
					maxIdleTime = idleTimeout;
				User user = conn.getUser();
				//				logger.finest(
				//					"User has been idle for "
				//						+ idle
				//						+ "s, max "
				//						+ maxIdleTime
				//						+ "s");

				if (idle >= maxIdleTime) {
					// idle time expired, logout user.
					conn.stop(
						"Idle time expired: "
							+ conn.getUser().getMaxIdleTime()
							+ "s");
				}
			}
		}
	}

	public void start(Socket sock) throws IOException {
		FtpConnection conn =
			new FtpConnection(
				sock,
				usermanager,
				slavemanager,
				slavemanager.getRoot(),
				this,
				this.nukelog);
		conn.ftpListeners = this.ftpListeners;
		connections.add(conn);
		conn.start();
	}
	
	private ArrayList ftpListeners = new ArrayList();
	public void addFtpListener(FtpListener listener) {
		ftpListeners.add(listener);
	}
	
	public void remove(BaseFtpConnection conn) {
		if (!connections.remove(conn)) {
			throw new RuntimeException("connections.remove() returned false.");
		}
	}

	/**
	 * returns a <code>Collection</code> of current connections
	 */
	public Collection getConnections() {
		return connections;
	}
	/**
	 * @deprecated use {@link net.sf.drftpd.master.BaseFtpConnection#stop}
	 * @param conn
	 * @param message
	 */
	public void killConnection(BaseFtpConnection conn, String message) {
		conn.stop(message);
	}
	
	public static final String VERSION = "drftpd alpha master server CVS";
	public static void main(String args[]) {
		System.out.println(VERSION+" starting.");
		System.out.println("http://drftpd.sourceforge.net");
		try {
			Handler handlers[] = Logger.getLogger("").getHandlers();
			if (handlers.length == 1) {
				handlers[0].setLevel(Level.FINEST);
			} else {
				logger.warning(
					"handlers.length != 1, can't setLevel() on root element");
			}

			/** load config **/
			logger.info("loading drftpd.conf");
			Properties cfg = new Properties();
			try {
				cfg.load(new FileInputStream("drftpd.conf"));
			} catch (IOException e) {
				logger.severe("Error reading drftpd.conf: " + e.getMessage());
				return;
			}

			logger.info("Starting ConnectionManager");
			ConnectionManager mgr = new ConnectionManager(cfg);
			System.setProperty("line.separator", "\r\n");
			/** listen for connections **/
			try {
				ServerSocket server =
					new ServerSocket(
						Integer.parseInt(cfg.getProperty("master.port")));
				logger.info("Listening on port " + server.getLocalPort());
				while (true) {
					mgr.start(server.accept());
				}
			} catch(BindException e) {
				throw new FatalException("Couldn't bind on port "+cfg.getProperty("master.port"), e);
			} catch (Exception e) {
				logger.log(Level.SEVERE, "", e);
			}
		} catch (Throwable th) {
			logger.log(Level.SEVERE, "", th);
			System.exit(0);
			return;
		}
	}
	
	public void reload() {
		
	}
	/**
	 * @return
	 */
	public SlaveManagerImpl getSlavemanager() {
		return slavemanager;
	}

	/**
	 * @return
	 */
	public UserManager getUsermanager() {
		return usermanager;
	}

}