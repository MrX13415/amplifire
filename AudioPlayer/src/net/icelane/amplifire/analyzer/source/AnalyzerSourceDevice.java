package net.icelane.amplifire.analyzer.source;

import javax.sound.sampled.AudioFormat;
import javax.swing.JFrame;

public interface AnalyzerSourceDevice {

	public String getDisplayName();	
	
	public String getDescription();
	
	public AudioFormat getAudioFormat();

	public boolean hasSettingsUI();
	
	public void OpenSettingsUI(JFrame parent);
	
	public void CloseSettingsUI();
}
