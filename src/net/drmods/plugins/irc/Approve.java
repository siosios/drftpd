/* 
 * Approve plugin for drftpd 1.1.4
 * Created on Aug 14, 2004 by Teflon
 *
 */
package net.drmods.plugins.irc;

import java.util.Iterator;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author Teflon
 *
 */
public class Approve extends GenericCommandAutoService implements IRCPluginInterface {

	private static final Logger logger = Logger.getLogger(Approve.class);

	private SiteBot _listener;
    private String _trigger;

	public String getCommands() {
		return _trigger + "approve";
	}

	public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "approve", user))
            help += _listener.getCommandPrefix() 
            		+ "approve <dir>: Creates a subfolder in the given dir named Approved.by.<user>\n";
		return help;
	}

	private GlobalContext getGlobalContext() {
		return _listener.getGlobalContext();
	}

	public Approve(SiteBot listener) {
		super(listener.getIRCConnection());
		_listener = listener;
        _trigger = _listener.getCommandPrefix();
	}

	protected void updateCommand(InCommand command) {
		if (!(command instanceof MessageCommand)) {
			return;
		}
		MessageCommand msgc = (MessageCommand) command;
		if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
			return;
		}

		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
		env.add("ircnick",msgc.getSource().getNick());

		String msg = msgc.getMessage().trim();
		if (msg.equals(_trigger + "approve")) {
			_listener.sayChannel(msgc.getDest(), 
				ReplacerUtils.jprintf("approve.usage", env, Approve.class));			
		} else if (msg.startsWith(_trigger + "approve ")) {
    		try {
                if (!_listener.getIRCConfig().checkIrcPermission(
                        _listener.getCommandPrefix() + "slaves",msgc.getSource())) {
                	_listener.sayChannel(msgc.getDest(), 
                			ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
                	return;				
                }
            } catch (NoSuchUserException e) {
    			_listener.sayChannel(msgc.getDest(), 
    					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
    			return;
            }

			String dirName;
			try {
				dirName = msgc.getMessage().substring((_trigger + "approve ").length());
			} catch (ArrayIndexOutOfBoundsException e) {
				logger.warn("", e);
				_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("approve.usage", env, Approve.class));
				return;
			} catch (StringIndexOutOfBoundsException e) {
				logger.warn("", e);
				_listener.sayChannel(msgc.getDest(), 
					ReplacerUtils.jprintf("approve.usage", env, Approve.class));
				return;
			}
			env.add("sdirname",dirName);
			
			User user;
            try {
                user = _listener.getIRCConfig().lookupUser(msgc.getSource());
            } catch (NoSuchUserException e2) {
    			_listener.sayChannel(msgc.getDest(), 
    					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
    			return;
            }
            LinkedRemoteFileInterface dir = findDir(getGlobalContext(),
													 getGlobalContext().getRoot(),
													 user,
													 dirName);
			if (dir!= null){
				env.add("sdirpath",dir.getPath());
				String approveDirName = ReplacerUtils.jprintf("approve.dirname", env, Approve.class);
				env.add("adirname",approveDirName);
				env.add("adirpath",dir.getPath() + "/" + approveDirName);
				try {
					LinkedRemoteFileInterface newdir = dir.createDirectory(approveDirName);
					newdir.setOwner(user.getName());
					newdir.setGroup(user.getGroup());
					_listener.sayChannel(msgc.getDest(), 
										 ReplacerUtils.jprintf("approve.success", env, Approve.class));
					getGlobalContext().dispatchFtpEvent(
							new DirectoryFtpEvent(user, "MKD", newdir));
				} catch (FileExistsException e1) {
					_listener.sayChannel(msgc.getDest(), 
										 ReplacerUtils.jprintf("approve.exists", env, Approve.class));
				}
			} else {
				_listener.sayChannel(msgc.getDest(), 
									 ReplacerUtils.jprintf("approve.error", env, Approve.class));
			}

		}
	}

	private static LinkedRemoteFileInterface findDir(
		GlobalContext gctx,
		LinkedRemoteFileInterface dir,
		User user,
		String searchstring) {

		if (!gctx.getConfig().checkPathPermission("privpath", user, dir, true)) {
			Logger.getLogger(Approve.class).debug("privpath: "+dir.getPath());
			return null;
		}

		for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
			if (file.isDirectory()) {
				if (file.getName().toLowerCase().equals(searchstring.toLowerCase())) {
					logger.info("Found " + file.getPath());
					return file;
				} 
				LinkedRemoteFileInterface dir2 = findDir(gctx, file, user, searchstring);
				if (dir2 != null) {
					return dir2;
				}		
			}
		}
		return null;
	}
}
