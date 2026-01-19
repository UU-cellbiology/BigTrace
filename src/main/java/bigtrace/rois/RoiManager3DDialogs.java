package bigtrace.rois;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import bdv.tools.brightness.ColorIcon;
import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.NumberField;
import bigtrace.gui.PanelOneClickTraceOptions;
import bigtrace.gui.PanelTracingOptions;
import bigtrace.io.ROIsIO;
import bigtrace.io.ROIsImportTrackMateBG;
import ij.Prefs;
import ij.io.OpenDialog;
import ij.io.SaveDialog;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class RoiManager3DDialogs < T extends RealType< T > & NativeType< T > > 
{	
	
	final BigTrace<T> bt;	

	public RoiManager3DDialogs(BigTrace<T> bt_)
	{
		bt = bt_;
	}
	
	public void dialSemiAutoProperties()
	{
		
		final JTabbedPane tabPane = new JTabbedPane();
		GridBagConstraints gbc = new GridBagConstraints();

		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		
		////////////TRACING OPTIONS
		final PanelTracingOptions panelGeneralTrace = new PanelTracingOptions(bt);

		
		////////////SEMI-AUTO TRACING OPTIONS
		final JPanel pSemiAuto = new JPanel(new GridBagLayout());
		
		NumberField nfGammaTrace = new NumberField(4);
		NumberField nfTraceBoxSize = new NumberField(4);
		NumberField nfTraceBoxScreenFraction = new NumberField(4);
		NumberField nfTBAdvance = new NumberField(4);
		
		nfTraceBoxSize.setText(Integer.toString((int)(2.0*bt.btData.nTraceBoxSize)));
		nfTraceBoxScreenFraction.setText(df.format(bt.btData.fTraceBoxScreenFraction));
		nfGammaTrace.setText(df.format(bt.btData.gammaTrace));
		nfTBAdvance.setText(df.format(bt.btData.fTraceBoxAdvanceFraction));
			
		GBCHelper.alighLeft( gbc );
		gbc.gridx = 0;
		gbc.gridy = 0;
		//cd.anchor=GridBagConstraints.WEST;
		pSemiAuto.add(new JLabel("Trace box size (px): "),gbc);
		gbc.gridx++;
		pSemiAuto.add(nfTraceBoxSize,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		//cd.anchor=GridBagConstraints.WEST;
		pSemiAuto.add(new JLabel("Trace box screen fraction (0-1): "),gbc);
		gbc.gridx++;
		pSemiAuto.add(nfTraceBoxScreenFraction,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		//cd.anchor=GridBagConstraints.WEST;
		pSemiAuto.add(new JLabel("Trace box advance [0-center..1-edge]: "),gbc);
		gbc.gridx++;
		pSemiAuto.add(nfTBAdvance,gbc);	

		gbc.gridx = 0;		
		gbc.gridy++;
		pSemiAuto.add(new JLabel("Orientation weight(0-1): "),gbc);
		gbc.gridx++;
		pSemiAuto.add(nfGammaTrace,gbc);
	
		//assemble pane
		tabPane.addTab("Tracing", panelGeneralTrace);
		tabPane.addTab("Semi auto", pSemiAuto);

		int reply = JOptionPane.showConfirmDialog(null, tabPane, "Semi-auto tracing settings", 
													JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (reply == JOptionPane.OK_OPTION) 
		{							
			bt.btData.nTraceBoxSize = (int)(Integer.parseInt(nfTraceBoxSize.getText())*0.5);
			Prefs.set("BigTrace.lTraceBoxSize", bt.btData.nTraceBoxSize);
		
			//TRACING OPTIONS
			panelGeneralTrace.getSetOptions();			
			
			bt.btData.fTraceBoxScreenFraction = Float.parseFloat(nfTraceBoxScreenFraction.getText());
			Prefs.set("BigTrace.dTraceBoxScreenFraction", bt.btData.fTraceBoxScreenFraction);
			
			bt.btData.fTraceBoxAdvanceFraction = Float.parseFloat(nfTBAdvance.getText());
			Prefs.set("BigTrace.fTraceBoxAdvanceFraction", bt.btData.fTraceBoxAdvanceFraction);
			
			bt.btData.gammaTrace = Double.parseDouble(nfGammaTrace.getText());
			Prefs.set("BigTrace.gammaTrace", bt.btData.gammaTrace);
	
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
	
	/** show ROI Appearance dialog**/
	public void dialRenderSettings()
	{
		final RoiManager3D<T> rm = bt.roiManager;
		JTabbedPane tabPane = new JTabbedPane();
		GridBagConstraints cd = new GridBagConstraints();

		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		////////////ROI RENDER OPTIONS
		JPanel pROIrender = new JPanel(new GridBagLayout());
		
		JButton butPointActiveColor = new JButton( new ColorIcon( rm.activePointColor ) );	
		butPointActiveColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose active point color", rm.activePointColor );
			if (newColor!=null)
			{
				rm.selectColors.setColor(newColor, 0);
				butPointActiveColor.setIcon(new ColorIcon(newColor));
			}
			
		});
		
		JButton butLineActiveColor = new JButton( new ColorIcon( rm.activeLineColor ) );	
		butLineActiveColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose active line color", rm.activeLineColor );
			if (newColor!=null)
			{
				rm.selectColors.setColor(newColor, 1);

				butLineActiveColor.setIcon(new ColorIcon(newColor));
			}
			
		});
		
		JCheckBox cbRoiDoubleClickClip = new JCheckBox();
		cbRoiDoubleClickClip.setSelected(bt.btData.bROIDoubleClickClip);
		
		NumberField nfRoiDoubleClickExpand = new NumberField(2);
		nfRoiDoubleClickExpand.setIntegersOnly(true);
		nfRoiDoubleClickExpand.setText(Integer.toString(bt.btData.nROIDoubleClickClipExpand));
		
		String[] sShapeInterpolationType = { "Voxel", "Smooth", "Spline"};
		JComboBox<String> shapeInterpolationList = new JComboBox<>(sShapeInterpolationType);
		shapeInterpolationList.setSelectedIndex(bt.btData.shapeInterpolation);
		
		NumberField nfSmoothWindow = new NumberField(2);
		nfSmoothWindow.setIntegersOnly(true);
		nfSmoothWindow.setText(Integer.toString(bt.btData.nSmoothWindow));
		
		
		String[] sTimeRenderROIs = { "current timepoint", "backward in time", "forward in time"};
		JComboBox<String> sTimeRenderROIsList = new JComboBox<>(sTimeRenderROIs);
		sTimeRenderROIsList.setSelectedIndex(bt.btData.timeRender);
		
		NumberField nfTimeFadeROIs = new NumberField(4);
		nfTimeFadeROIs.setIntegersOnly(true);
		nfTimeFadeROIs.setText(Integer.toString(Math.abs(bt.btData.timeFade)));
		
		
		
		cd.gridx = 0;
		cd.gridy = 0;
		GBCHelper.alighLoose(cd);
		pROIrender.add(new JLabel("Selected ROI point color: "),cd);
		cd.gridx++;
		pROIrender.add(butPointActiveColor,cd);
		cd.gridx = 0;
		cd.gridy++;
		pROIrender.add(new JLabel("Selected ROI line color: "),cd);
		cd.gridx++;
		pROIrender.add(butLineActiveColor,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		pROIrender.add(new JLabel("Clip volume on ROI double-click: "),cd);
		cd.gridx++;
		pROIrender.add(cbRoiDoubleClickClip,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		pROIrender.add(new JLabel("Expand clipping by (voxels): "),cd);
		cd.gridx++;
		pROIrender.add(nfRoiDoubleClickExpand,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		pROIrender.add(new JLabel("ROI Shape interpolation: "),cd);
		cd.gridx++;
		pROIrender.add(shapeInterpolationList,cd);	
		
		cd.gridx = 0;
		cd.gridy++;
		pROIrender.add(new JLabel("Trace smoothing window (points): "),cd);
		cd.gridx++;
		pROIrender.add(nfSmoothWindow,cd);
		

		if(bt.btData.nNumTimepoints > 1)
		{
			cd.gridx = 0;
			cd.gridy++;
			cd.gridwidth = 2;
			cd.anchor = GridBagConstraints.CENTER;
			pROIrender.add(new JLabel("<html>----  <B>Time</B>  ----</html>"),cd);
			cd.gridwidth=1;
			cd.anchor = GridBagConstraints.WEST;
			
			
			cd.gridx = 0;
			cd.gridy++;
			pROIrender.add(new JLabel("Show ROIs over time: "),cd);
			cd.gridx++;
			pROIrender.add(sTimeRenderROIsList,cd);
			cd.gridx = 0;
			cd.gridy++;
			pROIrender.add(new JLabel("Time fade range (frames): "),cd);
			cd.gridx++;
			pROIrender.add(nfTimeFadeROIs,cd);
		}

		////////////ROI SURFACE RENDER 
		JPanel pROIsurface = new JPanel(new GridBagLayout());
		
		NumberField nfSectorNLines = new NumberField(4);
		nfSectorNLines.setIntegersOnly(true);
		nfSectorNLines.setText(Integer.toString(bt.btData.sectorN));
		
		JCheckBox cbWireAA = new JCheckBox();
		cbWireAA.setSelected( bt.btData.wireAntiAliasing );
		
		NumberField nfWireContourStep = new NumberField(4);
		nfWireContourStep.setIntegersOnly(true);
		nfWireContourStep.setText(Integer.toString(bt.btData.wireCountourStep));
		
		NumberField nfCrossSectionGridStep = new NumberField(4);
		nfCrossSectionGridStep.setIntegersOnly(true);
		nfCrossSectionGridStep.setText(Integer.toString(bt.btData.crossSectionGridStep));
		
		String[] sSilhouetteRenderType = { "transparent", "front culling"};
		JComboBox<String> sSilhouetteRenderList = new JComboBox<>(sSilhouetteRenderType);
		sSilhouetteRenderList.setSelectedIndex(bt.btData.silhouetteRender);
		
		NumberField nfSilhouetteDecay = new NumberField(4);
		nfSilhouetteDecay.setText(df.format(bt.btData.silhouetteDecay));
		
		cd.gridx = 0;
		cd.gridy = 0;
		pROIsurface.add(new JLabel("# edges at curve cross-section:"),cd);
		cd.gridx++;
		pROIsurface.add(nfSectorNLines,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		cd.gridwidth = 2;
		cd.anchor = GridBagConstraints.CENTER;
		pROIsurface.add(new JLabel("<html>----  <B>Wire</B> mode  ----</html>"),cd);
		cd.gridwidth = 1;
		cd.anchor = GridBagConstraints.WEST;
		
		cd.gridx = 0;
		cd.gridy++;
		pROIsurface.add(new JLabel("Use anti-aliased lines :"),cd);
		cd.gridx++;
		pROIsurface.add(cbWireAA,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		pROIsurface.add(new JLabel("Distance between curve contours (px):"),cd);
		cd.gridx++;
		pROIsurface.add(nfWireContourStep,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		pROIsurface.add(new JLabel("Plane/cross-section ROI grid step (px): "),cd);
		cd.gridx++;
		pROIsurface.add(nfCrossSectionGridStep,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		cd.gridwidth = 2;
		cd.anchor = GridBagConstraints.CENTER;
		pROIsurface.add(new JLabel("<html>----  <B>Silhouette</B> surface ----</html>"),cd);
		cd.gridwidth=1;
		cd.anchor = GridBagConstraints.WEST;
	
		
		cd.gridx = 0;
		cd.gridy++;
		pROIsurface.add(new JLabel("Silhouette render:"),cd);
		cd.gridx++;
		pROIsurface.add(sSilhouetteRenderList,cd);	
		
		cd.gridx = 0;		
		cd.gridy++;
		pROIsurface.add(new JLabel("Silhouette decay: "),cd);
		cd.gridx++;
		pROIsurface.add(nfSilhouetteDecay,cd);
				
		//assemble pane
		tabPane.addTab("ROI render", pROIrender);
		tabPane.addTab("ROI surface", pROIsurface);
		int reply = JOptionPane.showConfirmDialog(null, tabPane, "ROI Manager render settings", 
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);


		if (reply == JOptionPane.OK_OPTION) 
		{
	
			//ROI appearance
			boolean bUpdateROIs = false;
			boolean bUpdateBVV = false;
			
			Color tempC;
			
			tempC = rm.selectColors.getColor(0);
			if(tempC!=null)
			{
				rm.activePointColor = new Color(tempC.getRed(),tempC.getGreen(),tempC.getBlue(),tempC.getAlpha());
				rm.selectColors.setColor(null, 0);
				Prefs.set("BigTrace.activePointColor", tempC.getRGB());
			}
			
			tempC = rm.selectColors.getColor(1);
			if(tempC != null)
			{
				rm.activeLineColor = new Color(tempC.getRed(),tempC.getGreen(),tempC.getBlue(),tempC.getAlpha());
				rm.selectColors.setColor(null, 1);
				Prefs.set("BigTrace.activeLineColor", tempC.getRGB());
			}
			
			bt.btData.bROIDoubleClickClip = cbRoiDoubleClickClip.isSelected();
			Prefs.set("BigTrace.bROIDoubleClickClip", bt.btData.bROIDoubleClickClip );
			
			if(bt.btData.nROIDoubleClickClipExpand != Integer.parseInt(nfRoiDoubleClickExpand.getText()))
			{
				bt.btData.nROIDoubleClickClipExpand = Integer.parseInt(nfRoiDoubleClickExpand.getText());
				Prefs.set("BigTrace.nROIDoubleClickClipExpand", bt.btData.nROIDoubleClickClipExpand);
			}
			
			if(bt.btData.sectorN != Integer.parseInt(nfSectorNLines.getText()))
			{
				bt.btData.sectorN = Integer.parseInt(nfSectorNLines.getText());
				Prefs.set("BigTrace.nSectorN", bt.btData.sectorN);
				bUpdateROIs  = true;
			}
			
			if(bt.btData.wireAntiAliasing != cbWireAA.isSelected())
			{
				bt.btData.wireAntiAliasing = cbWireAA.isSelected();
				Prefs.set("BigTrace.wireAntiAliasing", bt.btData.wireAntiAliasing);
				bUpdateROIs  = true;
			}
			
			if(bt.btData.wireCountourStep != Integer.parseInt(nfWireContourStep.getText()))
			{
				bt.btData.wireCountourStep = Integer.parseInt(nfWireContourStep.getText());
				Prefs.set("BigTrace.wireCountourStep", bt.btData.wireCountourStep);
				bUpdateROIs  = true;
			}
			
			if(bt.btData.crossSectionGridStep != Integer.parseInt(nfCrossSectionGridStep.getText()))
			{
				bt.btData.crossSectionGridStep = Integer.parseInt(nfCrossSectionGridStep.getText());
				Prefs.set("BigTrace.crossSectionGridStep", bt.btData.crossSectionGridStep);
				bUpdateROIs  = true;
			}
			
			
			if(bt.btData.silhouetteRender != sSilhouetteRenderList.getSelectedIndex())
			{
				bt.btData.silhouetteRender = sSilhouetteRenderList.getSelectedIndex();
				Prefs.set("BigTrace.silhouetteRender", bt.btData.silhouetteRender);
				bUpdateBVV = true;
				//bUpdateROIs  = true;
			}
			
			if(Math.abs(bt.btData.silhouetteDecay - Double.parseDouble(nfSilhouetteDecay.getText()))>0.0001)
			{
				bt.btData.silhouetteDecay = Double.parseDouble(nfSilhouetteDecay.getText());
				Prefs.set("BigTrace.silhouetteDecay",bt.btData.silhouetteDecay);
				bUpdateBVV = true;
				//bUpdateROIs  = true;
			}
			//INTERPOLATION
			
			if(bt.btData.nSmoothWindow != Integer.parseInt(nfSmoothWindow.getText())||bt.btData.shapeInterpolation != shapeInterpolationList.getSelectedIndex())
			{
				bt.btData.nSmoothWindow = Integer.parseInt(nfSmoothWindow.getText());
				Prefs.set("BigTrace.nSmoothWindow", bt.btData.nSmoothWindow);
				bt.btData.shapeInterpolation = shapeInterpolationList.getSelectedIndex();
				Prefs.set("BigTrace.ShapeInterpolation",bt.btData.shapeInterpolation);
				bUpdateROIs  = true;				
			}
			
			//TIME RENDER
			if(bt.btData.nNumTimepoints>1)
			{
				if(bt.btData.timeFade != Integer.parseInt(nfTimeFadeROIs.getText()) || bt.btData.timeRender!= sTimeRenderROIsList.getSelectedIndex())
				{
					bt.btData.timeRender= sTimeRenderROIsList.getSelectedIndex();
					Prefs.set("BigTrace.timeRender",bt.btData.timeRender);
					if(bt.btData.timeRender==0)
					{
						bt.btData.timeFade = 0;
					}
					else
					{
						if(bt.btData.timeRender == 1)
						{
							bt.btData.timeFade = (-1)*Integer.parseInt(nfTimeFadeROIs.getText());
						}
						else
						{
							bt.btData.timeFade = Integer.parseInt(nfTimeFadeROIs.getText());
						}
						Prefs.set("BigTrace.timeFade", bt.btData.timeFade);
					}
					bUpdateBVV = true;	
					//bUpdateROIs  = true;	
				}
			}
			
			
			if(bUpdateROIs)
			{
				rm.updateROIsDisplay();
			}
			else
			{
				if(bUpdateBVV)
				{
					bt.repaintBVV();
				}
			}
			
		}
	}
	
	/** show Group visibility dialog **/
	public void dialGroupVisibility()
	{
		Roi3DGroupVisibility<T> groupVis = new Roi3DGroupVisibility<>(bt.roiManager);
		groupVis.show();
	}
	
	/** Save ROIS dialog and saving **/
	public void diagSaveROIs()
	{
		
		String [] sRoiSaveOptions = new String [] {"Save ROIs BigTrace format","Export interpolated traces CSV", "Export neurite SWC"};	

		String output = (String) JOptionPane.showInputDialog(bt.roiManager, "Save/export ROIs",
				"Save mode:", JOptionPane.QUESTION_MESSAGE, null,
				sRoiSaveOptions, // Array of choices
				sRoiSaveOptions[(int)Prefs.get("BigTrace.SaveRoisMode", 0)]);
		if(output == null)
			return;

		int nSaveMode = 0;

		for (int i=0;i<3; i++)
		{
			if(output.equals( sRoiSaveOptions[i] ))
			{
				nSaveMode = i;
			}
		}
        Prefs.set("BigTrace.SaveRoisMode", nSaveMode);

        
		String filename = "";
		SaveDialog sd;
		String path = "";

		switch (nSaveMode)
		{
		case 1: 
			filename = bt.btData.sFileNameFullImg + "_traces";
			sd = new SaveDialog("Export ROIs ", filename, ".csv");
			path = sd.getDirectory();
			if (path == null)
				return;
			filename = path + sd.getFileName();
			break;
		case 2: 
			filename = bt.btData.sFileNameFullImg + "_traces";
			sd = new SaveDialog("Export ROIs to SWC ", filename, ".swc");
			path = sd.getDirectory();
			if (path == null)
				return;
			break;
		default: 
			filename = bt.btData.sFileNameFullImg + "_btrois";
			sd = new SaveDialog("Save ROIs ", filename, ".csv");
			path = sd.getDirectory();
			if (path == null)
				return;
			filename = path + sd.getFileName();
			break;
		}
		filename = path + sd.getFileName();
		ROIsIO.saveROIs( filename, nSaveMode, bt );
	}
	
	
	/** Load ROIS dialog and saving **/
    void diagLoadROIs()
	{
		String filename;
		
		OpenDialog openDial = new OpenDialog("Load BigTrace ROIs",bt.btData.lastDir, "*.csv");
		
        String path = openDial.getDirectory();
        if (path==null)
        	return;
        
        bt.btData.lastDir = path;
        Prefs.set( "BigTrace.lastDir", bt.btData.lastDir );
        
        filename = path+openDial.getFileName();
     
        String [] sRoiLoadOptions = new String [] {"Clean load ROIs and groups","Append ROIs as undefined group"};	
        String input = (String) JOptionPane.showInputDialog(bt.roiManager, "Loading ROIs",
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
		
        String input = (String) JOptionPane.showInputDialog(bt.roiManager, "Importing ROIs",
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
		
        String inputColor = (String) JOptionPane.showInputDialog(bt.roiManager, "Coloring ROIs",
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
        bt.roiManager.listModel.clear();
        
        ROIsImportTrackMateBG importTask = new ROIsImportTrackMateBG();
        importTask.nImportColor = nImportColor;
        importTask.sFilename = filename;
        importTask.bt = this.bt;
        importTask.addPropertyChangeListener(bt.btPanel);
        importTask.execute();
	}
}
