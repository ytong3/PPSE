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

public class ClientEncryption {
	private Paillier crypto;
	protected long Q1;//Q1 the scaling factor to preserve the digits after the decimal points
	protected long Q2;//Scaling factor to quantize the the Fourier matrix
	protected BigInteger bigQ1;
	protected BigInteger bigQ2;
	
	
	
	public ClientEncryption(String publicKey){
		crypto = new Paillier(publicKey,true);
		Q1 = 10000L;//use default value
		Q2 = 10000L;
	}
	
	/**
	 * The functions takes the source file contains the raw measurement data
	 * encrypt them using configured PaillierFFT engine
	 * finally output the encrypted result to the destinationFile
	 * @param sourceFile
	 * @param destinationFile
	 * @return
	 */
	public boolean encryptRawMeasurement(String sourceFile, String destinationFile){
		//get the buffer to store the raw input vector
		List<Complex> buf = new ArrayList<Complex>();
		
		//open the source file
		try(BufferedReader br = new BufferedReader(new FileReader(sourceFile))){
			String line = null;
			while((line = br.readLine())!=null){
				//each line is time stamp + measurement
				String[] strs = line.split(",");
				buf.add(Complex.valueOf(Double.parseDouble(strs[1]), 0));
			}
			
			//perform encryption
			List<BigComplex> encryptedData = encryptSequence(buf);
			
			//write to file
			//first write the parameters: Q1, Q2
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(destinationFile)));
			out.println("Q1:"+Q1+" "+"Q2:"+Q2);
			//print object in encryptedData to file
			for(BigComplex item: encryptedData){
				out.println(item.toString());
			}
			return true;
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * A quantization method to convert a complex number to BigComplex by *10000
	 * @param cn The complex number to be converted.
	 */
	private static BigComplex quantize(Complex cn, long scale) {
		return new BigComplex(BigInteger.valueOf((long)(cn.getReal()*scale)),BigInteger.valueOf((long)(cn.getImaginary()*scale)));
	}

	private ArrayList<BigComplex> quantize(List<Complex> cnArray){
		ArrayList<BigComplex> res = new ArrayList<BigComplex>();

		for (int i=0;i<cnArray.size();i++){
			res.add(quantize(cnArray.get(i), Q1));
		}
		return res;
	}

	public List<BigComplex> encryptSequence(List<Complex> plainSeq){
		//quantize first
		List<BigComplex> BigPlainSeq = quantize(plainSeq);
		//encrypt using Paillier's crypto
		ArrayList<BigComplex> res = new ArrayList<BigComplex>();
		for (BigComplex element:BigPlainSeq){
			res.add(new BigComplex(crypto.Encryption(element.real),crypto.Encryption(element.img)));
		}
		return res;
	}
}
