package utk.security.PPSE.crypto;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.jscience.mathematics.number.Complex;

public class ClientDecryption {
	private PaillierFFT crypto;
	protected long Q1;//Q1 the scaling factor to preserve the digits after the decimal points
	protected long Q2;//Scaling factor to quantize the the Fourier matrix
	
	public ClientDecryption(String privateKey){
		Q1 = 10000L;
		Q2 = 10000L;
		crypto = new PaillierFFT(Q1,Q2,privateKey,false);
	}
	
	public boolean decryptEncryptedData(String sourceFile, String destinationFile, boolean MeasurementData){
		//get the buffer to store the raw input vector
		List<BigComplex> buf = new ArrayList<BigComplex>();
		
		//open the source file
		BufferedReader br = null;
		try{
			br = new BufferedReader(new FileReader(sourceFile));
			String line = null;
			line = br.readLine();
			if (line!=null){
				//process Q1 and Q2
				String[] strs = line.split(" ");
				if (Q1!=Long.parseLong(strs[0].split(":")[1])||Q2!=Long.parseLong(strs[1].split(":")[1])){
					System.out.println("Crypto Q1 or Q2 does not match with the Q1 and Q2 in the encrypted file");
					System.out.println("Decryption failed");
					return false;
				}
			}
			while((line = br.readLine())!=null){
				//each line is time stamp + measurement or just the measurement
				String[] strs = line.split(",");
				if (strs.length>2)
					buf.add(new BigComplex(new BigInteger(strs[1]),new BigInteger(strs[2])));
				else{
					buf.add(new BigComplex(new BigInteger(strs[0]),new BigInteger(strs[1])));
				}	
			}
			
			//perform decryption
			List<Complex> decryptedData = null;
			if (MeasurementData){
				//multiply Q2 since it the encrypted is just the measurement data, Q2 is not involded.
				//but crypto.decryptSequence divide the immediate decryption of Paillier crypto by Q1 and Q2
				decryptedData = crypto.decryptMeasurementSequence(buf);
			}
			else {
				decryptedData = crypto.decryptFourierSequence(buf);
			}

			
			//write to file
			//first write the parameters: Q1, Q2
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(destinationFile)));
			//print object in encryptedData to file
			for(Complex item: decryptedData){
				//System.out.println(item.toString());
				
				out.println(item.toString());
			}
			out.close();
			return true;
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			try {
				br.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return false;
	}
	
	public static void main(String[] argv){
		ClientDecryption cd = new ClientDecryption("testPrivteKey.pri");
		cd.decryptEncryptedData("original_angles_4000samples.csv.enc_encryptedSpectrum.out", "original_angles_4000samples.csv.decryptedSepctrum",false);
	}
}
