package net.icelane.amplifire.player.codec.MPEG;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.icelane.amplifire.player.codec.AudioProcessingLayer;
import net.icelane.amplifire.player.codec.AudioType;
import net.icelane.amplifire.player.device.AudioDeviceLayer;
import net.icelane.amplifire.player.listener.PlayerEvent;
import net.icelane.amplifire.player.listener.PlayerListener;

/**
 *  amplifier - Audio-Player Project
 *  
 * Audio processing layer for the MPEG 1-2.5 Layer I-III audio file format 
 * 
 * @author Oliver
 * @version 1.2
 *
 */
public class MPEGAudioProcessingLayer extends AudioProcessingLayer implements Runnable{
		
	protected Bitstream bitstream;					//The MPEG audio bitstream
	protected Decoder decoder;						//The MPEG audio decoder
	
	protected SampleBuffer output;
	
	public MPEGAudioProcessingLayer() {
		super();
		//audioDevice = AudioDeviceLayer.getInstance();
	}
		
	public Bitstream getBitstream() {
		return bitstream;
	}

	public Decoder getDecoder() {
		return decoder;
	}
		
	private void initializeBitStreamDecoder() throws FileNotFoundException{
		bitstream = new Bitstream(new FileInputStream(file.getFile()));
		decoder = new Decoder();
	}
	
	protected AudioFormat getAudioFormat() throws FileNotFoundException {
		if (decoder == null || bitstream == null) return null;
			
		try {
			Header header = bitstream.readFrame();
			decoder.decodeFrame(header, bitstream);
			bitstream.closeFrame();
			return new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);
		} catch (BitstreamException | DecoderException e) {
			return null;
		}finally{
			try {
				bitstream.close();
			} catch (BitstreamException e) {}
			initializeBitStreamDecoder();
		}				
	}
	
	public SampleBuffer getOutput() {
		return output;
	}
	
	/** Resets the file bit stream and the audio device
	 * @return 
	 * @throws FileNotFoundException 
	 * @throws LineUnavailableException 
	 */
	public boolean initializeAudioDevice(){
		try {
			initializeBitStreamDecoder();
			audioDevice = AudioDeviceLayer.getInstance();
			if (!audioDevice.claim(this)) return false;
			audioDevice.open(getAudioFormat());
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
			for (PlayerListener pl : listener) pl.onPlayerStart(new PlayerEvent(this));

			boolean hasMoreFrames = true;
			
			while (hasMoreFrames && !decoderThread.isInterrupted()) {
				
				long tplStart = System.currentTimeMillis();
				
				boolean notPaused = !isPaused();
				boolean skip = skipFrames;
				
				if (!notPaused){
					try {
						Thread.sleep(20);
					} catch (Exception e) {}
				}
				
				if (!audioDevice.isOpen()) hasMoreFrames = false;

				Header header = null;
                                
				if (notPaused || skip)
					header = bitstream.readFrame();
                                        
				if (header != null){
					timePerFrame = header.ms_per_frame();

					if (!skip){
						output = (SampleBuffer) decoder.decodeFrame(header, bitstream);

						if (audioDevice.isOpen()) {
							audioDevice.setVolume(volume);
							
							byte[] b = toByteArray(output.getBuffer(), 0, output.getBufferLength());						
							audioDevice.write(this, b, 0, b.length);
						}
					}

				}else if (notPaused) hasMoreFrames = false;
				
				if (notPaused || skip) bitstream.closeFrame();

				if (skip){
					if (newTimePosition < internaltimePosition){
						closeStream();
						initializeAudioDevice();
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
                    System.err.println("Error while playing Audiofile: " + e);
                    e.printStackTrace();
		}finally{
			boolean nextSong = isPlaying();
			
			stop();
			
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

	private byte[] toByteArray(short[] samples, int offs, int len) {
		byte[] b = new byte[len * 2];
		int idx = 0;
		short s;
		while (len-- > 0) {
			s = samples[offs++];
			b[idx++] = (byte) s;
			b[idx++] = (byte) (s >>> 8);
			
		}
		return b;
	}
	public synchronized void closeStream() {		
		try {
			if (bitstream != null) bitstream.close();
		} catch (BitstreamException ex) {}
	}
	
	protected void determineTimePerFrame() throws BitstreamException, FileNotFoundException{
		Bitstream bitstream = null;
		try {
			bitstream = new Bitstream(new FileInputStream(file.getFile()));
	        Header header = bitstream.readFrame();
		    timePerFrame = header.ms_per_frame();
		}catch(BitstreamException bex){
	        throw bex;
        }catch(FileNotFoundException fex){
        	throw fex;
		}finally{
			bitstream.close();
		}
		
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
	 */
	public long calculateStreamLength(File f) throws StreamLengthException{
		Bitstream bitstream = null;
		long length = 0; //in ms
		 
		try{
	        bitstream = new Bitstream(new FileInputStream(f));
	        	
	        Header header = bitstream.readFrame();

	        long filesize = f.length();
	        if (filesize != AudioSystem.NOT_SPECIFIED) {
	        	length = (long) (((double)filesize * 8d / (double)header.bitrate()) * 1000d);
	        }	
        }catch(Exception bex){
        	throw new StreamLengthException(f);
        }finally{
           if (bitstream != null)
			try {
				bitstream.close();
			} catch (BitstreamException e) {}
        }
		
		return length;
	}

	/** Determines if the given file is supported by this class
	 * 
	 * @param f
	 * @return if the given file is an MPEG file (e.g. MP3)
	 */
	public boolean isSupportedAudioFile(File f) {
		return f.getName().toLowerCase().endsWith(".mp1") || f.getName().toLowerCase().endsWith(".mp2") || f.getName().toLowerCase().endsWith(".mp3");
	}

	@Override
	public AudioType getSupportedAudioType() {
		return new MPEGAudioType();
	}
	
}
