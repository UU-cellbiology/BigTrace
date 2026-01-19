package bigtrace.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.SwingWorker;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.BigTraceData;
import bigtrace.rois.Roi3DGroupManager;
import ij.IJ;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class ROIsSaveBG < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker
{

	private String progressState;
	public BigTrace<T> bt;
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
		int nRoi, nRoiN;		

		bt.bInputLock = true;
    	bt.setLockMode(true);
    	
    	//get the group manager to save groups
    	Roi3DGroupManager<T> roiGM = new Roi3DGroupManager<>(bt.roiManager);
    	
        try {
			final File file = new File(sFilename);
			
			try (FileWriter writer = new FileWriter(file))
			{
				setProgressState("saving Groups...");
				roiGM.saveGroups(writer);
				setProgressState("saving ROIs...");
				DecimalFormatSymbols symbols = new DecimalFormatSymbols();
				symbols.setDecimalSeparator('.');
				DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
				
				writer.write("BigTrace_ROIs,version," + BigTraceData.sVersion + "\n");
				writer.write("ImageUnits,"+bt.btData.sVoxelUnit+"\n");
				writer.write("ImageVoxelWidth," + df3.format(bt.btData.globCal[0]) + "\n");
				writer.write("ImageVoxelHeight," + df3.format(bt.btData.globCal[1]) + "\n");
				writer.write("ImageVoxelDepth," + df3.format(bt.btData.globCal[2]) + "\n");
				writer.write("TimeUnits," + bt.btData.sTimeUnit + "\n");
				writer.write("FrameInterval," + df3.format(bt.btData.dFrameInterval) + "\n");
				writer.write("Intensity Interpolation,"+Integer.toString( BigTraceData.intensityInterpolation )+"\n");
				writer.write("ROI Shape Interpolation,"+Integer.toString( BigTraceData.shapeInterpolation )+"\n");
				writer.write("Rotation min frame type,"+Integer.toString( BigTraceData.rotationMinFrame )+"\n");
				writer.write("Smooth window,"+Integer.toString( BigTraceData.nSmoothWindow )+"\n");

				nRoiN = bt.roiManager.rois.size();
				writer.write("ROIsNumber," + Integer.toString(nRoiN)+"\n");
				for(nRoi=0;nRoi<nRoiN;nRoi++)
				{
					  //Sleep for up to one second.
					try {
						Thread.sleep(1);
					} catch (InterruptedException ignore) {}
					setProgress(nRoi*100/nRoiN);
					writer.write("BT_Roi,"+Integer.toString(nRoi+1)+"\n");
					bt.roiManager.rois.get(nRoi).saveRoi(writer);
				}
				writer.write("End of BigTrace ROIs\n");
				writer.close();
			}
			setProgress(100);
			setProgressState("saving ROIs done.");
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
		setProgressState("saving ROIs done.");
    }
}
