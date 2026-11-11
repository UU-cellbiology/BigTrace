package bigtrace.gui.rois;

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
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import bdv.tools.brightness.ColorIcon;
import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.panels.NumberField;
import bigtrace.roimanager.RoiManager3D;
import ij.Prefs;

public class PanelROIRenderSettings extends JTabbedPane
{
	final BigTrace<?> bt;
	final RoiManager3D<?> rm;
	final JButton butPointActiveColor;
	final JButton butLineActiveColor;
	final JCheckBox cbRoiDoubleClickClip = new JCheckBox();
	final NumberField nfRoiDoubleClickExpand = new NumberField(2);
	final JComboBox<String> shapeInterpolationList;
	final NumberField nfSmoothWindow = new NumberField(2);
	final JComboBox<String> sTimeRenderROIsList;
	final NumberField nfTimeFadeROIs = new NumberField(4);
	final NumberField nfSectorNLines = new NumberField(4);
	final JCheckBox cbWireAA = new JCheckBox();
	final NumberField nfWireContourStep = new NumberField(4);
	final NumberField nfCrossSectionGridStep = new NumberField(4);
	final JComboBox<String> sSilhouetteRenderList;
	final NumberField nfSilhouetteDecay = new NumberField(4);
	
	public PanelROIRenderSettings(final BigTrace<?> bt_)
	{
		super();
		bt = bt_;
		
		rm = bt.roiManager;
		GridBagConstraints gbc = new GridBagConstraints();

		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		////////////ROI RENDER OPTIONS
		final JPanel pROIrender = new JPanel(new GridBagLayout());
		
		butPointActiveColor = new JButton( new ColorIcon( rm.activePointColor ) );	
		butPointActiveColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose active point color", rm.activePointColor );
			if (newColor != null)
			{
				rm.selectColors.setColor(newColor, 0);
				butPointActiveColor.setIcon(new ColorIcon(newColor));
			}			
		});
		
		butLineActiveColor = new JButton( new ColorIcon( rm.activeLineColor ) );	
		butLineActiveColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose active line color", rm.activeLineColor );
			if (newColor!=null)
			{
				rm.selectColors.setColor(newColor, 1);
				butLineActiveColor.setIcon(new ColorIcon(newColor));
			}			
		});
		

		cbRoiDoubleClickClip.setSelected(bt.btData.bROIDoubleClickClip);
		
		
		nfRoiDoubleClickExpand.setIntegersOnly(true);
		nfRoiDoubleClickExpand.setText(Integer.toString(bt.btData.nROIDoubleClickClipExpand));
		
		String[] sShapeInterpolationType = { "Voxel", "Smooth", "Spline"};
		shapeInterpolationList = new JComboBox<>(sShapeInterpolationType);
		shapeInterpolationList.setSelectedIndex(bt.btData.shapeInterpolation);
		
		
		nfSmoothWindow.setIntegersOnly(true);
		nfSmoothWindow.setText(Integer.toString(bt.btData.nSmoothWindow));
				
		String[] sTimeRenderROIs = { "current timepoint", "backward in time", "forward in time"};
		sTimeRenderROIsList = new JComboBox<>(sTimeRenderROIs);
		sTimeRenderROIsList.setSelectedIndex(bt.btData.timeRender);
		
		
		nfTimeFadeROIs.setIntegersOnly(true);
		nfTimeFadeROIs.setText(Integer.toString(Math.abs(bt.btData.timeFade)));
						
		gbc.gridx = 0;
		gbc.gridy = 0;
		GBCHelper.alighLoose(gbc);
		pROIrender.add(new JLabel("Selected ROI point color: "),gbc);
		gbc.gridx++;
		pROIrender.add(butPointActiveColor,gbc);
		gbc.gridx = 0;
		gbc.gridy++;
		pROIrender.add(new JLabel("Selected ROI line color: "),gbc);
		gbc.gridx++;
		pROIrender.add(butLineActiveColor,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIrender.add(new JLabel("Clip volume on ROI double-click: "),gbc);
		gbc.gridx++;
		pROIrender.add(cbRoiDoubleClickClip,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIrender.add(new JLabel("Expand clipping by (voxels): "),gbc);
		gbc.gridx++;
		pROIrender.add(nfRoiDoubleClickExpand,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIrender.add(new JLabel("ROI Shape interpolation: "), gbc);
		gbc.gridx++;
		pROIrender.add(shapeInterpolationList, gbc);	
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIrender.add(new JLabel("Trace smoothing window (points): "),gbc);
		gbc.gridx++;
		pROIrender.add(nfSmoothWindow,gbc);
		

		if(bt.btData.nNumTimepoints > 1)
		{
			gbc.gridx = 0;
			gbc.gridy++;
			gbc.gridwidth = 2;
			gbc.anchor = GridBagConstraints.CENTER;
			pROIrender.add(new JLabel("<html>----  <B>Time</B>  ----</html>"),gbc);
			gbc.gridwidth=1;
			gbc.anchor = GridBagConstraints.WEST;
			
			
			gbc.gridx = 0;
			gbc.gridy++;
			pROIrender.add(new JLabel("Show ROIs over time: "),gbc);
			gbc.gridx++;
			pROIrender.add(sTimeRenderROIsList,gbc);
			gbc.gridx = 0;
			gbc.gridy++;
			pROIrender.add(new JLabel("Time fade range (frames): "),gbc);
			gbc.gridx++;
			pROIrender.add(nfTimeFadeROIs,gbc);
		}

		////////////ROI SURFACE RENDER 
		final JPanel pROIsurface = new JPanel(new GridBagLayout());
		
		nfSectorNLines.setIntegersOnly(true);
		nfSectorNLines.setText(Integer.toString(bt.btData.sectorN));
		
		cbWireAA.setSelected( bt.btData.wireAntiAliasing );
		
		nfWireContourStep.setIntegersOnly(true);
		nfWireContourStep.setText(Integer.toString(bt.btData.wireCountourStep));
		
		nfCrossSectionGridStep.setIntegersOnly(true);
		nfCrossSectionGridStep.setText(Integer.toString(bt.btData.crossSectionGridStep));
		
		String[] sSilhouetteRenderType = { "transparent", "front culling"};
		sSilhouetteRenderList = new JComboBox<>(sSilhouetteRenderType);
		sSilhouetteRenderList.setSelectedIndex(bt.btData.silhouetteRender);
		
		nfSilhouetteDecay.setText(df.format(bt.btData.silhouetteDecay));
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		pROIsurface.add(new JLabel("# edges at curve cross-section:"),gbc);
		gbc.gridx++;
		pROIsurface.add(nfSectorNLines,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.CENTER;
		pROIsurface.add(new JLabel("<html>----  <B>Wire</B> mode  ----</html>"),gbc);
		gbc.gridwidth = 1;
		gbc.anchor = GridBagConstraints.WEST;
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIsurface.add(new JLabel("Use anti-aliased lines :"),gbc);
		gbc.gridx++;
		pROIsurface.add(cbWireAA,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIsurface.add(new JLabel("Distance between curve contours (px):"),gbc);
		gbc.gridx++;
		pROIsurface.add(nfWireContourStep,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIsurface.add(new JLabel("Plane/cross-section ROI grid step (px): "),gbc);
		gbc.gridx++;
		pROIsurface.add(nfCrossSectionGridStep,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.CENTER;
		pROIsurface.add(new JLabel("<html>----  <B>Silhouette</B> surface ----</html>"),gbc);
		gbc.gridwidth=1;
		gbc.anchor = GridBagConstraints.WEST;
	
		
		gbc.gridx = 0;
		gbc.gridy++;
		pROIsurface.add(new JLabel("Silhouette render:"),gbc);
		gbc.gridx++;
		pROIsurface.add(sSilhouetteRenderList,gbc);	
		
		gbc.gridx = 0;		
		gbc.gridy++;
		pROIsurface.add(new JLabel("Silhouette decay: "),gbc);
		gbc.gridx++;
		pROIsurface.add(nfSilhouetteDecay,gbc);
				
		//assemble pane
		this.addTab("ROI render", pROIrender);
		this.addTab("ROI surface", pROIsurface);
	}
	
	public void getSetOptions()
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
