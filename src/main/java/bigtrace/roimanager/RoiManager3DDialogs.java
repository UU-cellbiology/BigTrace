package bigtrace.roimanager;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import bigtrace.BigTrace;
import bigtrace.gui.rois.PanelROIProperties;
import bigtrace.gui.rois.PanelROIRenderSettings;
import bigtrace.gui.tracing.PanelOneClickTraceOptions;
import bigtrace.gui.tracing.PanelSemiAutoOptions;
import bigtrace.gui.tracing.PanelTracingOptions;
import bigtrace.io.ROIsIO;
import bigtrace.io.ROIsImportTrackMateBG;

import ij.Prefs;
import ij.io.OpenDialog;
import ij.io.SaveDialog;


public class RoiManager3DDialogs 
{	
	final BigTrace<?> bt;	

	public RoiManager3DDialogs(BigTrace<?> bt_)
	{
		bt = bt_;
	}
	
	public void dialSemiAutoProperties()
	{
		
		final PanelSemiAutoOptions panelSemiAutoOptions = new PanelSemiAutoOptions(bt);

		int reply = JOptionPane.showConfirmDialog(null, panelSemiAutoOptions, "Semi-auto tracing settings", 
													JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (reply == JOptionPane.OK_OPTION) 
		{							
			panelSemiAutoOptions.getSetOptions();
		}
	}	
	
	public void dialOneClickProperties()
	{
		final JTabbedPane tabPane = new JTabbedPane();
		
		////////////TRACING OPTIONS		
		final PanelTracingOptions panelGeneralTrace = new PanelTracingOptions(bt);
			
		////////////ONE-CLICK TRACING OPTIONS		
		final PanelOneClickTraceOptions panelOneClickOptions = new PanelOneClickTraceOptions(bt, true);
		
		//assemble pane
		tabPane.addTab("Tracing", panelGeneralTrace);
		tabPane.addTab("One click trace", panelOneClickOptions);
		int reply = JOptionPane.showConfirmDialog(null, tabPane, "One click tracing settings", 
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (reply == JOptionPane.OK_OPTION) 
		{	
			panelGeneralTrace.getSetOptions();		
			panelOneClickOptions.getSetOptions();	
		}
	}
	
	/** show ROI Properties dialog**/
	public void dialProperties()
	{
		PanelROIProperties panelROIProperties = new PanelROIProperties(bt);
	
		int reply = JOptionPane.showConfirmDialog(null, panelROIProperties, "ROI Properties", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
				
		if (reply == JOptionPane.OK_OPTION) 
		{
			
			panelROIProperties.getSetProperties();
		}
	}
	
	/** show ROI Rendering/ Appearance dialog**/
	public void dialRenderSettings()
	{
		
		final PanelROIRenderSettings roiRenderSettingsPanel = new PanelROIRenderSettings(bt);
		int reply = JOptionPane.showConfirmDialog(null, roiRenderSettingsPanel, "ROI Manager render settings", 
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (reply == JOptionPane.OK_OPTION) 
		{	
			roiRenderSettingsPanel.getSetOptions();
		}
	}
	
	/** show Group visibility dialog **/
	public void dialGroupVisibility()
	{
		Roi3DGroupVisibility groupVis = new Roi3DGroupVisibility(bt.roiManager);
		groupVis.show();
	}
	
	/** Save ROIS dialog and saving **/
	public void diagSaveROIs()
	{
		
		String [] sRoiSaveOptions = new String [] {"Save ROIs BigTrace format","Export interpolated traces CSV", "Export neurite SWC"};	

		String output = (String) JOptionPane.showInputDialog(bt.roiManager.rmPanel, "Save/export ROIs",
				"Save mode:", JOptionPane.QUESTION_MESSAGE, null,
				sRoiSaveOptions, // Array of choices
				sRoiSaveOptions[(int)Prefs.get("BigTrace.SaveRoisMode", 0)]);
		if(output == null)
			return;

		int nSaveMode = 0;

		for (int i = 0; i < 3; i++)
		{
			if(output.equals( sRoiSaveOptions[i] ))
			{
				nSaveMode = i;
			}
		}
        Prefs.set("BigTrace.SaveRoisMode", nSaveMode);

        
		String filename = "";
		SaveDialog sd;
		
		Path pathIn = Paths.get(bt.btData.sFileNameFullImg);
		String sFolder = pathIn.getParent().toString();
		String fileNamePrefix = pathIn.getFileName().toString();
		String pathOut = "";
		switch (nSaveMode)
		{
		case 1: 
			filename = fileNamePrefix + "_traces";
			sd = new SaveDialog("Export ROIs ", sFolder, filename, ".csv");
			pathOut = sd.getDirectory();
			if (pathOut == null)
				return;
			filename = pathOut + sd.getFileName();
			break;
		case 2: 
			filename = fileNamePrefix + "_traces";
			sd = new SaveDialog("Export ROIs to SWC ", sFolder, filename, ".swc");
			pathOut = sd.getDirectory();
			if (pathOut == null)
				return;
			break;
		default: 
			filename = fileNamePrefix + "_btrois";
			sd = new SaveDialog("Save ROIs ", sFolder, filename, ".csv");
			pathOut = sd.getDirectory();
			if (pathOut == null)
				return;
			filename = pathOut + sd.getFileName();
			break;
		}
		filename = pathOut + sd.getFileName();
		ROIsIO.saveROIs( filename, nSaveMode, bt );
	}
	
	
	/** Load ROIS dialog and saving **/
    void diagLoadROIs()
	{
		String filename;
		
		OpenDialog openDial = new OpenDialog("Load BigTrace ROIs",bt.btData.lastDir, "*.csv");
		
        String path = openDial.getDirectory();
        if (path == null)
        	return;
        
        bt.btData.lastDir = path;
        Prefs.set( "BigTrace.lastDir", bt.btData.lastDir );
        
        filename = path+openDial.getFileName();
     
        String [] sRoiLoadOptions = new String [] {"Clean load ROIs and groups","Append ROIs as undefined group"};	
        String input = (String) JOptionPane.showInputDialog(bt.roiManager.rmPanel, "Loading ROIs",
                "Load mode:", JOptionPane.QUESTION_MESSAGE, null,
                sRoiLoadOptions, // Array of choices
                sRoiLoadOptions[(int)Prefs.get("BigTrace.LoadRoisMode", 0)]);
        
        if(input == null)
        	 return;
        
        int nLoadMode = 0;
        
        if(input.equals(sRoiLoadOptions[1]))
        {
        	nLoadMode = 1;
        }
        
        Prefs.set("BigTrace.LoadRoisMode", nLoadMode);
        ROIsIO.loadROIs(filename, nLoadMode, bt);        
	}
    
	/** Import ROIs dialog **/
	public void diagImportROIs()
	{
	      
        String [] sRoiImportOptions = new String [] {"Points from TrackMate XML (Export)","Points from CSV (coming soon)"};
		
        String input = (String) JOptionPane.showInputDialog(bt.roiManager.rmPanel, "Importing ROIs",
                "Import:", JOptionPane.QUESTION_MESSAGE, null, // Use default icon
                sRoiImportOptions, // Array of choices
                sRoiImportOptions[(int)Prefs.get("BigTrace.ImportRoisMode", 0)]);

        if(input == null)
        	return;
        if(input.isEmpty())
        	return;
        int nImportMode;
        if(input.equals("Points from TrackMate XML (Export)"))
        {
        	nImportMode = 0;
        	diagImportTrackMate();
        }
        else
        {
        	nImportMode = 1;
        }
        Prefs.set("BigTrace.ImportRoisMode", nImportMode);
	}
	
	public void diagImportTrackMate()
	{
		String filename;
		OpenDialog openDial = new OpenDialog("Import TrackMate XML",bt.btData.lastDir, "*.xml");
		
        String path = openDial.getDirectory();
        if (path==null)
        	return;
        bt.btData.lastDir = path;
        Prefs.set( "BigTrace.lastDir", bt.btData.lastDir );
        filename = path + openDial.getFileName();
        
        String [] sTMColorOptions = new String [] {"Random color per track","Current active group color"};
		
        String inputColor = (String) JOptionPane.showInputDialog(bt.roiManager.rmPanel, "Coloring ROIs",
                "For color, use:", JOptionPane.QUESTION_MESSAGE, null, // Use
                                                                                // default
                                                                                // icon
                sTMColorOptions, // Array of choices
                sTMColorOptions[(int)Prefs.get("BigTrace.ImportTMColorMode", 0)]);
        
        if(inputColor == null)
        	return;
        if(inputColor.isEmpty())
        	return;
        int nImportColor;
        
        if(inputColor.equals("Random color per track"))
        {
        	nImportColor = 0;
        }
        else
        {
        	nImportColor = 1;
        }
        
        Prefs.set("BigTrace.ImportTMColorMode", nImportColor);		

       	bt.roiManager.rois.clear();
        bt.roiManager.rmPanel.listModel.clear();
        
        bt.bInputLock = true;
        bt.setLockMode(true);
        
        ROIsImportTrackMateBG importTask = new ROIsImportTrackMateBG();
        importTask.nImportColor = nImportColor;
        importTask.sFilename = filename;
        importTask.bt = this.bt;
        importTask.addPropertyChangeListener(bt.btPanel);
        importTask.execute();
	}
}
