/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.tools.clusterquantiser;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.kohsuke.args4j.CmdLineException;
import org.openimaj.data.RandomData;
import org.openimaj.feature.local.list.FileLocalFeatureList;
import org.openimaj.image.feature.local.keypoints.Keypoint;
import org.openimaj.io.IOUtils;
import org.openimaj.ml.clustering.CentroidsProvider;
import org.openimaj.ml.clustering.SpatialClusterer;
import org.openimaj.tools.clusterquantiser.ClusterQuantiser;
import org.openimaj.tools.clusterquantiser.ClusterQuantiserOptions;
import org.openimaj.tools.clusterquantiser.ClusterType;
import org.openimaj.tools.clusterquantiser.ClusterType.ClusterTypeOp;
import org.openimaj.tools.clusterquantiser.Precision;
import org.openimaj.util.array.ByteArrayConverter;

/**
 * Tests for {@link ClusterType}
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 */
public class ClusterTypeTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	private int nterms;
	private int nDocs;
	private int[][] data;
	private File inputFile;
	private String[] inputKeyFiles;

	/**
	 * Setup tests
	 */
	@Before
	public void setup() {
		nterms = 100;
		nDocs = 100;
		data = new int[nDocs][];
		for(int i = 0; i < nDocs; i ++ ){
			data[i] = new int[nterms];
			for(int j = 0; j < nterms; j++){
				data[i][j] = new Random().nextInt();
			}
		}
		String[] inputKeySets = new String[]{
				"ukbench00000.key",
				"ukbench00001.key",
				"ukbench00002.key",
				"ukbench00003.key",
				"ukbench00004.key"
		};
		
		inputKeyFiles = new String[inputKeySets.length];
		
		try {
			inputFile = folder.newFile("inputFile.txt");
			PrintWriter pw = new PrintWriter(new FileOutputStream(inputFile));
			
			int i = 0;
			for(String keyFile : inputKeySets){
				try
				{
					File f = new File(
						new URI(this.getClass().getResource("keys/"+keyFile).toString()).getPath());
					pw.println(f.getAbsolutePath());
					inputKeyFiles[i++] = f.getAbsolutePath();
				}
				catch( URISyntaxException e )
				{
					e.printStackTrace();
				}
			}
			pw.flush();
			pw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Test k-means init
	 * @throws Exception
	 */
	@Test public void testFastKMeansInit() throws Exception{
//		File randomFile = File.createTempFile("randomset", ".voc");
//		File fkmeansFile = File.createTempFile("fastkmeans", ".voc");
//		String[] intClusterArgs = new String[]{
//				"-ct","RANDOMSET",
//				"-c",randomFile.getAbsolutePath(),
//				"-t","LOWE_KEYPOINT_ASCII",
//				"-s","5",
//				"-k","5",
//				"-rs","1",
//				"-crs","1",
//				"-p","BYTE",
//				inputKeyFiles[0],inputKeyFiles[1],inputKeyFiles[2]};
////		ClusterQuantiserOptions intClusterCop = ClusterQuantiser.mainOptions(intClusterArgs);
////		Cluster<?> intCluster = ClusterQuantiser.do_create(intClusterCop);
////		intClusterArgs = new String[]{
////				"-ct","FASTKMEANS",
////				"-c",fkmeansFile.getAbsolutePath(),
////				"-t","LOWE_KEYPOINT_ASCII",
////				"-s","5",
////				"-k","5",
////				"-rs","1",
////				"-crs","1",
////				"-p","BYTE",
////				"-cin","RANDOMSETCLUSTER",
////				"-rss",randomFile.getAbsolutePath(),
////				inputKeyFiles[0],inputKeyFiles[1],inputKeyFiles[2]};
////		intClusterCop = ClusterQuantiser.mainOptions(intClusterArgs);
////		intCluster = ClusterQuantiser.do_create(intClusterCop);
//		intClusterArgs = new String[]{
//				"-ct","FASTKMEANS",
//				"-c",fkmeansFile.getAbsolutePath(),
//				"-t","LOWE_KEYPOINT_ASCII",
//				"-bs","-sf","/Volumes/memling/ukbench/samples/batch-samples-ALL-sift-intensity.samples",
//				"-k","10",
//				"-rs","1",
//				"-crs","1",
//				"-p","BYTE",
//				"-cin","RANDOMSETCLUSTER",
//				"-rss","/Volumes/memling/ukbench/newcodebooks/10/sift-intensity/randomsetbyte.voc",
//				inputKeyFiles[0],inputKeyFiles[1],inputKeyFiles[2]};
//		ClusterQuantiserOptions intClusterCop = ClusterQuantiser.mainOptions(intClusterArgs);
//		Cluster<?> intCluster = ClusterQuantiser.do_create(intClusterCop);
	}
	
	/**
	 * Test precisions
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws CmdLineException
	 */
	@SuppressWarnings("unchecked")
	@Test public void testClusterTypePrecision() throws IOException, InterruptedException, CmdLineException{
		// given the same random seed all clusters should quantise to the same value regardless of precision
		List<Keypoint> kpl = FileLocalFeatureList.read(new File(inputKeyFiles[0]), Keypoint.class);
		for(ClusterType ct : ClusterType.values()){
			if(!ct.equals(ClusterType.FASTKMEANS) && !ct.equals(ClusterType.RANDOM) && !ct.equals(ClusterType.RANDOMSET) ) continue;
			System.out.println ("TESTING CLUSTER TYPE: " + ct);
			File tempFile = folder.newFile("cluster"+ct+".voc");
			String[] intClusterArgs = new String[]{
					"-ct",ct.toString(),
					"-c",tempFile.getAbsolutePath(),
					"-t","LOWE_KEYPOINT_ASCII",
					"-s","5",
					"-k","5",
					"-rs","1",
					"-crs","1",
					"-p", "INT",
					inputKeyFiles[0],inputKeyFiles[1],inputKeyFiles[2]};
			ClusterQuantiserOptions intClusterCop = ClusterQuantiser.mainOptions(intClusterArgs);
			SpatialClusterer<?,?> intCluster = ClusterQuantiser.do_create(intClusterCop);

			int[][] intClusterCenters = ((CentroidsProvider<int[]>) intCluster).getCentroids();
			
			for(Precision p : Precision.values()){
				if(!p.equals(Precision.BYTE) && !p.equals(Precision.INT) ) continue;
				System.out.println ("TESTING PRECISION: " + p);
				File precTempFile = folder.newFile("preccluster"+ct+"-"+p+".voc");
				String[] precClusterArgs = new String[]{
						"-ct",ct.toString(),
						"-c",precTempFile.getAbsolutePath(),
						"-t","LOWE_KEYPOINT_ASCII",
						"-s","5",
						"-k","5",
						"-rs","1",
						"-crs","1",
						"-p",p.toString(),
						inputKeyFiles[0],inputKeyFiles[1],inputKeyFiles[2]};
				ClusterQuantiserOptions precisionClusterCop = ClusterQuantiser.mainOptions(precClusterArgs);
				SpatialClusterer<?,?> precisionCluster = ClusterQuantiser.do_create(precisionClusterCop);
				if(p.equals(Precision.BYTE)){
					int[][] precisionClusterCenters = ByteArrayConverter.byteToInt(((CentroidsProvider<byte[]>)precisionCluster).getCentroids());
					int i = 0;
					for(int[] precisionClusterCenter : precisionClusterCenters ){
						if(!Arrays.equals(precisionClusterCenter,intClusterCenters[i]))
							System.err.println("BYTE version has different clusters to INT version");
						
						assertTrue(Arrays.equals(precisionClusterCenter,intClusterCenters[i]));
						i++;
					}
					for(int j = 0; j < 100; j++){
						byte[] pushdata = kpl.get(j).ivec;
						int[] intpushdata = ByteArrayConverter.byteToInt(pushdata);
						assertTrue(((SpatialClusterer<?,byte[]>)precisionCluster).defaultHardAssigner().assign(pushdata) == ((SpatialClusterer<?,int[]>)intCluster).defaultHardAssigner().assign(intpushdata));
					}
					
				}
				else{
					int[][] precisionClusterCenters = ((CentroidsProvider<int[]>) precisionCluster).getCentroids();
					int i = 0;
					for(int[] precisionClusterCenter : precisionClusterCenters ){
						assertTrue(Arrays.equals(precisionClusterCenter,intClusterCenters[i++]));
					}
					for(int j = 0; j < 100; j++){
						byte[] pushdata = kpl.get(j).ivec;
						int[] intpushdata = ByteArrayConverter.byteToInt(pushdata);
						assertTrue(((SpatialClusterer<?,int[]>)precisionCluster).defaultHardAssigner().assign(intpushdata) == ((SpatialClusterer<?,int[]>)intCluster).defaultHardAssigner().assign(intpushdata));
					}
				}
				
			}
		}
	}
//	
	/**
	 * Test types
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@Test public void testAllClusterTypes() throws IOException{
		for(ClusterType ct : ClusterType.values()) {
			for(Precision p : Precision.values()) {
				ClusterTypeOp opts = (ClusterTypeOp) ct.getOptions();
				opts.precision = p;
				
				File oldout = folder.newFile("old"+ct+"-"+p+".voc");
				
				byte[][] data = ByteArrayConverter.intToByte(RandomData.getRandomIntArray(10, 10, 0, 20, 0));
				int[] pushdata = RandomData.getRandomIntArray(1, 10, 0, 20, 0)[0];
				
				SpatialClusterer<?,?> oldstyle = opts.create(data);
				
				IOUtils.writeBinary(oldout, oldstyle);
				ClusterTypeOp sniffedType = ClusterType.sniffClusterType(oldout);
				SpatialClusterer<?,?> newstyle = IOUtils.read(oldout, sniffedType.getClusterClass());
				
				if(newstyle.getClass().getName().contains("Byte")) {
					assertTrue(((SpatialClusterer<?,byte[]>)newstyle).defaultHardAssigner().assign(ByteArrayConverter.intToByte(pushdata)) == ((SpatialClusterer<?,byte[]>)oldstyle).defaultHardAssigner().assign(ByteArrayConverter.intToByte(pushdata)));
				}
				else{
					assertTrue(((SpatialClusterer<?,int[]>)newstyle).defaultHardAssigner().assign(pushdata) == ((SpatialClusterer<?,int[]>)oldstyle).defaultHardAssigner().assign(pushdata));
				}
			}
		}
	}
	
	private void testAllArgs(String[] args) throws CmdLineException {
//		ClusterQuantiserOptions opt = new ClusterQuantiserOptions(args);
//		opt.prepare();
//		System.out.println(opt );
		try {
			ClusterQuantiser.mainOptions(args);
		} catch (InterruptedException e) {
			throw new CmdLineException(null,e.getMessage());
		}
	}
	
	/**
	 * Test random forest
	 * @throws CmdLineException
	 * @throws IOException
	 */
	@Test
	public void testRForest() throws CmdLineException, IOException{
		testAllArgs(new String[]{"-c", folder.newFile("codebook-testRForest.voc").getAbsolutePath(), "-v","1","-t","LOWE_KEYPOINT_ASCII","-ct","RFOREST","-s","5","-f",inputFile.getAbsolutePath(),"-d","2","-nt","2"});
	}
	
	/**
	 * test random set
	 * @throws CmdLineException
	 * @throws IOException
	 */
	@Test
	public void testRandomSet() throws CmdLineException, IOException{
		testAllArgs(new String[]{"-c", folder.newFile("codebook-testRandomSet.voc").getAbsolutePath(), "-v","1","-t","LOWE_KEYPOINT_ASCII","-ct","RANDOMSET","-s","5","-f",inputFile.getAbsolutePath(),"-k","5"});
//		return IOUtils.read(new File("codebook1000-kmeans-agsift.voc"), RandomIntCluster.class);
	}
}
