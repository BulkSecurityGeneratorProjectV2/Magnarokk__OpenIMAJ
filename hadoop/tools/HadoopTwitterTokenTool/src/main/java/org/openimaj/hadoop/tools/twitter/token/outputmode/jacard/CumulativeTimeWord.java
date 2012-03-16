package org.openimaj.hadoop.tools.twitter.token.outputmode.jacard;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.openimaj.hadoop.mapreduce.MultiStagedJob;
import org.openimaj.hadoop.mapreduce.MultiStagedJob.Stage;
import org.openimaj.hadoop.mapreduce.StageAppender;
import org.openimaj.hadoop.mapreduce.StageProvider;
import org.openimaj.hadoop.tools.HadoopToolsUtil;
import org.openimaj.hadoop.tools.twitter.utils.WordDFIDF;
import org.openimaj.io.IOUtils;
import org.openimaj.io.wrappers.ReadableListBinary;

/**
 * Count word instances (not occurences) across times. Allows for investigation of how
 * the vocabulary has changed over time.
 * 
 * @author ss
 *
 */
public class CumulativeTimeWord implements StageAppender{
	private long timeDelta;
	
	/**
	 * @param timeDelta the delta between times
	 * @param timeEldest the eldest time
	 */
	public CumulativeTimeWord(long timeDelta, long timeEldest) {
		this.timeDelta = timeDelta;
		this.timeEldest = timeEldest;
	}
	private long timeEldest;
	/**
	 * For every word occurrence, emit <word-time,false> for its time period, and <word-time,true> for every time period from
	 * timePeriod + delta until eldestTime. The final time period should be comparing itself to every word ever emitted.
	 * 
	 * @author ss
	 */
	public static class IntersectionUnionMap extends Mapper<Text,BytesWritable,BytesWritable,BooleanWritable>{
		private long eldestTime;
		private long deltaTime;
		
		public IntersectionUnionMap() { }
		@Override
		protected void setup(Mapper<Text,BytesWritable,BytesWritable,BooleanWritable>.Context context) throws IOException ,InterruptedException {
			this.eldestTime = context.getConfiguration().getLong(TIME_ELDEST, -1);
			this.deltaTime = context.getConfiguration().getLong(TIME_DELTA, -1);
			if(eldestTime < 0 || deltaTime < 0){
				throw new IOException("Couldn't read reasonable time configurations");
			}
		};
		@Override
		protected void map(final Text word, BytesWritable value, final Mapper<Text,BytesWritable,BytesWritable,BooleanWritable>.Context context) throws java.io.IOException ,InterruptedException {
			IOUtils.deserialize(value.getBytes(), new ReadableListBinary<Object>(new ArrayList<Object>()){
				private BooleanWritable TRUE_WRITEABLE = new BooleanWritable(true);
				private BooleanWritable FALSE_WRITEABLE = new BooleanWritable(false);

				@Override
				protected Object readValue(DataInput in) throws IOException {
					WordDFIDF idf = new WordDFIDF();
					idf.readBinary(in);
					try {
						String currentword = word.toString();
						ReadWritableStringLong timeWordPair = new ReadWritableStringLong(currentword, idf.timeperiod);
						context.write(new BytesWritable(IOUtils.serialize(timeWordPair)),FALSE_WRITEABLE );
						for (long futureTime = idf.timeperiod + deltaTime; futureTime <= eldestTime; futureTime+=deltaTime) {
							ReadWritableStringLong futurePair = new ReadWritableStringLong(currentword, futureTime);
							context.write(new BytesWritable(IOUtils.serialize(futurePair)),TRUE_WRITEABLE );
						}
					} catch (InterruptedException e) {
						throw new IOException("");
					}
					return new Object();
				}
			});
		};
	}
	
	/**
	 * Recieve every word-time either from the current time period or from past time periods.
	 * Has this word appeared either in the past and now? intersection == 1
	 * Has this word appeared both in the past and now? union == 1
	 * 
	 * emit the time period with the length of the union set, the length of the intersection set and the ratio of these two (The Jacard Index)
	 * 
	 * @author ss
	 *
	 */
	public static class IntersectionUnionReduce extends Reducer<BytesWritable,BooleanWritable,LongWritable,BytesWritable>{
		public IntersectionUnionReduce() {}
		@Override
		protected void reduce(BytesWritable wordtimeb, Iterable<BooleanWritable> wordBools, Reducer<BytesWritable,BooleanWritable,LongWritable,BytesWritable>.Context context) throws IOException ,InterruptedException {
			ReadWritableStringLong wordtime = IOUtils.deserialize(wordtimeb.getBytes(), ReadWritableStringLong.class);
			long time = wordtime.secondObject();
			boolean seenInPresent = false;
			boolean seenInPast = false;
			for (BooleanWritable isfrompast: wordBools) {
				boolean frompast = isfrompast.get();
				seenInPresent |= !frompast;
				seenInPast |= frompast;
				if(seenInPast && seenInPresent){
					// then we've seen all the ones from this time if we were to see them, so we can break early. MASSIVE SAVINGS HERE
					break;
				}
			}
			ReadWritableBooleanBoolean intersectionUnion = new ReadWritableBooleanBoolean(seenInPast && seenInPresent,seenInPast || seenInPresent);
			context.write(new LongWritable(time), new BytesWritable(IOUtils.serialize(intersectionUnion)));
		};
	}
	
	/**
	 * 
	 * 
	 * @author ss
	 *
	 */
	public static class JacardReduce extends Reducer<LongWritable,BytesWritable,NullWritable,Text>{
		public JacardReduce () {}
		@Override
		protected void reduce(LongWritable time, Iterable<BytesWritable> inersectionUnionBs, Reducer<LongWritable,BytesWritable,NullWritable,Text>.Context context) throws IOException ,InterruptedException {
			long intersection = 0;
			long union = 0;
			for (BytesWritable intersectionUnionb : inersectionUnionBs) {				
				ReadWritableBooleanBoolean intersectionUnion = IOUtils.deserialize(intersectionUnionb.getBytes(), ReadWritableBooleanBoolean.class);
				intersection += intersectionUnion.firstObject() ? 1 : 0;
				union += intersectionUnion.secondObject() ? 1 : 0;
			}
			JacardIndex jind = new JacardIndex(time.get(),intersection,union);
			StringWriter writer = new StringWriter();
			IOUtils.writeASCII(writer, jind);
			context.write(NullWritable.get(), new Text(writer.toString()));
		};
	}
	
	protected static final String TIME_DELTA = "org.openimaj.hadoop.tools.twitter.token.time_delta";
	protected static final String TIME_ELDEST = "org.openimaj.hadoop.tools.twitter.token.time_eldest";
	@Override
	public void stage(MultiStagedJob stages) {
		Stage intersectionunion = new Stage() {

			@Override
			public Job stage(Path[] inputs, Path output, Configuration conf) throws IOException {
				Job job = new Job(conf);
				
				job.setInputFormatClass(SequenceFileInputFormat.class);
				job.setMapOutputKeyClass(BytesWritable.class);
				job.setMapOutputValueClass(BooleanWritable.class);
				job.setOutputKeyClass(LongWritable.class);
				job.setOutputValueClass(BytesWritable.class);
				job.setOutputFormatClass(SequenceFileOutputFormat.class);
				job.setJarByClass(this.getClass());
			
				SequenceFileInputFormat.setInputPaths(job, inputs);
				SequenceFileOutputFormat.setOutputPath(job, output);
				job.setMapperClass(CumulativeTimeWord.IntersectionUnionMap.class);
				job.setReducerClass(CumulativeTimeWord.IntersectionUnionReduce.class);
				job.getConfiguration().setLong(CumulativeTimeWord.TIME_DELTA, timeDelta);
				job.getConfiguration().setLong(CumulativeTimeWord.TIME_ELDEST, timeEldest);
				job.setNumReduceTasks((int) (1.75 * 6 * 8));
				return job;
			}
			
			@Override
			public String outname() {
				return "intersectionunion";
			}
		};
		stages.queueStage(intersectionunion);
		Stage s = new Stage() {

			@Override
			public Job stage(Path[] inputs, Path output, Configuration conf) throws IOException {
				Job job = new Job(conf);
				
				job.setInputFormatClass(SequenceFileInputFormat.class);
				job.setMapOutputKeyClass(LongWritable.class);
				job.setMapOutputValueClass(BytesWritable.class);
				job.setOutputKeyClass(NullWritable.class);
				job.setOutputValueClass(Text.class);
				job.setOutputFormatClass(TextOutputFormat.class);
				job.setJarByClass(this.getClass());
			
				SequenceFileInputFormat.setInputPaths(job, inputs);
				TextOutputFormat.setOutputPath(job, output);
				TextOutputFormat.setCompressOutput(job, false);
				job.setReducerClass(CumulativeTimeWord.JacardReduce.class);
				job.setNumReduceTasks((int) (1.75 * 6 * 8));
				return job;
			}
			
			@Override
			public String outname() {
				return "jacardindex";
			}
		};
		stages.queueStage(s);
	}
	
	/**
	 * from a report output path get the words
	 * @param path report output path
	 * @return map of time to an a pair containing <count, JacardIndex> 
	 * @throws IOException 
	 */
	public static LinkedHashMap<Long, JacardIndex> readTimeCountLines(String path) throws IOException {
		String wordPath = path + "/jacardindex";
		Path p = HadoopToolsUtil.getInputPaths(wordPath)[0];
		FileSystem fs = HadoopToolsUtil.getFileSystem(p);
		FSDataInputStream toRead = fs.open(p);
		BufferedReader reader = new BufferedReader(new InputStreamReader(toRead));
		LinkedHashMap<Long, JacardIndex> toRet = new LinkedHashMap<Long, JacardIndex>();
		String next = null;
		while((next = reader.readLine())!=null){
			JacardIndex jindex = JacardIndex.fromString(next);
			toRet.put(jindex.time, jindex);
		}
		return toRet;
	}

}
