package bigtrace.tracks;


import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;
import javax.swing.ImageIcon;
import javax.swing.JButton;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.math.OneClickTrace;
import bigtrace.math.RoiTraceMask;
import bigtrace.measure.MeasureValues;
import bigtrace.rois.AbstractCurve3D;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Roi3D;
import bigtrace.rois.Roi3DGroup;
import bigtrace.volume.VolumeMisc;

public class CurveTracker < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker
{	
	final BigTrace<T> bt;
	
	public int nFirstTP;
	
	public int nLastTP;
	
	public int nNextFrame = 0;
	
	public int nBoxExpand;
	
	Roi3D currentRoi;
	
	MeasureValues oldVect;
	
	MeasureValues newVect;
	
	final OneClickTrace<T> oneClickTask = new OneClickTrace<>();
	
	Roi3DGroup newGroupTrack;
	
	final RandomAccessibleInterval<T> full_RAI;
	
	private String progressState;
	
	JButton butTrack = null;
	
	ImageIcon tabIconTrain = null; 
	
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
	
	public CurveTracker(BigTrace<T> bt)
	{
		this.bt = bt;
		full_RAI = bt.btData.getAllDataRAI();	
	}
	
	@Override
	protected Void doInBackground() throws Exception 
	{
	
		int nInitialTimePoint = bt.btData.nCurrTimepoint;		
		
		final Roi3D initialRoi =  bt.roiManager.getActiveRoi();
		currentRoi = initialRoi;
		
		int nTotTP = nLastTP - nFirstTP;
		int nTPCount = 0;
		setProgressState("tracking curves over time...("+Integer.toString( 0 )+"/"+Integer.toString( nTotTP )+")");
		setProgress(0);	
		
		//make a New Group
		newGroupTrack = new Roi3DGroup( currentRoi, getNewGroupNameInteger()); 
		
		bt.roiManager.addGroup( newGroupTrack );		
		bt.roiManager.applyGroupToROI( currentRoi, newGroupTrack  );
		
		//get direction between ends of the current ROI
		oldVect = new MeasureValues();
		newVect = new MeasureValues();
		((AbstractCurve3D)currentRoi).getEndsDirection(oldVect, bt.btData.globCal);
		
		//int nTP = nInitialTimePoint+1; 
		
		oneClickTask.bNewTrace = true;
		oneClickTask.bUnlockInTheEnd = false;
		oneClickTask.bUpdateProgressBar = false;
		oneClickTask.bt = this.bt;
		oneClickTask.bInit = false;
		
		if(bt.btData.bTrackingUseMask)
		{
			oneClickTask.traceMask = new RoiTraceMask<>(bt);
			oneClickTask.bUseMask = true;
		}
		
		boolean bTracing = true;
	
		//tracing back in time
		oneClickTask.bInsertROI = true;
		oneClickTask.nInsertROIInd = bt.roiManager.activeRoi.get();
		oneClickTask.init();
		for(int nTP = nInitialTimePoint-1; nTP >= nFirstTP && bTracing; nTP--)
		{

			bTracing = getNextTrace(nTP);
			nTPCount++;
			if(isCancelled())
			{
				bt.visualBoxes.bShowTraceBox = false;
				oneClickTask.releaseMultiThread();
				return null;				
			}
			setProgressState("tracking curve over time...("+Integer.toString( nTPCount )+"/"+Integer.toString( nTotTP )+")");
			setProgress(100*nTPCount/nTotTP);	
		}
		
		//tracing forward in time		
		currentRoi = initialRoi;
		((AbstractCurve3D)currentRoi).getEndsDirection(oldVect, bt.btData.globCal);
		bTracing = true;
		oneClickTask.bInsertROI = false;
		for(int nTP = nInitialTimePoint+1; nTP<=nLastTP && bTracing; nTP++)
		{

			bTracing = getNextTrace(nTP);
			nTPCount++;
			if(isCancelled())
			{
				bt.visualBoxes.bShowTraceBox = false;
				oneClickTask.releaseMultiThread();
				return null;		
			}
			setProgressState("tracking curve over time...("+Integer.toString( nTPCount )+"/"+Integer.toString( nTotTP )+")");
			setProgress(100*nTPCount/nTotTP);	
		}
		
    	setProgressState("tracking finished, track of " + Integer.toString( nTPCount ) + " frames found.");
    	setProgress(100);	
		return null;
	}
	
	
	boolean getNextTrace(int nTP)
	{
		boolean bTracing = true;
		
		RealPoint rpMax = new RealPoint(3);
		
		bt.viewer.setTimepoint(nTP);		

		
		//use bounding box
		if(nNextFrame == 0)
		{
			final Interval boxNext = Intervals.intersect( bt.btData.getDataCurrentSourceFull(),Intervals.expand(currentRoi.getBoundingBox(),nBoxExpand));
			final long [][] nInt = new long [2][5];
			boxNext.min( nInt[0] );
			boxNext.max( nInt[1] );
			//set time point
			nInt[0][3] = nTP;
			nInt[1][3] = nTP;
			//set channel
			nInt[0][4] = bt.btData.nChAnalysis; 
			nInt[1][4] = bt.btData.nChAnalysis;	
			IntervalView<T> searchBox = Views.interval( full_RAI, new FinalInterval(nInt[0],nInt[1]) );
			VolumeMisc.findMaxLocation(searchBox,  rpMax );
		}
		else
		{
			findMaxLocationROIshape(bt.getTraceInterval(bt.btData.bTraceOnlyClipped), (AbstractCurve3D)currentRoi, rpMax );
		}
		//ImageJFunctions.show( searchBox,"Test");
		
		final IntervalView<T> traceIV =  bt.getTraceInterval(bt.btData.bTraceOnlyClipped);			
		oneClickTask.fullInput = traceIV;
		oneClickTask.startPoint = rpMax;
		if(bt.btData.bTrackingUseMask)
		{
			oneClickTask.traceMask.initTraceMask( traceIV, true );
			
		}
		
		oneClickTask.runTracing();
		//oneClickTask.releaseMultiThread();

		//get the new box
		//currentRoi = bt.roiManager.rois.get(bt.roiManager.rois.size()-1);
		currentRoi = bt.roiManager.getActiveRoi();
		//found just one vertex, abort
		if(((LineTrace3D)currentRoi).vertices.size()<2)
		{
			bt.roiManager.removeRoi(bt.roiManager.rois.size()-1);
			bTracing = false;
			System.out.println("Very short (one voxel) ROI found, stopping tracing at frame "+ Integer.toString( nTP )+".");
		}
		else
		{
			bt.roiManager.applyGroupToROI( currentRoi, newGroupTrack  );
			((AbstractCurve3D)currentRoi).getEndsDirection(newVect, bt.btData.globCal);
			//if not looking at the same direction
			if(LinAlgHelpers.dot( oldVect.direction.positionAsDoubleArray(),  newVect.direction.positionAsDoubleArray())<0)
			{
				currentRoi.reversePoints();
				for(int d=0;d<3;d++)
				{
					newVect.direction.setPosition((-1)*newVect.direction.getDoublePosition( d ) , d );
				}
				//System.out.println("swapped");

			}
			for(int d=0;d<3;d++)
			{
				oldVect.direction.setPosition(newVect.direction.getDoublePosition( d ) , d );
			}
		}
		return bTracing;
	}
	//findNextTrace(final int nTP);
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
    	catch (ExecutionException e) 
    	{
    		e.getCause().printStackTrace();
    		String msg = String.format("Unexpected error during tracking: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} 
    	catch (InterruptedException e) 
    	{
    		// Process e here
    	}
    	catch (Exception e)
    	{

    		//System.out.println("Tracking interrupted by user.");
    		bt.visualBoxes.bShowTraceBox = false;
    		oneClickTask.releaseMultiThread();
        	setProgressState("Tracking interrupted by user.");
        	setProgress(100);	
    	}
	

    	if(butTrack != null && tabIconTrain != null)
    	{
    		butTrack.setIcon( tabIconTrain );
    		butTrack.setToolTipText( "Track" );
    	}
    	bt.visualBoxes.bShowTraceBox = false;
    	oneClickTask.releaseMultiThread();
    	//unlock user interaction
    	bt.bInputLock = false;
    	bt.setLockMode(false);

    }
    
    String getNewGroupNameInteger()
    {
    	
    	int nCand = 1;
    	boolean bFoundGood = false;
    	boolean bExistsAlready;
    	String sCandName = String.format("%03d", nCand);
    	while (!bFoundGood)
    	{
    		bExistsAlready = false;
    		for (int i=0;i<bt.roiManager.groups.size() && !bExistsAlready;i++)
    		{
    			if(sCandName.equals( bt.roiManager.groups.get( i ).getName() ))
    			{
    				bExistsAlready = true;
    			}
    		}
    		if(bExistsAlready)
    		{
    			nCand++;
    			sCandName = String.format("%03d", nCand);
    		}
    		else
    		{
    			return sCandName;
    		}
    	}
    	return "Error";
    }
    
    void findMaxLocationROIshape(final IntervalView< T > source, final AbstractCurve3D roi, final RealPoint maxLocation )
    {
    	final Cursor< T > cursor = roi.getSingle3DVolumeCursor(( RandomAccessibleInterval< T > ) source);
		T type = cursor.next();
		T max = type.copy();
		maxLocation.setPosition( cursor );
		// loop over the rest of the data and determine min and max value
		while ( cursor.hasNext() )
		{
			// we need this type more than once
			type = cursor.next();
			if(Intervals.contains( source, cursor ))
			{
				if ( type.compareTo( max ) > 0 )
				{
					max.set( type );
					maxLocation.setPosition( cursor );	
				}
			}
		}
		return ;
    }

}
