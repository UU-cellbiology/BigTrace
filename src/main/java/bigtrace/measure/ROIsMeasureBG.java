package bigtrace.measure;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.rois.Roi3D;

public class ROIsMeasureBG extends SwingWorker<Void, String> implements BigTraceBGWorker
{

	public ArrayList<MeasureValues> vals;
	public BigTrace<?> bt;
	public ArrayList<Roi3D> rois;
	private String progressState;
	public boolean resetTable = false;
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

    	final int nRoiN = rois.size();
    	vals = new ArrayList<>();
    	setProgressState("measuring ROIs...");
    	setProgress(0);
		for(int i = 0; i<nRoiN;i++)
		{
			setProgress((i+1)*100/nRoiN);
			setProgressState("measuring ROI #"+Integer.toString(i+1)+" of "+Integer.toString(nRoiN)+"...");
			vals.add(bt.roiManager.roiMeasure.measureRoi(rois.get(i)));
		}
		return null;
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
    		String msg = String.format("Unexpected error during measurements: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} 
    	catch (InterruptedException e) 
    	{
    		// Process e here
    	}
    	//show results
    	//measure all -> reset
    	if(resetTable)
    	{
    		bt.roiManager.roiMeasure.resetTable(vals);
    	}
    	//measure one ROI -> update table
    	else
    	{
    		bt.roiManager.roiMeasure.updateTable(vals.get(0), true);
    	}
		//unlock user interaction
    	bt.bInputLock = false;
    	bt.setLockMode(false);
		setProgress(100);
		setProgressState("measuring ROIs done.");
    }

}
