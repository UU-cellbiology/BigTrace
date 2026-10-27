package bigtrace.gui.tracing;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.panels.NumberField;
import ij.Prefs;

public class PanelSemiAutoOptions extends JTabbedPane
{
	final BigTrace<?> bt;
	
	////////////TRACING OPTIONS
	final PanelTracingOptions panelGeneralTrace;
	////////////SEMI-AUTO TRACING OPTIONS
	final JPanel pSemiAuto = new JPanel(new GridBagLayout());
	
	final NumberField nfGammaTrace = new NumberField(4);
	final NumberField nfTraceBoxSize = new NumberField(4);
	final NumberField nfTraceBoxScreenFraction = new NumberField(4);
	final NumberField nfTBAdvance = new NumberField(4);
	
	public PanelSemiAutoOptions(final BigTrace<?> bt_)
	{
		super();
		bt = bt_;
		panelGeneralTrace = new PanelTracingOptions(bt);
		GridBagConstraints gbc = new GridBagConstraints();

		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);

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
		this.addTab("Tracing", panelGeneralTrace);
		this.addTab("Semi auto", pSemiAuto);
	}
	
	public void getSetOptions()
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
