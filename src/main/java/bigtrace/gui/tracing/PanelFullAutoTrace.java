package bigtrace.gui.tracing;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.TaskBT;
import bigtrace.gui.panels.NumberField;
import bigtrace.gui.panels.RangeSliderPanel;
import bigtrace.math.FullAutoTrace;
import ij.Prefs;

public class PanelFullAutoTrace < T extends RealType< T > & NativeType< T > > implements ActionListener
{
	final BigTrace<T> bt;
	
	final ImageIcon tabIconAuto;
	final ImageIcon tabIconCancel;
	FullAutoTrace<T> fullAutoTrace;
	JButton butAuto;
	
	public PanelFullAutoTrace (final BigTrace<T> bt_)
	{
		bt = bt_;
		URL icon_path = this.getClass().getResource("/bt_icons/autotrace.png");
		tabIconAuto = new ImageIcon(icon_path);
		icon_path = this.getClass().getResource("/bt_icons/cancel.png");
		tabIconCancel = new ImageIcon(icon_path);
	}
	
	public void initButton(final JButton butAutoTrace)
	{
		butAuto = butAutoTrace;
		butAuto.addActionListener( this );		
	}
	
	public void showDialogFullAutoTrace()
	{
		final JTabbedPane tabPane = new JTabbedPane();

		final JPanel dialogFullAutoSettings = new JPanel();
		
		dialogFullAutoSettings.setLayout(new GridBagLayout());
		
		final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		final DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
		
		final GridBagConstraints gbc = new GridBagConstraints();

		GBCHelper.alighLeft(gbc);
		
		gbc.weighty = 0.0;
		
		final NumberField nfMaxIntFullTraceStart = new NumberField(5);
		final NumberField nfAutoMinCurveLength = new NumberField(5);
		final NumberField nfAutoMaxTraces = new NumberField(5);
		
		nfMaxIntFullTraceStart.setIntegersOnly( false );
		nfMaxIntFullTraceStart.setText( df3.format(Prefs.get("BigTrace.dAutoMinStartTraceInt", 128.0)) );
		nfAutoMinCurveLength.setIntegersOnly( false );
		nfAutoMinCurveLength.setText( df3.format(Prefs.get("BigTrace.dAutoMinCurveLength", 3)) );
		nfAutoMaxTraces.setIntegersOnly( true );
		nfAutoMaxTraces.setText( Integer.toString((int)Prefs.get("BigTrace.nAutoMaxTraces", 0)) );
		nfAutoMaxTraces.setToolTipText( "0 = no limit" );

		final JCheckBox cbMinLengthInUnits = new JCheckBox();
		cbMinLengthInUnits.setSelected( Prefs.get("BigTrace.bMinCurveLengthInUnits", false) );
		
		final JCheckBox cbStartOnlyInsideClip = new JCheckBox();
		cbStartOnlyInsideClip.setSelected( Prefs.get("BigTrace.bStartOnlyInsideClip", false) );

		
		gbc.gridx = 0;
		gbc.gridy = 0;
		dialogFullAutoSettings.add(new JLabel("Min intensity trace start:"), gbc);
		gbc.gridx++;
		
		dialogFullAutoSettings.add(nfMaxIntFullTraceStart, gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		dialogFullAutoSettings.add(new JLabel("Minimum curve length:"), gbc);
		gbc.gridx++;
		dialogFullAutoSettings.add(nfAutoMinCurveLength, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		dialogFullAutoSettings.add(new JLabel("Min length is in units:"), gbc);
		gbc.gridx++;
		dialogFullAutoSettings.add(cbMinLengthInUnits, gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		dialogFullAutoSettings.add(new JLabel("Stop when added N traces (0 = all):"), gbc);
		gbc.gridx++;
		dialogFullAutoSettings.add(nfAutoMaxTraces, gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		dialogFullAutoSettings.add(new JLabel("Start tracing only inside clip:"), gbc);
		gbc.gridx++;
		dialogFullAutoSettings.add(cbStartOnlyInsideClip, gbc);
		
		
		final RangeSliderPanel timeRange;
		final JCheckBox cbSingleTimeFrame;
		if(bt.btData.nNumTimepoints > 1)
		{
			
			final int [] nRange = new int [2];
			nRange[0] = 0;
			nRange[1] = bt.btData.nNumTimepoints - 1;
			timeRange = new RangeSliderPanel(nRange, nRange);
			//timeRange.makeConstrained( bt.btData.nCurrTimepoint, bt.btData.nCurrTimepoint );
			
			gbc.gridy++;
			gbc.gridx = 0;
			gbc.gridwidth = 2;
			dialogFullAutoSettings.add(timeRange, gbc);
			gbc.gridwidth = 1;
			
			cbSingleTimeFrame = new JCheckBox();
			boolean bSingleTimePoint = Prefs.get( "BigTrace.bFullAutoSingleTimeFrame", false );
			
			timeRange.setEnabled( !bSingleTimePoint );
			
			cbSingleTimeFrame.addItemListener( (e)->{timeRange.setEnabled(!cbSingleTimeFrame.isSelected());} );	
			cbSingleTimeFrame.setSelected(bSingleTimePoint);			

			gbc.gridx = 0;
			gbc.gridy++;
			dialogFullAutoSettings.add(new JLabel("Only current timepoint:"), gbc);
			gbc.gridx++;
			dialogFullAutoSettings.add(cbSingleTimeFrame,gbc);
		}
		else
		{
			timeRange = null;
			cbSingleTimeFrame = null;
		}
		
		//filler
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.weightx = 0.01;
		gbc.weighty = 0.2;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.VERTICAL;
		dialogFullAutoSettings.add(new JLabel(), gbc);
		
		////////////TRACING OPTIONS		
		final PanelTracingOptions panelGeneralTrace = new PanelTracingOptions(bt);
			
		////////////ONE-CLICK TRACING OPTIONS		
		final PanelOneClickTraceOptions panelOneClickOptions = new PanelOneClickTraceOptions(bt, false);

		//assemble pane
		tabPane.addTab("AutoTrace", dialogFullAutoSettings);
		tabPane.addTab("Tracing", panelGeneralTrace);
		tabPane.addTab("One click trace", panelOneClickOptions);
		
		int reply = JOptionPane.showConfirmDialog(null, tabPane, "Full auto tracing", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			panelGeneralTrace.getSetOptions();		
			panelOneClickOptions.getSetOptions();

			final double dAutoMinStartTrace = Double.parseDouble(nfMaxIntFullTraceStart.getText());
			Prefs.set("BigTrace.dAutoMinStartTraceInt", dAutoMinStartTrace );
			
			final double dAutoMinCurveLength = Double.parseDouble(nfAutoMinCurveLength.getText());
			Prefs.set("BigTrace.dAutoMinCurveLength", dAutoMinCurveLength );

			final int nAutoMaxTraces = Math.max(0, Integer.parseInt(nfAutoMaxTraces.getText()));
			Prefs.set("BigTrace.nAutoMaxTraces", nAutoMaxTraces );
			
			final boolean bMinCurveLengthInUnits = cbMinLengthInUnits.isSelected();
			Prefs.set("BigTrace.bMinCurveLengthInUnits", bMinCurveLengthInUnits );
			
			final boolean bStartOnlyInsideClip = cbStartOnlyInsideClip.isSelected();
			Prefs.set("BigTrace.bStartOnlyInsideClip", bStartOnlyInsideClip );
			
			int nFirstTP = 0;
			int nLastTP = 0;
			if(timeRange != null && cbSingleTimeFrame != null)
			{
				Prefs.set("BigTrace.bFullAutoSingleTimeFrame", cbSingleTimeFrame.isSelected() );
				if(cbSingleTimeFrame.isSelected())
				{
					nFirstTP = bt.bvvViewer.state().getCurrentTimepoint();
					nLastTP = bt.bvvViewer.state().getCurrentTimepoint();					
				}
				else
				{
					nFirstTP = timeRange.getMin();
					nLastTP = timeRange.getMax();
				}
			}
			launchFullAutoTrace(dAutoMinStartTrace, dAutoMinCurveLength, nFirstTP, nLastTP, nAutoMaxTraces);
		}

	}
	
	/** no limit on the number of traces (e.g. macros) **/
	public void launchFullAutoTrace(final double dAutoMinStartTrace, final double dAutoMinCurveLength, final int nFirstTP, final int nLastTP)
	{
		launchFullAutoTrace(dAutoMinStartTrace, dAutoMinCurveLength, nFirstTP, nLastTP, 0);
	}
	
	/** nAutoMaxTraces: stop after this number of added traces, 0 = no limit **/
	public void launchFullAutoTrace(final double dAutoMinStartTrace, final double dAutoMinCurveLength, final int nFirstTP, final int nLastTP, final int nAutoMaxTraces)
	{
		fullAutoTrace = new FullAutoTrace<>(bt);
		
		fullAutoTrace.nAutoMaxTraces = nAutoMaxTraces;
		
		fullAutoTrace.nFirstTP = nFirstTP;
		fullAutoTrace.nLastTP = nLastTP;

		fullAutoTrace.dAutoMinStartTraceInt = dAutoMinStartTrace;

		fullAutoTrace.dAutoMinCurveLength = dAutoMinCurveLength;
		
		bt.bInputLock = true;
		bt.setLockMode( true );
		fullAutoTrace.addPropertyChangeListener( bt.btPanel );

		if(bt.btMacro.bMacroMode)
		{
			  SwingUtilities.invokeLater(()->
			  {
				  butAuto.setEnabled( true );
				  butAuto.setIcon( tabIconCancel );
				  butAuto.setToolTipText( "Stop auto trace" );
			  });
		}
		else
		{
			  butAuto.setEnabled( true );
			  butAuto.setIcon( tabIconCancel );
			  butAuto.setToolTipText( "Stop auto trace" );
		}

		fullAutoTrace.butAuto = butAuto;
		fullAutoTrace.tabIconRestore = tabIconAuto;
		if( !bt.btMacro.bMacroMode )
		{
			fullAutoTrace.execute();
		}
		else
		{
			fullAutoTrace.runFullAutoTrace();
			TaskBT.runOnEDTAndWait( () ->
			{
				fullAutoTrace.wrapUp();
		});
		}
	}

	@Override
	public void actionPerformed( ActionEvent e )
	{
		// RUN TRACKING
		if(e.getSource() == butAuto)
		{
			if(!bt.bInputLock)
			{
				showDialogFullAutoTrace();
			}
			else
			{
				if(bt.bInputLock && butAuto.isEnabled() && fullAutoTrace!=null && !fullAutoTrace.isCancelled() && !fullAutoTrace.isDone())
				{
					fullAutoTrace.cancel( false );
				}
			}
		}
		
	}
}
