package utk.security.PPSE.master;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import utk.security.PPSE.slave.PSSEJob;

public class JobScheduler {
	
	public enum SlaveStatus {
		UP,DOWN
	}

	private List<SlaveStatus> slaveStatus;
	private ExecutorService executor;
	private List<String> slaveAddr;
	
	public JobScheduler(int threadNum, List<String> slaveAddr){
		executor= Executors.newFixedThreadPool(threadNum);
		slaveStatus = new ArrayList<SlaveStatus>();
		slaveAddr = new ArrayList<String>();
		//TODO process the strings in slaveAddr
		
		//begin monitor the status of RMI servers
		executor.submit(new Thread(new ServerStatusTracker(),"status_tracker"));
	}
	
	public ArrayList<Task> generateTasks(PSSEJob job){
		ArrayList<Task> res = new ArrayList<Task>();
		//split job into slaveStatus in terms of frequency band
		double freqDelta = (job.freqBand[1]-job.freqBand[0])/slaveStatus.size();
		double taskFreqBandStart = job.freqBand[0];
		double taskFreqBandEnd = taskFreqBandStart+freqDelta;
		for(int t=0;t<slaveAddr.size();t++){
			Task task = new Task();
			task.freqBand = new double[]{taskFreqBandStart,taskFreqBandEnd};
			task.inputFile =job.inputFileName;
			task.timeWindow = job.timeWindow;
			taskFreqBandStart+=freqDelta;
			taskFreqBandEnd+=freqDelta;
			res.add(task);
		}
		return res;		
	}
	
	public void feedPSSEJob(PSSEJob job){
		Future JobResult = executor.submit(new PSSEJobThread(job));
		
		//do something according to JobResult
		try {
			if (((String) JobResult.get()).equalsIgnoreCase("SUCCESS")){
				//merge result
				
				//update the database
				
				
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void mergeResult(PSSEJob job){
		//TODO to implement
		
	}
	
	public void DBUpdate(PSSEJob job){
		//TODO implement the DB update
	}
	
	private class ServerStatusTracker implements Runnable{
		@Override
		public void run() {
			//polling status of RMI servers every 60 second
			while(true){
				//TODO check RMI server status by calling the responsive functions.
				
				//every 60 second
				
				
				//print out status information
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
			}
			
		}
	}
	
	private class PSSEJobThread implements Callable<String>{
		PSSEJob job;
		CompletionService<Boolean> taskExecutor = new ExecutorCompletionService<Boolean>(Executors.newFixedThreadPool(slaveAddr.size()));

		public PSSEJobThread(PSSEJob job){
			this.job = job;
		}

		@Override
		public String call() throws Exception {
			//get a list of tasks
			List<Task> tasks = generateTasks(job);
			List<String> taskResults = new ArrayList<String>();
			for(Task task:tasks){
				Future result = taskExecutor.submit(new Callable<Boolean>(){
					public Boolean call() throws Exception {
						//TODO find a viable RMI server according to a consistent hashing?
						
						//TODO get the stub of RMI
						
						//TODO pass the task
						
						//TODO wait for result, pass the result from the RMI server to this callable
					
					}
				});
			}
			//check if tasks are finished using completion service
			for(int i=0;i<tasks.size();i++){
				if(((Boolean)taskExecutor.take().get(60,TimeUnit.SECONDS)).booleanValue()==false)
					return "FAILURE";
			}
			//now it is safe to say the job is completed
			return "SUCCESS";		
		}
		
	}
}
