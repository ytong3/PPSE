package utk.security.PPSE.slave;

import java.util.List;

import utk.security.PPSE.crypto.BigComplex;

public interface HomomorphicDFT {
	/**
	 * Perform DFT of the whole spectrum using homomorphic encryption using given the encrypted time series in inputFile, and public key in public key file
	 * @param inputList contains exactly one time window worth of data
	 * @param PubKeyFile
	 * @return the path to the encryted output if successful or "FAILURE" if unsuccessful
	 */
	 List<BigComplex> homoDFT(List<BigComplex> inputList);
	
	/**
	 * Perform DFT of a certain frequency range between freqStart and freqEnd
	 * @param inputList contains exactly one time window worth of encrypted data
	 * @param PubKeyFile
	 * @param freqStart
	 * @param freqEnd
	 * @return the path to the encryted output if successful or "FAILURE" if unsuccessful
	 */
	List<BigComplex> homoDFT(List<BigComplex> inputList, double freqStart, double freqEnd);
	
	
}
