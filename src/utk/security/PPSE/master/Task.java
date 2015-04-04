package utk.security.PPSE.master;

import java.io.Serializable;

public class Task implements Serializable{
	public String inputFile;
	public double[] freqBand;
	public int[] timeWindow;
	public double samplingRate;
	
	public Task(){};
	
	public Task(String inputFile, double[] freqBand, int[] timeWindow, double samplingRate){
		this.inputFile = inputFile;
		this.freqBand = freqBand;
		this.timeWindow = timeWindow;
		this.samplingRate = samplingRate;
	}
	
	@Override
	public String toString(){
		return String.format("Task File Name: %s, Frequence band %.3f ~ %.3f",inputFile,freqBand[0],freqBand[1]);
	}
}
