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
package org.drftpd.tests;

import java.rmi.RemoteException;
import java.util.List;

import net.sf.drftpd.master.SlaveManagerImpl;

import org.drftpd.GlobalContext;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

/**
 * @author mog
 * @version $Id: DummySlaveManager.java,v 1.2 2004/07/13 06:41:58 zubov Exp $
 */
public class DummySlaveManager extends SlaveManagerImpl {
	public DummySlaveManager() throws RemoteException {
		super();
	}
	public void setSlaves(List rslaves) {
		_rslaves = rslaves;
	}

	public void setGlobalContext(GlobalContext gctx) {
		_gctx = gctx;
	}
	
	public void setSlaveSelectionManager(SlaveSelectionManagerInterface ssm) {
		_slaveSelectionManager = ssm;
	}
}
