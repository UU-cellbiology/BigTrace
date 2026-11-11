package bigtrace.gui.rois;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import bdv.tools.brightness.ColorIcon;
import bigtrace.gui.ColorUserSettings;
import bigtrace.gui.panels.NumberField;
import bigtrace.roimanager.Roi3DGroup;
import ij.Prefs;

public class PanelGroupProperties extends JPanel
{
	final Roi3DGroup preset;
	
	final JTextField tfName = new JTextField(10); 
	final NumberField nfPointSize = new NumberField(4);
	final NumberField nfLineThickness = new NumberField(4);
	final NumberField nfOpacity = new NumberField(4);
	final JComboBox<String> renderTypeList;
	public ColorUserSettings selectColors = new ColorUserSettings();
	
	public PanelGroupProperties(final Roi3DGroup preset_, boolean bNameChangable, final JDialog dialog)
	{
		super(new GridBagLayout());
		preset = preset_;
		GridBagConstraints cd = new GridBagConstraints();


		String[] sRenderType = { "Outline", "Wire", "Surface" };
		renderTypeList = new JComboBox<>(sRenderType);	
		
		tfName.setText(preset.getName());
		tfName.setEnabled(bNameChangable);
		
		nfPointSize.setText(Float.toString(preset.getPointSize()));
		nfLineThickness.setText(Float.toString(preset.getLineThickness()));
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.00", decimalFormatSymbols);
		nfOpacity.setText(df.format(preset.getOpacity()));
		nfOpacity.setLimits(0.0, 1.0);
		
		JButton butPointColor = new JButton( new ColorIcon( preset.getPointColor() ) );
		selectColors.setColor(preset.getPointColor(), 0);
		butPointColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(dialog, "Choose point color", preset.getPointColor() );
			if (newColor!=null)
			{
				selectColors.setColor(newColor, 0);
				butPointColor.setIcon(new ColorIcon(newColor));
			}
			
		});
		
		JButton butLineColor  = new JButton( new ColorIcon( preset.getLineColor()) );

		selectColors.setColor(preset.getLineColor(), 1);
		butLineColor.addActionListener( e -> {
				Color newColor = JColorChooser.showDialog(dialog, "Choose line color", preset.getPointColor() );
				if (newColor!=null)
				{	
					selectColors.setColor(newColor, 1);							
					butLineColor.setIcon(new ColorIcon(newColor));
				}
				
		});		

		JButton butSaveAsDefault = new JButton ("Save as new default");
		
		butSaveAsDefault.addActionListener( 
				new ActionListener() 
				{
					@Override
					public void actionPerformed( ActionEvent e )
					{
						Prefs.set("BigTrace.undefPointSize",Float.parseFloat(nfPointSize.getText()));	
						Prefs.set("BigTrace.undefLineThickness",Float.parseFloat(nfLineThickness.getText()));	
						Prefs.set("BigTrace.undefRenderType",renderTypeList.getSelectedIndex());
						Prefs.set("BigTrace.undefPointColor",selectColors.getColor(0).getRGB());
						Prefs.set("BigTrace.undefLineColor",selectColors.getColor(1).getRGB());
					}
				});
	
		cd.gridx = 0;
		cd.gridy = 0;

		this.add(new JLabel("Name: "),cd);
		cd.gridx++;
		this.add(tfName,cd);				
		
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

		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Opacity: "),cd);
		cd.gridx++;
		this.add(nfOpacity,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Render as: "),cd);
		renderTypeList.setSelectedIndex(preset.getRenderType());
		cd.gridx++;
		this.add(renderTypeList,cd);
		
		if(!bNameChangable)
		{
			cd.gridx = 0;
			cd.gridy++;
			this.add(butSaveAsDefault,cd);
		}		
	}
	
	public void getSetOptions()
	{
		//name 		
		String newName = tfName.getText();
		if ((newName != null) && (newName.length() > 0)) 
		{
			preset.setName(newName);
		}
		//point size 
		preset.setPointSize(Float.parseFloat(nfPointSize.getText()));
		
		//point color
		if(selectColors.getColor(0) != null)
		{
			preset.setPointColorRGB(selectColors.getColor(0));
			selectColors.setColor(null, 0);
			//newPointColor = null;
		}
		//opacity
		float fNewOpacity= Float.parseFloat(nfOpacity.getText());
		if(fNewOpacity < 0.0f)
			{fNewOpacity = 0.0f;}
		if(fNewOpacity > 1.0f)
			{fNewOpacity = 1.0f;}
		preset.setOpacity(fNewOpacity);

		//line

		//line thickness
		float fNewLineThickess = Float.parseFloat(nfLineThickness.getText());
		if(Math.abs(fNewLineThickess - preset.getLineThickness()) > 0.00001)
		{
			preset.setLineThickness(fNewLineThickess );
		}
		//line color
		if(selectColors.getColor(1) != null)
		{				
			preset.setLineColorRGB(selectColors.getColor(1));
			selectColors.setColor(null, 1);
		
		}
		//render type
		if(renderTypeList.getSelectedIndex() != preset.getRenderType())
		{
			preset.setRenderType(renderTypeList.getSelectedIndex());
		}

	}
}
