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
package net.sf.drftpd.master;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import net.sf.drftpd.SFVFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileTest;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author mog
 * @version $Id: RemoteSlaveTest.java,v 1.2 2004/06/11 03:45:50 zubov Exp $
 */
public class RemoteSlaveTest extends TestCase {
	public static TestSuite suite() {
		return new TestSuite(RemoteSlaveTest.class);
	}
	
	public RemoteSlaveTest(String fName) {
		super(fName);
	}
	
	public void testEquals() {
		RemoteSlave rslave1 = new RemoteSlave("test1", Collections.EMPTY_LIST);
		RemoteSlave rslave2 = new RemoteSlave("test1", Collections.EMPTY_LIST);
		assertTrue(rslave1.equals(rslave1));
		assertTrue(rslave1.equals(rslave2));
	}
	public class SlaveImplTest implements Slave {

		private HashSet _filelist;
		public SlaveImplTest(HashSet filelist) {
			_filelist = filelist;
		}
		
		public long checkSum(String path) throws RemoteException, IOException {
			// TODO Auto-generated method stub
			return 0;
		}

		public Transfer listen(boolean encrypted) throws RemoteException, IOException {
			// TODO Auto-generated method stub
			return null;
		}

		public Transfer connect(InetSocketAddress addr, boolean encrypted) throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		public SlaveStatus getSlaveStatus() throws RemoteException {
			// TODO Auto-generated method stub
			return null;
		}

		public void ping() throws RemoteException {
			// TODO Auto-generated method stub
			
		}

		public SFVFile getSFVFile(String path) throws RemoteException, IOException {
			// TODO Auto-generated method stub
			return null;
		}

		public void rename(String from, String toDirPath, String toName) throws RemoteException, IOException {
			_filelist.remove(from);
			_filelist.add(new String(toDirPath+"/"+toName));
		}

		public void delete(String path) throws RemoteException, IOException {
			_filelist.remove(path);
		}

		public LinkedRemoteFile getSlaveRoot() throws IOException {
			// TODO Auto-generated method stub
			return null;
		}
	}
	
	public void testSetSlave() throws IOException {
		RemoteSlave rslave = new RemoteSlave("test", Collections.EMPTY_LIST);
		rslave.delete("/deleteme");
		rslave.rename("/renameme", "/indir", "tofile");
		List list = new ArrayList();
		list.add(rslave);
		HashSet filelist = new HashSet();
		filelist.add("/deleteme");
		filelist.add("/renameme");
		filelist.add("/indir");
		Slave slave = new SlaveImplTest(filelist);
		rslave.setSlave(slave,null,null,256);
		assertFalse(filelist.contains("/deleteme"));
		assertFalse(filelist.contains("/renameme"));
		assertTrue(filelist.contains("/indir"));
		assertTrue(filelist.contains("/indir/tofile"));
	}
}
