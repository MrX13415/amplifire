package net.icelane.lolplayer.gui.components;

import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JMenu;

/**
 *  LoLPlayer II - Audio-Player Project
 * 
 * @author Oliver Daus
 * 
 */
public class Menu extends JMenu{
	/**
	 * 
	 */
	private static final long serialVersionUID = -4792554976830903762L;

	@Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(getBackground());
        g2d.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }
}