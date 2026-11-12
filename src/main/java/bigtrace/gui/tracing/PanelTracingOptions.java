package bigtrace.gui.tracing;


import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
	
	/** resulting ROI diameter (voxels and units), updated interactively **/
	final JLabel lROIThickness = new JLabel();
	
	final NumberField [] nfSigmas = new NumberField [] {nfSigmaX, nfSigmaY, nfSigmaZ};
	
	final JCheckBox cbTraceOnlyClipped = new JCheckBox("Trace only clipped volume");
	final JCheckBox cbTraceDownsample = new JCheckBox("Downsample data for large thickness");
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
		cbTraceDownsample.setSelected(bt.btData.bTraceDownsample);
		cbTraceDownsample.setToolTipText( "One-click and semi-auto tracing use a block-averaged grid, so the kernel stays ~target SD voxels" );
		
		final String [] sAxes = new String [] {"X", "Y", "Z"};
		for(int d = 0; d < 3; d++)
		{
			gbc.gridx = 0;
			gbc.gridy = d;
			this.add(new JLabel("Curve thickness " + sAxes[d] + " (SD, px): "),gbc);
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
				public void insertUpdate( DocumentEvent e ) { updateSigmaUnits(dim, nfSigmas[dim].getText()); updateROIThickness(); }
				@Override
				public void removeUpdate( DocumentEvent e ) { updateSigmaUnits(dim, nfSigmas[dim].getText()); updateROIThickness(); }
				@Override
				public void changedUpdate( DocumentEvent e ) { updateSigmaUnits(dim, nfSigmas[dim].getText()); updateROIThickness(); }
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
		panEstimate.add(new JLabel(" X "), gbc2);
		gbc2.gridx++;
		panEstimate.add(cbEstimateThicknessMode, gbc2);
		gbc2.gridx++;
		panEstimate.add(new JLabel("of all SDs"), gbc2);
		
		//resulting ROI diameter
		gbc2.gridx = 0;
		gbc2.gridy++;
		gbc2.gridwidth = 5;
		panEstimate.add(lROIThickness, gbc2);
		
		gbc.insets = new Insets(8, 0, 8, 0);
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 3;
		this.add(panEstimate,gbc);
		gbc.insets = new Insets(0, 0, 0, 0);

		updateROIThickness();
		cbEstimateThicknessCoeff.addDocumentListener( new DocumentListener()
		{
			@Override
			public void insertUpdate( DocumentEvent e ) { updateROIThickness(); }
			@Override
			public void removeUpdate( DocumentEvent e ) { updateROIThickness(); }
			@Override
			public void changedUpdate( DocumentEvent e ) { updateROIThickness(); }
		});
		final ActionListener alThickness = e -> updateROIThickness();
		cbEstimateThicknessMode.addActionListener( alThickness );
		cbEstimateThickness.addActionListener( alThickness );

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.anchor = GridBagConstraints.WEST;
		this.add(cbTraceDownsample,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		this.add(cbTraceOnlyClipped,gbc);
		

	}
	
	/** shows SD along axis d (text in pixels) in physical units **/
	void updateSigmaUnits(final int d, final String sPixels)
	{
		String sOut;
		boolean bValid = true;
		double dPixels = 0.0;
		try
		{
			dPixels = Double.parseDouble( sPixels );
			final double dSD = Double.parseDouble( sPixels ) * bt.btData.globCal[d];
			sOut = "(" + dfUnits.format( dSD ) + " " + bt.btData.sVoxelUnit + ")";
		}
		catch(NumberFormatException e)
		{
			sOut = " = ? " + bt.btData.sVoxelUnit;
			bValid = false;
		}
		lSigmaUnits[d].setText( sOut );
		if(bValid && bt.btData.bShowDownSampleMessage && dPixels > 4.0)
		{
			showDownSampleMessage();
		}
	}
	
	/** shows the ROI diameter resulting from the current (typed) SDs, coefficient and mode,
	 * the same way as BigTraceData.estimateROIThicknessFromTracing (voxels = smallest voxel size) **/
	void updateROIThickness()
	{
		if(lROIThickness == null)
			return;
		final boolean bOn = cbEstimateThickness.isSelected();
		String sOut;
		try
		{
			final double [] sd = new double[3];
			for(int d = 0; d < 3; d++)
			{
				sd[d] = Double.parseDouble( nfSigmas[d].getText() ) * bt.btData.globCal[d];
			}
			final double dCoeff = Math.abs( Double.parseDouble( cbEstimateThicknessCoeff.getText() ) );
			double dSD;
			switch(cbEstimateThicknessMode.getSelectedIndex())
			{
			case 0:
				dSD = Math.max( sd[0], Math.max( sd[1], sd[2] ) );
				break;
			case 1:
				dSD = (sd[0] + sd[1] + sd[2]) / 3.0;
				break;
			default:
				dSD = Math.min( sd[0], Math.min( sd[1], sd[2] ) );
				break;
			}
			final double dUnits = dSD * dCoeff;
			sOut = "Calculated: " + dfUnits.format( dUnits / bt.btData.dMinVoxelSize ) + " voxels ("
					+ dfUnits.format( dUnits ) + " " + bt.btData.sVoxelUnit + ")";
		}
		catch(NumberFormatException e)
		{
			sOut = "Calculated: ?";
		}
		if(!bOn)
		{
			sOut += ", not used";
		}
		lROIThickness.setText( sOut );
		lROIThickness.setEnabled( bOn );
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
		bt.btData.bTraceDownsample = cbTraceDownsample.isSelected();
		Prefs.set("BigTrace.bTraceDownsample", bt.btData.bTraceDownsample);
	}
	
	void showDownSampleMessage()
	{
		JPanel pDownSampleInfo = new JPanel(new GridBagLayout());
		JLabel infoLabel = new JLabel(
			    "<html>For large curve thickness values (> 4 px) it is recommended to use <br>"
			    + "\"Downsample data for large thickness\" option</html>"
			);

		String[] options = {"OK"};
		JCheckBox cbShowAgain = new JCheckBox("Do not show this message again");
		cbShowAgain.setSelected( false );
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.insets = new Insets(8, 0, 8, 0);
		gbc.anchor = GridBagConstraints.WEST;

		// Add the single label containing the entire message
		pDownSampleInfo.add(infoLabel, gbc);

		gbc.gridy = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.anchor = GridBagConstraints.EAST;
		pDownSampleInfo.add(cbShowAgain, gbc);
		JOptionPane.showOptionDialog(null, pDownSampleInfo, "Tracing advice", 
				JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
		bt.btData.bShowDownSampleMessage = !cbShowAgain.isSelected();
		Prefs.set( "BVB.bShowDownSampleMessage", bt.btData.bShowDownSampleMessage);
	}
}
