package utk.security.PPSE.crypto;
import java.util.ArrayList;
import java.util.List;

import org.jscience.mathematics.number.Complex;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * PaillierFFT process a file contains the encrypted measurement data
 * the header of the file contains 
 * numPoint, the windows length, defaults to 4000,
 * overlaps, overlaps of the two adjacent window, defaults to 3900, giving the step size of 100.
 * *** use doDFTOnEncryptedFiles to perform secure computation with encrypted measurement data.
 * @author ytong3
 *
 */
public class PaillierFFT {
	protected Paillier crypto;
	protected long Q1;//Q1 the scaling factor to preserve the digits after the decimal points
	protected long Q2;//Scaling factor to quantize the the Fourier matrix
	protected BigInteger bigQ1;
	protected BigInteger bigQ2;
	protected static int windowLength = 4000; // the window length
	protected static int overlap = 3900; //steps of the sliding window slide each time.
	protected ArrayList<ArrayList<Complex>> DFTLookup;

	//private ArrayList<Integer> quantizationCounter;
	private static final boolean debug = true;

	protected PaillierFFT(long Q1, long Q2, String pubKey){
		crypto = new Paillier(pubKey,true);
		this.Q1 = Q1;
		this.Q2 = Q2;
		bigQ1 = BigInteger.valueOf(Q1);
		bigQ2 = BigInteger.valueOf(Q2);
		buildLookUpTable(windowLength);
	}
	
	private void buildLookUpTable(int windowLength){
		DFTLookup = new ArrayList<ArrayList<Complex> >();
		Complex Wik;;
		Complex ExpWik;
		for (int k=0;k<windowLength;k++)
		{
			ArrayList<Complex> tempRow = new ArrayList<Complex>(windowLength);
			for (int i=0;i<windowLength;i++)
			{
				Wik = Complex.valueOf(0,0-(double)2*k*Math.PI*i/windowLength);
				ExpWik = Wik.exp();
				tempRow.add(ExpWik);
			}
			DFTLookup.add(tempRow);
		}	
	}

	private void _fft(List<Complex> buf, List<Complex> out, int start, int end, int n, int step ){
		if (step<n){
			_fft(out, buf, start, end, n, step*2);
			_fft(out, buf, start+step, end+step,n,step*2);

			for (int i=0;i<n;i+=step*2){
				Complex t = Complex.valueOf(0, Math.PI*i/n);
				t = t.exp().times(out.get(start+i+step));
				//System.out.println("current Element = "+out.get(start+i+step));
				//System.out.println("prod = "+t.toString());
				buf.set(start+i/2, out.get(start+i).plus(t));
				buf.set(start+(i+n)/2, out.get(start+i).minus(t));
			}
		}
	}

	/**
	 * Plain discrete Fourier transform with List<Complext>
	 * @param buf the list to perform DFT with
	 * @param n number of frequency coefficients in the result
	 * @return the frequency coeffcients
	 */
	private List<Complex> dft(List<Complex> buf, int n) {
		List<Complex> spectrum = new ArrayList<Complex>(buf.size());
		for (int k=0;k<n;k++){
			//spectrum.add(Complex.valueOf(0,0));
			Complex tmp = Complex.valueOf(0,0);
			for (int i=0;i<buf.size();i++){
				Complex Wik = Complex.valueOf(0,0-2*k*Math.PI*i/n);
				Wik = Wik.exp();
				tmp = tmp.plus(Wik.times(buf.get(i)));
				//System.out.println("tmp" + tmp);
			}

			spectrum.add(tmp);
		}
		return spectrum;
	}

	/**
	 * privacy preserving DFT enabled by DFT
	 * @param buf the list of encrypted BigComplex
	 * @param n the number of coefficients to compute
	 * @return the encrypted frequency coefficients
	 */
	private List<BigComplex> ppDFT(List<BigComplex> buf, int n) {
		List<BigComplex> encSpectrum = new ArrayList<BigComplex>(buf.size());
		long t0;
		t0 = System.currentTimeMillis();
		for (int k=0;k<10;k++){
			//TODO change the start value to the encryption of some random value to randomize the final result
			//Blinding
			
			BigComplex tmp = new BigComplex(BigInteger.ONE,BigInteger.ONE);
			for (int i=0;i<buf.size();i++){
				Complex Wik = Complex.valueOf(0,0-(double)2*k*Math.PI*i/n);
				Wik = Wik.exp();
				//now quantize Wik
				BigComplex qWik= quantize(Wik,Q2);

				//pull out encrypted buf[i];
				BigComplex currentElement = buf.get(i);
				assert currentElement.img.equals(BigInteger.ZERO);
				//calculate E[a(b+ci)], where a is already encrypted
				BigInteger encProdReal = currentElement.real.modPow(qWik.real, crypto.nsquare);
				BigInteger encProdImg = currentElement.real.modPow(qWik.img, crypto.nsquare);
				//quantizationCounter.set(k, quantizationCounter.get(i).intValue()+1);

				//utilizing the multiplicative property
				tmp.real = tmp.real.multiply(encProdReal).mod(crypto.nsquare);
				tmp.img = tmp.img.multiply(encProdImg).mod(crypto.nsquare);
				//quantizationCounter.set(k, quantizationCounter.get(i).intValue()+1);

			}		
			encSpectrum.add(tmp);
		}
		System.out.println((System.currentTimeMillis()-t0)/10*n);
		return encSpectrum;
	}

	private void fft(List<Complex> buf, int n){
		ArrayList<Complex> out = new ArrayList<Complex>(n);
		for (int i=0;i<n;i++){
			out.add(buf.get(i));
			//System.out.println("out"+i+out.get(i));
		}

		_fft(buf,out,0,buf.size(),n,1);
	}

	public void show(final String s, List<Complex> buf){
		System.out.print(s+": ");
		for (int i=0;i<buf.size();i++)
			System.out.printf("%6.3f+%6.3fi ", buf.get(i).getReal(), buf.get(i).getImaginary());
		System.out.print("\n");
	}

	private Complex dequantize(BigComplex bigComplexNum) {
		BigInteger[] realDqRes = bigComplexNum.real.divideAndRemainder(bigQ1.multiply(bigQ2));
		BigInteger[] imgDqRes = bigComplexNum.img.divideAndRemainder(bigQ1.multiply(bigQ2));

		Complex res = Complex.valueOf(realDqRes[0].doubleValue()+realDqRes[1].doubleValue()/(Q1*Q2), imgDqRes[0].doubleValue()+imgDqRes[1].doubleValue()/(Q1*Q2));
		return res;
	}
	
	/**
	 * A quantization method to convert a complex number to BigComplex by *10000
	 * @param cn The complex number to be converted.
	 */
	protected BigComplex quantize(Complex cn, long scale) {
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

	private Complex decryptBigComplex(BigComplex cn) throws Exception{
		BigComplex decryption = new BigComplex(crypto.Decryption(cn.real),crypto.Decryption(cn.img));
		return dequantize(decryption);
	}

	public ArrayList<Complex> decryptSequence(List<BigComplex> encryptedSeq) throws Exception{
		ArrayList<Complex> res = new ArrayList<Complex>();
		for(BigComplex element: encryptedSeq){
			res.add(decryptBigComplex(element));
		}
		return res;
	}
	
	List<Complex> dft(List<Complex> buf, int n, int fStart, int fEnd) {
		List<Complex> spectrum = new ArrayList<Complex>(buf.size());
		for (int k=0;k<n;k++){
			//spectrum.add(Complex.valueOf(0,0));
			Complex tmp = Complex.valueOf(0,0);
			long tic = System.nanoTime();
			for (int i=fStart;i<fEnd+1;i++){
				Complex Wik = Complex.valueOf(0,0-2*k*Math.PI*i/n);
				Wik = Wik.exp();
				tmp = tmp.plus(Wik.times(buf.get(i)));
				//System.out.println("tmp" + tmp);
			}
			spectrum.add(tmp);
			System.out.println((System.nanoTime()-tic));
		}
		return spectrum;
	}
	
	private List<BigComplex> ppDFT(List<BigComplex> buf, int n, double fStart, double fEnd) {
		List<BigComplex> encSpectrum = new ArrayList<BigComplex>(buf.size());
		
		//double t0 = System.currentTimeMillis();
		int fStartIndex = (int) ((fStart/100)*n);
		int fEndIndex = (int) ((fEnd/100)*n);
		
		// compute only the coeffcients needed
		for (int k=fStartIndex;k<fEndIndex+1;k++){
			//TODO change the initial value to the encryption of some random value to randomize the final result
			//Blinding
			BigComplex tmp = new BigComplex(BigInteger.ONE,BigInteger.ONE);
			
			long tic = System.nanoTime();

			for (int i=0;i<n;i++){
				//now quantize Wik
				BigComplex qWik= quantize(DFTLookup.get(i).get(k),Q2);

				//pull out encrypted buf[i];
				BigComplex currentElement = buf.get(i);
				assert currentElement.img.equals(BigInteger.ZERO);
				//calculate E[a(b+ci)], where a is already encrypted
				BigInteger encProdReal = currentElement.real.modPow(qWik.real, crypto.nsquare);
				BigInteger encProdImg = currentElement.real.modPow(qWik.img, crypto.nsquare);
				//quantizationCounter.set(k, quantizationCounter.get(i).intValue()+1);

				//add to tmp;
				//utilizing the multiplicative property
				tmp.real = tmp.real.multiply(encProdReal).mod(crypto.nsquare);
				tmp.img = tmp.img.multiply(encProdImg).mod(crypto.nsquare);
				//quantizationCounter.set(k, quantizationCounter.get(i).intValue()+1);

			}
			encSpectrum.add(tmp);
			System.out.println(System.nanoTime()-tic);
		}
		//System.out.println((System.currentTimeMillis()-t0));
		return encSpectrum;
	}
	
	/** write a BigComplex list to the file in fileName. The data is appended to the file. 
	 *  all data takes a line, ending with '\n'.
	 * @param fileName output fileName
	 *  @param list the list convert
	 * @throws IOException */
	public static void writeComplexArrayToFile(String fileName, List<BigComplex> list){
		PrintWriter out = null;
		try{
			out = new PrintWriter(new BufferedWriter(new FileWriter(fileName)),true);
			for (BigComplex x:list){
				//output only the real part for visualization
				//this function writes both real and imaginary parts
				out.println(x.real.toString()+","+x.img.toString());
			}
			out.print('\n');
		}catch(IOException e){
			e.printStackTrace(); 
		}finally{
			if(out!=null) out.close();
		}
	}
	
	/**
	 * Perform encryption with encrypteFile with the crypto whose pubKey is given in pubKey
	 * @param encryptedFile path to the encryptedFile
	 * @param stream boolean flag for static file (false), stream data (true)
	 * @return true if the operation is successful, otherwise not
	 */
	public static boolean doDFTOnEncryptedFile(String encryptedFile, String pubKey, double startFreq, double endFreq){
		//read file
		InputStream fis = null;
		BufferedReader br = null;
		
		try {
			//read file
			fis = new FileInputStream(encryptedFile);
			br = new BufferedReader(new InputStreamReader(fis,Charset.defaultCharset()));
			String line =null;
			
			//get configuration line Q1 and Q2
			line = br.readLine();
			if (line==null) throw new FileNotFoundException("File Corrupted. Scaling factor not found in the first line");
			String[] fileParams = line.trim().split(" ");
			//extrac Q1, Q2, numPoint, steps
			long Q1=0, Q2=0;
			for(String str:fileParams){
				String[] param = str.split(":");
				if ("Q1".equalsIgnoreCase(param[0])) Q1=Long.parseLong(param[1]);
				else if ("Q2".equalsIgnoreCase(param[0])) Q2=Long.parseLong(param[1]);
			}
			
			if (Q1==0||Q2==0||windowLength==0||overlap==0) throw new Exception("Bad inialization parameter for PaillierFFT engine");
					
			PaillierFFT engine = new PaillierFFT(Q1, Q2, pubKey);
			List<BigComplex> encryptedMeasurementData = new ArrayList<BigComplex>();
			
			//load all encrypted measurement data into the the list.
			while ((line=br.readLine())!=null){
				String[] tmpStrs = line.split("\t ");
				//tmpStrs[0] is the timestamp
				BigComplex singleEncryptedMeasurement = new BigComplex(new BigInteger(tmpStrs[1]),BigInteger.ZERO);
				encryptedMeasurementData.add(singleEncryptedMeasurement);
			}
			
			//perform FFT over the time window and move the time window
			int startIndex = 0;
			int endIndex = windowLength;
			
			while (endIndex<encryptedMeasurementData.size()){
				String encryptedSpectrumFile = encryptedFile+"_"+startIndex+"_"+endIndex+" "+String.format("%.2f", startFreq)+String.format("%.2f", endFreq);
				List<BigComplex> encryptedSpectrum = engine.ppDFT(encryptedMeasurementData.subList(startIndex, endIndex), windowLength,startFreq,endFreq);
				writeComplexArrayToFile(encryptedSpectrumFile,encryptedSpectrum);
				startIndex+=(windowLength-overlap);
				endIndex+=(windowLength-overlap);
			}
			//perform ppDFT with encryptedMeasurementData
			//write encryptedSpectrum to disk
			return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			try{
				if (fis!=null) fis.close();
				if (br!=null) br.close();
			}
			catch(IOException ex){
			}
		}
		return false;
	}
}
