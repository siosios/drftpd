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
package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.SFVFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import de.hampelratte.id3.ID3v1Tag;

/**
 * Slave interface, this interface is used to initate transfers to and from remote slaves.
 * @author Morgan Christiansson <mog@linux.nu>
 * @version $Id: Slave.java,v 1.35 2004/07/24 01:38:10 teflon114 Exp $
 */
public interface Slave extends Remote {
	public long checkSum(
		String path)
		throws RemoteException, IOException;
        
	public Transfer listen(boolean encrypted) throws RemoteException, IOException;
	public Transfer connect(InetSocketAddress addr, boolean encrypted) throws RemoteException;
	/**
	 * Get statistics for this slave, usefull when deciding which slave to use when transferring files.
	 */
	public SlaveStatus getSlaveStatus() throws RemoteException;

	/**
	 * Check to see if slave is still up.
	 */
	public void ping() throws RemoteException;
	
	public SFVFile getSFVFile(String path)
		throws RemoteException, IOException;
	
	public ID3v1Tag getID3v1Tag(String path) throws IOException;

	/**
	 * Rename files, should only be called by RemoteSlave
	 */
	public void rename(String from, String toDirPath, String toName)
		throws RemoteException, IOException;
		
	/**
	 * Delete files, should only be called by RemoteSlave
	 */
	public void delete(String path) throws RemoteException, IOException;
	/**
	 * Builds a new updated slaveroot
	 */
	public LinkedRemoteFile getSlaveRoot() throws IOException;
}
