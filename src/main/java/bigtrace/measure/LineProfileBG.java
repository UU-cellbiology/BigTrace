package bigtrace.measure;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.SwingWorker;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.rois.Roi3D;
import ij.IJ;

public class LineProfileBG extends SwingWorker<Void, String> implements BigTraceBGWorker
{
	private String progressState;
	public BigTrace<?> bt;
	public String sFilename;
	@Override
	public String getProgressState()
	{
		return progressState;
	}
	@Override
	public void setProgressState(String state_)
	{
		progressState = state_;
	}
	@Override
	protected Void doInBackground() throws Exception {

    	final int nRoiN = bt.roiManager.rois.size();
    	setProgressState("measuring line profiles...");
    	setProgress(0);
    	int j,k;
        try 
        {
			final File file = new File(sFilename);
			
			try (FileWriter writer = new FileWriter(file))
			{
				double [][] profile;
				String sPrefix;
				String out;
				Roi3D roi;
				final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
				symbols.setDecimalSeparator('.');
				final DecimalFormat df3 = new DecimalFormat ("#.###", symbols);
				

				writer.write("ROI_Name,ROI_Type,ROI_Group,ROI_TimePoint,Length,Intensity,X_coord,Y_coord,Z_coord\n");
				for(int i = 0; i<nRoiN;i++)
				{
					setProgress((i+1)*100/nRoiN);
					setProgressState("line profile ROI #"+Integer.toString(i+1)+" of "+Integer.toString(nRoiN)+"...");
					roi = bt.roiManager.rois.get(i);
					sPrefix = roi.getName() + ","+Roi3D.intTypeToString(roi.getType())+","+bt.roiManager.getGroupName(roi)+","+Integer.toString(roi.getTimePoint());
					profile=bt.roiManager.roiMeasure.measureLineProfile(roi, false);
					if(profile!=null)
					{
						for(j=0;j<profile[0].length;j++)
						{
							out="".concat(sPrefix);
							
							for(k=0;k<5;k++)
							{
								out = out.concat(","+df3.format(profile[k][j]));
							}
							out = out.concat("\n");
							writer.write(out);
						}
					}
				}
				writer.close();
			}
    	
        } catch (IOException e) {	
			IJ.log(e.getMessage());
			//e.printStackTrace();
		}
		return null;
	}
    /*
     * Executed in event dispatching thread
     */
	@Override
    public void done() 
    {

		//unlock user interaction
    	bt.bInputLock = false;
    	bt.setLockMode(false);
		setProgress(100);
		setProgressState("saved line profiles of "+Integer.toString(bt.roiManager.rois.size())+" ROIs");
    }
}
