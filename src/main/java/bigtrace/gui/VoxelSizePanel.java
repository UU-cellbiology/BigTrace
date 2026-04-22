package bigtrace.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;




public class VoxelSizePanel extends JPanel implements NumberField.Listener, FocusListener
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 7324190494486161144L;
	
	private double [] vxAllSize;
	public NumberField [] nfAllSize;
	public JTextField tfUnits; 
	private ArrayList<Listener> listeners =	new ArrayList<>();
	
	public static interface Listener {
		public void voxelSizeChanged(double [] newVoxelSize);

	}
	
	public VoxelSizePanel(double [] dVoxelSize, String sUnits)
	{
		super();
		
		vxAllSize = new double [3];
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		
		nfAllSize = new NumberField[3];
		for (int d = 0; d < 3; d++)
		{
			vxAllSize[ d ] = dVoxelSize[ d ];
			nfAllSize[ d ] = new NumberField( 4 );
			nfAllSize[ d ].setText( df.format( dVoxelSize[ d ] ) );
			nfAllSize[ d ].setMinimumSize( nfAllSize[ d ].getPreferredSize() );
			nfAllSize[ d ].addListener(this);
			nfAllSize[ d ].addNumberFieldFocusListener(this);			
		}

		tfUnits = new JTextField(8);
		tfUnits.setText(sUnits);	

		setLayout(gridbag);
		
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridx = 0;
		c.gridy = 0;
		this.add(new JLabel("X"),c);
		c.gridx++;
		this.add(nfAllSize[0],c);
		
		c.gridx++;
		this.add(new JLabel("Y"),c);
		c.gridx++;
		this.add(nfAllSize[1],c);

		c.gridx++;
		this.add(new JLabel("Z"),c);
		c.gridx++;
		this.add(nfAllSize[2],c);
		
		c.gridwidth = 3;
		c.gridx = 0;
		c.gridy++;

		JLabel jlUnits =new JLabel("Units");
		jlUnits.setHorizontalAlignment(SwingConstants.RIGHT);
		this.add(jlUnits,c);
		c.gridx+=3;
		this.add(tfUnits,c);
	}

	@Override
	public void valueChanged(double v) {
	
		if(updateVoxelSize())
			fireVoxelSizeChanged(vxAllSize);
	}
	
	private void fireVoxelSizeChanged(double [] newVoxelSize) 
	{
		for(Listener l : listeners)
			l.voxelSizeChanged(newVoxelSize);
	}
	public void addVoxelSizePanelListener(Listener l) {
        listeners.add(l);
    }

	@Override
	public void focusGained(FocusEvent arg0) {
	
	}

	@Override
	public void focusLost(FocusEvent arg0) {
		if(updateVoxelSize())
			fireVoxelSizeChanged(vxAllSize);
	}
	
	public void setVoxelSize(double [] dVoxelSize, String sUnits)
	{
		tfUnits.setText(sUnits);
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		for (int i = 0; i < 3; i++)
		{
			nfAllSize[i].setText(df.format( dVoxelSize[i]));
			vxAllSize[i] = dVoxelSize[i];
		}
		fireVoxelSizeChanged(vxAllSize);
	}
	
	private boolean updateVoxelSize()
	{
		double diff = 0.0;
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.00", decimalFormatSymbols);
		
		for(int d = 0; d < 3; d++)
		{
			//just in case
			if(Double.parseDouble(nfAllSize[d].getText()) <= 0)
			{
				nfAllSize[d].setText(df.format(vxAllSize[d]));
			}
			diff += Math.abs(vxAllSize[d]-Double.parseDouble(nfAllSize[d].getText()));
			vxAllSize[d]=Double.parseDouble(nfAllSize[d].getText());
		}
		if(diff > 0.001)
		{
			return true;
		}
		return false;
	}
	
	public void allowVoxelSizeChange(boolean allow)
	{
		for(int d = 0; d < 3; d++)
		{
			nfAllSize[ d ].setTFEnabled(allow);
		}
		tfUnits.setEnabled(allow);
	}

}
