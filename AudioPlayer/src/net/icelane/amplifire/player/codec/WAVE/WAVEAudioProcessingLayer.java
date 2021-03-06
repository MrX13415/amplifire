package net.icelane.amplifire.player.codec.WAVE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.JavaLayerException;
import net.icelane.amplifire.player.codec.AudioProcessingLayer;
import net.icelane.amplifire.player.codec.AudioType;
import net.icelane.amplifire.player.device.AudioDeviceLayer;
import net.icelane.amplifire.player.listener.PlayerEvent;
import net.icelane.amplifire.player.listener.PlayerListener;

/**
 *  amplifier - Audio-Player Project
 *  
 *  Audio processing layer for the WAVE audio file format
 * 
 * @author Oliver
 * @version 1.0
 * 
 */
public class WAVEAudioProcessingLayer extends AudioProcessingLayer implements Runnable{
				
	protected AudioInputStream bitstream;					//The MPEG audio bitstream
	//protected Decoder decoder;						//The MPEG audio decoder
	
	public int bps = 1;
	
	public WAVEAudioProcessingLayer() {
		super();
		//audioDevice = AudioDeviceLayer.getInstance();
	}
			
	/** Resets the file bit stream and the audio device
	 * @throws IOException 
	 * @throws UnsupportedAudioFileException 
	 * @throws FileNotFoundException 
	 * @throws LineUnavailableException 
	 */
	public boolean initializeAudioDevice(){
		try {
			bitstream = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(file.getFile())));
			audioDevice = AudioDeviceLayer.getInstance();
			if (!audioDevice.claim(this)) return false;
			audioDevice.open(bitstream.getFormat());
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	/** Frame decoding and audio playing routine
	 *  <br>
	 *  <br>
	 *  NOTE: Do not call this method directly! Use <code>play()</code> instead 
	 */
	@Override
	public void decode() {
		try {
			if (!isPaused()) state = PlayerState.PLAYING;
			
			//Listener
			synchronized (listener) {
				for (int i = 0; i < listener.size(); i++) {
					PlayerListener pl = listener.get(i);
					pl.onPlayerStart(new PlayerEvent(this));	
				}
			}
			
			boolean hasMoreFrames = true;

			while (hasMoreFrames && !decoderThread.isInterrupted()) {
				
				long tplStart = System.currentTimeMillis();
				
				boolean notPaused = !isPaused();
						
				if (!notPaused){
					try {
						Thread.sleep(20);
					} catch (Exception e) {}
				}
				
				if (!audioDevice.isOpen()) hasMoreFrames = false;

				if (notPaused || skipFrames){
					
					int btr = 4096;
					byte[] b = new byte[btr];
					int r = bitstream.read(b, 0, btr);

					if (r == -1)
						hasMoreFrames = false;
					
					if (r > -1 && !skipFrames){
						try {	
							if (audioDevice.isOpen()) {
								audioDevice.setVolume(volume);
								audioDevice.write(this, b, 0, r);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

				}else if (notPaused) hasMoreFrames = false;
				
				if (skipFrames){
					if (newTimePosition < internaltimePosition){
						closed = false;
						internaltimePosition = 0;
						skipFrames = true;
					}
					
					skipedFrames += timePerFrame;
					if(newTimePosition - skipedFrames <= internaltimePosition) {
						internaltimePosition += skipedFrames;
						skipedFrames = 0;
						skipFrames = false;
					}
				}else{
					if (timePerFrame <= 0) determineTimePerFrame();
					if (notPaused) internaltimePosition += timePerFrame;
                                            timePosition = (long) internaltimePosition;
				}
								
				timePerLoop = System.currentTimeMillis() - tplStart;
			} //loop end
			
			reachedEnd = !hasMoreFrames;
			
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			System.out.println("A:"+timePosition);
			boolean nextSong = isPlaying();
			
			stop();
			
			System.out.println("B:"+timePosition);
			
			if (nextSong && reachedEnd()){
				//Listener
				synchronized (listener) {
					for (int i = 0; i < listener.size(); i++) {
						PlayerListener pl = listener.get(i);
						pl.onPlayerNextSong(new PlayerEvent(this));
					}
				}				
			}
		}
	}
			
	public void closeStream() {
//		try {
//			if (bitstream != null) bitstream.close();
//		} catch (Exception ex) {}
	}
	
	protected void determineTimePerFrame(){
		long length = (long) Math.round((bitstream.getFrameLength() / (double)bitstream.getFormat().getFrameRate()) * 1000); 
		long frames = (long) Math.round(bitstream.getFrameLength() / 4096d * bitstream.getFormat().getFrameSize());
		timePerFrame = length / (double)frames;
	}
	
	/** Return the length of a given file in milliseconds
	 * <br>
	 * <br>
	 * <code> lenght = file_size * 8 / bitrate </code>
	 * <br>
	 * <br>
	 * @param f The file
	 * @return The length of the given file 'f' in milliseconds
	 * @throws StreamLengthException 
	 * @throws Exception 
	 * @throws BitstreamException
	 * @throws FileNotFoundException
	 */
	public long calculateStreamLength(File f) throws StreamLengthException{
		AudioInputStream bitstream = null;
		long length = 0; //in ms
		try {
			bitstream = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(f)));
			length = (long) ((bitstream.getFrameLength() / bitstream.getFormat().getFrameRate()) * 1000); 
			closeStream();
		} catch (Exception e) {
			throw new StreamLengthException(f);
		}
		return length;
	}

	/** Determines if the given file is supported by this class
	 * 
	 * @param f
	 * @return if the given file is an WAVE file (e.g. WAVE)
	 */
	public boolean isSupportedAudioFile(File f) {
		try {
			calculateStreamLength(f);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public AudioType getSupportedAudioType() {
		return new WAVEAudioType();
	}
}
