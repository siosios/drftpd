/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.commands;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandHandlerBundle;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;
import org.drftpd.mirroring.ArchiveHandler;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.sections.SectionInterface;
import org.tanesha.replacer.ReplacerEnvironment;

/*
 * @author zubov
 * @version $Id
 */
public class ArchiveCommandHandler implements CommandHandlerBundle {

	public ArchiveCommandHandler() {
		super();
	}
	private static final Logger logger = Logger.getLogger(ArchiveCommandHandler.class);
	
	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE LISTARCHIVETYPES".equals(cmd)) {
			return doLISTARCHIVETYPES(conn);
		}
		if ("SITE ARCHIVE".equals(cmd)) {
			return doARCHIVE(conn);
		}
		throw UnhandledCommandException.create(
				ArchiveCommandHandler.class,
				conn.getRequest());
	}

	private FtpReply doARCHIVE(BaseFtpConnection conn) {
		FtpReply reply = new FtpReply(200);
		ReplacerEnvironment env = new ReplacerEnvironment();
		if (!conn.getRequest().hasArgument()) {
			reply.addComment(conn.jprintf(ArchiveCommandHandler.class, "archive.usage", env));
			return reply;
		}
		StringTokenizer st =
			new StringTokenizer(conn.getRequest().getArgument());
		String dirname = st.nextToken();
		LinkedRemoteFileInterface lrf;
		try {
			lrf = conn.getCurrentDirectory().getFile(dirname);
		} catch (FileNotFoundException e1) {
			try {
				lrf = conn.getConnectionManager().getRoot().lookupFile(dirname);
			} catch (FileNotFoundException e2) {
				reply.addComment(
					conn.jprintf(ArchiveCommandHandler.class, "archive.usage", env));
				env.add("dirname", dirname);
				reply.addComment(
					conn.jprintf(ArchiveCommandHandler.class, "archive.baddir", env));
				return reply;
			}
		}
		net.sf.drftpd.event.listeners.Archive archive;
		try {
			archive =
				(net.sf.drftpd.event.listeners.Archive) conn
					.getConnectionManager()
					.getFtpListener(net.sf.drftpd.event.listeners.Archive.class);
		} catch (ObjectNotFoundException e3) {
			reply.addComment(
				conn.jprintf(ArchiveCommandHandler.class, "archive.loadarchive", env));
			return reply;
		}
		String archiveTypeName = null;
		ArchiveType archiveType = null;
		SectionInterface section =
			conn.getConnectionManager().getSectionManager().lookup(
				lrf.getPath());
		if (st.hasMoreTokens()) {
			archiveTypeName = st.nextToken();
			Class[] classParams = {net.sf.drftpd.event.listeners.Archive.class, SectionInterface.class};
			Constructor constructor = null;
			try {
				constructor = Class.forName(
						"org.drftpd.mirroring.archivetypes." + archiveTypeName).getConstructor(classParams);
			} catch (Exception e1) {
				logger.debug("Unable to load ArchiveType for section " + section.getName(), e1);
				reply.addComment(conn.jprintf(ArchiveCommandHandler.class, "archive.badarchivetype", env));
				return reply;
			}
			Object[] objectParams = { archive, section };
			try {
				archiveType = (ArchiveType) constructor.newInstance(objectParams);
			} catch (Exception e2) {
				logger.debug("Unable to load ArchiveType for section " + section.getName(), e2);
				reply.addComment(conn.jprintf(ArchiveCommandHandler.class, "archive.badarchivetype", env));
				return reply;
			}
		}
		if (archiveType == null) {
			archiveType = archive.getArchiveType(section);
		}
		if (archiveTypeName == null) {
			archiveTypeName = archiveType.getClass().getName();
		}
		HashSet slaveSet = new HashSet();
		synchronized(archiveType) {
			if (archiveType.isBusy()) {
				env.add("section", section.getName());
				reply.addComment(
						conn.jprintf(ArchiveCommandHandler.class, "archive.wait1", env));
				reply.addComment(
						conn.jprintf(ArchiveCommandHandler.class, "archive.wait2", env));
				return reply;
			}
			while (st.hasMoreTokens()) {
				String slavename = st.nextToken();
				try {
					RemoteSlave rslave =
						conn.getConnectionManager().getSlaveManager().getSlave(
								slavename);
					slaveSet.add(rslave);
				} catch (ObjectNotFoundException e2) {
					env.add("slavename", slavename);
					reply.addComment(
							conn.jprintf(ArchiveCommandHandler.class, "archive.badslave", env));
				}
			}
			archiveType.setDirectory(lrf);
		}
		if (!slaveSet.isEmpty())
			archiveType.setRSlaves(slaveSet);
		ArchiveType usedArchiveType = ArchiveHandler.getArchiveTypeForDirectory(archiveType.getDirectory());
		if (usedArchiveType != null) {
			env.add("archivetype",usedArchiveType);
			reply.addComment(conn.jprintf(ArchiveCommandHandler.class, "archive.fail", env));
			return reply;
		}
		ArchiveHandler archiveHandler = new ArchiveHandler(archiveType);
		archiveHandler.start();
		env.add("dirname", lrf.getPath());
		env.add("archivetypename", archiveTypeName);
		reply.addComment(conn.jprintf(ArchiveCommandHandler.class, "archive.success", env));
		return reply;
	}

	private FtpReply doLISTARCHIVETYPES(BaseFtpConnection conn) {
		FtpReply reply = new FtpReply(200);
		int x = 0;
		for (Iterator iter = ArchiveHandler.getArchiveHandlers().iterator(); iter.hasNext(); x++) {
			ArchiveHandler archiveHandler = (ArchiveHandler) iter.next();
			reply.addComment(x + ". " + archiveHandler.getArchiveType());
		}
		return reply;
	}

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}

}