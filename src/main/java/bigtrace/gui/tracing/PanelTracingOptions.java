package bigtrace.gui.tracing;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import bigtrace.BigTrace;
import bigtrace.gui.panels.NumberField;
import ij.Prefs;


/** General tracing options, common for all tracings **/
public class PanelTracingOptions extends JPanel
{
	final BigTrace<?> bt;
	
	final NumberField nfSigmaX = new NumberField(4);
	final NumberField nfSigmaY = new NumberField(4);
	final NumberField nfSigmaZ = new NumberField(4);
	
	/** SD of each axis in physical units (pixel size globCal), updated while typing **/
	final JLabel [] lSigmaUnits = new JLabel [] {new JLabel(), new JLabel(), new JLabel()};
	
	final DecimalFormat dfUnits;
	
	final JCheckBox cbTraceOnlyClipped = new JCheckBox("Trace only clipped volume");
	final String [] sEstimateMode = new String [] {"MAX", "AVG", "MIN"};
	final JComboBox<String> cbEstimateThicknessMode = new JComboBox<>(sEstimateMode);
	final NumberField cbEstimateThicknessCoeff = new NumberField(2);
	final JCheckBox cbEstimateThickness = new JCheckBox(" Set ROI diameter ");	
	
	public PanelTracingOptions(final BigTrace<?> bt_)
	{
		super();
		bt = bt_;
		setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.00", decimalFormatSymbols);
		DecimalFormat df2 = new DecimalFormat("0.0", decimalFormatSymbols);
		dfUnits = new DecimalFormat("0.###", decimalFormatSymbols);
	
		nfSigmaX.setText(df.format(bt.btData.sigmaTrace[0]));
		nfSigmaY.setText(df.format(bt.btData.sigmaTrace[1]));
		nfSigmaZ.setText(df.format(bt.btData.sigmaTrace[2]));
		
		cbEstimateThicknessCoeff.setText(df2.format( bt.btData.dTraceROIThicknessCoeff));
		cbEstimateThicknessMode.setSelectedIndex( bt.btData.nTraceROIThicknessMode );
		cbEstimateThickness.setSelected(bt.btData.bEstimateROIThicknessFromParams);		
		cbTraceOnlyClipped.setSelected(bt.btData.bTraceOnlyClipped);
		
		final NumberField [] nfSigmas = new NumberField [] {nfSigmaX, nfSigmaY, nfSigmaZ};
		final String [] sAxes = new String [] {"X", "Y", "Z"};
		for(int d = 0; d < 3; d++)
		{
			gbc.gridx = 0;
			gbc.gridy = d;
			this.add(new JLabel("Curve thickness " + sAxes[d] + " axis (SD, px): "),gbc);
			gbc.gridx++;
			this.add(nfSigmas[d],gbc);
			gbc.gridx++;
			gbc.anchor = GridBagConstraints.WEST;
			this.add(lSigmaUnits[d],gbc);
			gbc.anchor = GridBagConstraints.CENTER;
			
			final int dim = d;
			updateSigmaUnits(dim, nfSigmas[dim].getText());
			nfSigmas[d].addDocumentListener( new DocumentListener()
			{
				@Override
				public void insertUpdate( DocumentEvent e ) { updateSigmaUnits(dim, nfSigmas[dim].getText()); }
				@Override
				public void removeUpdate( DocumentEvent e ) { updateSigmaUnits(dim, nfSigmas[dim].getText()); }
				@Override
				public void changedUpdate( DocumentEvent e ) { updateSigmaUnits(dim, nfSigmas[dim].getText()); }
			});
		}
		
		JPanel panEstimate = new JPanel(new GridBagLayout());
		GridBagConstraints gbc2 = new GridBagConstraints();
		gbc2.gridx = 0;
		gbc2.gridy = 0;
		panEstimate.add(cbEstimateThickness, gbc2);
		gbc2.gridx++;
		panEstimate.add(cbEstimateThicknessCoeff, gbc2);
		gbc2.gridx++;
		panEstimate.add(new JLabel(" times "), gbc2);
		gbc2.gridx++;
		panEstimate.add(cbEstimateThicknessMode, gbc2);
		gbc2.gridx++;
		panEstimate.add(new JLabel("of all SDs"), gbc2);
		
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 3;
		//this.add(new JLabel("Estimate ROI thickness from params: "),gbc);
		//gbc.gridx++;
		//this.add(cbEstimateThickness,gbc);
		this.add(panEstimate,gbc);
		//gbc.gridwidth = 1;
		
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.anchor = GridBagConstraints.WEST;
		this.add(cbTraceOnlyClipped,gbc);
	}
	
	/** shows SD along axis d (text in pixels) in physical units **/
	void updateSigmaUnits(final int d, final String sPixels)
	{
		String sOut;
		try
		{
			final double dSD = Double.parseDouble( sPixels ) * bt.btData.globCal[d];
			sOut = " = " + dfUnits.format( dSD ) + " " + bt.btData.sVoxelUnit;
		}
		catch(NumberFormatException e)
		{
			sOut = " = ? " + bt.btData.sVoxelUnit;
		}
		lSigmaUnits[d].setText( sOut );
	}
	
	public void getSetOptions()
	{
		bt.btData.sigmaTrace[0] = Double.parseDouble(nfSigmaX.getText());
		Prefs.set("BigTrace.sigmaTraceX", bt.btData.sigmaTrace[0]);
		
		bt.btData.sigmaTrace[1] = Double.parseDouble(nfSigmaY.getText());
		Prefs.set("BigTrace.sigmaTraceY", bt.btData.sigmaTrace[1]);
		
		bt.btData.sigmaTrace[2] = Double.parseDouble(nfSigmaZ.getText());
		Prefs.set("BigTrace.sigmaTraceZ", bt.btData.sigmaTrace[2]);

		bt.btData.bEstimateROIThicknessFromParams = cbEstimateThickness.isSelected();
		Prefs.set("BigTrace.bEstimateROIThicknessFromParams", bt.btData.bEstimateROIThicknessFromParams);
		if(bt.btData.bEstimateROIThicknessFromParams )
		{
			bt.btData.dTraceROIThicknessCoeff = Math.abs( Double.parseDouble(cbEstimateThicknessCoeff.getText()));
			Prefs.set("BigTrace.dTraceROIThicknessCoeff",bt.btData.dTraceROIThicknessCoeff);
			
			bt.btData.nTraceROIThicknessMode = cbEstimateThicknessMode.getSelectedIndex();
			Prefs.set("BigTrace.nTraceROIThicknessMode", (double)bt.btData.nTraceROIThicknessMode);
		}
		bt.btData.bTraceOnlyClipped = cbTraceOnlyClipped.isSelected();
		Prefs.set("BigTrace.bTraceOnlyClipped", bt.btData.bTraceOnlyClipped);	
	}
}
