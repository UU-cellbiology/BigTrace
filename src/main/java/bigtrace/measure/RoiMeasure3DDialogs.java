package bigtrace.measure;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;

import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.GetFolderDialog;
import bigtrace.gui.panels.NumberField;
import bigtrace.rois.AbstractCurve3D;
import bigtrace.rois.CrossSection3D;
import bigtrace.rois.Roi3D;
import bigtrace.volume.ExtractROIBox;
import bigtrace.volume.SplitVolumePlane;
import bigtrace.volume.StraightenCurve;
import ij.IJ;
import ij.Prefs;

public class RoiMeasure3DDialogs < T extends RealType< T > & NativeType< T > > 
{
	final BigTrace<T> bt;	

	public RoiMeasure3DDialogs(BigTrace<T> bt_)
	{
		bt = bt_;
	}
	
	/** dialog for the box around ROI extraction**/
	public void dialExtractROIBox(final Roi3D roiIn)
	{
		
		int nExtractBoxROIList;
		int nExtractRoiType;
		int nExpandROIBox;
		int nTimeRange;
		int nExtractBoxOutput;
		boolean bOnlyVoxelsInsideROI;
		
		final JPanel extractROISettings = new JPanel();
		
		extractROISettings.setLayout(new GridBagLayout());
		
		final GridBagConstraints gbc = new GridBagConstraints();

		final NumberField nfBoxExpand = new NumberField(4);
		nfBoxExpand.setIntegersOnly(true);

		GBCHelper.alighLeft(gbc);
		gbc.gridx = 0;
		gbc.gridy = 0;	
		extractROISettings.add(new JLabel("Extract box around:"),gbc);
		gbc.gridx++;
		final String[] sExtractBoxROIsRange = { "Selected ROI", "All visible ROIs" };
		JComboBox<String> extractBoxRoiList = new JComboBox<>(sExtractBoxROIsRange);
		extractBoxRoiList.setSelectedIndex((int)Prefs.get("BigTrace.nExtractBoxROIList", 0));
		extractROISettings.add(extractBoxRoiList, gbc);	
		
		gbc.gridy++;
		gbc.gridx = 0;	
		extractROISettings.add(new JLabel("Extract only voxels inside ROI:"),gbc);
		gbc.gridx++;
		JCheckBox extractVoxelsInsideROI = new JCheckBox();
		extractVoxelsInsideROI.setSelected( Prefs.get("BigTrace.bOnlyVoxelsInsideROI", false) );
		extractROISettings.add(extractVoxelsInsideROI,gbc);
		
		gbc.gridy++;
		gbc.gridx = 0;	
		extractROISettings.add(new JLabel("Box size:"),gbc);
		gbc.gridx++;
		String[] sExtractRoiType = { "Tight", "Enlarge/shrink below" };
		JComboBox<String> extractBoxTypeList = new JComboBox<>(sExtractRoiType);
		extractBoxTypeList.setSelectedIndex((int)Prefs.get("BigTrace.nExtractRoiType", 0));
		extractROISettings.add(extractBoxTypeList,gbc);
		
		nfBoxExpand.setText(Integer.toString((int)Prefs.get("BigTrace.nExpandROIBox", 5)));
		gbc.gridy++;
		gbc.gridx = 0;
		extractROISettings.add(new JLabel("Expand box by (px):"),gbc);
		gbc.gridx++;
		extractROISettings.add(nfBoxExpand,gbc);
		
		String[] sExtractBoxTime = { "Single ROI's time point", "All time points" };
		JComboBox<String> extractBoxTimeList = new JComboBox<>(sExtractBoxTime);
		if(bt.btData.nNumTimepoints > 1 )
		{
			gbc.gridy++;
			gbc.gridx = 0;
			extractROISettings.add(new JLabel("Time range per ROI:"),gbc);
			extractBoxTimeList.setSelectedIndex((int)Prefs.get("BigTrace.nExtractBoxTime", 0));
			gbc.gridx++;
			extractROISettings.add(extractBoxTimeList,gbc);
			
		}
		gbc.gridy++;
		gbc.gridx = 0;	
		extractROISettings.add(new JLabel("Output:"),gbc);
		gbc.gridx++;
		String[] sExtractBoxOutput = { "show in ImageJ", "save as TIF", "save as BDV XML/HDF5" };
		JComboBox<String> extractBoxOutputList = new JComboBox<>(sExtractBoxOutput);
		extractBoxOutputList.setSelectedIndex((int)Prefs.get("BigTrace.nExtractBoxOutput", 0));
		extractROISettings.add(extractBoxOutputList,gbc);
		
		if(bt.bApplyLLSTransform)
		{
			gbc.gridy++;
			gbc.gridx = 0;	
			extractROISettings.add(new JLabel("Intensity interpolation:"),gbc);
			gbc.gridx++;
			String[] sIntInterpolationType = { "Nearest Neighbor", "Linear", "Lanczos" };
			extractROISettings.add(new JLabel(sIntInterpolationType[bt.btData.intensityInterpolation]),gbc);
		}
		
		int reply = JOptionPane.showConfirmDialog(null, extractROISettings, "Extract box around ROI(s)", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			nExtractBoxROIList = extractBoxRoiList.getSelectedIndex();
			Prefs.set("BigTrace.nExtractBoxROIList", nExtractBoxROIList);
			
			bOnlyVoxelsInsideROI = extractVoxelsInsideROI.isSelected();
			Prefs.set("BigTrace.bOnlyVoxelsInsideROI", bOnlyVoxelsInsideROI);
			
			nExtractRoiType = extractBoxTypeList.getSelectedIndex();
			Prefs.set("BigTrace.nExtractRoiType", nExtractRoiType);
			if(nExtractRoiType == 0)
			{
				nExpandROIBox = 0;
			}
			else
			{
				nExpandROIBox = Integer.parseInt(nfBoxExpand.getText());
				Prefs.set("BigTrace.nExpandROIBox", nExpandROIBox);				
			}
			nTimeRange = 0;
			if(bt.btData.nNumTimepoints > 1)
			{
				nTimeRange = extractBoxTimeList.getSelectedIndex();
				Prefs.set("BigTrace.nExtractBoxTime", nTimeRange);
			}

			nExtractBoxOutput = extractBoxOutputList.getSelectedIndex();
			Prefs.set("BigTrace.nExtractBoxOutput", nExtractBoxOutput);
			//if saving, ask for the path
			String sSaveDir = "";
			if(nExtractBoxOutput > 0)
			{
				sSaveDir = GetFolderDialog.getSelectedFolder( bt, (nExtractBoxOutput == 1) ? "Save box ROI TIF to folder..." : "Save box ROI XML/HDF5 to folder..." );
				if(sSaveDir == null)
				{
					bt.btPanel.progressBar.setString("curve straightening aborted.");
					return;
				}
			}
			
			//build list of ROIs
			final ArrayList<Roi3D> roiOut = new ArrayList<>();
			//single ROI
			if(nExtractBoxROIList == 0)
			{		
				roiOut.add(roiIn);
			}
			//all curve ROIs
			else
			{
				for (int nRoi = 0; nRoi < bt.roiManager.rois.size(); nRoi++)
				{
					Roi3D roi = bt.roiManager.rois.get(nRoi);
					if(bt.roiManager.groups.get(roi.getGroupInd()).bVisible)
					{
						roiOut.add(roi);
					}
				}
				
			}
			if(roiOut.size() > 0)
			{		
		        bt.bInputLock = true;
		        bt.setLockMode(true);
		        
				//run in a separate thread
				ExtractROIBox<T> extractBoxBG = new ExtractROIBox<>(roiOut, bt, nExpandROIBox, nTimeRange, nExtractBoxOutput, bOnlyVoxelsInsideROI, sSaveDir);				
				extractBoxBG.addPropertyChangeListener(bt.btPanel);
				extractBoxBG.execute();
			}
			else
			{
				IJ.log("Cannot find ROIs for box extraction.");
				bt.btPanel.progressBar.setString("extract ROI box aborted.");
			}

		}
	}
	
	/** dialog for the Straighten procedure **/
	public void dialStraightenCurve(final AbstractCurve3D curveLine)
	{
		
		float fRadiusStraighted = 0.0f;
		int nRadiusType = 0;
		int nTimeRange = -1;
		int nStraightenAxis = 0;
		int nStraightenShape = 0;
		int nROIList = 0;
		int nStraightenOutput = 0;
		
		NumberField nfRadius = new NumberField(4);
		
		JPanel straightenSettings = new JPanel();
		
		straightenSettings.setLayout(new GridBagLayout());
	
		GridBagConstraints cd = new GridBagConstraints();

		GBCHelper.alighLeft(cd);

		cd.gridx = 0;
		cd.gridy = 0;	
		straightenSettings.add(new JLabel("Straighten:"),cd);
		cd.gridx++;
		String[] sStraightenROIsRange = { "Selected ROI", "All visible ROIs" };
		JComboBox<String> straightenRoiList = new JComboBox<>(sStraightenROIsRange);
		straightenRoiList.setSelectedIndex((int)Prefs.get("BigTrace.nStraightROIList", 0));
		straightenSettings.add(straightenRoiList,cd);	
	
		cd.gridy++;
		cd.gridx = 0;	
		straightenSettings.add(new JLabel("Curve thickness:"),cd);
		cd.gridx++;
		String[] sStraightenType = { "Use ROI settings", "Override here" };
		JComboBox<String> straightenRadiusList = new JComboBox<>(sStraightenType);
		straightenRadiusList.setSelectedIndex((int)Prefs.get("BigTrace.nRadiusType", 0));
		straightenSettings.add(straightenRadiusList,cd);
		
		
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.00", decimalFormatSymbols);
		nfRadius.setText(df.format(Prefs.get("BigTrace.fRadiusStraighted", 5)));
		cd.gridy++;
		cd.gridx = 0;
		straightenSettings.add(new JLabel("Override radius (px):"),cd);
		cd.gridx++;
		straightenSettings.add(nfRadius,cd);

		String[] sStraightenAxis = { "X", "Y", "Z" };
		JComboBox<String> cbStraightenAxis = new JComboBox<>(sStraightenAxis);
		cd.gridy++;
		cd.gridx = 0;
		straightenSettings.add(new JLabel("Line becomes output axis:"),cd);
		cd.gridx++;
		cbStraightenAxis.setSelectedIndex((int)Prefs.get("BigTrace.nStraightenAxis", 0));
		straightenSettings.add(cbStraightenAxis,cd);
		
		String[] sShapeOutput = { "Square", "Round" };
		JComboBox<String> cbStraightenShape = new JComboBox<>(sShapeOutput);
		cd.gridy++;
		cd.gridx = 0;
		straightenSettings.add(new JLabel("Shape:"),cd);
		cd.gridx++;
		cbStraightenShape.setSelectedIndex((int)Prefs.get("BigTrace.nStraightenShape", 0));
		straightenSettings.add(cbStraightenShape,cd);
		
		String[] sStraightenTime = { "Single ROI's time point", "All time points" };
		JComboBox<String> straightenTimeList = new JComboBox<>(sStraightenTime);
		if(bt.btData.nNumTimepoints > 1)
		{
			cd.gridy++;
			cd.gridx = 0;
			straightenSettings.add(new JLabel("Time range per ROI:"),cd);
			straightenTimeList.setSelectedIndex((int)Prefs.get("BigTrace.nStraightenTime", 0));
			cd.gridx++;
			straightenSettings.add(straightenTimeList,cd);
			
		}
		cd.gridy++;
		cd.gridx = 0;	
		straightenSettings.add(new JLabel("Output:"),cd);
		cd.gridx++;
		String[] sStraightenOutput = { "show in ImageJ", "save as TIF" };
		JComboBox<String> straightenOutputList = new JComboBox<>(sStraightenOutput);
		straightenOutputList.setSelectedIndex((int)Prefs.get("BigTrace.nStraightenOutput", 0));
		straightenSettings.add(straightenOutputList,cd);
		
		cd.gridy++;
		cd.gridx = 0;	
		straightenSettings.add(new JLabel("ROI Shape:"),cd);
		cd.gridx++;
		String[] sShapeInterpolationType = { "Voxel", "Smooth", "Spline"};
		straightenSettings.add(new JLabel(sShapeInterpolationType[bt.btData.shapeInterpolation]),cd);
		
		cd.gridy++;
		cd.gridx = 0;	
		straightenSettings.add(new JLabel("Intensity interpolation:"),cd);
		cd.gridx++;
		String[] sIntInterpolationType = { "Nearest Neighbor", "Linear", "Lanczos" };
		straightenSettings.add(new JLabel(sIntInterpolationType[bt.btData.intensityInterpolation]),cd);
		
		int reply = JOptionPane.showConfirmDialog(null, straightenSettings, "Straighten curve(s)", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			nROIList = straightenRoiList.getSelectedIndex();
			Prefs.set("BigTrace.nStraightROIList", nROIList);
			nRadiusType = straightenRadiusList.getSelectedIndex();
			Prefs.set("BigTrace.nRadiusType", nRadiusType);
			if(nRadiusType == 0)
			{
				fRadiusStraighted = -1.0f;
			}
			else
			{
				fRadiusStraighted = Float.parseFloat(nfRadius.getText());
				Prefs.set("BigTrace.fRadiusStraighted", fRadiusStraighted);				
			}
			
			nStraightenAxis = cbStraightenAxis.getSelectedIndex();
			Prefs.set("BigTrace.nStraightenAxis", nStraightenAxis);
			
			nStraightenShape = cbStraightenShape.getSelectedIndex();
			Prefs.set("BigTrace.nStraightenShape", nStraightenShape);
			
			nTimeRange = 0;
			if(bt.btData.nNumTimepoints > 1)
			{
				nTimeRange = straightenTimeList.getSelectedIndex();
				Prefs.set("BigTrace.nStraightenTime", nTimeRange);
			}

			nStraightenOutput = straightenOutputList.getSelectedIndex();
			Prefs.set("BigTrace.nStraightenOutput", nStraightenOutput);
			//if saving, ask for the path
			String sSaveDir = "";
			if(nStraightenOutput > 0)
			{
				sSaveDir = GetFolderDialog.getSelectedFolder( bt, "Save straightened TIF to folder..." );
				if(sSaveDir == null)
				{
					bt.btPanel.progressBar.setString("curve straightening aborted.");
					return;
				}
			}
			
			//build list of ROIs
			final ArrayList<AbstractCurve3D> curvesOut = new ArrayList<>();
			//single ROI
			if(nROIList == 0)
			{
				if(curveLine.vertices.size() < 2)					
				{ 
					IJ.log("Curve ROI must have more then two vertices.");
					bt.btPanel.progressBar.setString("curve straightening aborted.");
					return;
				}
				curvesOut.add(curveLine);
			}
			//all curve ROIs
			else
			{
				for (int nRoi = 0; nRoi < bt.roiManager.rois.size(); nRoi++)
				{
					Roi3D roi = bt.roiManager.rois.get(nRoi);
					if(bt.roiManager.groups.get(roi.getGroupInd()).bVisible)
					{
						if((roi.getType() == Roi3D.LINE_TRACE) || (roi.getType() == Roi3D.POLYLINE))
						{
							final AbstractCurve3D roiCurve = (AbstractCurve3D) roi;
							if(roiCurve.vertices.size() >1 )
							{
								curvesOut.add( roiCurve );
							}
						}
					}
				}		
			}
			if(curvesOut.size() > 0)
			{			
				//lock interaction
		        bt.bInputLock = true;
		        bt.setLockMode(true);
				//run in a separate thread
				StraightenCurve<T> straightBG = new StraightenCurve<>(curvesOut, bt, fRadiusStraighted, nStraightenAxis, nStraightenShape, nTimeRange, nStraightenOutput, sSaveDir);
				straightBG.addPropertyChangeListener(bt.btPanel);
				straightBG.execute();
			}
			else
			{
				IJ.log("Cannot find proper curve ROIs to straighten.");
				bt.btPanel.progressBar.setString("curve straightening aborted.");
			}

		}

	}
	
	/** given cross-section ROI, splits provided volume in two and shows them (dialog) **/
	public void dialSliceVolume(final CrossSection3D crossSection)
	{
		int nSliceType = 0;
		JPanel sliceSettings = new JPanel();
		sliceSettings.setLayout(new BoxLayout(sliceSettings, BoxLayout.PAGE_AXIS));
		
		String[] sSliceType = { "As original", "Tight crop" };
		JComboBox<String> sliceTypeList = new JComboBox<>(sSliceType);
		
		sliceSettings.add(new JLabel("Output volumes size: "));
		sliceTypeList.setSelectedIndex((int)Prefs.get("BigTrace.nSliceType", 0));
		sliceSettings.add(sliceTypeList);
		
		int reply = JOptionPane.showConfirmDialog(null, sliceSettings, "Split/slice volume crop", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			nSliceType = sliceTypeList.getSelectedIndex();
			Prefs.set("BigTrace.nSliceType", nSliceType);
			
			//lock interaction
	        bt.bInputLock = true;
	        bt.setLockMode(true);
			//run in a separate thread
			SplitVolumePlane<T> splitBG = new SplitVolumePlane<>(crossSection, bt, nSliceType);		
	        splitBG.addPropertyChangeListener(bt.btPanel);
	        splitBG.execute();
		}
	}
	
	/** show Measure settings dialog**/
	public void dialSettings()
	{
		JPanel pMeasureSettings = new JPanel(new GridLayout(0,2,6,0));
	
		ArrayList<JCheckBox> measureStates = new ArrayList<>();

		boolean[] states = new boolean[RoiMeasure3D.listMeasurements.length];

		for (int i = 0; i < RoiMeasure3D.listMeasurements.length; i++)
		{
			states[i] = (RoiMeasure3D.systemMeasurements&(int)(Math.pow( 2, i ))) != 0;
		}
				
		for(int i = 0; i < RoiMeasure3D.listMeasurements.length; i++)
		{

			measureStates.add(new JCheckBox(RoiMeasure3D.labels[i]));
			measureStates.get(i).setSelected(states[i]);
			pMeasureSettings.add(measureStates.get(i));
			
		}
		//to account for the empty slot
		pMeasureSettings.add(new JLabel(" "));
		
		pMeasureSettings.add(new JLabel("Ignore clipped volume?"));
		JCheckBox cbIgnoreClipped = new JCheckBox();
		cbIgnoreClipped.setSelected( bt.roiManager.roiMeasure.bIgnoreClipped );
		pMeasureSettings.add( cbIgnoreClipped );
		
		String[] sIntInterpolationType = { "Nearest Neighbor", "Linear", "Lanczos" };
		JComboBox<String> intensityInterpolationList = new JComboBox<>(sIntInterpolationType);
		pMeasureSettings.add(new JLabel("Intensity interpolation: "));
		intensityInterpolationList.setSelectedIndex(bt.btData.intensityInterpolation);
		pMeasureSettings.add(intensityInterpolationList);
		
		String[] sShapeInterpolationType = { "Voxel", "Smooth", "Spline"};
		JComboBox<String> shapeInterpolationList = new JComboBox<>(sShapeInterpolationType);
		shapeInterpolationList.setSelectedIndex(bt.btData.shapeInterpolation);
		pMeasureSettings.add(new JLabel("ROI Shape interpolation: "));
		pMeasureSettings.add(shapeInterpolationList);
		
		NumberField nfSmoothWindow = new NumberField(2);
		nfSmoothWindow.setIntegersOnly(true);
		nfSmoothWindow.setText(Integer.toString(bt.btData.nSmoothWindow));
		pMeasureSettings.add(new JLabel("Smoothing window/spline points (points): "));
		pMeasureSettings.add(nfSmoothWindow);	
		
		String[] sRotationFrame = { "Wang et al 2008", "Experimental"};
		JComboBox<String> rotationFrameList = new JComboBox<>(sRotationFrame);
		rotationFrameList.setSelectedIndex(bt.btData.rotationMinFrame);
		pMeasureSettings.add(new JLabel("Rotation minimizing frame: "));
		pMeasureSettings.add(rotationFrameList);
		
		int reply = JOptionPane.showConfirmDialog(null, pMeasureSettings, "Set Measurements", 
		        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (reply == JOptionPane.OK_OPTION) 
		{

			for (int i = 0; i < RoiMeasure3D.listMeasurements.length; i++) 
			{
				states[i] = measureStates.get(i).isSelected();
				if (states[i])
					RoiMeasure3D.systemMeasurements |= RoiMeasure3D.listMeasurements[i];
				else
					RoiMeasure3D.systemMeasurements &= ~RoiMeasure3D.listMeasurements[i];
			}
			Prefs.set("BigTrace.Measurements", RoiMeasure3D.systemMeasurements);
						
			// whether to clip measurements
			bt.roiManager.roiMeasure.bIgnoreClipped = cbIgnoreClipped.isSelected();
			Prefs.set("BigTrace.bIgnoreClipped", bt.roiManager.roiMeasure.bIgnoreClipped);
			
			//intensity interpolation
			bt.btData.intensityInterpolation = intensityInterpolationList.getSelectedIndex();
			Prefs.set("BigTrace.IntInterpolation",bt.btData.intensityInterpolation);
			bt.btData.setInterpolationFactory();
			
			
			if(bt.btData.nSmoothWindow != Integer.parseInt(nfSmoothWindow.getText()) ||
					bt.btData.shapeInterpolation != shapeInterpolationList.getSelectedIndex() ||
							bt.btData.rotationMinFrame != rotationFrameList.getSelectedIndex())
			{
				bt.btData.nSmoothWindow = Integer.parseInt(nfSmoothWindow.getText());
				Prefs.set("BigTrace.nSmoothWindow", bt.btData.nSmoothWindow);
				bt.btData.shapeInterpolation = shapeInterpolationList.getSelectedIndex();
				Prefs.set("BigTrace.ShapeInterpolation",bt.btData.shapeInterpolation);
				bt.btData.rotationMinFrame = rotationFrameList.getSelectedIndex();
				Prefs.set("BigTrace.RotationMinFrame",bt.btData.rotationMinFrame);
				bt.roiManager.updateROIsDisplay();
			}
			
			if (bt.roiManager.roiMeasure.rt != null)
			{	
				boolean bUpdate = false;
				for (int i = 0; i < RoiMeasure3D.listMeasurements.length; i++) 
				{
					//column not selected
					//remove it from results table
					if(!states[i])
					{
						String sColName;
						for (int nCol = 0; nCol <= bt.roiManager.roiMeasure.rt.getLastColumn(); nCol++)
						{
							sColName = bt.roiManager.roiMeasure.rt.getColumnHeading(nCol);
							if(sColName.startsWith(RoiMeasure3D.colTemplates[i]))
							{
								bt.roiManager.roiMeasure.rt.deleteColumn(sColName);
								bUpdate = true;
							}
						}					
					}
				}
				if(bUpdate)
				{
					bt.roiManager.roiMeasure.rt.updateResults();
				}
			}
		}
	}
	
	public boolean dialCoalignmentVector()
	{
		JPanel pCoalignVector = new JPanel(new GridLayout(0,2,6,0));
		
		ArrayList<NumberField> nfCoalignVector = new ArrayList<>();

		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		for(int d = 0; d < 3; d++)
		{
			nfCoalignVector.add( new NumberField(4));
			nfCoalignVector.get(d).setText(df.format(bt.roiManager.roiMeasure.coalignVector[d]));
		}
		pCoalignVector.add(new JLabel("X coord: "));
		pCoalignVector.add(nfCoalignVector.get(0));
		pCoalignVector.add(new JLabel("Y coord: "));
		pCoalignVector.add(nfCoalignVector.get(1));
		pCoalignVector.add(new JLabel("Z coord: "));
		pCoalignVector.add(nfCoalignVector.get(2));
		
		int reply = JOptionPane.showConfirmDialog(null, pCoalignVector, "Coalignment vector coordinates", 
		        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			double [] inVector = new double[3];
			for(int d = 0; d < 3; d++)
				inVector[ d ] = Double.parseDouble(nfCoalignVector.get(d).getText());
			double len = LinAlgHelpers.length(inVector);
			if(len < 0.000001)
			{
				IJ.error("Vector length should be more than zero!");
			}
			else
			{
				LinAlgHelpers.normalize(inVector);
				for(int d = 0; d < 3; d++)
				{
					bt.roiManager.roiMeasure.coalignVector[ d ] = inVector[ d ];
					Prefs.set("BigTrace.coalignVec" + Integer.toString(d), bt.roiManager.roiMeasure.coalignVector[d]);				
				}
			}
			return true;
		}
		return false;
		
	}
}
