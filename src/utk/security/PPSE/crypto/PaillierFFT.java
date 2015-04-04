package utk.security.PPSE.crypto;
import java.util.ArrayList;
import java.util.List;

import org.jscience.mathematics.number.Complex;

import utk.security.PPSE.slave.HomomorphicDFT;

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
public class PaillierFFT implements HomomorphicDFT{
	protected Paillier crypto;
	protected long Q1;//Q1 the scaling factor to preserve the digits after the decimal points
	protected long Q2;//Scaling factor to quantize the the Fourier matrix
	protected double samplingRate = 100; //Hz 
	protected BigInteger bigQ1;
	protected BigInteger bigQ2;
	protected static int windowLength = 4000; // the window length
	protected static int overlap = 3900; //steps of the sliding window slide each time.
	protected ArrayList<ArrayList<Complex>> DFTLookup;
	private boolean mode;

	//private ArrayList<Integer> quantizationCounter;
	private static final boolean debug = true;

	public PaillierFFT(long Q1, long Q2, String keyFile, boolean keyType){
		crypto = new Paillier(keyFile,keyType);
		mode = keyType;//true for encrytion, false for decryption
		this.Q1 = Q1;
		this.Q2 = Q2;
		bigQ1 = BigInteger.valueOf(Q1);
		bigQ2 = BigInteger.valueOf(Q2);
		buildLookUpTable(windowLength);
	}
	
	private void buildLookUpTable(int windowLength){
		DFTLookup = new ArrayList<ArrayList<Complex> >();
		Complex Wik;
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
		for (int k=0;k<n;k++){
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
		System.out.println("Runtime for computing one frequency coefficient "+(System.currentTimeMillis()-t0)/10*n);
		return encSpectrum;
	}

	private List<BigComplex> ppDFT(List<BigComplex> buf, int n, double fStart, double fEnd) {
		List<BigComplex> encSpectrum = new ArrayList<BigComplex>(buf.size());
		
		//double t0 = System.currentTimeMillis();
		int fStartIndex = (int) ((fStart/samplingRate)*n);
		int fEndIndex = (int) ((fEnd/samplingRate)*n);
		
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
	
	private Complex dequantizeMeasurementData(BigComplex bigComplexNum){
		BigInteger[] realDqRes = bigComplexNum.real.divideAndRemainder(bigQ1);
		BigInteger[] imgDqRes = bigComplexNum.img.divideAndRemainder(bigQ1);

		Complex res = Complex.valueOf(realDqRes[0].doubleValue()+realDqRes[1].doubleValue()/(Q1), imgDqRes[0].doubleValue()+imgDqRes[1].doubleValue()/(Q1));
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
			System.out.println(res.get(res.size()-1));
		}
		return res;
	}

	private Complex decryptBigComplex(BigComplex cn, boolean rawMeasurement) throws Exception{
		BigComplex decryption = new BigComplex(crypto.Decryption(cn.real),crypto.Decryption(cn.img));
		if (rawMeasurement)
			return dequantizeMeasurementData(decryption);
		return dequantize(decryption);
	}

	public ArrayList<Complex> decryptFourierSequence(List<BigComplex> encryptedSeq) throws Exception{
		if (mode==true){
			System.err.println("Cannot perform decryption in the Encryption mode");
			return null;
		}
		ArrayList<Complex> res = new ArrayList<Complex>();
		for(BigComplex element: encryptedSeq){
			res.add(decryptBigComplex(element,false));
		}
		return res;
	}
	
	public ArrayList<Complex> decryptMeasurementSequence(List<BigComplex> encryptedSeq) throws Exception{
		if (mode==true){
			System.err.println("Cannot perform decryption in the Encryption mode");
			return null;
		}
		ArrayList<Complex> res = new ArrayList<Complex>();
		for(BigComplex element: encryptedSeq){
			BigComplex decryption = new BigComplex(crypto.Decryption(element.real),crypto.Decryption(element.img));
			res.add(decryptBigComplex(element,true));
		}
		return res;
	}
	
	public static List<Complex> dft(List<Complex> buf, int n, int fStart, int fEnd) {
		List<Complex> spectrum = new ArrayList<Complex>(buf.size());
		for (int k=fStart;k<fEnd+1;k++){
			//spectrum.add(Complex.valueOf(0,0));
			Complex tmp = Complex.valueOf(0,0);
			//long tic = System.nanoTime();
			for (int i=0;i<n;i++){
				Complex Wik = Complex.valueOf(0,(double)-Math.PI*2*k*i/n);
				Wik = Wik.exp();
				tmp = tmp.plus(Wik.times(buf.get(i)));
				//System.out.println("tmp" + tmp);
			}
			spectrum.add(tmp);
		}
		return spectrum;
	}
	
	@Override
	public List<BigComplex> homoDFT(List<BigComplex> inputList) {
		return ppDFT(inputList,windowLength,0,samplingRate);
	}

	@Override
	public List<BigComplex> homoDFT(List<BigComplex> inputList, double freqStart, double freqEnd) {
		return ppDFT(inputList,windowLength,freqStart,freqEnd);
	}
	
	public static List<Complex> readFromFile(String sourceFile){
		//read from file
		List<Complex> buf = new ArrayList<Complex>();
		//open the source file
		BufferedReader br = null;
		try{
			br = new BufferedReader(new FileReader(sourceFile));
			String line = null;
			while((line = br.readLine())!=null){
				//each line is time stamp + measurement or just the measurement
				//or just the measurement
				String[] strs = line.split(",");
				if (strs.length>1)
					buf.add(Complex.valueOf(Double.parseDouble(strs[1]), 0));
				else{
					buf.add(Complex.valueOf(Double.parseDouble(strs[0]), 0));
				}	
			}
			return buf;
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		} finally{
			try {
				br.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public static void main(String[] argv){
		List<Complex> rawSequence = readFromFile("original_angles_4000samples.csv");
		List<Complex> spectrum = dft(rawSequence,4000,(int)((0.1/100)*4000),(int)((1.0/100)*4000));
		System.out.println(spectrum);
	}
}
