package utk.security.PPSE.master;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Used to describe a job of performing PPSE on one time window worth of data
 * @author ytong3
 *
 */
public class PPSEJob{
	
	/**
	 * the path to the input in the NFS
	 */
	public String inputFileName;
	public Date creationDate;
	public double[] freqBand = new double[]{0.1,1};
	public double samplingRate;
	public int[] timeWindow;
	public int overlap;
	
	public PPSEJob (String inputFileName, double[] freqBand, double samplingRate, int[] timeWindow){
		this.inputFileName = inputFileName;
		creationDate = new Date();
		this.samplingRate = samplingRate;
		this.freqBand = freqBand;
		this.timeWindow = timeWindow;
	}
}
