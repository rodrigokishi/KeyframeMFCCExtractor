package intermidia.KeyframeMFCCExtractor;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;

import org.openimaj.audio.SampleChunk;
import org.openimaj.audio.features.MFCC;
import org.openimaj.audio.processor.FixedSizeSampleAudioProcessor;
import org.openimaj.util.pair.IntLongPair;
import org.openimaj.util.pair.LongLongPair;
import org.openimaj.video.xuggle.XuggleAudio;
import org.openimaj.video.xuggle.XuggleVideo;

import TVSSUnits.Shot;
import TVSSUnits.ShotList;
import TVSSUtils.KeyframeReader;
import TVSSUtils.ShotReader;



public class KeyframeMFCCExtractor 
{
	//Usage: MFCCExtractor <in: video file> <in: shot list csv> <in: keyframe list csv> <out: audio segments output folder> <out: mfcc feature vectors file> <in: audio segment len/2 in ms>
	//NOTE: audio segment size is input divided by 2 because it is easier to control 
    public static void main( String[] args ) throws Exception
    { 	
    	File inputFile = new File(args[0]);
    	
    	//Read shot boundaries and keyframe positions    	
    	ShotList shotList = ShotReader.readFromCSV(args[1]);
    	ArrayList<IntLongPair> keyframes = KeyframeReader.readFromCSV(args[2]);    	   	   	    	  	
    	
    	//Create a millisecond map to the audio segments
    	XuggleVideo inputVideo = new XuggleVideo(inputFile);    	
    	double videoFPS = inputVideo.getFPS();
    	inputVideo.close();
    	long lastBoundary = 0;
    	long halfSegmentSize = Integer.parseInt(args[5]);
    	ArrayList<LongLongPair> auralSegments = new ArrayList<LongLongPair>(); 
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
    		}else
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
    				auralSegments.add(new LongLongPair(segmentStartTime, segmentEndTime));
    			}
    		}

    		    		
    	  	//Generate and write MFCC descriptors
        	XuggleAudio inputAudioMFCCRaw = new XuggleAudio(inputFile);    	
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
        	Iterator<LongLongPair> auralSegmentsIterator = auralSegments.iterator();
        	LongLongPair actualSegment = auralSegmentsIterator.next();
        	FileWriter mfccWriter = new FileWriter(args[4]);
    		while( (scMFCC = mfcc.nextSampleChunk()) != null && auralSegmentsIterator.hasNext())
    		{
    			long sampleStartTime = scMFCC.getStartTimecode().getTimecodeInMilliseconds();
    			long sampleEndTime = sampleStartTime + 30;
    			
    			//Iterate to the segment which contains this sample.
    			while(sampleStartTime > actualSegment.getSecond() && auralSegmentsIterator.hasNext())
    			{
    				actualSegment = auralSegmentsIterator.next();
    			}
    			
    			
    			//If the sample is fully contained inside the segment then write it
    			if(actualSegment.getFirst() < sampleStartTime && actualSegment.getSecond() > sampleEndTime)
    			{    			
    				double[][] mfccs = mfcc.getLastCalculatedFeature();
	    			//mfccWriter.write(Integer.toString(shotNum));
	    			for(int i = 0; i < mfccs[0].length; i++)
	    			{
	    				mfccWriter.write(" " + mfccs[0][i]);
	    			}
	    			mfccWriter.write("\n");
    			}
    		}
    		mfccWriter.close();
    	}
    	
    	
    	System.exit(0);    	    
    }
}
