package utk.security.PPSE.master;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import utk.security.PPSE.slave.PPSERMIServer;
import utk.security.PPSE.slave.PPSERemoteTask;

public class JobScheduler {
	
	public enum SlaveStatus {
		UP,DOWN
	}

	private int taskTimeOut = 60;
	private List<String> slaveList;
	private Map<String,SlaveStatus> slaveStatus;
	private Map<String,PPSERMIServer> slaveStubs;
	
	private ExecutorService executor;
	private ExecutorService pingExecutor;
	private CompletionService<String> taskExecutor;
	
	
	public JobScheduler(int threadNum, List<String> slaveAddr){
		executor= Executors.newFixedThreadPool(threadNum);
		slaveStatus = new HashMap<>();
		
		//add all slaves to list and get their remote objects
		for (String addrStr:slaveAddr){
			String[] addr = addrStr.split(":");
			slaveList.add(addrStr);
			try{
				Registry registry = LocateRegistry.getRegistry(addr[0], Integer.parseInt(addr[1]));
				PPSERMIServer stub = (PPSERMIServer) registry.lookup("PPSE");
				slaveStubs.put(addrStr, stub);
			}catch(RemoteException | NotBoundException e){
				e.printStackTrace();
			}
		}
		
		pingExecutor = Executors.newFixedThreadPool(slaveList.size()/2);
		taskExecutor = new ExecutorCompletionService<String>(Executors.newFixedThreadPool(slaveList.size()));
		//begin monitor the status of RMI servers
		executor.submit(new Thread(new ServerStatusTracker(),"status_tracker"));
		
	}
	
	//generate tasks from a job
	//job contains exactly one time window worth of data
	public ArrayList<Task> generateTasks(PPSEJob job){
		ArrayList<Task> res = new ArrayList<Task>();
		//split job into slaveStatus in terms of frequency band
		//asumming a single file contains only one time windows worth of data.
		//this can be achieved by having an additional method to preprocess the measurement data before writing them onto the disk.
		double freqDelta = (job.freqBand[1]-job.freqBand[0])/slaveStatus.size();
		double taskFreqBandStart = job.freqBand[0];
		double taskFreqBandEnd = taskFreqBandStart+freqDelta;
		for(int t=0;t<slaveList.size();t++){
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
	
	public void feedPSSEJob(PPSEJob job){
		Future<List<String>> JobResult = executor.submit(new PSSEJobThread(job));
		
		//do something according to JobResult
		try {
			if (JobResult.get()!=null){
				// merge result
				mergeResult(job.inputFileName,JobResult.get());
				// update the database
				DBUpdate(job,true);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void mergeResult(String inputFile, List<String> taskResults){
		//TODO 
		Collections.sort(taskResults);
		System.err.println(taskResults.toString());
		
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(inputFile+"_encryptedSpectrum.out"))){
			PrintWriter out = new PrintWriter(bw);
			
			for (String taskResult:taskResults){
				//Strip out the first line of each element in taskResult
				String[] parsedLines = taskResult.split("\n");
				//write from the second line:
				for (int i=1;i<parsedLines.length;i++){
					out.println(parsedLines[i]);
				}
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void DBUpdate(PPSEJob job, boolean completed){
		//TODO implement the DB update
		System.err.println("DBUpdate Placeholder: job completed!");
	}
	
	private class ServerStatusTracker implements Runnable{
		
		@Override
		public void run() {
			//polling status of RMI servers every 60 second
			while(true){
				//TODO check RMI server status by calling the responsive functions.
				for (final String slaveAddr:slaveList){
					final PPSERMIServer stub = slaveStubs.get(slaveAddr);
					Future res = pingExecutor.submit(new Runnable(){
						@Override
						public void run() {
							try {
								if(stub.checkHealth().equals("GOOD"))
									slaveStatus.put(slaveAddr, SlaveStatus.UP);
							} catch (RemoteException e) {
								// TODO Auto-generated catch block
								
								e.printStackTrace();
							}
						}
					});
					
					try {
						res.get(5000, TimeUnit.MILLISECONDS);
					} catch (TimeoutException | InterruptedException | ExecutionException e) {
						slaveStatus.put(slaveAddr,SlaveStatus.DOWN);
						e.printStackTrace();
					}
				}

				//print out status information
				System.err.println("Minutely status infor");
				for(Map.Entry<String, SlaveStatus> entry:slaveStatus.entrySet()){
					System.err.println(entry.getKey()+":"+entry.getValue());
				}
				
				try {
					//Check status every 60 seconds
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					//try to bring the same tracker again
					executor.submit(new ServerStatusTracker());
					return;
				}
			}
			
		}
	}
	
	private class PSSEJobThread implements Callable<List<String>>{
		PPSEJob job;
		
		public PSSEJobThread(PPSEJob job){
			this.job = job;
		}

		@Override
		public List<String> call() throws Exception {
			//get a list of tasks
			List<Task> tasks = generateTasks(job);
			List<String> res = new ArrayList<>();
			for(int taskNum=0;taskNum<tasks.size();taskNum++){
				// static allocation of worker servers. do not consider the failover for now.
				final int slaveIndex = taskNum/tasks.size()*slaveList.size();
				
				
				final Task task = tasks.get(taskNum);
				taskExecutor.submit(new Callable<String>(){
					public String call() throws RemoteException {
						// find the corresponding slave, using consistent hashing or static binding.
						PPSERMIServer server = slaveStubs.get(slaveList.get(slaveIndex));
						//TODO get the stub of RMI. If not available, get the backup server. If backup server is down, too. Shut down the system.
						//TODO pass the task
						//TODO wait for result, pass the result from the RMI server to this callable
						return server.executeTask(new PPSERemoteTask(task));
					}
				});
			}
			//check if tasks are finished using completion service
			for(int i=0;i<tasks.size();i++){
				String taskResult = taskExecutor.take().get(taskTimeOut,TimeUnit.SECONDS);
				if (taskResult.equalsIgnoreCase("FAILURE")==false)
					res.add(taskResult);
				else
					return null;					
			}
			//now it is safe to say the job is completed
			return res;
		}
		
	}
	
	public static void main(String[] args){
		//test the distributed system
		List<String> slaveList = Arrays.asList("hydra12.eecs.utk.edu:22239",
											   "hydra13.eecs.utk.edu:22239",
											   "hydra14.eecs.utk.edu:22239",
											   "hydra15.eecs.utk.edu:22239",
											   "hydra16.eecs.utk.edu:22239");
		
		
		JobScheduler testScheduler = new JobScheduler(5,slaveList);
		
		//construct a job
		PPSEJob testJob = new PPSEJob("TestEncryptedMeasurement.dat",new double[]{0.1,1},100,new int[]{0,1});
		
		//begin computing
		testScheduler.feedPSSEJob(testJob);
		
	}
}
