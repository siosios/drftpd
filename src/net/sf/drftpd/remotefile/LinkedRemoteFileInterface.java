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
package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile.NonExistingFile;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferStatus;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public interface LinkedRemoteFileInterface extends RemoteFileInterface {
	/**
	 * Updates lastMofidied() on this directory, use putFile() to avoid it.
	 */
	public abstract LinkedRemoteFile addFile(AbstractRemoteFile file);
	public abstract void addSlave(RemoteSlave slave);
	/**
	 * @throws ClassCastException if object is not an instance of RemoteFileInterface.
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public abstract int compareTo(Object o);
	public abstract LinkedRemoteFile createDirectories(String path);
	public abstract LinkedRemoteFile createDirectory(String fileName)
		throws FileExistsException;
	public abstract LinkedRemoteFile createDirectory(
		String owner,
		String group,
		String fileName)
		throws FileExistsException;
	/**
	 * Deletes a file or directory, if slaves are offline, the file cannot be deleted.
	 * To work around this, the file gets a deleted flag set and when the offline slave is remerge()'d, it is deleted from the slave and delete() is called again.
	 *
	 * Trying to lookupFile() or getFile() a deleted file throws FileNotFoundException.
	 */
	public abstract void delete();
	public abstract void deleteOthers(Set destSlaves);
	public abstract long dirSize();
	public abstract RemoteSlave getASlave(char direction, BaseFtpConnection conn)
		throws NoAvailableSlaveException;
	public abstract Collection getAvailableSlaves()
		throws NoAvailableSlaveException;
	/**
	 * Uses cached checksum if the cached checksum is not 0
	 */
	public abstract long getCheckSum() throws NoAvailableSlaveException;
	/**
	 * Returns 0 if the checksum cannot be read.
	 */
	public abstract long getCheckSumFromSlave()
		throws NoAvailableSlaveException, IOException;
	public abstract Collection getDirectories();
	/**
	 * Returns fLinkedRemoteFileInterfaced in this directory.
	 * 
	 * @param fileName
	 * @throws FileNotFoundException if fileName doesn't exist in the files Map
	 */
	public abstract LinkedRemoteFileInterface getFile(String fileName)
		throws FileNotFoundException;
	public abstract LinkedRemoteFileInterface getFileDeleted(
		String fileName)
		throws FileNotFoundException;

	/**
	 * Returns the underlying Map for this directory.
	 * 
	 * It is dangerous to modify without knowing what you're doing.
	 * Dirsize needs to be taken into account as well as sending approperiate commands to the slaves. 
	 * @return the underlying Map for this directory.
	 */
	public abstract Map getMap();
	public abstract LinkedRemoteFileInterface getOldestFile()
		throws ObjectNotFoundException;
	public abstract LinkedRemoteFile getParentFile()
		throws FileNotFoundException;
	public abstract LinkedRemoteFile getParentFileNull();
	public abstract LinkedRemoteFile getRoot();
	public abstract SFVFile getSFVFile()
		throws IOException, FileNotFoundException, NoAvailableSlaveException;
	public abstract long getXferspeed();
	/**
	 * Returns true if this directory contains a file named filename, this is case sensitive.
	 * @param filename The name of the file
	 * @return true if this directory contains a file named filename, this is case sensitive.
	 */
	public abstract boolean hasFile(String filename);
	public abstract int hashCode();
	/**
	 * Returns true if this file or directory uses slaves that are currently offline.
	 * @return true if this file or directory uses slaves that are currently offline.
	 */
	public abstract boolean hasOfflineSlaves();
	public abstract boolean hasSlave(RemoteSlave slave);
	/**
	 * Does file have online slaves?
	 * 
	 * @return Always true for directories
	 */
	public abstract boolean isAvailable();
	public abstract LinkedRemoteFile lookupFile(String path)
		throws FileNotFoundException;
	public abstract LinkedRemoteFile lookupFile(
		String path,
		boolean includeDeleted)
		throws FileNotFoundException;
	public abstract NonExistingFile lookupNonExistingFile(String path);
	public abstract NonExistingFile lookupNonExistingFile(
		String path,
		boolean includeDeleted);
	/**
	 * Returns path for a non-existing file. Performs path normalization and returns an absolute path
	 */
	public abstract String lookupPath(String path);
	public abstract SFVFile lookupSFVFile()
		throws IOException, FileNotFoundException, NoAvailableSlaveException;
	/**
	 * Use addFile() if you want lastModified to be updated.
	 */
	public abstract LinkedRemoteFile putFile(RemoteFileInterface file);

	public TransferStatus receiveFile(Transfer transfer, char type, long offset) throws IOException;
	/**
	 * Merges mergedir directory onto <code>this</code> directories.
	 * If duplicates exist, the slaves are added to this object and the file-attributes of the oldest file (lastModified) are kept.
	 */
	public abstract void remerge(LinkedRemoteFile mergedir, RemoteSlave rslave);
	public abstract boolean removeSlave(RemoteSlave slave);
	/**
	 * Renames this file
	 */
	public abstract LinkedRemoteFile renameTo(String toDirPath, String toName)
		throws IOException, FileNotFoundException;
	public TransferStatus sendFile(Transfer transfer, char type, long offset) throws IOException;
	public abstract void setCheckSum(long checkSum);
	public abstract void setGroup(String group);
	public abstract void setLastModified(long lastModified);
	public abstract void setLength(long length);
	public abstract void setOwner(String owner);
	public abstract void setXfertime(long xfertime);
	public abstract String toString();
	public abstract void unmergeDir(RemoteSlave rslave);
	public abstract void unmergeFile(RemoteSlave rslave);
}