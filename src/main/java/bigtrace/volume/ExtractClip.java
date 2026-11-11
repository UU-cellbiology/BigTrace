package bigtrace.volume;

import java.nio.file.Path;
import java.nio.file.Paths;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.io.BoxHDF5Saver;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class ExtractClip < T extends RealType< T > & NativeType< T > > extends BigTraceBGTask
{

	final BigTrace<T> bt;
	
	Calibration cal;
	
	/** 0 - show in ImageJ, 1 - save as TIFF, 2 - save as BDV XML/HDF5 **/
	int nOutput;
	
	public int nMinTimePoint, nMaxTimePoint;
	
	/** output path and file name without extension (BDV XML/HDF5 output) **/
	String sFullPathNoExt = null;

	public ExtractClip( final BigTrace<T> bt_, final int nTimePointMin_, final int nTimePointMax_, final int nOutput_)
	{
		super();
		
		bt = bt_;
		nMinTimePoint = nTimePointMin_;
		nMaxTimePoint = nTimePointMax_;
		nOutput = nOutput_;
		cal = new Calibration();
	}
	
	/** for BDV XML/HDF5 output: path and file name without extension (.xml/.h5 are added) **/
	public void setOutputPath(final String sFullPathNoExt_)
	{
		sFullPathNoExt = sFullPathNoExt_;
	}
	@Override
	protected Void doInBackground() throws Exception {

		try {
			  Thread.sleep(1);
		  } catch (InterruptedException ignore) {}
		setProgress(0);
		setProgressState("extracting volume..");
		
		//get the all data RAI
		//XYZTC
		RandomAccessibleInterval<T> full_RAI = bt.btData.getAllDataRAI();
		
		FinalInterval cropInt = new FinalInterval (full_RAI);
		long[] cropMin = cropInt.minAsLongArray();
		long[] cropMax = cropInt.maxAsLongArray();
		for(int d = 0; d < 3; d++)
		{
			cropMin[d] = bt.btData.nDimCurr[0][d];
			cropMax[d] = bt.btData.nDimCurr[1][d];
		}
		cropMin[3] = nMinTimePoint;
		cropMax[3] = nMaxTimePoint;
		cropInt = new FinalInterval(cropMin,cropMax);
			
		//output calibration
		cal.setUnit(bt.btData.sVoxelUnit);
		cal.setTimeUnit(bt.btData.sTimeUnit);
		cal.pixelWidth = bt.btData.globCal[0];
		cal.pixelHeight = bt.btData.globCal[1];
		cal.pixelDepth = bt.btData.globCal[2];
		
		if(nOutput == 2)
		{
			//BDV XML/HDF5, placed at the clipped position in the original volume
			if(sFullPathNoExt != null)
			{
				setProgressState("saving clipped volume as HDF5 (see log)...");
				new BoxHDF5Saver<>(bt, Views.interval(full_RAI, cropInt), cropMin, sFullPathNoExt).export();
			}
		}
		else
		{
			Path p = Paths.get(bt.btData.sFileNameFullImg);
			String filename = p.getFileName().toString();
			ImagePlus ip = VolumeMisc.wrapImgImagePlusCal(Views.interval(full_RAI, cropInt), filename + "_crop", cal, bt.btData.nNumTimepoints);
			if(nOutput == 0)
			{
				ip.show();
			}
			else
			{
				IJ.saveAsTiff(ip, null);
			}
		}
		
		try {
			  Thread.sleep(1);
		  } catch (InterruptedException ignore) {}
		setProgress(100);
		setProgressState("extracting volume...done.");
		
		return null;
	}

    @Override
    public void done() 
    {
		//unlock user interaction
    	bt.bInputLock = false;
        bt.setLockMode(false);

    }
}
