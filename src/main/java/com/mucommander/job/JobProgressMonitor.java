/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2016 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.job;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.EventListenerList;

/**
 * A class that monitors jobs progress.
 * @author Arik Hadas, Mariusz Jakubowski
 *
 */
public class JobProgressMonitor implements FileJobListener {
	
    /** Controls how often should current file label be refreshed (in ms) */
	private final static int CURRENT_FILE_LABEL_REFRESH_RATE = 100;
	
	/** Controls how often should progress information be refreshed */
    private final static int MAIN_REFRESH_RATE = 10;
    
    /** Time after which remove finished job from a monitor */
    private final static int FINISHED_JOB_REMOVE_TIME = 1500;

    /** Timer used to monitor jobs progress */
    private Timer progressTimer;
	
    /** List of listeners */
	private EventListenerList listenerList = new EventListenerList();
	
	/** A list of monitored jobs. */
	private List<FileJob> jobs;

	/** An instance of this class */
	private static final JobProgressMonitor instance = new JobProgressMonitor();
		
	
	/**
	 * Creates a new JobProgressMonitor instance.
	 */
	private JobProgressMonitor() {
		JobProgressTimer timerListener = new JobProgressTimer(); 
    	progressTimer = new Timer(CURRENT_FILE_LABEL_REFRESH_RATE, timerListener);
    	jobs  = new ArrayList<>();
	}
	
	/**
	 * Returns the instance of JobProgressMonitor.
	 * @return the instance of JobProgressMonitor.
	 */
	public static JobProgressMonitor getInstance() {
		return instance;
	}
    
	
    /**
     * Adds a listener to the list that's notified each time a job 
     * progress is updated.
     *
     * @param	l		the JobListener
     */
    public void addJobListener(JobListener l) {
    	listenerList.add(JobListener.class, l);
    }

    /**
     * Removes a listener from the list that's notified each time job
     * progress is updated.
     *
     * @param	l		the JobListener
     */
    public void removeJobListener(JobListener l) {
    	listenerList.remove(JobListener.class, l);
    }

    /**
     * Forwards the progress notification event to all
     * <code>JobListeners</code> that registered
     * themselves as listeners.
     * @param source a job for which the progress has been updated
     * @param fullUpdate if false only file label has been updated 
     * 
     * @see #addJobListener
     * @see JobListener#jobProgress
     */
    private void fireJobProgress(FileJob source, boolean fullUpdate) {
    	Object[] listeners = listenerList.getListenerList();
    	for (int i = listeners.length-2; i>=0; i-=2) {
    		((JobListener)listeners[i+1]).jobProgress(source, fullUpdate);
    	}
    }
    
    /**
     * Forwards the job added notification event to all
     * <code>JobListeners</code> that registered
     * themselves as listeners.
     * @param source an added job 
     * 
     * @see #addJobListener
     * @see JobListener#jobAdded(FileJob, int)
     */
    private void fireJobAdded(FileJob source) {
    	Object[] listeners = listenerList.getListenerList();
    	for (int i = listeners.length-2; i>=0; i-=2) {
    		((JobListener)listeners[i+1]).jobAdded(source);
    	}    	
    }
    
    /**
     * Forwards the job removed notification event to all
     * <code>JobListeners</code> that registered
     * themselves as listeners.
     * @param source a removed job
     * 
     * @see #addJobListener
     * @see JobListener#jobRemoved(FileJob, int)
     */
    private void fireJobRemoved(FileJob source) {
    	Object[] listeners = listenerList.getListenerList();
    	for (int i = listeners.length-2; i>=0; i-=2) {
    		((JobListener)listeners[i+1]).jobRemoved(source);
    	}    	
    }

    /**
     * Adds a new job to the list of monitored jobs. 
     * This method is executed in Swing Thread (EDT).
     * After adding a new job a {@link JobListener#jobAdded(FileJob, int)} 
     * event is fired.
     * @param job a job to be added
     */
    public void addJob(final FileJob job) {
    	// ensure that this method is called in EDT
    	if (!SwingUtilities.isEventDispatchThread()) {
    		SwingUtilities.invokeLater(() -> addJob(job));
    	}

    	jobs.add(job);
		fireJobAdded(job);
    	if (!progressTimer.isRunning()) {
    		progressTimer.start();
    	}
    	job.addFileJobListener(this);
    }
    
    /**
     * Removes a job from a list of monitored jobs.
     * This method is executed in Swing Thread (EDT).
     * After adding a new job a {@link JobListener#jobRemoved(FileJob, int)} 
     * event is fired.
     * @param job a job to be removed
     */
    public void removeJob(final FileJob job) {
    	// ensure that this method is called in EDT
    	if (!SwingUtilities.isEventDispatchThread()) {
    		SwingUtilities.invokeLater(() -> removeJob(job));
    	}

    	jobs.remove(job);
		if (jobs.isEmpty()) {
			progressTimer.stop();
		}
		fireJobRemoved(job);
		job.removeFileJobListener(this);
    }
    
	/**
	 * Returns number of monitored jobs.
	 * @return number of monitored jobs.
	 */
	public int getJobCount() {
		return jobs.size();
	}

	
	/**
	 * Returns a progress of a job with specified index.
	 * @param rowIndex an index of a job
	 * @return a progress information or null if job doesn't exists
	 */
	public JobProgress getJobProgres(int rowIndex) {
		if (rowIndex < jobs.size()) {
			FileJob job = jobs.get(rowIndex);
			return job.getJobProgress();
		}
		return null;
	}

	/**
	 * A {@link FileJobListener} implementation.
	 * Removes a finished job after a small delay.
	 */
	public void jobStateChanged(final FileJob source, FileJobState oldState, FileJobState newState) {
		if (newState == FileJobState.FINISHED || newState == FileJobState.INTERRUPTED) {
			ActionListener jobToRemove = event -> removeJob(source);
			Timer timer = new Timer(FINISHED_JOB_REMOVE_TIME, jobToRemove);
			timer.setRepeats(false);
			timer.start();
		}		
	}
	
	
	
	/**
     * 
     * This class implements a listener for a job progress timer.
     *
     */
	private class JobProgressTimer implements ActionListener {
		
		/** a loop index indicating if this refresh is partial (label only) or full */
		private int loopCount;

		public void actionPerformed(ActionEvent e) {
			loopCount++;

			boolean fullUpdate;			
			if (loopCount >= MAIN_REFRESH_RATE) {
				fullUpdate = true;
				loopCount = 0;
			} else {
				fullUpdate = false;
			}
			
			// for each job calculate new progress and notify listeners
			for (FileJob job : jobs) {
				JobProgress jobProgress = job.getJobProgress();
				boolean updateFullUI = jobProgress.calcJobProgress(fullUpdate);
				fireJobProgress(job, updateFullUI);
			}
			
		}

	}


}
