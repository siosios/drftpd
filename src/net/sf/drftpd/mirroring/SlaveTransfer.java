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
package net.sf.drftpd.mirroring;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;
import org.apache.log4j.Logger;
/**
 * @author mog
 * @author zubov
 * @version $Id
 */
public class SlaveTransfer {
	class DstXfer extends Thread {
		private Transfer dstxfer;
		private Throwable e;
		public DstXfer(Transfer dstxfer) {
			this.dstxfer = dstxfer;
		}
		public long getChecksum() throws RemoteException {
			return dstxfer.getChecksum();
		}
		/**
		 * @return
		 */
		public int getLocalPort() throws RemoteException {
			return dstxfer.getLocalPort();
		}
		public void run() {
			try {
				_file.receiveFile(dstxfer, 'I', 0L);
			} catch (Throwable e) {
				this.e = e;
			}
		}
	}
	class SrcXfer extends Thread {
		private Throwable e;
		private Transfer srcxfer;
		public SrcXfer(Transfer srcxfer) {
			this.srcxfer = srcxfer;
		}
		public long getChecksum() throws RemoteException {
			return srcxfer.getChecksum();
		}
		public void run() {
			try {
				_file.sendFile(srcxfer, 'I', 0L);
			} catch (Throwable e) {
				this.e = e;
			}
		}
	}
	private static final Logger logger = Logger.getLogger(SlaveTransfer.class);
	private RemoteSlave _destSlave;
	private LinkedRemoteFile _file;
	private RemoteSlave _sourceSlave;
	private boolean finished = false;
	private Throwable stackTrace;
	/**
	 * Slave to Slave Transfers
	 */
	public SlaveTransfer(LinkedRemoteFile file, RemoteSlave sourceSlave,
			RemoteSlave destSlave) {
		_file = file;
		_sourceSlave = sourceSlave;
		_destSlave = destSlave;
	}
	public void interruptibleSleepUntilFinished() throws Throwable {
		while (!finished) {
			try {
				Thread.sleep(1000); // 1 sec
				//System.err.println("slept 1 secs");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (stackTrace != null)
			throw stackTrace;
	}
	/**
	 * Returns true if the crc passed, false otherwise
	 * 
	 * @return @throws
	 *         IOException
	 */
	public boolean transfer(boolean checkCRC) throws IOException {
		DstXfer dstxfer = new DstXfer(_destSlave.getSlave().listen(false));
		SrcXfer srcxfer = new SrcXfer(_sourceSlave.getSlave().connect(
				new InetSocketAddress(_destSlave.getInetAddress(), dstxfer
						.getLocalPort()), false));
		dstxfer.start();
		srcxfer.start();
		while (srcxfer.isAlive() && dstxfer.isAlive()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		if (srcxfer.e != null) {
			if (srcxfer.e instanceof IOException)
				throw (IOException) srcxfer.e;
			throw new RuntimeException(srcxfer.e);
		}
		if (dstxfer.e != null) {
			if (dstxfer.e instanceof IOException)
				throw (IOException) dstxfer.e;
			throw new RuntimeException(dstxfer.e);
		}
		if (!checkCRC) {
			// crc passes if we're not using it
			return true;
		}
		if (dstxfer.getChecksum() == 0
				|| _file.getCheckSumCached() == dstxfer.getChecksum()
				|| _file.getCheckSumCached() == _destSlave.getSlave().checkSum(
						_file.getPath())) {
			_file.addSlave(_destSlave);
			return true;
		}
		return false;
	}
}
