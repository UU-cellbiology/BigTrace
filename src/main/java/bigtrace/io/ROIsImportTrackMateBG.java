package bigtrace.io;

import java.awt.Color;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

import javax.swing.DefaultListModel;
import javax.swing.SwingWorker;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.rois.Point3D;
import bigtrace.rois.Roi3D;
import ij.IJ;
import net.imglib2.RealPoint;

public class ROIsImportTrackMateBG  extends SwingWorker<Void, String> implements BigTraceBGWorker
{
	
	private String progressState;
	public int nImportColor = 0;
	public BigTrace<?> bt;
	public String sFilename;
	String sFinalOut = "";
	
	
	@Override
	public String getProgressState() {
		
		return progressState;
	}

	@Override
	public void setProgressState(String state_) {
		
		progressState = state_;
		
	}

	@Override
	protected Void doInBackground() throws Exception {
		
		int nTotTracks = 0;
		long nTotParticles = 0;
		int nTrack = 0;
    	int nFrameRead;
    	int nFrame;
    	
		ArrayList<Roi3D> allRois = new ArrayList<>();
		Color cCurrentTrackColor = null;
		//enter locked mode

		try
        {

        	
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        	InputStream in = new FileInputStream(sFilename);
        	XMLStreamReader streamReader = inputFactory.createXMLStreamReader(in);
        	int nPoint = 0;
        	RealPoint vertex = new RealPoint();
        	while (streamReader.hasNext()) 
        	{
                if (streamReader.isStartElement()) 
                {
                    switch (streamReader.getLocalName()) 
                    {
	                    case "Tracks": 
	                    {
	                    	IJ.log("Importing total of: " + streamReader.getAttributeValue(0) + " tracks. "); 
	                    	nTotTracks = Integer.parseInt(streamReader.getAttributeValue(0));
	
	                        break;
	                    }
	                    case "particle": 
	                    {
	                    	nPoint = 1;
	                    	nTrack = nTrack + 1;
	                    	cCurrentTrackColor = SwitchingColorPalette(nTrack);
	                    	try {
							  Thread.sleep(1);
	                    	} catch (InterruptedException ignore) {}
	                    	setProgress(nTrack*100/nTotTracks);
	                    	setProgressState("importing track #"+Integer.toString(nTrack) +" of "+Integer.toString(nTotTracks));
	                        break;
	                    }
	                    case "detection": 
	                    {
	                    	nTotParticles++;
	                    	//IJ.log(streamReader.getAttributeValue(0));
	                    	nFrameRead = Integer.parseInt(streamReader.getAttributeValue(0));
	                    	
	                    	nFrame = Math.min(Math.max(0, nFrameRead), bt.btData.nNumTimepoints - 1);
	                    	if(nFrame!=nFrameRead)
	                    	{
	                    		IJ.log("Warning: time frame is outside dataset range for track "+Integer.toString(nTrack)+" particle "+Integer.toString(nPoint)+".");
	                    	}

	                    	Point3D roiP = (Point3D) bt.roiManager.makeRoi(Roi3D.POINT, nFrame);
	                    	roiP.setName("track_"+Integer.toString(nTrack)+"_point_"+Integer.toString(nPoint));
	                    	
	                    	vertex = new RealPoint(Float.parseFloat(streamReader.getAttributeValue(1))/bt.btData.globCal[0],
	                    			               Float.parseFloat(streamReader.getAttributeValue(2))/bt.btData.globCal[1],
	                    			               Float.parseFloat(streamReader.getAttributeValue(3))/bt.btData.globCal[2]);
							roiP.setVertex(vertex);
							if(nImportColor == 0)
							{
								roiP.setPointColorRGB(cCurrentTrackColor);
							}
							allRois.add(roiP);
	                    	nPoint++;
	                        break;
	                    }
                    }
                } 
                streamReader.next();           
        	}
        }
        catch (Exception em)
        {
        	 IJ.log("Error reading "+sFilename);
        	 IJ.log(em.getMessage());
        }
		
		setProgress(100);
		bt.roiManager.rois.clear();
		for(final Roi3D roi: allRois)
		{
			bt.roiManager.rois.add( roi );
		}		
		
		DefaultListModel<String> listModelin = new  DefaultListModel<>();
		for (int i = 0; i < allRois.size(); i++)
		{
			listModelin.addElement(allRois.get(i).getName());
		}
		bt.roiManager.listModel = listModelin;
		bt.roiManager.jlist.setModel(bt.roiManager.listModel);
		sFinalOut="importing TrackMate tracks done.";
		setProgressState(sFinalOut);
		IJ.log("Imported " + Integer.toString(nTotTracks)+ " tracks, created "+Long.toString(nTotParticles)+" point ROIs."); 
		
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
        setProgressState(sFinalOut);
        bt.repaintBVV();

    }
    
	//function given an integer number 
	//returns color from a list of 8 very distinct numbers 
	Color SwitchingColorPalette(long nNumber)	
	{
		int nColor = (int) (nNumber%8);	
		switch(nColor){
		case 0:
			return Color.blue;
		case 1:
			return Color.cyan;
		case 2:
			return Color.green;
		case 3:
			return Color.magenta;
		case 4:
			return Color.orange;
		case 5:
			return Color.pink;
		case 6:
			return Color.red;
		case 7:
			return Color.yellow;
		default:
			return Color.white;		
		
		}				
	}
	
}
