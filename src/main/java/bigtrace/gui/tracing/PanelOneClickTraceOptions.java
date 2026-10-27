package bigtrace.gui.tracing;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.NumberField;
import ij.Prefs;

/** options for one-click tracing **/
public class PanelOneClickTraceOptions extends JPanel
{
	final BigTrace<?> bt;
	
	final NumberField nfIntensityThreshold = new NumberField(5);
	
	final JCheckBox cbIntensityStop = new JCheckBox("Use intensity threshold");
	
	final NumberField nfPlaceVertex = new NumberField(4);
	
	final NumberField nfDirectionalityOneClick = new NumberField(4);
	
	final JCheckBox cbTraceMask = new JCheckBox("Avoid (mask) existing ROIs");
	
	boolean bShowMaskOption = true;
	
	public PanelOneClickTraceOptions(final BigTrace<?> bt_, boolean bShowMaskOption_)
	{
		super();
		bt = bt_;
		bShowMaskOption = bShowMaskOption_;
		setLayout(new GridBagLayout());
		
		final GridBagConstraints gbc = new GridBagConstraints();
		GBCHelper.alighLeft( gbc );
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		
		nfPlaceVertex.setIntegersOnly(true);
		nfPlaceVertex.setText(Integer.toString(bt.btData.nVertexPlacementPointN));
		nfDirectionalityOneClick.setText(df.format(bt.btData.dDirectionalityOneClick));
		cbIntensityStop.setSelected(bt.btData.bOCIntensityStop);
		nfIntensityThreshold.setText(df.format(bt.btData.dOCIntensityThreshold));
		nfIntensityThreshold.setTFEnabled( bt.btData.bOCIntensityStop);
		
		cbTraceMask.setSelected( bt.btData.bOneClickUseMask );
		
		
		gbc.gridx = 0;		
		gbc.gridy = 0;
		this.add(new JLabel("Constrain directionality (0-1): "),gbc);
		gbc.gridx++;
		this.add(nfDirectionalityOneClick,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		//cd.anchor=GridBagConstraints.WEST;
		//this.add(new JLabel("Use intensity threshold"),gbc);
		//gbc.gridx++;
		this.add(cbIntensityStop,gbc);
		gbc.gridwidth = 1;
		
		cbIntensityStop.addActionListener( new ActionListener()
				{
					@Override
					public void actionPerformed( ActionEvent arg0 )
					{						
						nfIntensityThreshold.setTFEnabled( cbIntensityStop.isSelected() );
					}
			
				});
		
		gbc.gridx = 0;
		gbc.gridy++;
		this.add(new JLabel("Minimum intensity: "),gbc);
		gbc.gridx++;
		this.add(nfIntensityThreshold,gbc);

		gbc.gridx = 0;
		gbc.gridy ++;		
		this.add(new JLabel("Ref points placement (px, >=3): "),gbc);
		gbc.gridx++;
		this.add(nfPlaceVertex,gbc);
		
		if(bShowMaskOption)
		{
			gbc.gridx = 0;
			gbc.gridy++;
			gbc.gridwidth = 2;
			this.add(cbTraceMask,gbc);		
		}
	}
	
	public void getSetOptions()
	{
		bt.btData.nVertexPlacementPointN = Math.max(3, Integer.parseInt(nfPlaceVertex.getText()));
		Prefs.set("BigTrace.nVertexPlacementPointN", (double)(bt.btData.nVertexPlacementPointN));
		
		bt.btData.dDirectionalityOneClick = Math.min(1.0, (Math.max(0, Double.parseDouble(nfDirectionalityOneClick.getText()))));
		Prefs.set("BigTrace.dDirectionalityOneClick",bt.btData.dDirectionalityOneClick);
		
		bt.btData.bOCIntensityStop = cbIntensityStop.isSelected();
		Prefs.set("BigTrace.bOCIntensityStop", bt.btData.bOCIntensityStop);	
		if(bt.btData.bOCIntensityStop)
		{
			bt.btData.dOCIntensityThreshold = Math.max(0, Double.parseDouble(nfIntensityThreshold.getText()));
			Prefs.set("BigTrace.dOCIntensityThreshold",bt.btData.dOCIntensityThreshold);		
		}
		if(bShowMaskOption)
		{
			bt.btData.bOneClickUseMask = cbTraceMask.isSelected();
			Prefs.get( "BigTrace.bOneClickUseMask", bt.btData.bOneClickUseMask );			
		}
	}
}
