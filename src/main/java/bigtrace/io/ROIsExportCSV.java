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
import bigtrace.rois.AbstractCurve3D;
import bigtrace.rois.Roi3D;

import ij.IJ;

public class ROIsExportCSV < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker
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
				//column titles
				
				writer.write("ROI_Number,X_coord,Y_coord,Z_coord,Radius,ROI_Name,ROI_Type,ROI_Group,ROI_TimePoint\n");
				DecimalFormatSymbols symbols = new DecimalFormatSymbols();
				symbols.setDecimalSeparator('.');
				DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
				nRoiN = bt.roiManager.rois.size();
				String sRoiName;
				String sRoiGroup;
				String sRoiType;
				String sRoiTP;
				String sRadius;
				int nRoiCurrN = 0;
				//writer.write("ROIsNumber," + Integer.toString(nRoiN)+"\n");
				for(nRoi=0; nRoi<nRoiN; nRoi++)
				{
					  //Sleep for up to one second.
					try {
						Thread.sleep(1);
					} catch (InterruptedException ignore) {}
					setProgress(nRoi*100/nRoiN);
					if(bt.roiManager.rois.get( nRoi ) instanceof AbstractCurve3D)
					{
						nRoiCurrN++;
						AbstractCurve3D currRoi = ((AbstractCurve3D)bt.roiManager.rois.get( nRoi ));
						sRoiTP = Integer.toString( currRoi.getTimePoint() );
						sRoiName = currRoi.getName();
						sRoiType = Roi3D.intTypeToString( currRoi.getType());
						sRoiGroup = bt.roiManager.groups.get(currRoi.getGroupInd()).getName();
						sRadius = df3.format( currRoi.getLineThickness() * 0.5 * bt.btData.dMinVoxelSize);
						ArrayList< RealPoint > points = currRoi.getJointSegmentResampled();
						for(int nP=0; nP<points.size(); nP++)
						{
							writer.write( Integer.toString( nRoiCurrN ) +"," );
							for (int d=0;d<3;d++)
							{
								writer.write( df3.format(points.get( nP ).getDoublePosition( d ) ) +"," );								
							}
							writer.write( sRadius +"," + sRoiName + "," + sRoiType + "," + sRoiGroup + "," + sRoiTP + "\n");
						}
					}

				}
				writer.close();
			}
			setProgress(100);
			setProgressState("exporting ROIs done.");
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
		setProgressState("export ROIs to CSV done.");
    }
}
