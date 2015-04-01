package utk.security.PPSE.master;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
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

	private List<String> slaveList;
	private Map<String,SlaveStatus> slaveStatus;
	private Map<String,PPSERMIServer> slaveStubs;
	
	private ExecutorService executor;
	private ExecutorService pingExecutor = Executors.newFixedThreadPool(slaveList.size()/2);
	private CompletionService<Boolean> taskExecutor = new ExecutorCompletionService<Boolean>(Executors.newFixedThreadPool(slaveList.size()));
	
	
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
		
		//begin monitor the status of RMI servers
		executor.submit(new Thread(new ServerStatusTracker(),"status_tracker"));
		
	}
	
	public ArrayList<Task> generateTasks(PPSEJob job){
		ArrayList<Task> res = new ArrayList<Task>();
		//split job into slaveStatus in terms of frequency band
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
	
	public void mergeResult(PPSEJob job){
		//TODO to implement
		
	}
	
	public void DBUpdate(PPSEJob job){
		//TODO implement the DB update
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
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					//try to bring the same tracker again
					executor.submit(new ServerStatusTracker());
					return;
				}
			}
			
		}
	}
	
	private class PSSEJobThread implements Callable<String>{
		PPSEJob job;
		public PSSEJobThread(PPSEJob job){
			this.job = job;
			//For each job
			
		}

		@Override
		public String call() throws Exception {
			//get a list of tasks
			List<Task> tasks = generateTasks(job);
			List<String> taskResults = new ArrayList<String>();
			for(int taskNum=0;taskNum<tasks.size();taskNum++){
				final int slaveIndex = taskNum;
				final Task task = tasks.get(taskNum);
				Future<Boolean> result = taskExecutor.submit(new Callable<Boolean>(){
					public Boolean call() throws Exception {
						//TODO find the corresponding slave, using consistent hashing or static binding.
						PPSERMIServer server = slaveStubs.get(slaveList.get(slaveIndex));
						//TODO get the stub of RMI. If not available, get the backup server. If backup server is down, too. Shut down the system.
						//TODO pass the task
						//TODO wait for result, pass the result from the RMI server to this callable
						if(server.executeTask(new PPSERemoteTask(task)).equalsIgnoreCase("SUCCESS"))
							return true;
						return false;
					}
				});
			}
			//check if tasks are finished using completion service
			for(int i=0;i<tasks.size();i++){
				if(taskExecutor.take().get(60,TimeUnit.SECONDS).booleanValue()==false)
					return "FAILURE";
			}
			//now it is safe to say the job is completed
			return "SUCCESS";		
		}
		
	}
}
