package utk.security.PPSE.slave;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import utk.security.PPSE.crypto.BigComplex;
import utk.security.PPSE.crypto.PaillierFFT;

/**
 * RMI server that performs Paillier Cryptographic computation
 * @author ytong3
 */
public class RMIServer implements PPSERMIServer{
	private String pubKeyFile;
	
	public RMIServer(String pubKey){
		super();
		this.pubKeyFile = pubKey;
	}
	
	@Override
	public String executeTask(PPSERemoteTask task) throws RemoteException {
		System.err.println("Task Received. Begin Executing.");
		//server takes care of read and write
		//read file
		InputStream fis = null;
		BufferedReader br = null;
		
		try {
			//read file
			System.err.println("Read file.");
			fis = new FileInputStream(task.inputFile);
			br = new BufferedReader(new InputStreamReader(fis,Charset.defaultCharset()));
			String line =null;
			
			//get configuration line Q1 and Q2
			line = br.readLine();
			if (line==null) throw new FileNotFoundException("File Corrupted. Scaling factor not found in the first line");
			String[] fileParams = line.trim().split(" ");
			//extract Q1, Q2, numPoint, steps
			long Q1=0, Q2=0;
			for(String str:fileParams){
				String[] param = str.split(":");
				if ("Q1".equalsIgnoreCase(param[0])) Q1=Long.parseLong(param[1]);
				else if ("Q2".equalsIgnoreCase(param[0])) Q2=Long.parseLong(param[1]);
			}
			
			if (Q1==0||Q2==0) throw new IOException("Bad inialization parameter for PaillierFFT engine");
			
			System.err.println("Create HomomorphicDFT engine");
			HomomorphicDFT engine = new PaillierFFT(Q1, Q2, pubKeyFile, true);
			List<BigComplex> encryptedMeasurementData = new ArrayList<BigComplex>();
			
			System.err.println("load windowLength worth encrypted measurement data into the the list");
			//load windowLength encrypted measurement data into the the list.
			int counter = 0;
			while ((line=br.readLine())!=null){
				counter++;
				String[] tmpStrs = line.split(",");
				BigComplex singleEncryptedMeasurement = new BigComplex(new BigInteger(tmpStrs[0]),new BigInteger(tmpStrs[1]));
				encryptedMeasurementData.add(singleEncryptedMeasurement);
				// check counter. Exit if there are windowLength elements already
				if (counter==(task.timeWindow[1]-task.timeWindow[0])*task.samplingRate) break;
			}
						
			//perform ppDFT with encryptedMeasurementData
			System.err.println("Perform homoDFT");
			List<BigComplex> encryptedSpectrum = engine.homoDFT(encryptedMeasurementData, task.freqBand[0], task.freqBand[1]);
			
			//send back the encryptedSpectrum
			System.err.println("Sending back result");
			return String.format("[%.3f,%.3f]\n",task.freqBand[0],task.freqBand[1])+
				   String.format("Q1:%d Q2:%d\n",Q1,Q2)+
				   BigComplex.BigComplexListToString(encryptedSpectrum);
		} catch (IOException e) {
			System.err.println(e.getClass()+":"+e.getMessage());
			//e.printStackTrace();
		} finally{
			try{
				if (fis!=null) fis.close();
				if (br!=null) br.close();
			}
			catch(IOException ex){
				System.err.println(ex.getClass()+":"+ex.getMessage());
				ex.printStackTrace();
			}
		}
		
		return "FAILURE";
	}

	@Override
	public String checkHealth() throws RemoteException {
		return "GOOD";
		//TODO more status later
	}
	
	public static void main(String[] argv){
		try{
			if(argv.length!=2){
				System.err.println("Usage: RMIServer 'PortNumber' 'PubKeyFile'");
				return;
			}
			int portNum = Integer.parseInt(argv[0]);
			System.out.println("Binding to rmi registry at port: "+portNum);
			RMIServer serverObj = new RMIServer(argv[1]);
			PPSERMIServer stub = (PPSERMIServer) UnicastRemoteObject.exportObject(serverObj, 0);
			//Bind the remote object's stub in the registry
			Registry registry = LocateRegistry.getRegistry(portNum);
			registry.rebind("PPSERMIServer", stub);
			
			System.out.println("Server Ready");
		} catch(Exception e){
			System.err.println("Server exception: "+e.toString());
			e.printStackTrace();
		}
	}
	
}
