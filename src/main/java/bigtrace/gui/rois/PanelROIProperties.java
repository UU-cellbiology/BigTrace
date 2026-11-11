package bigtrace.gui.rois;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import bdv.tools.brightness.ColorIcon;
import bigtrace.BigTrace;
import bigtrace.gui.ColorUserSettings;
import bigtrace.gui.panels.NumberField;
import bigtrace.roimanager.RoiManager3D;
import bigtrace.rois.Roi3D;

public class PanelROIProperties extends JPanel
{
	final BigTrace<?> bt;
	final NumberField nfTimePoint = new NumberField(4);
	final NumberField nfPointSize = new NumberField(4);
	final NumberField nfLineThickness = new NumberField(4);
	final NumberField nfOpacity = new NumberField(4);
	final JComboBox<String> renderTypeList;
	final ColorUserSettings selectColors = new ColorUserSettings(); 
	final Roi3D currentROI;
	
	public PanelROIProperties(final BigTrace<?> bt_)
	{
		super(new GridBagLayout());
		this.bt = bt_;

		GridBagConstraints cd = new GridBagConstraints();

		currentROI = bt.roiManager.getActiveRoi();

		String[] sRenderType = { "Outline", "Wire", "Surface" };
		renderTypeList = new JComboBox<>(sRenderType);
		nfTimePoint.setIntegersOnly(true);
		nfTimePoint.setText(Integer.toString(currentROI.getTimePoint()));
		nfPointSize.setText(Float.toString(currentROI.getPointSize()));
		nfLineThickness.setText(Float.toString(currentROI.getLineThickness()));
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.00", decimalFormatSymbols);
		nfOpacity.setText(df.format(currentROI.getOpacity()));
		
		nfOpacity.setLimits(0.0, 1.0);
		

		JButton butPointColor = new JButton( new ColorIcon( currentROI.getPointColor() ) );
		
		butPointColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose point color", currentROI.getPointColor() );
			if (newColor != null)
			{
				selectColors.setColor(newColor, 0);
				butPointColor.setIcon(new ColorIcon(newColor));
			}
			
		});
		
		JButton butLineColor  = new JButton( new ColorIcon( currentROI.getLineColor()) );

		
		butLineColor.addActionListener( e -> {
				Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose line color", currentROI.getPointColor() );
				if (newColor != null)
				{	
					selectColors.setColor(newColor, 1);							
					butLineColor.setIcon(new ColorIcon(newColor));
				}
				
		});
		
		cd.gridx = 0;
		cd.gridy = 0;
		this.add(new JLabel("ROI Type: "),cd);
		cd.gridx++;
		this.add(new JLabel(Roi3D.intTypeToString(currentROI.getType())),cd);

		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Group: "),cd);
		cd.gridx++;
		//just in case
		if(currentROI.getGroupInd() > bt.roiManager.groups.size()-1)
		{
			this.add(new JLabel(RoiManager3D.sUndefinedGroupName),cd);
		}
		else
		{
			this.add(new JLabel(bt.roiManager.groups.get(currentROI.getGroupInd()).getName()),cd);	
		}
	
		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Time point: "),cd);
		cd.gridx++;
		this.add(nfTimePoint,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Point size: "),cd);
		cd.gridx++;
		this.add(nfPointSize,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Point color: "),cd);
		cd.gridx++;
		this.add(butPointColor,cd);
		
		if(currentROI.getType() > Roi3D.POINT)
		{
			cd.gridx = 0;
			cd.gridy++;
			this.add(new JLabel("Line thickness: "),cd);
			cd.gridx++;
			this.add(nfLineThickness,cd);
			cd.gridx = 0;
			cd.gridy++;
			this.add(new JLabel("Line color: "),cd);
			cd.gridx++;
			this.add(butLineColor,cd);
		}
		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Opacity: "),cd);
		cd.gridx++;
		this.add(nfOpacity,cd);
		

		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Render as: "),cd);
		renderTypeList.setSelectedIndex(currentROI.getRenderType());
		cd.gridx++;
		this.add(renderTypeList,cd);
	}
	
	public void getSetProperties()
	{
		//time point
		currentROI.setTimePoint(Math.min(Math.max(0, Integer.parseInt(nfTimePoint.getText())), bt.btData.nNumTimepoints - 1));
		//point size 
		currentROI.setPointSize(Float.parseFloat(nfPointSize.getText()));
		
		//point color
		if(selectColors.getColor(0)!=null)
		{
			currentROI.setPointColorRGB(selectColors.getColor(0));
			selectColors.setColor(null, 0);
			//newPointColor = null;
		}
		//opacity
		float fNewOpacity = Float.parseFloat(nfOpacity.getText());
		if(fNewOpacity < 0.0f)
			{fNewOpacity = 0.0f;}
		if(fNewOpacity > 1.0f)
			{fNewOpacity = 1.0f;}
		currentROI.setOpacity(fNewOpacity);
		
		//render type
		if(renderTypeList.getSelectedIndex() != currentROI.getRenderType())
		{
			currentROI.setRenderType( renderTypeList.getSelectedIndex() );
		}
		
		//line
		if(currentROI.getType() > Roi3D.POINT)
		{
			//line thickness
			float fNewLineThickess = Float.parseFloat( nfLineThickness.getText() );
			if(Math.abs(fNewLineThickess-currentROI.getLineThickness()) > 0.00001)
			{
				currentROI.setLineThickness(fNewLineThickess );
			}
			//line color
			if(selectColors.getColor(1)!=null)
			{				
				currentROI.setLineColorRGB(selectColors.getColor(1));
				selectColors.setColor(null, 1);				
			}

		}
		bt.repaintBVV();	
		
	}
}
