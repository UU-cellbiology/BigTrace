package bigtrace.rois;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import net.imglib2.RealPoint;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.BigTraceData;
import ij.IJ;

public class RoiBGScaler extends BigTraceBGTask
{
	final BigTrace<?> bt;
	public boolean bMacroMessage = false;
	final double [] scaleFactor;
	final List<Roi3D> scaleROIs;
	final String [] sAxes = new String[] {"X ", "Y ", "Z "};
	
	public RoiBGScaler(final BigTrace<?> bt_, final List<Roi3D> scaleROIs_, final double [] scaleFactor_)
	{
		bt = bt_;
		scaleFactor = new double[3];
		for (int d = 0; d < 3; d++)
		{
			scaleFactor[d] = scaleFactor_[d];
		}
		scaleROIs = scaleROIs_;
	}
	
	@Override
	protected Void doInBackground() throws Exception 
	{
		scaleROIs();
		return null;
	}
	
	void scaleROIs()
	{
		final double sizeFactor = getSizeFactor(bt.btData, scaleFactor);
		for(final Roi3D roi:scaleROIs)
		{
			roi.scale( scaleFactor, sizeFactor );
		}
		bt.roiManager.repaintBVV();
	}
	
	public static double getSizeFactor(final BigTraceData<?> btdata, final double [] scaleFactor)
	{
		double newMin = Double.MAX_VALUE;
		for(int d = 0; d < 3; d++ )
		{
			newMin = Math.min( newMin, Math.abs( btdata.globCal[d] * scaleFactor[d]));
		}
		return newMin / btdata.dMinVoxelSize;
	}
	
	public static ArrayList<RealPoint> scaleRealPointArray(final List<RealPoint> arr, final double [] coordFactor)
	{
		final ArrayList<RealPoint> newVert = new ArrayList<>();
		for(int i = 0; i < arr.size(); i++)
		{
			final RealPoint newV = new RealPoint(3);
			final RealPoint oldV = arr.get( i );
			for(int d = 0; d < 3; d++)
			{
				newV.setPosition( oldV.getDoublePosition( d ) * coordFactor[d], d );
			}
			newVert.add( newV );
		}
		return newVert;
	}
	
    @Override
    public void done() 
    {
    	//see if we have some errors
    	try {
    		get();
    	} 
    	catch (ExecutionException e) {
    		e.getCause().printStackTrace();
    		String msg = String.format("Unexpected problem during straightening: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} catch (InterruptedException e) {
    		// Process e here
    	}
		//unlock user interaction
    	bt.bInputLock = false;
        bt.setLockMode(false);
        if(bMacroMessage)
        {
        	String sScales = "";
        	for(int d = 0; d < 3; d++)
        		sScales = sScales + sAxes[d] + Double.toString( scaleFactor [d]);
        	IJ.log( "BigTrace: scaled ROIs with factors " + sScales);
        	
        }

    }
}
