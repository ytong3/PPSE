package utk.security.PPSE.slave;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import utk.security.PPSE.crypto.BigComplex;

public class Common {
	public static String makeOutputFilename(String inputFileName, double freqStart, double freqEnd){
		return inputFileName+String.format("Frequency[%.3f,%.3f]",freqStart,freqEnd)+".out";
	}
	
	
	/** write a BigComplex list to the file in fileName. The data is appended to the file. 
	 *  all data takes a line, ending with '\n'.
	 * @param fileName output fileName
	 *  @param list the list convert
	 * @throws IOException 
	 * */
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
}
