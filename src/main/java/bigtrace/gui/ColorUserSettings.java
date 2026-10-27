package bigtrace.gui;

import java.awt.Color;

/** helper class to store user selection of colors **/
public class ColorUserSettings {
	
	public Color[] colors = new Color[4];
	
	public ColorUserSettings()
	{
		for(int i=0;i<4;i++)
		{
			colors[i] = null;
		}
	}
	public void setColor(Color color_in, int index)
	{
		if (color_in == null)
		{
			colors[index] = null;
		}
		else
		{
			colors[index] = new Color(color_in.getRed(),color_in.getGreen(),color_in.getBlue(),color_in.getAlpha());
		}
	}
	public Color getColor(int index)
	{
		return colors[index];
	}
}
