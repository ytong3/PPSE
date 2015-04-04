package utk.security.PPSE.crypto;
import java.math.BigInteger;
import java.util.List;

import org.jscience.mathematics.number.Complex;

public class BigComplex {
	public BigInteger real;
	public BigInteger img;
	
	//requires that cn has been quantized
	public static BigComplex valueOf(Complex cn){
		return new BigComplex(BigInteger.valueOf((long) cn.getReal()),BigInteger.valueOf((long) cn.getImaginary()));
	}
	public BigComplex(BigInteger real, BigInteger img){
		this.real = real;
		this.img = img;
	}
	
	public BigComplex(){
		real = BigInteger.valueOf(0);
		img = BigInteger.valueOf(0);
	};
	
	public String toString(){
		return real.toString()+","+img.toString();
	}
	
	//FIXME overflow of long type may happen
	public Complex toComplex(){
		//Overflow check
		BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
		//TODO throws an exception, or use other library than jscience
		assert real.compareTo(maxLong)<0&&img.compareTo(maxLong)<0;
		return Complex.valueOf(real.doubleValue(),img.doubleValue());
	}
	
	public static String BigComplexListToString(List<BigComplex> list){
		String res = "";
		for (BigComplex item:list){
			res+=item.toString()+"\n";
		}
		return res;
	}
}
