package bigtrace.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import bigtrace.BigTrace;
import bigtrace.BigTraceData;
import bigtrace.animation.AnimationPanel;
import bigtrace.animation.KeyFrame;
import ij.IJ;

public class StorylineSave
{
	
	/** plugin instance **/
	final BigTrace<?> bt;
	final AnimationPanel aPanel;
	
	public StorylineSave(final BigTrace<?> bt_, AnimationPanel aPanel_)
	{
		bt = bt_;	
		this.aPanel = aPanel_;
	}
	
	public void saveAnimation(String sFilename)
	{
		final File file = new File(sFilename);
		
		try (FileWriter writer = new FileWriter(file))
		{
			DecimalFormatSymbols symbols = new DecimalFormatSymbols();
			symbols.setDecimalSeparator('.');
			DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
			writer.write("BigTrace_StoryLine,version," + BigTraceData.sVersion + "\n");
			writer.write("TotalTime," + Integer.toString(aPanel.kfAnim.getTotalTime()) + "\n");
			final int nTotKeyFramesN = aPanel.kfAnim.keyFrames.size();
			writer.write("KeyFrameNumber," + Integer.toString(nTotKeyFramesN) + "\n");
			for(int nKF=0; nKF < nTotKeyFramesN; nKF++)
			{
				KeyFrame currKF =  aPanel.kfAnim.keyFrames.get( nKF );
				writer.write("KeyFrame,"+Integer.toString(nKF+1)+"\n");
				writer.write("KeyFrameName,"+currKF.getName()+"\n");
				writer.write("KeyFrameTime," + df3.format(currKF.getMovieTimePoint()) + "\n");
				currKF.getScene().save( writer );
				
			}
			writer.write("End of BigTrace Story Line\n");
			writer.close();
		} catch (IOException e) {	
			IJ.log(e.getMessage());
			//e.printStackTrace();
		}
		return;
	}
}
