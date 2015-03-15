package utk.security.PPSE.slave;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PSSERMIServer extends Remote {
	String executeTask(PSSERemoteTask task) throws RemoteException;
	String checkHealth() throws RemoteException;
}
