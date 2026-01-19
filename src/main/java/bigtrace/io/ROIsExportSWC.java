package bigtrace.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

import javax.swing.SwingWorker;

import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.BigTraceData;
import bigtrace.rois.AbstractCurve3D;

import ij.IJ;

public class ROIsExportSWC< T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker
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
		progressState=state_;
	}

	@Override
	protected Void doInBackground() throws Exception {
		int nRoi, nRoiN;		

		bt.bInputLock = true;
    	bt.setLockMode(true);
    	
        try {
			final File file = new File(sFilename);
			
			try (FileWriter writer = new FileWriter(file))
			{
				DecimalFormatSymbols symbols = new DecimalFormatSymbols();
				symbols.setDecimalSeparator('.');
				DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
				nRoiN = bt.roiManager.rois.size();
				long nPointCount = 1;
				//int nCurrActivePoint;
				String sRadius;
				writer.write("#raw "+ bt.btData.sFileNameFullImg+"\n");
				writer.write("#original_source BigTrace_ver_" +BigTraceData.sVersion+"\n");
				writer.write( "#roi_shape_interpolation " );
				switch (BigTraceData.shapeInterpolation)
				{
				case BigTraceData.SHAPE_Voxel:
					writer.write("voxel\n");
					break;
				case BigTraceData.SHAPE_Smooth:
					writer.write("smooth\n");
					break;
				case BigTraceData.SHAPE_Spline:
					writer.write("spline\n");
					break;
				}
				writer.write( "#smooth_window "+Integer.toString( BigTraceData.nSmoothWindow )+"\n" );

				for(nRoi=0; nRoi<nRoiN; nRoi++)
				{
					
					  //Sleep for up to one second.
					try {
						Thread.sleep(1);
					} catch (InterruptedException ignore) {}
					setProgress(nRoi*100/nRoiN);
					if(bt.roiManager.rois.get( nRoi ) instanceof AbstractCurve3D)
					{						
						AbstractCurve3D currRoi = ((AbstractCurve3D)bt.roiManager.rois.get( nRoi ));
						sRadius = df3.format( currRoi.getLineThickness() * 0.5 * bt.btData.dMinVoxelSize);
						ArrayList< RealPoint > points = currRoi.getJointSegmentResampled();
						for(int nP = 0; nP < points.size(); nP++)
						{
							writer.write( Long.toString( nPointCount ) +" 0 " );
							for (int d = 0; d < 3; d++)
							{
								writer.write( df3.format(points.get( nP ).getDoublePosition( d ) ) +" " );								
							}
							writer.write( sRadius + " ");
							if(nP == 0)
							{
								writer.write( "-1 \n");
								
							}
							else
							{
								writer.write( Long.toString( nPointCount-1 ) + " \n");
							}							
							nPointCount++;
						}
					}

				}
				writer.close();
			}
			setProgress(100);
			setProgressState("SWC export curve ROIs done.");
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
		setProgressState("SWC export curve ROI done.");
    }


}
