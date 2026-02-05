package bigtrace.math;
import java.util.concurrent.ExecutionException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingWorker;

import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.rois.LineTrace3D;

public class FullAutoTrace < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker
{
	final BigTrace<T> bt;
	
	private String progressState;
	
	public int nFirstTP;
	
	public int nLastTP;
	
	public double dAutoMinStartTraceInt = 128.;
	
	public int nAutoMinPointsCurve = 0;
	
	final OneClickTrace<T> oneClickTrace = new OneClickTrace<>();
	
	final RoiTraceMask<T> mask;
	
	public JButton butAuto = null;
	
	public ImageIcon tabIconRestore = null;
	
	public FullAutoTrace(BigTrace<T> bt)
	{
		this.bt = bt;
		mask = new RoiTraceMask<>(bt);
	}
	
	@Override
	public String getProgressState()
	{
		return progressState;
	}
	@Override
	public void setProgressState(String state_)
	{
		progressState = state_;
		firePropertyChange( "ProgressBarString", "a", "b");
	}
	
	@Override
	protected Void doInBackground() throws Exception 
	{	
		runFullAutoTrace();
		return null;
	}

	
	public void runFullAutoTrace()
	{
		
		oneClickTrace.bNewTrace = true;
		oneClickTrace.bUnlockInTheEnd = false;
		oneClickTrace.bUpdateProgressBar = false;
		oneClickTrace.bt = this.bt;	
		oneClickTrace.bInsertROI = false;
		oneClickTrace.bUseMask = true;
		
		oneClickTrace.bInit = false;
		oneClickTrace.init();
		
		setProgress(0);
		for(int nTP = nFirstTP; nTP <= nLastTP; nTP++)
		{
			
			bt.viewer.setTimepoint(nTP);
			IntervalView<T> traceIV =  bt.getTraceInterval(bt.btData.bTraceOnlyClipped);	
			mask.initTraceMask( traceIV, false );
			oneClickTrace.traceMask = mask;
			oneClickTrace.fullInput = traceIV;
			
			boolean bKeepTracing = true;
			//int nCount = 0;
			while(bKeepTracing)
			{
				if(isCancelled())
				{
					bt.visualBoxes.bShowTraceBox = false;
					return;				
				}

				ValuePair<Double, RealPoint> newMax = mask.findMaskedMax();
				//final ValuePair<Double, RealPoint> newMax = mask.findMaskedMaxAboveThreshold(dAutoMinStartTraceInt);
				//System.out.println(newMax.getA());

				if(newMax.getA() > dAutoMinStartTraceInt)
				{
					oneClickTrace.startPoint = newMax.getB();			
					setProgressState("current curve start intensity " + Double.toString( newMax.getA() ));

					oneClickTrace.runTracing();
					mask.markLocation(newMax.getB() );
					if(!oneClickTrace.bStartLocationOccupied)
					{
						mask.markROI( bt.roiManager.getActiveRoi() );
					}
					if(bt.roiManager.getActiveRoi() instanceof LineTrace3D)
					{
						final LineTrace3D newtrace = (LineTrace3D)bt.roiManager.getActiveRoi();
						if(newtrace.getNumberOfPointsInJointSegment() < nAutoMinPointsCurve)
						{
							bt.roiManager.deleteActiveROI();
						}
					}
				}
				else
				{
					bKeepTracing = false;
				}
			}
			setProgress(100 * ( nTP - nFirstTP + 1)/( nLastTP - nFirstTP + 1));
		}
		oneClickTrace.releaseMultiThread();
		//System.out.println("done");
		//System.out.println(nCount);
		return;
		
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
    	catch (ExecutionException e) 
    	{
    		e.getCause().printStackTrace();
    		String msg = String.format("Unexpected error during auto-tracing: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} 
    	catch (InterruptedException e) 
    	{
    		// Process e here
    	}
    	catch (Exception e)
    	{
    		bt.visualBoxes.bShowTraceBox = false;
        	setProgressState("Auto-tracing interrupted by user.");
        	setProgress(100);	
    	}
    	
    	if(butAuto != null && tabIconRestore != null)
    	{
    		butAuto.setIcon( tabIconRestore );
    		butAuto.setToolTipText( "Full auto tracing" );
    	}
    	bt.visualBoxes.bShowTraceBox = false;
    	oneClickTrace.releaseMultiThread();
    	
    	//unlock user interaction
    	bt.bInputLock = false;
    	bt.setLockMode(false);
    }
    
}
