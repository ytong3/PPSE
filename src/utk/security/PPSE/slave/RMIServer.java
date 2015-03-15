package utk.security.PPSE.slave;

import java.rmi.RemoteException;

/**
 * RMI server that performs Paillier Cryptographic computation
 * @author ytong3
 *
 */
public class RMIServer implements PSSERMIServer{
	private PaillierFFT crypto;
	private String pubKeyFile;
	
	public RMIServer(){
		super();
		//initialize the crypto
		
	}

	
	
	@Override
	public String executeTask(PSSERemoteTask task) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String checkHealth() throws RemoteException {
		// TODO Auto-generated method stub
		return "GOOD";
	}
	
	public static void main(String[] argv){
		
	}
	
}
