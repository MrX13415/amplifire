package audioplayer.player.codec;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import javax.activity.InvalidActivityException;

import audioplayer.player.AudioDeviceLayer;
import audioplayer.player.codec.AudioFile.AudioType;
import audioplayer.player.listener.PlayerListener;

/**
 * Base audio processing layer interface
 * 
 * @author Oliver
 * @version 1.2
 * 
 */
public abstract class AudioProcessingLayer implements Runnable{
	
	public enum PlayerState{
		NEW, INIT, PLAYING, STOPPED, PAUSED;
	}
		
	protected AudioDeviceLayer audioDevice;			

	protected AudioFile file;								
	protected Thread decoderThread;					
	protected ArrayList<PlayerListener> listener = new ArrayList<PlayerListener>();			
	
	protected PlayerState state = PlayerState.NEW; 	
	
	protected boolean closed;							

	protected double internaltimePosition;			//in milliseconds
    protected long timePosition;
	protected long newTimePosition;					//in milliseconds
	protected boolean skipFrames;					
	protected double skipedFrames;				
	protected double timePerFrame;					//in milliseconds
	
	protected float volume = 25f; 					//default: 80%
	
	protected long timePerLoop = 0;
	
	public AudioProcessingLayer() {
		audioDevice = new AudioDeviceLayer();
	}
		
	public static AudioProcessingLayer getEmptyInstance (){
		return new AudioProcessingLayer() {
			
			@Override
			public long getStreamLength(){
				return 1;
			}
			
			@Override
			public void stop() {}
			
			@Override
			public void run() {}
			
			@Override
			public void resetPlayer() throws Exception {}
			
			@Override
			public void initialzePlayer(AudioFile f) {}
			
			@Override
			protected void determineTimePerFrame() throws Exception,
					FileNotFoundException {}
			
			@Override
			public long calculateStreamLength(File f) {
				return 0;
			}

			@Override
			public boolean isSupportedAudioFile(File f) {
				return false;
			}

			@Override
			public AudioType getSupportedAudioType() {
				return AudioType.UNKNOW;
			}
		};
	}
	
	public void cleanInstance(){
		stop();
		file = null;
		decoderThread = null;
		synchronized (audioDevice) {
			listener.clear();
		}
	}
	
	public AudioDeviceLayer getAudioDevice() {
		return audioDevice;
	}

	public void setAudioDevice(AudioDeviceLayer audioDevice) {
		this.audioDevice = audioDevice;
	}

	public ArrayList<PlayerListener> getPlayerListener() {
		return listener;
	}
	
	public void addPlayerListener(PlayerListener playerListener) {
		synchronized (listener) {
			listener.add(playerListener);
		}
	}
	
	public void removePlayerListener(PlayerListener playerListener) {
		synchronized (listener) {
			listener.remove(playerListener);
		}
	} 

	public Thread getDecoderThread() {
		return decoderThread;
	}

	public PlayerState getState() {
		return state;
	}

	public long getTimePerLoop() {
		return timePerLoop;
	}

	public boolean isSkipFrames() {
		return skipFrames;
	}

	public boolean isClosed() {
		return closed;
	}
		
	public boolean isStopped(){
		return state == PlayerState.STOPPED;
	}

	public boolean isPlaying(){
		return state == PlayerState.PLAYING;
	}
	
	public boolean isPaused(){
		return state == PlayerState.PAUSED;
	}
	
	public boolean isInitialized(){
		return state == PlayerState.INIT;
	}
	
	public boolean isNew(){
		return state == PlayerState.NEW;
	}
	
	protected void setState(PlayerState state) {
		this.state = state;
	}

	/** Set the volume of the player
	 * 
	 * @param vol The volume in range from 0.0 till 100.0
	 */
	public void setVolume(float vol){
		this.volume = vol;
	}
	
	/** Get the volume of the player
	 * 
	 * @return The volume in range from 0.0 till 100.0
	 */
	public float getVolume(){
		return this.volume;
	}
	
	/** Get the current playing file
	 * 
	 * @return The file
	 */
	public AudioFile getAudioFile() {
		return file;
	}

	/** Get the current position of the current playing file
	 * 
	 * @return The position in milliseconds
	 */
	public long getTimePosition() {
		return timePosition;
	}

	/** Toggle pause for the current song
	 * 
	 */
	public void togglePause(){
		setPause(!isPaused());
	}
	
	/** Pause the current song
	 * 
	 * @param pause <code>true</code> to pause the song
	 */
	public void setPause(boolean pause){
		if (pause) state = PlayerState.PAUSED;
		else state = PlayerState.PLAYING;
	}
	
	
	public void togglePlayPause(){
		if (isPlaying()) togglePause();
		else try { play(); } catch (InvalidActivityException e) {}
	}
	
	/** Start playing the current song or resume it if paused
	 * @throws InvalidActivityException 
	 * 
	 */
	public void play() throws InvalidActivityException{
		if (state == PlayerState.INIT){
			createDecoderThread();
			
		}else if (state == PlayerState.PAUSED) {
			togglePause();
			
		}else if (state == PlayerState.STOPPED || closed) {
			if (file != null){
				initialzePlayer(file);
				createDecoderThread();
			}
		}else if (state == PlayerState.NEW) {
			throw new InvalidActivityException("player not initalized");
		}
	}
	
	/**
	 * Creates the audio file decoder thread 
	 */
	public void createDecoderThread(){
		decoderThread = new Thread(this){
			/*
			 * Interrupt logic for decoder thread ...
			 */
			protected boolean isInterrupted;
			
			public boolean isInterrupted(){
				return isInterrupted || super.isInterrupted();
			}
			
			public void interrupt(){
				isInterrupted = true;
				super.interrupt();
			}
		};
		
		decoderThread.setName("Stream Decoder");
		decoderThread.start();
	}
	
	public boolean reachedEnd(){
		return getStreamLength() - timePosition < 10000;
	}
	
	/** Set the position of the current file to play from
	 * 
	 * @param ms time to play from in milliseconds
	 */
	public void setPostion(long ms){
		newTimePosition = ms;
		skipFrames = true;
	}
	
	/** Returns the length of the current file 
	 * <br>
	 * <br>
	 * <code> lenght = file_size * 8 / bitrate </code>
	 * <br>
	 * <br>
	 * @return The length of the given file in milliseconds
	 * @throws BitstreamException
	 * @throws FileNotFoundException
	 */
	public long getStreamLength(){
		try{
			if (file != null)
				return calculateStreamLength(this.file.getFile());
			else if (state == PlayerState.NEW) 
				throw new InvalidActivityException("player not initalized");
        }catch(Exception ex){}
		return 0;
	}
		
	/** Initialize the play with the given file<br>
	 * Always call this first, to play a song or to change the current playing song
	 *   
	 * @param f	The file to be played
	 */
	public abstract void initialzePlayer(AudioFile f);
	
	/** Resets the file bitstream and the audiodevice
	 */
	public abstract void resetPlayer() throws Exception;
	
	/** Frame decoding and audio playing routine
	 *  <br>
	 *  <br>
	 *  NOTE: Do not call this method directly! Use <code>play()</code> instead 
	 */
	@Override
	public abstract void run();
	
	/** Stops the current playing file and closes the file stream
	 */
	public abstract void stop();
	
	/** Determines the time needed per frame
	 */
	protected abstract void determineTimePerFrame() throws Exception, FileNotFoundException;
	
	/** Return the length of a given file in milliseconds
	 * <br>
	 * <br>
	 * <code> lenght = file_size * 8 / bitrate </code>
	 * <br>
	 * <br>
	 * @param f The file
	 * @return The length of the given file 'f' in milliseconds
	 * @throws BitstreamException
	 * @throws FileNotFoundException
	 */
	public abstract long calculateStreamLength(File f);
	
	/** Determines if the given file is supported by this class
	 * 
	 * @param f
	 * @return
	 */
	public abstract boolean isSupportedAudioFile(File f);
	
	public abstract AudioType getSupportedAudioType();
	
}
