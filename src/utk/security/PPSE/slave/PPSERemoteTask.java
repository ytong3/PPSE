package utk.security.PPSE.slave;

import java.io.Serializable;

import utk.security.PPSE.master.Task;

public class PPSERemoteTask extends Task implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PPSERemoteTask(Task t){
		this.freqBand = t.freqBand;
		this.inputFile = t.inputFile;
		this.timeWindow = t.timeWindow;
	}
}
