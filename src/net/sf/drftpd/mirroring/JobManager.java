package net.sf.drftpd.mirroring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Logger;

/**
 * @author zubov
 * @version $Id: JobManager.java,v 1.15 2004/01/13 06:36:59 zubov Exp $
 */
public class JobManager implements FtpListener {
	private static final Logger logger = Logger.getLogger(JobManager.class);
	private ConnectionManager _cm;
	private ArrayList _jobList = new ArrayList();
	private ArrayList _slaveSendingList = new ArrayList();
	private ArrayList _threadList = new ArrayList();

	/**
	 * Keeps track of all jobs and controls them
	 */
	public JobManager() {
	}

	public void actionPerformed(Event event) {
		if (!(event instanceof SlaveEvent))
			return;
		SlaveEvent slaveEvent = (SlaveEvent) event;
		if (slaveEvent.getCommand().equals("DELSLAVE")) {
			//synchronized (_threadList) { // does not need to be synchronized if only one thread handles events 
			for (Iterator iter = _threadList.iterator(); iter.hasNext();) {
				JobManagerThread tempThread = (JobManagerThread) iter.next();
				if (tempThread.getRSlave() == slaveEvent.getRSlave()) {
					tempThread.stopme();
					_threadList.remove(tempThread);
					logger.debug(
						"Stopped slave thread for "
							+ slaveEvent.getRSlave().getName());
					break; // only one slave can die at a time
					// have to break here or else get ConcurrentModificationException
					// no point in searching for more anyhow
				}
			}
			//}
		} else if (slaveEvent.getCommand().equals("ADDSLAVE")) {
			JobManagerThread newThread =
				new JobManagerThread(slaveEvent.getRSlave(), this);
			_threadList.add(newThread);
			logger.debug(
				"Started slave thread for " + slaveEvent.getRSlave().getName());
			newThread.start();
		}
		logger.debug(
			slaveEvent.getCommand()
				+ " was issued on "
				+ slaveEvent.getRSlave().getName());
	}
	public synchronized void addJob(Job job) {
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job temp = (Job) iter.next();
			if (temp.getFile() == job.getFile()) {
				temp.addSlaves(job.getDestinationSlaves());
				return;
			}
		}
		_jobList.add(job);
		//		logger.debug(
		//			"Added job " + job.getFile().getPath() + " to the jobQueue");
		Collections.sort(_jobList, new JobComparator());
	}

	/**
	 * Gets all jobs.
	 * @return All jobs.
	 */
	public List getAllJobs() {
		return Collections.unmodifiableList(_jobList);
	}

	/**
	 * Get all jobs for a specific LinkedRemoteFile.
	 * @param lrf The LinkedRemoteFile to return all jobs for.
	 * @return A <code>java.util.List</code> of all jobs for the specific LinkedRemoteFile
	 */
	public synchronized List getAllJobs(LinkedRemoteFile lrf) {
		ArrayList tempList = new ArrayList();
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getFile() == lrf) {
				tempList.add(tempJob);
			}
		}
		return tempList;
	}

	/**
	 * Get all jobs where Job#getSource() is source 
	 * @param source The source of all objects to get.
	 * @return List of all <code>Job</code>s
	 */
	public synchronized List getAllJobs(Object source) {
		ArrayList tempList = new ArrayList();
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getSource().equals(source))
				tempList.add(tempJob);
		}
		return tempList;
	}
	/**
	 * Gets the next job suitable for the slave, returns true if it is a mirror job
	 */
	public synchronized Job getNextJob(RemoteSlave slave) {
		Job jobToReturn = null;
		for (Iterator iter = _jobList.iterator(); iter.hasNext();) {
			Job tempJob = (Job) iter.next();
			if (tempJob.getDestinationSlaves().contains(null)) { // mirror job
				try {
					if (jobToReturn == null) {
						if (tempJob.getFile() == null) {
							logger.error("tempJob.getFile() == null");
							continue;
						}
						if (tempJob.getFile().getAvailableSlaves() == null) {
							logger.debug(
								"tempJob.getFile().getAvailableSlaves() == null, can't transfer the file from nowhere");
							continue;
						}
						if (!tempJob
							.getFile()
							.getAvailableSlaves()
							.contains(slave)) {
							//							logger.debug(
							//								"jobToReturn is assigned - " + slave.getName());
							jobToReturn = tempJob;
						}
					}
				} catch (NoAvailableSlaveException e) {
					logger.debug(
						"NoAvailableSlaveException for mirror algorithm - "
							+ slave.getName(),
						e);
					// can't transfer it, so don't set jobToReturn
				}
				continue;
			}
			if (tempJob.getDestinationSlaves().contains(slave)) {
				//				logger.debug(
				//					"an Archive job is being returned - " + slave.getName());
				removeJob(tempJob);
				return tempJob;
			}
		}
		if (jobToReturn != null) {
			removeJob(jobToReturn);
			//			logger.info(
			//				"jobToReturn is returning a mirror job for - "
			//					+ slave.getName());
		}
		return jobToReturn;
	}

	public void init(ConnectionManager mgr) {
		_cm = mgr;
		Collection slaveList;
		try {
			slaveList = _cm.getSlaveManager().getAvailableSlaves();
		} catch (NoAvailableSlaveException e) {
			return;
		}

		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			JobManagerThread newThread = new JobManagerThread(rslave, this);
			_threadList.add(newThread);
			newThread.start();
		}
	}
	/**
	 * Returns true if the slave could possibly have another file to immediately transfer
	 */
	public boolean processJob(RemoteSlave slave) {
		Job temp = getNextJob(slave);

		if (temp == null) { // nothing to process for this slave
			//			logger.debug("Nothing to process for slave " + slave.getName());
			return false;
		}
		if (temp.getFile().getSlaves().contains(slave)) {
			if (temp.removeDestinationSlave(slave)) {
				//				logger.debug(
				//					"Removed "
				//						+ slave.getName()
				//						+ " from the destination list ("
				//						+ temp.getDestinationSlaves().size()
				//						+ " left) of the job "
				//						+ temp);
				// if it's already there, remove it from the destinationList
				if (temp.getDestinationSlaves().size() > 0) {
					addJob(temp);
				} else {
					temp.setDone();
				}
				return true;
			}
			if (temp.removeDestinationSlave(null)) {
				//				logger.debug(
				//					"Removed "
				//						+ slave.getName()
				//						+ " from the destination list ("
				//						+ temp.getDestinationSlaves().size()
				//						+ " left) of the job "
				//						+ temp);
				if (temp.getDestinationSlaves().size() > 0) {
					addJob(temp);
				} else {
					temp.setDone();
				}
				return true;
			}
			logger.debug(
				"Unable to remove "
					+ slave.getName()
					+ " from the destination list of the job "
					+ temp);
			return false; // should not get here
		}
		logger.info(
			"Sending " + temp.getFile().getName() + " to " + slave.getName());
		long time = System.currentTimeMillis();
		long difference = 0;
		RemoteSlave sourceSlave = null;
		try {
			try {
				sourceSlave = temp.getFile().getASlaveForDownload();
			} catch (NoAvailableSlaveException e) {
				logger.debug(
					"Could not send the file "
						+ temp.getFile()
						+ " because getASlaveForDownload returned NoAvailableSlaveException",
					e);
				addJob(temp);
				return false;
			}
			while (true) {
				synchronized (_slaveSendingList) {
					if (!_slaveSendingList.contains(sourceSlave))
						break;
					_slaveSendingList.add(sourceSlave);
				}
				Thread.sleep(20000);
			}
			new SlaveTransfer(temp.getFile(), sourceSlave, slave).transfer();
		} catch (Exception e) {
			_slaveSendingList.remove(sourceSlave);
			logger.debug(
				"Error Sending "
					+ temp.getFile().getName()
					+ " from "
					+ sourceSlave.getName()
					+ " to "
					+ slave.getName(),
				e);
			addJob(temp);
			return false;
		}
		_slaveSendingList.remove(sourceSlave);
		difference = System.currentTimeMillis() - time;
		logger.debug(
			"Sent file "
				+ temp.getFile().getName()
				+ " to "
				+ slave.getName()
				+ " from "
				+ sourceSlave.getName());
		temp.addTimeSpent(difference);
		//		logger.info(
		//			"Done Sending "
		//				+ temp.getFile().getName()
		//				+ " from "
		//				+ sourceSlave.getName()
		//				+ " to "
		//				+ slave.getName()
		//				+ " in "
		//				+ difference / 1000
		//				+ " seconds");
		if (!temp.removeDestinationSlave(slave)) {
			if (!temp.removeDestinationSlave(null))
				logger.debug(
					"Not able to remove slave "
						+ slave.getName()
						+ " or a null reference from job "
						+ temp);
		}
		if (temp.getDestinationSlaves().size() > 0) {
			logger.debug("Adding job " + temp + " back into the jobList");
			addJob(temp); // job still has more places to transfer
		} else {
			temp.setDone();
			//			logger.debug("Setting job " + temp + " to be done");
		}
		return true;
	}
	public synchronized void removeJob(Job job) {
		_jobList.remove(job);
		Collections.sort(_jobList, new JobComparator());
	}
}