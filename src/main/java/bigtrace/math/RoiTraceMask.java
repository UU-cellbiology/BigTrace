package bigtrace.math;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.BooleanArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.NativeBoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.rois.Roi3D;

public class RoiTraceMask < T extends RealType< T > & NativeType< T > > extends BigTraceBGTask
{
    final BigTrace<T> bt;
    
    public IntervalView< T > traceInterval = null;
    
    boolean bMaskInit = false;
    
    IntervalView< NativeBoolType > traceMask;
    
    public OneClickTrace <T> oneclick = null;
    
    public RoiTraceMask(final BigTrace<T> bt_)
	{
		bt = bt_;
    }
    
	@Override
	protected Void doInBackground() throws Exception
	{
		initTraceMask ( traceInterval, true);
		return null;
	}
    
    public void initTraceMask (final IntervalView< T > traceInterval_, boolean bReportProgress)
    {
    	//current data
    	traceInterval = traceInterval_;
    	final long[] dim = Intervals.dimensionsAsLongArray(traceInterval);
    	//make a mask
    	ArrayImg< NativeBoolType, BooleanArray > maskArr = ArrayImgs.booleans( dim );//.unsignedBytes(dim);
    	traceMask = Views.translate(maskArr, traceInterval.minAsLongArray());
    	
    	//fill mask with current ROI shapes
    	final ArrayList<Roi3D> rois = bt.roiManager.rois;
    	int nTotROIs = 0;   	
    	int nCurrRoi = 0;
    	if(bReportProgress)
    	{
    		setProgressState("Creating ROI mask..");
        	
          	for (final Roi3D roi : rois) 
        	{
        		 if (roi.getTimePoint() == bt.btData.nCurrTimepoint)
        		 {
        			 nTotROIs++;
        		 }
        	}
    	}
    	bMaskInit = true;
    	for (final Roi3D roi : rois) 
    	{
    		 if (roi.getTimePoint() == bt.btData.nCurrTimepoint)
    		 {
    			 markROI(roi);
    			 if(bReportProgress)
    			 {
    				 nCurrRoi++;
    				 setProgress( 100*nCurrRoi/nTotROIs );
    			 }
    		 }
    	}
		 if(bReportProgress)
		 {
			 setProgressState("ROI mask created.");
		 }
		
    	//ImageJFunctions.show( traceMask);
    }
    
    public boolean isOccupied(final RealPoint point)
    {    	
    	//see if the point inside the interval
     	if(bMaskInit)
    	{
     		if(!Intervals.contains( traceMask, point ))
     			return false;
     		final long [] posLong = new long[3];
     		for(int d=0;d<3;d++)
     		{
     			posLong[d] = Math.round( point.getFloatPosition( d ));
     		}
     		return traceMask.getAt( posLong ).get();
    	}
		System.err.println("auto-trace mask was not initialized!");
		return false;
    }
    
    /** fast method, does not check boundaries of the mask 
     * or if it is initialized**/
    public boolean isOccupied(final long[] posLong)
    {
 		return traceMask.getAt( posLong ).get();
    }
    
    public void markLocation(final RealPoint point)
    {
     	//see if the point inside the interval
     	if(bMaskInit)
    	{
     		if(!Intervals.contains( traceMask, point ))
     			return;
     		final long [] posLong = new long[3];
     		for(int d = 0; d < 3; d++)
     		{
     			posLong[d] = Math.round( point.getFloatPosition( d ));
     		}
     		traceMask.getAt( posLong ).set( true );
     		
     		return;
     	
    	}
		System.err.println("auto-trace mask was not initialized!");
		return;
    }
    
    public void markInterval(final Interval inputInt)
    {
     	//see if the point inside the interval
     	if(bMaskInit)
    	{
     		Cursor< NativeBoolType > markInt = Views.interval( traceMask, Intervals.intersect( inputInt, traceMask ) ).cursor();
     		while(markInt.hasNext())
     		{
     			markInt.next().set( true );
     		}
     		return;
    	}
		System.err.println("auto-trace mask was not initialized!");
		return;
    }
    
    /** marks ROI in the current mask.
     * does not check timepoint **/
    public void markROI(final Roi3D roi)
    {
    	if(bMaskInit)
    	{
    		final Cursor< NativeBoolType > cursorRoi = roi.getSingle3DVolumeCursor( traceMask );
    		
    		if(cursorRoi != null)
    		{
    			cursorRoi.reset();
    			while ( cursorRoi.hasNext() )
    			{
    				cursorRoi.fwd();
    				
    				if(Intervals.contains( traceMask, cursorRoi ))
    				{
    					cursorRoi.get().set( true );
    				}
    			}
    		}
    	}
    	else
    	{
    		System.err.println("auto-trace mask was not initialized!");
    	}
    }
    
    public ValuePair<Double, RealPoint> findMaskedMax()
    {
    	final double [] maxLocation = new double[3];
    	double maxVal = (-1)*Double.MAX_VALUE;
    	//double [] cursorLoc = new double[3];
    	//double [] maskLoc = new double[3];
    	if(bMaskInit)
    	{
    		// create a cursor for the image (the order does not matter)
    		final Cursor< T > cursor = traceInterval.localizingCursor();
    		final Cursor< NativeBoolType > cursorMask = traceMask.localizingCursor();

    		T currVal;
    		while(cursor.hasNext())
    		{
    			cursor.fwd();    			
    			if(!cursorMask.next().get())
    			{ 
    				currVal = cursor.get();
    				if(currVal.getRealDouble() > maxVal)
    				{
    					maxVal = currVal.getRealDouble();
    					cursor.localize( maxLocation );
    					//maxLocation.setPosition( cursor );
    				}
    			}
    			
    		}
 
    	}
    	else
    	{
    		System.err.println("auto-trace mask was not initialized!");
    	}
    	return new ValuePair< >(maxVal, new RealPoint(maxLocation));
    }
    
    public ValuePair<Double, RealPoint> findMaskedMaxAboveThreshold(final double dThreshold)
    {
    	final double [] maxLocation = new double[3];
    	double maxVal = (-1)*Double.MAX_VALUE;
    	//double [] cursorLoc = new double[3];
    	//double [] maskLoc = new double[3];
    	if(bMaskInit)
    	{
    		// create a cursor for the image (the order does not matter)
    		final Cursor< T > cursor = traceInterval.localizingCursor();
    		final Cursor< NativeBoolType > cursorMask = traceMask.localizingCursor();

    		T currVal;
    		boolean bKeepSearch = true;
    		while(bKeepSearch && cursor.hasNext())
    		{
    			cursor.fwd();    			
    			if(!cursorMask.next().get())
    			{ 
    				currVal = cursor.get();
    				if(currVal.getRealDouble() > dThreshold)
    				{
    					maxVal = currVal.getRealDouble();
    					cursor.localize( maxLocation );
    					bKeepSearch = false;
    					//maxLocation.setPosition( cursor );
    				}
    			}
    			
    		}
 
    	}
    	else
    	{
    		System.err.println("auto-trace mask was not initialized!");
    	}
    	return new ValuePair< >(maxVal, new RealPoint(maxLocation));
    }


	
	@Override
	public void done() 
	{
		//see if we have some errors
		try {

			get();
		} 
		catch (ExecutionException e) 
		{
			e.getCause().printStackTrace();
			String msg = String.format("Unexpected problem during one-click tracing: %s", 
					e.getCause().toString());
			System.out.println(msg);
		} 
		catch (InterruptedException e) 
		{
			// Process e here
		}
		catch (Exception e)
		{

			//System.out.println("Tracing interrupted by user.");
			setProgressState("Tracing interrupted by user.");
			setProgress(100);	
		}
		if(oneclick != null)
		{
			oneclick.traceMask = this;
			oneclick.execute();
		}

	}
}
