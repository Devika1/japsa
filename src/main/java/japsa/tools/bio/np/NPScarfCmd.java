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
 * 30/06/2014 - Minh Duc Cao: Created                                        
 *  
 ****************************************************************************/

package japsa.tools.bio.np;

import japsa.bio.hts.scaffold.ContigBridge;
import japsa.bio.hts.scaffold.RealtimeScaffolding;
import japsa.bio.hts.scaffold.ScaffoldGraph;
import japsa.bio.hts.scaffold.ScaffoldGraphDFS;
import japsa.seq.SequenceReader;
import japsa.util.CommandLine;
import japsa.util.deploy.Deployable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author sonnguyen, minhduc
 * 
 */
@Deployable(
		scriptName = "jsa.np.npscarf",
		scriptDesc = "Experimental Scaffold and finish assemblies using Oxford Nanopore sequencing reads",
		seeAlso = "jsa.np.npreader, jsa.util.streamServer, jsa.util.streamClient"
		)
public class NPScarfCmd extends CommandLine{
	private static final Logger LOG = LoggerFactory.getLogger(NPScarfCmd.class);

	public NPScarfCmd(){
		super();
		Deployable annotation = getClass().getAnnotation(Deployable.class);		
		setUsage(annotation.scriptName() + " [options]");
		setDesc(annotation.scriptDesc());

		addString("seqFile", null, "Name of the assembly file (sorted by length)",true);

		addString("input", "-", "Name of the input file, - for stdin", true);
		addString("format", "sam", "Format of the input: fastq/fasta or sam/bam", true);
		addBoolean("index", true, "Whether to index the contigs sequence by the aligner or not.");
		//TODO: adding minimap2 as default aligner
		//options: -aligner <bwa|minimap2> ; -command "customized cmd for alignment"
		addString("bwaExe", "bwa", "Path to bwa");
		addInt("bwaThread", 4, "Theads used by bwa");
		addBoolean("long", false, "Whether report all sequences, including short/repeat contigs (default) or only long/unique/completed sequences.");
		
		addString("spadesDir", null, "Name of the output folder by SPAdes: assembly graph and paths will be used for better gap-filling.");
		addString("prefix", "out", "Prefix for the output files");	
		addString("genes", null , "Realtime annotation: name of annotated genes in GFF 3.0 format");
		addString("resistGene", null , "Realtime annotation: name of antibiotic resistance gene fasta file");
		addString("insertSeq", null , "Realtime annotation: name of IS fasta file");
		addString("oriRep", null, "Realtime annotation: name of fasta file containing possible origin of replication");
		//addInt("marginThres", 1000, "Margin threshold: to limit distance to the contig's ends of the alignment used in bridging."); 
		addInt("minContig", 300, "Minimum contigs length that are used in scaffolding."); 
		addInt("maxRepeat", 7500, "Maximum length of repeat in considering species."); 

		addDouble("cov", 0, "Expected average coverage of Illumina, <=0 to estimate");
		addInt("qual", 1, "Minimum quality");
		addInt("support", 1, "Minimum supporting long read needed for a link between markers");

		addBoolean("realtime", false, "Process in real-time mode. Default is batch mode (false)");
		addInt("read", 50,  "Minimum number of reads between analyses");		
		addInt("time", 10,   "Minimum number of seconds between analyses");
		addBoolean("verbose", false, "Turn on debugging mode");

		addStdHelp();		

	} 	
	//static boolean hardClip = false;

	public static void main(String[] args) throws 
	IOException, InterruptedException {
		CommandLine cmdLine = new NPScarfCmd();		
		args = cmdLine.stdParseLine(args);

		/***********************************************************************/
		String prefix = cmdLine.getStringVal("prefix");
		//String bamFile = cmdLine.getStringVal("bamFile");

		String input = cmdLine.getStringVal("input");
		String bwaExe = cmdLine.getStringVal("bwaExe");
		int bwaThread = cmdLine.getIntVal("bwaThread");
		String format = cmdLine.getStringVal("format").toLowerCase();

		String sequenceFile = cmdLine.getStringVal("seqFile"),
				spadesFolder = cmdLine.getStringVal("spadesDir"),

				genesFile = cmdLine.getStringVal("genes"),
				resistFile = cmdLine.getStringVal("resistGene"),
				isFile = cmdLine.getStringVal("insertSeq"),
				oriFile = cmdLine.getStringVal("oriRep");


		File 	graphFile = new File(spadesFolder+"/assembly_graph.fastg"),
				pathFile = new File(spadesFolder+"/contigs.paths");


		if (format.startsWith("fastq") ||
				format.startsWith("fasta") ||
				format.startsWith("fq") ||
				format.startsWith("fa")){
			try{
				ProcessBuilder pb = new ProcessBuilder(bwaExe).redirectErrorStream(true);
				Process process =  pb.start();
				//BWA process doesn't produce gzip-compressed output
				BufferedReader bf = SequenceReader.openInputStream(process.getInputStream());


				String line;
				String version = "";
				Pattern versionPattern = Pattern.compile("^Version:\\s(\\d+\\.\\d+\\.\\d+).*");
				Matcher matcher=versionPattern.matcher("");
				
				while ((line = bf.readLine())!=null){				
					matcher.reset(line);
					if (matcher.find()){
					    version = matcher.group(1);
					    break;//while
					}
					
									
				}	
				bf.close();
				
				if (version.length() == 0){
					LOG.error(bwaExe + " is not the right path to bwa. bwa is required");
					System.exit(1);
				}else{
					LOG.info("bwa version: " + version);
					if (version.compareTo("0.7.11") < 0){
						LOG.error(" Require bwa of 0.7.11 or above");
						System.exit(1);
					}
				}
				
				//run indexing 
				if(cmdLine.getBooleanVal("index")){
					LOG.info("bwa index running...");
					ProcessBuilder pb2 = new ProcessBuilder(bwaExe,"index",sequenceFile);
					Process indexProcess =  pb2.start();
					indexProcess.waitFor();
					LOG.info("bwa index finished!");
				}
			}catch (IOException e){
				System.err.println(e.getMessage());
				System.exit(1);
			}

		}else if (format.startsWith("sam") || format.startsWith("bam")){
			// no problem
		}else{
			LOG.error("Unrecognized format: " + format);
			System.exit(1);
		}

		if(spadesFolder !=null && graphFile.exists() && pathFile.exists())
			LOG.info("===> Use assembly graph and path from SPAdes!");
		else{
			LOG.warn("Not found any legal SPAdes output folder, assembly graph thus not included!");
			spadesFolder=null;
		}

		//ProcessBuilder pb = new ProcessBuilder(bwaExe,  
		//		"mem",
		//		
		//		"-k", "21,33,55,77,99,127", 
		//		"--careful",
		//		"--pe1-1", String.valueOf(sampleRecord.readFile1),
		//		"--pe1-2", String.valueOf(sampleRecord.readFile2),
		//		"-m", "60",
		//		"-t", String.valueOf(threads),
		//		"-o", assemblyPath + "spades_output_" + sampleID
		//		);


		int 	//marginThres = cmdLine.getIntVal("marginThres"),
		minContig = cmdLine.getIntVal("minContig"),
		minSupport = cmdLine.getIntVal("support"),
		maxRepeat = cmdLine.getIntVal("maxRepeat");
		//if(marginThres < 0)
		//	LOG.exit("Marginal threshold must not be negative", 1);
		if(minContig <= 0) {
			LOG.error("Minimum contig length has to be positive");
			System.exit(1);

		}if(minSupport <= 0) {
			LOG.error("Minimum supporting reads has to be positive");
			System.exit(1);
		}
		if(maxRepeat <= 0) {
			LOG.error("Maximal possible repeat length has to be positive", 1);
		}


		ScaffoldGraph.minContigLength = minContig;
		ScaffoldGraph.minSupportReads = minSupport;	
		ScaffoldGraph.maxRepeatLength = maxRepeat;		
		//ScaffoldGraph.marginThres = marginThres;
		ScaffoldGraph.verbose = cmdLine.getBooleanVal("verbose");
		ScaffoldGraph.reportAll = !cmdLine.getBooleanVal("long");

		double cov = cmdLine.getDoubleVal("cov");
		int qual = cmdLine.getIntVal("qual");
		if(qual < 0) {
			LOG.error("Phred score of quality has to be positive");
			System.exit(1);
		}

		int number = cmdLine.getIntVal("read"),
				time = cmdLine.getIntVal("time");

		if(number <= 0) {
			LOG.error("Number of reads has to be positive");
			System.exit(1);
		}
		if(time < 0) {
			LOG.error("Sleeping time must not be negative");
			System.exit(1);
		}
		/**********************************************************************/

		ScaffoldGraph graph;
		boolean rt = cmdLine.getBooleanVal("realtime");
		ContigBridge.relaxFilling();
		if(rt){
			RealtimeScaffolding rtScaffolding = new RealtimeScaffolding(sequenceFile, genesFile, resistFile, isFile, oriFile, "-");

			graph = rtScaffolding.graph;
			if(prefix != null)
				graph.prefix = prefix;
			if(spadesFolder!=null)
				synchronized(graph){
					graph.readMore(spadesFolder+"/assembly_graph.fastg",spadesFolder+"/contigs.paths");
				}
			if (cov <=0)
				cov = ScaffoldGraph.estimatedCov;

			rtScaffolding.scaffolding2(input, number, time, cov/1.6, qual, format, bwaExe, bwaThread, sequenceFile);

		}
		else{
			graph = new ScaffoldGraphDFS(sequenceFile, genesFile, resistFile, isFile, oriFile);
			if(spadesFolder!=null)
				graph.readMore(spadesFolder+"/assembly_graph.fastg",spadesFolder+"/contigs.paths");

			if (cov <=0)
				cov = ScaffoldGraph.estimatedCov;

			graph.makeConnections2(input, cov / 1.6, qual, format, bwaExe, bwaThread, sequenceFile);

			graph.connectBridges();
			if(prefix != null)
				graph.prefix = prefix;

			ContigBridge.forceFilling();
			graph.printSequences();
		}

	}
}

/*RST*
----------------------------------------------------------------------------------
*npScarf*: real-time scaffolder using SPAdes contigs and Nanopore sequencing reads
----------------------------------------------------------------------------------

*npScarf* (jsa.np.npscarf) is a program that connect contigs from a draft genomes 
to generate sequences that are closer to finish. These pipelines can run on a single laptop
for microbial datasets. In real-time mode, it can be integrated with simple structural 
analyses such as gene ordering, plasmid forming.

*npScarf* is included in the `Japsa package <http://mdcao.github.io/japsa/>`_.

<usage>

~~~~~~~~~~~~~~
Usage examples
~~~~~~~~~~~~~~

A summary of *npScarf* usage can be obtained by invoking the --help option::

    jsa.np.npscarf --help

Input
=====
 *npScarf* takes two files as required input::

	jsa.np.npscarf -seq <draft> -input <nanopore>

<*draft*> input is the FASTA file containing the pre-assemblies. Normally this 
is the output from running SPAdes on Illumina MiSeq paired end reads.

<*nanopore*> is either the long reads in FASTA/FASTQ file or SAM/BAM formated alignments 
between them to <*draft*> file. We use BWA-MEM as the recommended aligner 
with the fixed parameter set as follow::

	bwa mem -k11 -W20 -r10 -A1 -B1 -O1 -E1 -L0 -a -Y <draft> <nanopore> > <bam>
	
The input file format is specified by option --format. The default is FASTA/FASTQ in which 
the path to BWA version 0.7.11 or newer is required. Remember to always *INDEXING* the 
reference before running BWA::
	
	bwa index <draft>
	
Missing this step would break down the whole pipeline.

Output
=======
*npScarf* output is specified by *-prefix* option. The default prefix is \'out\'.
Normally the tool generate two files: *prefix*.fin.fasta and *prefix*.fin.japsa which 
indicate the result scaffolders in FASTA and JAPSA format.

In realtime mode, if any annotation analysis is enabled, a file named 
*prefix*.anno.japsa is generated instead. This file contains features detected after
scaffolding.

Real-time scaffolding
=====================
To run *npScarf* in streaming mode::

   	jsa.np.npscarf -realtime [options]

In this mode, the <*bam*> file will be processed block by block. The size of block 
(number of BAM/SAM records) can be manipulated through option *-read* and *-time*.

The idea of streaming mode is when the input <*nanopore*> file is retrieved in stream.
npReader is the module that provides such data from fast5 files returned from the real-time
base-calling cloud service Metrichor. Ones can run::

    jsa.np.npreader -realtime -folder c:\Downloads\ -fail -output - | \
      jsa.np.npscarf --realtime -bwaExe=<path_to_BWA> -bwaThread=10 -input - -seq <draft> > log.out 2>&1
    
For the same purpose, you can also invoke BWA-MEM explicitly as in the old version of *npScarf*,
In this case, option --format=SAM must be presented as follow:
      
    jsa.np.npreader -realtime -folder c:\Downloads\ -fail -output - | \
      bwa mem -t 10 -k11 -W20 -r10 -A1 -B1 -O1 -E1 -L0 -a -Y -K 3000 <draft> - 2> /dev/null | \ 
      jsa.np.npscarf --realtime -input - -format=SAM -seq <draft> > log.out 2>&1

or if you have the whole set of Nanopore long reads already and want to emulate the 
streaming mode::

    jsa.np.timeEmulate -s 100 -i <nanopore> -output - | \
      jsa.np.npscarf --realtime -bwaExe=<path_to_BWA> -bwaThread=10 -input - -seq <draft> > log.out 2>&1

Note that jsa.np.timeEmulate based on the field *timestamp* located in the read name line to
decide the order of streaming data. So if your input <*nanopore*> already contains the field,
you have to sort it::

    jsa.seq.sort -i <nanopore> -o <nanopore-sorted> -sortKey=timestamp

or if your file does not have the *timestamp* data yet, you can manually make ones. For example::

    cat <nanopore> | \
       awk 'BEGIN{time=0.0}NR%4==1{printf "%s timestamp=%.2f\n", $0, time; time++}NR%4!=1{print}' \
       > <*nanopore-with-time*> 

Real-time annotation
====================

The tool includes usecase for streaming annotation. Ones can provides database of antibiotic
resistance genes and/or Origin of Replication in FASTA format for the analysis of gene ordering
and/or plasmid identifying respectively::

    jsa.np.timeEmulate -s 100 -i <nanopore> -output - | \ 
      jsa.np.npscarf --realtime -bwaExe=<path_to_bwa> -input - -seq <draft> -resistGene <resistDB> -oriRep <origDB> > log.out 2>&1

Assembly graph
==============

*npScarf* can read the assembly graph info from SPAdes to make the results more precise.
The results might be slightly deviate from the old version in term of number of final contigs::

    jsa.np.npscarf --spadesFolder=<SPAdes_output_directory> <options...>

where SPAdes_output_directory indicates the result folder of SPAdes, containing files such as contigs.fasta, 
contigs.paths and assembly_graph.fastg.
 *RST*/
