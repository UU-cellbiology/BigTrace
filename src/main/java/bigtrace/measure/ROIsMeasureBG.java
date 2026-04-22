package bigtrace.measure;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.rois.Roi3D;
import static bigtrace.measure.Measurements.*;
import ij.IJ;

public class ROIsMeasureBG extends SwingWorker<Void, String> implements BigTraceBGWorker
{
	public BigTrace<?> bt;

	public final ArrayList<MeasureValues> vals = new ArrayList<>();

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
		progressState = state_;
	}

	@Override
	protected Void doInBackground() throws Exception 
	{
		runMeasure();
		return null;
	}
	
	public void runMeasure()
	{
    	final int nRoiN = rois.size();
    	setProgressState("measuring ROIs...");
    	setProgress(0);
		for(int i = 0; i < nRoiN; i++)
		{
			setProgress(( i + 1) * 100/nRoiN);
			setProgressState("measuring ROI #" + Integer.toString(i + 1) + " of " + Integer.toString(nRoiN)+"...");
			vals.add(bt.roiManager.roiMeasure.measureRoi(rois.get(i)));
		}
		
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
    	
    	showResultsTable();
		//unlock user interaction
    	bt.bInputLock = false;
    	bt.setLockMode(false);
		setProgress(100);
		setProgressState("measuring ROIs done.");
    }
    
    public void showResultsTable()
    {
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
    	
    }
    
    /** writes obtained result measurements to CSV file **/
    public void saveMeasurementsCSV(String sFilename)
    {
    	if(vals.isEmpty())
    	{
    		IJ.log("Could not find ROIs/measurements, aborting saving.");
    		return;
    	}
    	
    	try {
    		final File file = new File(sFilename);
    		try (FileWriter writer = new FileWriter(file))
    		{
				DecimalFormatSymbols symbols = new DecimalFormatSymbols();
				symbols.setDecimalSeparator('.');
				DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
				
				//HEADER
				final ArrayList< String > header = getHeader();
				writer.write( header.get( 0 ) );
				for(int i = 1; i < header.size(); i++)
				{
					writer.write( "," + header.get( i ) );
				}
				writer.write( "\n");
				final int systemMeasurements = RoiMeasure3D.systemMeasurements;
				//Measurements
				for ( final MeasureValues val: vals)
				{
					//ROI params
					writer.write(val.roiName + ",");					
					writer.write(Roi3D.intTypeToString( val.roiType) + ",");					
					writer.write(val.roiGroupName + ",");
					writer.write(df3.format(val.pointSize) + ",");
					writer.write(df3.format(val.lineThickness));
			    	if(bt.btData.nNumTimepoints > 1)
					{
			    		writer.write("," + Integer.toString(val.nTimePoint ) );
					}
			    	for(int i = 0; i < 9; i++)
			    	{
				    	if((systemMeasurements & RoiMeasure3D.listMeasurements[i]) != 0)
				    	{
				    		writer.write("," + df3.format( val.getFirstNineMeasurements( RoiMeasure3D.listMeasurements[i] ) ));				    		
				    	}
			    	}
			    	if ((systemMeasurements & ENDS_COORDS) != 0)
					{
						for(int nEnd = 0; nEnd < 2; nEnd++)
						{
							for (int d = 0; d < 3; d ++)
							{
								writer.write("," + df3.format( val.ends[nEnd].getDoublePosition(d) ) );
							}
						}
			    		
					}
					if ((systemMeasurements & ENDS_DIR) != 0)
					{
						for (int d = 0; d < 3; d ++)
						{
							writer.write("," + df3.format( val.direction.getDoublePosition(d) ));
						}
					}
			    	writer.write("\n");
				}

    		}
    	} catch (IOException e) {	
    		IJ.log(e.getMessage());

    	}       
    }
    
    ArrayList<String> getHeader()
    {
    	final ArrayList<String> header = new ArrayList<>();
    	header.add( "ROI_Name" );
    	header.add( "ROI_Type" );
    	header.add( "ROI_Group" );
    	header.add( "Point_Size" );
    	header.add( "Line_Thickness" );
    	
    	if(bt.btData.nNumTimepoints > 1)
		{
        	header.add( "ROI_TimePoint" );    		
		}
    	final int systemMeasurements = RoiMeasure3D.systemMeasurements;
    	
    	for(int i = 0; i < 9; i++)
    	{
    		if((systemMeasurements & RoiMeasure3D.listMeasurements[i]) != 0)
    		{	header.add( RoiMeasure3D.colTemplates[ i ] ); }
    	}

    	String [] sXYZ = new String [] {"X", "Y", "Z"};
    	if ((systemMeasurements & ENDS_COORDS) != 0)
		{
			for(int nEnd = 0; nEnd < 2; nEnd++)
			{
				for(int d = 0; d < 3; d++)
				{
					header.add("End_" + Integer.toString(nEnd + 1) + "_" + sXYZ[d]);
				}
			}
		}

		if ((systemMeasurements & ENDS_DIR) != 0)
		{
			for(int d = 0; d < 3; d++)
			{
				header.add("Direction_" + sXYZ[d]);
			}
		}

    	return header;
    }
}
