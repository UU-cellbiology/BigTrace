package bigtrace.gui;

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

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.BigTrace;
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
		URL icon_path = this.getClass().getResource("/icons/autotrace.png");
		tabIconAuto = new ImageIcon(icon_path);
		icon_path = this.getClass().getResource("/icons/cancel.png");
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
		
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
		
		GridBagConstraints gbc = new GridBagConstraints();

		GBCHelper.alighLeft(gbc);
		
		gbc.weighty = 0.0;
		
		NumberField nfMaxIntFullTraceStart = new NumberField(5);
		NumberField nfAutoMinCurvePoints = new NumberField(5);
		
		nfMaxIntFullTraceStart.setIntegersOnly( false );
		nfMaxIntFullTraceStart.setText( df3.format(Prefs.get("BigTrace.dAutoMinStartTraceInt",128.0)) );
		nfAutoMinCurvePoints.setIntegersOnly( true );
		nfAutoMinCurvePoints.setText( Integer.toString((int)Prefs.get("BigTrace.nAutoMinPointsCurve",3)) );
	
		gbc.gridx = 0;
		gbc.gridy = 0;
		dialogFullAutoSettings.add(new JLabel("Min intensity trace start:"),gbc);
		gbc.gridx++;
		
		dialogFullAutoSettings.add(nfMaxIntFullTraceStart,gbc);
		
		gbc.gridx = 0;
		gbc.gridy++;
		dialogFullAutoSettings.add(new JLabel("Min # points in curve:"),gbc);
		gbc.gridx++;
		dialogFullAutoSettings.add(nfAutoMinCurvePoints,gbc);
		
		
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
			
			final int nAutoMinPointsCurve = Integer.parseInt(nfAutoMinCurvePoints.getText());
			Prefs.set("BigTrace.nAutoMinPointsCurve", nAutoMinPointsCurve );
			int nFirstTP = 0;
			int nLastTP = 0;
			if(timeRange != null && cbSingleTimeFrame != null)
			{
				Prefs.set("BigTrace.bFullAutoSingleTimeFrame", cbSingleTimeFrame.isSelected() );
				if(cbSingleTimeFrame.isSelected())
				{
					nFirstTP = bt.viewer.state().getCurrentTimepoint();
					nLastTP = bt.viewer.state().getCurrentTimepoint();					
				}
				else
				{
					nFirstTP = timeRange.getMin();
					nLastTP = timeRange.getMax();
				}
			}
			launchFullAutoTrace(dAutoMinStartTrace, nAutoMinPointsCurve, nFirstTP, nLastTP);
		}

	}
	
	public void launchFullAutoTrace(final double dAutoMinStartTrace, final int nAutoMinPointsCurve, final int nFirstTP, final int nLastTP)
	{
		fullAutoTrace = new FullAutoTrace<>(bt);
		
		fullAutoTrace.nFirstTP = nFirstTP;
		fullAutoTrace.nLastTP = nLastTP;

		fullAutoTrace.dAutoMinStartTraceInt = dAutoMinStartTrace;

		fullAutoTrace.nAutoMinPointsCurve = nAutoMinPointsCurve;
		
		bt.bInputLock = true;
		bt.setLockMode(true);
		fullAutoTrace.addPropertyChangeListener( bt.btPanel );
		butAuto.setEnabled( true );
		butAuto.setIcon( tabIconCancel );
		butAuto.setToolTipText( "Stop auto trace" );
		fullAutoTrace.butAuto = butAuto;
		fullAutoTrace.tabIconRestore = tabIconAuto;
		fullAutoTrace.execute();
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
