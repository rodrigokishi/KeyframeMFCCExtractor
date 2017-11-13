package intermidia.KeyframeMFCCExtractor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.openimaj.audio.SampleChunk;
import org.openimaj.audio.features.MFCC;
import org.openimaj.audio.processor.FixedSizeSampleAudioProcessor;
import org.openimaj.util.pair.IntLongPair;
import org.openimaj.video.xuggle.XuggleAudio;
import org.openimaj.video.xuggle.XuggleVideo;

import TVSSUnits.Shot;
import TVSSUnits.ShotList;
import TVSSUtils.AudioStreamSelector;
import TVSSUtils.KeyframeReader;
import TVSSUtils.ShotReader;



public class KeyframeMFCCExtractor 
{
	private static class AuralSegment 
	{
		private int shotIndex;
		private int segmentIndex;
		private long startTime;
		private long endTime;
		
		public AuralSegment(int shot, int segment, long start, long end) 
		{
			this.shotIndex = shot;
			this.segmentIndex = segment;
			this.startTime = start;
			this.endTime = end;
		}
		
		public int getShotIndex() 
		{
			return shotIndex;
		}
		public long getStartTime() 
		{
			return startTime;
		}
		public long getEndTime() 
		{
			return endTime;
		}
		public int getSegIndex()
		{
			return segmentIndex;
		}
	}
	
	//Usage: MFCCExtractor <in: video file> <in: shot list csv> <in: keyframe list csv> <out: audio segments output folder> 
	//<out: mfcc feature vectors file> <in: audio streams> <in: stream to use> <in: audio segment len/2 in ms>
	//NOTE: audio segment size is input divided by 2 (easier to control) 
    public static void main( String[] args ) throws Exception
    { 	
    	File inputFile = new File(args[0]);
    	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();    	
    	String outputAudiosFolder = args[3];   	    	    	    	
    	int audioStreams = Integer.parseInt(args[5]);
    	int selectedStream = Integer.parseInt(args[6]);
    	long halfSegmentSize = Integer.parseInt(args[7]);
    	
    	//Read shot boundaries and keyframe positions    	
    	ShotList shotList = ShotReader.readFromCSV(args[1]);
    	ArrayList<IntLongPair> keyframes = KeyframeReader.readFromCSV(args[2]);    	   	   	    	  	
    	
    	//Create a millisecond map to the audio segments
    	XuggleVideo inputVideo = new XuggleVideo(inputFile);    	
    	double videoFPS = inputVideo.getFPS();
    	inputVideo.close();
    	long lastBoundary = 0;
    	ArrayList<AuralSegment> auralSegments = new ArrayList<AuralSegment>(); 
    	int lastShot = 0;
    	int segmentIndex = 0;
    	for(IntLongPair keyframe : keyframes)
    	{    		
    		
    		long keyframeTime = Math.round((keyframe.getSecond() / videoFPS) * 1000);
    		Shot shot = shotList.getList().get(keyframe.getFirst());
    		long shotBeginTime = Math.round((shot.getStartBoundary() / videoFPS) * 1000);
    		long shotEndTime = Math.round((shot.getEndBoundary() / videoFPS) * 1000);
    		
    		//Compute segment boundaries
    		long segmentStartTime, segmentEndTime;
    		
    		
    		//Compute start boundary
    		//Check if the new segment overlaps with last segment.
    		if( (keyframeTime - halfSegmentSize) > lastBoundary)
    		{
    			segmentStartTime = keyframeTime - halfSegmentSize;
    		}
    		else
    		{
    			segmentStartTime = lastBoundary + 1;
    		}
    		//Check if the start boundary happens inside the shot
    		if(segmentStartTime < shotBeginTime)
    		{
    			segmentStartTime = shotBeginTime;
    		}
    		
    		//Compute end boundary
    		//If end boundary isn't greater than start boundary do nothing 
    		if( (keyframeTime + halfSegmentSize) > segmentStartTime )
    		{
    			if((keyframeTime + halfSegmentSize) < shotEndTime)
    			{
    				segmentEndTime = (keyframeTime + halfSegmentSize);    				
    			}
    			else
    			{
    				segmentEndTime = shotEndTime;
    			}
    			
    			if(segmentStartTime < segmentEndTime)
    			{
    				if(keyframe.getFirst() != lastShot)
    				{
    					lastShot = keyframe.getFirst();
    					segmentIndex = 0;
    				}
    				else
    				{
    					segmentIndex++;
    				}
    				auralSegments.add(new AuralSegment(keyframe.getFirst(),segmentIndex, segmentStartTime, segmentEndTime));
    			}
    		}
    	}    	

    	//Generate and write MFCC descriptors
		XuggleAudio inputAudioMFCCRaw = new XuggleAudio(inputFile);    	
	
		//If there is more than one stream (dual audio videos for example) choose one
		if(audioStreams > 1)
		{
			inputAudioMFCCRaw = AudioStreamSelector.separateAudioStream(inputAudioMFCCRaw, audioStreams, selectedStream);
		} 
	
		//Calculate how many audio samples must be in a millisecond
		double samplesInAMillisecond = inputAudioMFCCRaw.getFormat().getSampleRateKHz();
		//30ms Audio frames
		int frameSizeInSamples = (int)(samplesInAMillisecond * 30);
		//10ms Overlap between frames
		int overlapSizeInSamples = (int)(samplesInAMillisecond *10);
		//Fixes the audio processor to work with 30ms windows and 10ms overlap between adjacent windows
		FixedSizeSampleAudioProcessor inputAudioMFCC = new FixedSizeSampleAudioProcessor(inputAudioMFCCRaw, 
			frameSizeInSamples, overlapSizeInSamples);
	
		MFCC mfcc = new MFCC( inputAudioMFCC );
		SampleChunk scMFCC = null;
		Iterator<AuralSegment> auralSegmentsIterator = auralSegments.iterator();
		AuralSegment actualSegment;
		FileWriter mfccWriter = new FileWriter(args[4]);
		scMFCC = mfcc.nextSampleChunk();
		long chunkIndex = 0;
		long sampleStartTime = scMFCC.getStartTimecode().getTimecodeInMilliseconds()/scMFCC.getFormat().getNumChannels();
		long sampleEndTime = sampleStartTime + 30;
		while( auralSegmentsIterator.hasNext() && (actualSegment = auralSegmentsIterator.next()) != null ) 
		{
			//I don't know why, but getTimecodeInMilliseconds when using window overlap returns two times the correct timecode.
			
			//Look for the audio correspoinding to the segment
			while(scMFCC != null && sampleStartTime <  actualSegment.getStartTime())
			{
				scMFCC = mfcc.nextSampleChunk();
				chunkIndex++;
				sampleStartTime = scMFCC.getStartTimecode().getTimecodeInMilliseconds()/scMFCC.getFormat().getNumChannels();
				sampleEndTime = sampleStartTime + 30;
			}
			//Write it down while
			while(scMFCC != null && sampleEndTime < actualSegment.getEndTime())
			{
				sampleStartTime = scMFCC.getStartTimecode().getTimecodeInMilliseconds()/scMFCC.getFormat().getNumChannels();
				sampleEndTime = sampleStartTime + 30;
				
				double[][] mfccs = mfcc.getLastCalculatedFeature();
				mfccWriter.write(Long.toString(actualSegment.getShotIndex()));
				for(int i = 0; i < mfccs[0].length; i++)
				{
					mfccWriter.write(" " + mfccs[0][i]);
				}
				mfccWriter.write("\n");
				
				//Write output stream not including overlapped chunks 
				if(chunkIndex % 3 == 0)
				{
					byteArrayOutputStream.flush();
					byteArrayOutputStream.write(scMFCC.getSamples());
					byteArrayOutputStream.flush(); 
				}	
				scMFCC = mfcc.nextSampleChunk();
				chunkIndex++;
			}
			
			//Write file on disk
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
			AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, inputAudioMFCC.getFormat().getJavaAudioFormat(), byteArrayOutputStream.size());
			String audioSampleName = "shot" + String.format("%04d", actualSegment.getShotIndex()) + "seg" + String.format("%04d", actualSegment.getSegIndex()) + ".wav";
			AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, new File(outputAudiosFolder + audioSampleName));			
			//Clear the byteArrayOutputStream
			byteArrayOutputStream.reset();				
			
		}
		mfccWriter.close();
	    	
	    System.exit(0);    	    
    }
}
