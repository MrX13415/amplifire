package audioplayer.player.codec;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import audioplayer.player.AudioDeviceLayer;
import audioplayer.player.AudioFile;
import audioplayer.player.listener.PlayerEvent;
import audioplayer.player.listener.PlayerListener;

/**
 * 
 * @author Oliver
 * @version 1.1
 * 
 * version: 1.1
 *  - Changed File to AudioFile
 */
public class WAVEAudioProcessingLayer extends AudioProcessingLayer implements Runnable{
			
	protected AudioInputStream bitstream;					//The MPEG audio bitstream
	//protected Decoder decoder;						//The MPEG audio decoder
	
	public int bps = 1;
	
	public WAVEAudioProcessingLayer() {
            audioDevice = new AudioDeviceLayer();
	}
		
	/** Initialize the play with the given file<br>
	 * Always call this first, to play a song or to change the current playing song
	 *   
	 * @param f	The file to be played
	 */
	public void initialzePlayer(AudioFile f){
		try {
			this.file = f;
			
			if (decoderThread != null) decoderThread.interrupt();
//			try {
//				if (bitstream != null) bitstream.close();
//			} catch (BitstreamException ex) {}
//			if (audioDevice != null) audioDevice.close();
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
			
			resetPlayer();
			
			newTimePosition = 0;
			skipFrames = false;
			skipedFrames = 0;

			if (!isPlaying())
				state = PlayerState.INIT;
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** Resets the file bit stream and the audio device
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 * @throws UnsupportedAudioFileException 
	 * @throws JavaLayerException 
	 */
	public void resetPlayer() throws FileNotFoundException, UnsupportedAudioFileException, IOException, JavaLayerException{

		bitstream = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(file.getFile())));
		
		audioDevice = new AudioDeviceLayer();
		audioDevice.open(bitstream.getFormat());

		closed = false;
		internaltimePosition = 0;
	}
	
	/** Frame decoding and audio playing routine
	 *  <br>
	 *  <br>
	 *  NOTE: Do not call this method directly! Use <code>play()</code> instead 
	 */
	@Override
	public void run() {
		try {
			if (!isPaused()) state = PlayerState.PLAYING;
			
			//Listener
			for (PlayerListener pl : listener) pl.onPlayerStart(new PlayerEvent(this));

			boolean hasMoreFrames = true;
			
			while (hasMoreFrames && !decoderThread.isInterrupted()) {
				long tplStart = System.currentTimeMillis();
				
				boolean notPaused = !isPaused();
								
//				if (!audioDevice.isOpen()) hasMoreFrames = false;

//				Header h = null; 
				//if (notPaused || skipFrames) h.
								
//				if (1 == 1){
//					timePerFrame = h.ms_per_frame();

					if (skipFrames){
						

						byte[] b = new byte[10000];
						bitstream.read(b, 0, 1024);
						
						if (audioDevice.isOpen()) {
							audioDevice.setVolume(volume);
							audioDevice.writeImpl(b, 0, 1024);
						}
					}

//				}else if (notPaused) hasMoreFrames = false;
				
				//if (notPaused || skipFrames) bitstream.closeFrame();

				if (!skipFrames){
					if (newTimePosition < internaltimePosition){
						resetPlayer();
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
                                            timePosition = internaltimePosition;
				}
								
				timePerLoop = System.currentTimeMillis() - tplStart;
			} //loop end
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			boolean nextSong = isPlaying();
			
			stop();
			
			System.out.println(timePosition + " " + internaltimePosition + " " + getStreamLength());
			
			if (nextSong && reachedEnd()){
				//Listener
				for (PlayerListener pl : listener) pl.onPlayerNextSong(new PlayerEvent(this));
			}
		}
	}
		
	/** Set the position of the current file to play from
	 * 
	 * @param ms time to play from in milliseconds
	 */
	public void setPostion(long ms){
		newTimePosition = ms;
		skipFrames = true;
	}
	
	/** Stops the current playing file and closes the file stream
	 */
	public void stop() {
		if (closed != true && !isNew()) {
			state = PlayerState.STOPPED;
			if (decoderThread != null) decoderThread.interrupt();
			
			//Listener
			for (PlayerListener pl : listener) pl.onPlayerStop(new PlayerEvent(this));
			
//			try {
//			//	if (audioDevice != null) bitstream.close();
//			} catch (BitstreamException ex) {}
//
//			if (audioDevice != null) audioDevice.close();
            
			closed = true;
			internaltimePosition = 0;
			newTimePosition = 0;
			skipFrames = false;
			skipedFrames = 0;
		}
	}
	
	protected void determineTimePerFrame() throws BitstreamException, FileNotFoundException{
//		Bitstream bitstream = null;
//		try {
//			bitstream = new Bitstream(new FileInputStream(file.getFile()));
//	        Header header = bitstream.readFrame();
//		    timePerFrame = header.ms_per_frame();
//		}catch(BitstreamException bex){
//	        throw bex;
//        }catch(FileNotFoundException fex){
//        	throw fex;
//		}finally{
//			bitstream.close();
//		}
//		
	}

}
