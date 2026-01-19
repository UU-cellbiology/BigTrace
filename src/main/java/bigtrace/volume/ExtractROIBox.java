package bigtrace.volume;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.rois.Roi3D;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class ExtractROIBox < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker{

	private String progressState;
	
	final public BigTrace<T> bt;
	
	int nExpandROIBox;
	/** 0 - single time point, 1 - all time points **/
	int nTimeRange;
	/** list of ROIs to process **/
	ArrayList<Roi3D> listROIs;	

	boolean bOnlyVoxelsInsideROI = false;
	
	/** 0 - show in ImageJ, 1 - save as TIFF **/
	int nOutput;
	
	String sSaveFolderPath;
	Calibration cal;
	
	public ExtractROIBox(final ArrayList<Roi3D> listROIs_, final BigTrace<T> bt_, final int nExpandROIBox_, final int nTimePoint_, final int nOutput_, final boolean bOnlyVoxelsInsideROI_, final String sSaveFolderPath_)
	{
		super();
		listROIs = listROIs_;
		bt = bt_;
		nExpandROIBox = nExpandROIBox_;
		nTimeRange = nTimePoint_;
		bOnlyVoxelsInsideROI = bOnlyVoxelsInsideROI_;
		nOutput = nOutput_;
		sSaveFolderPath = sSaveFolderPath_;
		cal = new Calibration();
	}
	
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
	protected Void doInBackground() throws Exception 
	{

        bt.bInputLock = true;
        bt.setLockMode(true);
        

        try 
        {
        	Thread.sleep(1);
        } 
        catch (InterruptedException ignore) {}
        setProgress(1);
        setProgressState("Extracting box around ROI(s)..");
		
		//get the all data RAI
		//XYZTC
		RandomAccessibleInterval<T> full_RAI = bt.btData.getAllDataRAI();
		
		//output calibration
		cal.setUnit(bt.btData.sVoxelUnit);
		cal.setTimeUnit(bt.btData.sTimeUnit);
		cal.pixelWidth = bt.btData.globCal[0];
		cal.pixelHeight= bt.btData.globCal[1];
		cal.pixelDepth = bt.btData.globCal[2];
		final int nTotROIs = listROIs.size();  
		if(nTotROIs == 0)
			return null;
		String sRoiName;

		for(int nRoi=0; nRoi<nTotROIs; nRoi++)
		{
			sRoiName = bt.roiManager.getFullDisplayedRoiName(listROIs.get(nRoi));
			try {
				Thread.sleep(1);
			} catch (InterruptedException ignore) {}
			setProgress(100*nRoi/(nTotROIs));
			setProgressState("extracting ROI ("+Integer.toString(nRoi+1)+"/"+Integer.toString(nTotROIs)+") "+ sRoiName);
			final IntervalView<T> extractedRAI;
			if(bOnlyVoxelsInsideROI)
			{
				extractedRAI = extractBoxRoiVolumeShape(listROIs.get(nRoi),  full_RAI);
			}
			else
			{
				extractedRAI = extractBoxRoiNoZeros(listROIs.get(nRoi),  full_RAI);				
			}
			
			setProgressState("extracting ROI ("+Integer.toString(nRoi+1)+"/"+Integer.toString(nTotROIs)+") "+ sRoiName);
			if(extractedRAI != null)
			{
				outputImagePlus(VolumeMisc.wrapImgImagePlusCal(extractedRAI, sRoiName + "_bbox",cal, bt.btData.nNumTimepoints));
			}
		}
		
		setProgressState("ROI bounding box extraction finished.");
		setProgress(100);	

		return null;
	}
	
	void outputImagePlus(ImagePlus ip)
	{
		if(nOutput == 0)
		{
			ip.show();
		}
		else
		{
			IJ.saveAsTiff(ip, sSaveFolderPath+ip.getTitle());
		}
	}
	IntervalView<T> extractBoxRoiNoZeros(final Roi3D roiIn, final RandomAccessibleInterval<T> all_RAI)
	{
		FinalInterval roiBoxInt = (FinalInterval) roiIn.getBoundingBox();
		
		//expand box
		FinalInterval finBox3D = Intervals.expand(roiBoxInt, nExpandROIBox);
		long [][] nInt = new long [2][5];
		
		finBox3D.min(nInt[0]);
		finBox3D.max(nInt[1]);
		//channel
		nInt[0][4] = all_RAI.min(4);
		nInt[1][4] = all_RAI.max(4);
		//time
		
		//current timepoint
		if(nTimeRange == 0)
		{
			nInt[0][3] = roiIn.getTimePoint();
			nInt[1][3] = roiIn.getTimePoint();
			
		}
		//all time points
		else
		{
			nInt[0][3] = all_RAI.min(3);
			nInt[1][3] = all_RAI.max(3);
		}

		FinalInterval finBoxInt = new FinalInterval(nInt[0],nInt[1]);
		
		//check the time axis
		//Img<T> out1 = Util.getSuitableImgFactory(all_RAI, Util.getTypeFromInterval(all_RAI)).create(Intervals.zeroMin(finBoxInt));
		//IntervalView<T> finBox = Views.interval(Views.extendZero(all_RAI),finBoxInt);
		return Views.interval(Views.extendZero(all_RAI),finBoxInt);
	}	
	
	IntervalView<T> extractBoxRoiVolumeShape(final Roi3D roiIn, final RandomAccessibleInterval<T> all_RAI)
	{
		FinalInterval roiBoxInt = (FinalInterval) roiIn.getBoundingBox();

		//expand box
		FinalInterval finBox3D = Intervals.expand(roiBoxInt, nExpandROIBox);
		long [][] nInt = new long [2][5];
		
		finBox3D.min(nInt[0]);
		finBox3D.max(nInt[1]);
		//channel
		nInt[0][4] = all_RAI.min(4);
		nInt[1][4] = all_RAI.max(4);
		//time
		
		//current timepoint
		if(nTimeRange == 0)
		{

			nInt[0][3] = roiIn.getTimePoint();
			nInt[1][3] = roiIn.getTimePoint();
			
		}
		//all time points
		else
		{
			nInt[0][3] = all_RAI.min(3);
			nInt[1][3] = all_RAI.max(3);
		}

		FinalInterval finBoxInt = new FinalInterval(nInt[0],nInt[1]);
			
		
		//check the time and channel axes!!!
		
		
		IntervalView<T> bboxMeasure = Views.interval(Views.extendZero(all_RAI),finBoxInt);
		
		Img<T> out1 =  Util.getSuitableImgFactory(bboxMeasure, bboxMeasure.getType()).create(bboxMeasure);
		
		IntervalView< T > trans = Views.translate( out1, bboxMeasure.minAsLongArray() );

		final Cursor< T > cursorRoi = roiIn.getSingle3DVolumeCursor(Views.hyperSlice(Views.hyperSlice( trans, 3, 0),3,0) );
		
		final RandomAccess<T> raOut = trans.randomAccess();

		final RandomAccess< T > bboxMeasureRA = bboxMeasure.randomAccess();

		final int [] pos = new int[5];
		//time
		for (int nTP = ( int ) nInt[0][3]; nTP<=nInt[1][3]; nTP++)
			//channels
			for(int nCh = ( int ) nInt[0][4]; nCh<=nInt[1][4];nCh++)
			{
				pos[3]= nTP;
				pos[4]= nCh;
				cursorRoi.reset();
				while ( cursorRoi.hasNext() )
				{
					cursorRoi.fwd();
					cursorRoi.localize( pos );
					raOut.setPosition( pos );
					bboxMeasureRA.setPosition( pos );
					raOut.get().set( bboxMeasureRA.get());
				}
	
			}
		
		return Views.interval( out1, out1 );
	}
    /*
     * Executed in event dispatching thread
     */
    @Override
    public void done() 
    {
    	//see if we have some errors
    	try {
    		get();
    	} 
    	catch (ExecutionException e) {
    		e.getCause().printStackTrace();
    		String msg = String.format("Unexpected problem during ROI bounding box extraction: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} catch (InterruptedException e) {
    		// Process e here
    	}
		//unlock user interaction
    	bt.bInputLock = false;
        bt.setLockMode(false);

    }
}
