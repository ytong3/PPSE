package utk.security.PPSE.slave;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PPSERMIServer extends Remote {
	String executeTask(PPSERemoteTask task) throws RemoteException;
	String checkHealth() throws RemoteException;
}
