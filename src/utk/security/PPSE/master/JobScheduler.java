package utk.security.PPSE.master;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.rmi.ConnectException;
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

	private int taskTimeOut = 10;
	private List<String> slaveList;
	private Map<String,SlaveStatus> slaveStatus;
	private Map<String,PPSERMIServer> slaveStubs;
	
	private ExecutorService executor;
	private ExecutorService pingExecutor;
	private CompletionService<String> taskExecutor;
	
	
	public JobScheduler(int threadNum, List<String> slaveAddr){
		executor= Executors.newFixedThreadPool(threadNum);
		slaveStatus = new HashMap<String, SlaveStatus>();
		slaveList = new ArrayList<String>();
		slaveStubs = new HashMap<String, PPSERMIServer>();
		
		//add all slaves to list and get their remote objects
		for (String addrStr:slaveAddr){
			System.out.println(addrStr);
			String[] addr = addrStr.split(":");
			slaveList.add(addrStr);
			try{
				Registry registry = LocateRegistry.getRegistry(addr[0], Integer.parseInt(addr[1]));
				PPSERMIServer stub = (PPSERMIServer) registry.lookup("PPSERMIServer");
				slaveStubs.put(addrStr, stub);
			}catch(RemoteException e){
				e.printStackTrace();
			}catch(NotBoundException e){
				e.printStackTrace();
			}
		}
		
		pingExecutor = Executors.newFixedThreadPool(Math.max(1,slaveList.size()/2));;
		taskExecutor = new ExecutorCompletionService<String>(Executors.newFixedThreadPool(slaveList.size()));
		//begin monitor the status of RMI servers
		executor.submit(new Thread(new ServerStatusTracker(),"status_tracker"));
		
	}
	
	public void feedPSSEJob(PPSEJob job){
		System.err.println("Job Received at job scheduler");
		Future<List<String>> JobResult = executor.submit(new PSSEJobThread(job));
		System.err.println("Job Submitted to executor at job scheduler");
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

	//generate tasks from a job
	//job contains exactly one time window worth of data
	public ArrayList<Task> generateTasks(PPSEJob job){
		ArrayList<Task> res = new ArrayList<Task>();
		//split job into slaveStatus in terms of frequency band
		//asumming a single file contains only one time windows worth of data.
		//this can be achieved by having an additional method to preprocess the measurement data before writing them onto the disk.
		double freqDelta = (job.freqBand[1]-job.freqBand[0])/slaveList.size();
		double taskFreqBandStart = job.freqBand[0];
		double taskFreqBandEnd = taskFreqBandStart+freqDelta;
		for(int t=0;t<slaveList.size();t++){
			Task task = new Task(job.inputFileName,new double[]{taskFreqBandStart,taskFreqBandEnd},job.timeWindow, job.samplingRate);
			taskFreqBandStart+=freqDelta;
			taskFreqBandEnd+=freqDelta;
			res.add(task);
		}
		return res;		
	}
	
	public void mergeResult(String inputFile, List<String> taskResults){

		Collections.sort(taskResults);
		System.err.println(taskResults.toString());
		BufferedWriter bw = null;
		try{
			bw = new BufferedWriter(new FileWriter(inputFile+"_encryptedSpectrum.out"));
			PrintWriter out = new PrintWriter(bw);
			if(taskResults.size()>0){
				String[] parsedLines = taskResults.get(0).split("\n");
				for(int i=1;i<parsedLines.length;i++){
					out.println(parsedLines[i]);
				}
			}
				
			for (int i=1;i<taskResults.size();i++){
				//Strip out the first line of each element in taskResult
				String[] parsedLines = taskResults.get(i).split("\n");
				//write from the second line:
				for (int row=2;row<parsedLines.length;row++){//write after the second row
					out.println(parsedLines[row]);
				}
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally{
			try {
				bw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
								slaveStatus.put(slaveAddr,SlaveStatus.DOWN);
								e.printStackTrace();
							}
						}
					});
					
					try {
						res.get(5000, TimeUnit.MILLISECONDS);
					} catch (TimeoutException e) {
						slaveStatus.put(slaveAddr,SlaveStatus.DOWN);
						e.printStackTrace();
					} catch (ExecutionException e){
						slaveStatus.put(slaveAddr,SlaveStatus.DOWN);
						e.printStackTrace();
					} catch (InterruptedException e) {
						slaveStatus.put(slaveAddr,SlaveStatus.DOWN);
						e.printStackTrace();
					}
				}

				//print out status information
				System.err.println("Minutely status information");
				for(Map.Entry<String, SlaveStatus> entry:slaveStatus.entrySet()){
					System.err.println(entry.getKey()+":"+entry.getValue());
				}
				
				try {
					//Check status every 60 seconds
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					//try to bring the same tracker again
					executor.submit(new Thread(new ServerStatusTracker(),"status_tracker"));
					return;
				}
			}
			
		}
	}
	
	private class PSSEJobThread implements Callable<List<String>>{
		final PPSEJob job;
		
		public PSSEJobThread(PPSEJob job){
			this.job = job;
		}

		@Override
		public List<String> call() throws Exception {
			//get a list of tasks
			List<Task> tasks = generateTasks(job);
			
			System.err.println("Job divided into tasks");
			for(Task task:tasks){
				System.out.println(task.toString());
			}
			
			List<String> res = new ArrayList<String>();
			for(int taskNum=0;taskNum<tasks.size();taskNum++){
				// static allocation of worker servers. do not consider the failover for now.
				System.out.println("Processing task number: "+taskNum);
				final int slaveIndex = taskNum/tasks.size()*slaveList.size();
				
				final Task task = tasks.get(taskNum);
				taskExecutor.submit(new Callable<String>(){
					public String call() throws RemoteException {
						// find the corresponding slave, using consistent hashing or static binding.
						PPSERMIServer server = slaveStubs.get(slaveList.get(slaveIndex));
						System.out.println(task.toString()+" Sent to Slave: "+slaveList.get(slaveIndex));
						// get the stub of RMI. If not available, get the backup server. If backup server is down, too. Shut down the system.
						// pass the task
						// wait for result, pass the result from the RMI server to this callable
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
		List<String> slaveList = Arrays.asList("localhost:22233");
		
		System.out.println("number of threads in pool: "+(slaveList.size()+3));
		JobScheduler testScheduler = new JobScheduler(slaveList.size()+3,slaveList);
		
		//construct a job
		PPSEJob testJob = new PPSEJob("original_angles_4000samples.csv.enc",new double[]{0.1,1},100,new int[]{0,40});
		
		//begin computing
		testScheduler.feedPSSEJob(testJob);
		
	}
}
