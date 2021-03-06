/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.icelane.amplifire.process;

import java.io.File;

import net.icelane.amplifire.AppCore;
import net.icelane.amplifire.player.AudioFile;
import net.icelane.amplifire.player.codec.AudioType;
import net.icelane.amplifire.process.api.Process;

/**
 * amplifier - Audio-Player Project
 * 
 * @author Oliver Daus
 * 
 */
public class LoadDirProcess extends Process {

	private File[] dir;

	public LoadDirProcess(AppCore control, File[] dir) {
		super(control);
		this.dir = dir;
	}

	@Override
	public void process() {
		control.getStatusbar().getBar().setMinimum(0);
		control.getStatusbar().getBar().setMaximum(dir.length);
		control.getStatusbar().setMessageText(
				String.format("Loading dir ... (dirs: %6s/%6s ; files: %6s)",
						0, dir.length, 0));
		control.getStatusbar().setVisible(true);

		
		DirSearcher ds = new DirSearcher() {
			@Override
			public void processFile(File f) {
				control.getAudioPlaylist().add(new AudioFile(f));
			}
		};

		for (File file : dir) {
			ds.addDir(file);
		}
		ds.setFilenameFilter(AudioType.getAllSupportedFilenamesFilter());
		ds.startSearcher();

		while (ds.isRunning()) {
			try {
				Thread.sleep(33); //30 FPS
			} catch (InterruptedException e) {}
			
			control.getStatusbar().getBar().setMaximum(ds.getDirsCount());
			control.getStatusbar().getBar().setValue(ds.getDirsDoneSearched());
			control.getStatusbar().setMessageText(
					String.format(
							"Loading dir ... (dirs: %6s/%6s ; files: %6s)",
							ds.getDirsDoneSearched(), ds.getDirsCount(),
							ds.getFilesCount()));

			if (!running)
				ds.stopSearcher();
		}

		control.getStatusbar().setVisible(false);
	}
}
