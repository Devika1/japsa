/*****************************************************************************
 * Copyright (c) Minh Duc Cao, Monash Uni & UQ, All rights reserved.         *
 *                                                                           *
 * Redistribution and use in source and binary forms, with or without        *
 * modification, are permitted provided that the following conditions        *
 * are met:                                                                  * 
 *                                                                           *
 * 1. Redistributions of source code must retain the above copyright notice, *
 *    this list of conditions and the following disclaimer.                  *
 * 2. Redistributions in binary form must reproduce the above copyright      *
 *    notice, this list of conditions and the following disclaimer in the    *
 *    documentation and/or other materials provided with the distribution.   *
 * 3. Neither the names of the institutions nor the names of the contributors*
 *    may be used to endorse or promote products derived from this software  *
 *    without specific prior written permission.                             *
 *                                                                           *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS   *
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR         *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR        *
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF    *
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING      *
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS        *
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.              *
 ****************************************************************************/

/**************************     REVISION HISTORY    **************************
 * 25/08/2014 - Minh Duc Cao: Created                                        
 *  
 ****************************************************************************/

package japsa.seq.tools;

import japsa.seq.JapsaAnnotation;
import japsa.seq.JapsaFeature;
import japsa.seq.SequenceOutputStream;
import japsa.seq.XAFReader;
import japsa.util.CommandLine;
import japsa.util.deploy.Deployable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

/**
 * @author minhduc
 *
 */
@Deployable(scriptName = "jsa.seq.annovcf", scriptDesc = "Annotate variation from a vcf file using annotation from gff file ")
public class AnnotateVCF {
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		/*********************** Setting up script ****************************/
		Deployable annotation = AnnotateVCF.class.getAnnotation(Deployable.class);		 		
		CommandLine cmdLine = new CommandLine("\nUsage: " + annotation.scriptName() + " [options]", annotation.scriptDesc());		
		/**********************************************************************/		

		cmdLine.addString("gffin", "-",  "GFF file");
		cmdLine.addInt("upstream", 0, "Add upstream ");
		cmdLine.addInt("downstream", 0, "Add downstream ");		
		cmdLine.addString("output", "-", "Name of output file, - for standard out");
		cmdLine.addString("vcf", null, "Name of vcf file", true);
		
		args = cmdLine.stdParseLine(args);	
		/**********************************************************************/
		String gffFile   =  cmdLine.getStringVal("gffin");
		String output  =  cmdLine.getStringVal("output");
		String vcf  =  cmdLine.getStringVal("vcf");
		
		int upStr   = cmdLine.getIntVal("upstream");
		int downStr   = cmdLine.getIntVal("downstream");
		
		
		//BufferedReader in = SequenceReader.openFile(gffIn);
		
		//JapsaAnnotation anno = JapsaAnnotation.readGFF(in, upStr, downStr,"all");		
		//in.close();
		
		
		FileInputStream gffIn = new FileInputStream(gffFile);
		HashMap<String, JapsaAnnotation>
		annoMap = JapsaAnnotation.readMGFF(gffIn,upStr,downStr,"CDS");
		gffIn.close();		
		
		
		SequenceOutputStream out =  SequenceOutputStream.makeOutputStream(output);
		//for (JapsaFeature feature : anno.getFeatureList()){
		//	out.print(feature.getLength() + " " + feature.getType() + " " + feature.getID() + " " + feature.getParent());
		//	out.println();
		//}		
		//out.close();
		
		JapsaAnnotation anno = null;
		XAFReader xaf = new XAFReader(vcf);		
		while (xaf.next() != null){
			//System.out.println(xaf.recordNo());			
			String [] toks = xaf.getCurrentRecord().split("\t");	
			int pos = Integer.parseInt(xaf.getField(1));			
			String chrom = xaf.getField(0);			
			out.print(toks[0] + "\t" + toks[1] + "\t" + toks[3] + "\t" + toks[4]);
			for (int i = 9; i < toks.length; i++){
				out.print("\t" + toks[i].charAt(0));
			}
			
			
			if (anno == null || !anno.getAnnotationID().equals(chrom))
				anno = annoMap.get(chrom);		
			if (anno != null){
				//System.out.println(anno.getAnnotationID() + " : " + anno.numFeatures() + " " + pos);
				for (int i =0; i < anno.numFeatures(); i++){
					JapsaFeature feature = anno.getFeature(i);
					//System.out.println("====" + feature.getStart() + " -- " + feature.getEnd());
					if (feature.getStart() <= pos && feature.getEnd() >= pos){
						out.print("\t" + feature.getID() + "(" + feature.getStart()+"," + feature.getEnd()+")");
						String [] tt = feature.getDesc().split(";|\n");
						for (String t:tt){
							if (t.startsWith("product="))
								out.print(";" + t);
						}
						
						
					}else if (feature.getStart() > pos)
						break;//for					
				}
			}
			out.println();
		}
		
		xaf.close();		
		out.close();
	}
	
	

}
