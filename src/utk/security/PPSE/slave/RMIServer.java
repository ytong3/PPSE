package utk.security.PPSE.slave;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import utk.security.PPSE.crypto.PaillierFFT;

/**
 * RMI server that performs Paillier Cryptographic computation
 * @author ytong3
 *
 */
public class RMIServer implements PPSERMIServer{
	private String pubKeyFile;
	
	public RMIServer(String pubKey){
		super();
		this.pubKeyFile = pubKey;
	}

	@Override
	public String executeTask(PPSERemoteTask task) throws RemoteException {
		// TODO Auto-generated method stub
		if(PaillierFFT.doDFTOnEncryptedFile(task.inputFile, pubKeyFile, task.freqBand[0], task.freqBand[1]))
			return "SUCCESS";
		return "FAILURE";
	}

	@Override
	public String checkHealth() throws RemoteException {
		// TODO Auto-generated method stub
		return "GOOD";
	}
	
	public static void main(String[] argv){
		try{
			if(argv.length!=2){
				System.err.println("Usage: RMIServer 'PortNumber' 'PubKeyFile'");
				return;
			}
			int portNum = Integer.parseInt(argv[0]);
			RMIServer serverObj = new RMIServer(argv[1]);
			PPSERMIServer stub = (PPSERMIServer) UnicastRemoteObject.exportObject(serverObj, 0);
			//Bind the remote object's stub in the registry
			Registry registry = LocateRegistry.getRegistry(portNum);
			registry.rebind("PPSE", stub);
			
			System.err.println("Server Ready");
		} catch(Exception e){
			System.err.println("Server exception: "+e.toString());
			e.printStackTrace();
		}
	}
	
}
