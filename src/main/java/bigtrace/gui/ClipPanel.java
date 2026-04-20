package bigtrace.gui;


import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatIntelliJLaf;

import net.imglib2.Interval;


public class ClipPanel extends JPanel {
	
	public static void main(String[] args) {
	
		try {
		    UIManager.setLookAndFeel( new FlatIntelliJLaf() );
		} catch( Exception ex ) {
		    System.err.println( "Failed to initialize LaF" );
		}
		JFrame frame = new JFrame();

		ClipPanel slider = new ClipPanel(new long[] {60,80,100});
		frame.getContentPane().add(slider);
		frame.pack();
		frame.setVisible(true);
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1885320351623882576L;
	private RangeSliderPanel [] bbAxes = new RangeSliderPanel[3];
	private ArrayList<Listener> listeners =	new ArrayList<>();
	
	public JButton butExtractClipped;
	public JCheckBox showClippedBox;
	public JCheckBox clipROIBox;
	private boolean blockUpdates = false;
	
	public static interface Listener {
		public void boundingBoxChanged(long [][] box);

	}
	
	private RangeSliderPanel addRangeSlider(String label, int[] realMinMax, int[] setMinMax, GridBagConstraints c) {
		RangeSliderPanel slider = new RangeSliderPanel(realMinMax, setMinMax);

		GridBagLayout layout = (GridBagLayout)getLayout();

		c.gridx = 0;
		if(label != null) {
			JLabel theLabel = new JLabel(label);
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.EAST;
			c.gridwidth = 1;
			c.weightx = 0;
			layout.setConstraints(theLabel, c);
			add(theLabel);
			c.gridx++;
		}
		//c.gridx++;
		c.gridwidth = 3;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		layout.setConstraints(slider, c);
		add(slider);
		c.gridy++;
		return slider;
	}
	
	//public ClipPanel(int nW, int nH, int nSl) {
	public ClipPanel(long [] maxDim) {
		super();
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints cd = new GridBagConstraints();

		setLayout(gridbag);

		cd.gridy = 0;
		//cd.gridwidth=3;
		String[] axesS = {"X","Y","Z"};
		for(int d = 0; d < 3; d++)
		{
			bbAxes[d] = addRangeSlider(
					axesS[d],
					new int[] {0, (int) maxDim[d]},
					new int[] {0, (int) maxDim[d]},
					cd);
		}

		cd.gridwidth = 1;
		cd.weightx = 0.1;
		cd.fill = GridBagConstraints.NONE;
		cd.anchor = GridBagConstraints.WEST;
		showClippedBox = new JCheckBox("Box", false);
		
		clipROIBox = new JCheckBox("Clip ROIs", false);
		cd.gridx = 1;
		//c.gridy++;
		this.add(showClippedBox,cd);
		cd.gridx = 2;
		this.add(clipROIBox,cd);
		cd.gridx = 3;
		cd.anchor = GridBagConstraints.EAST;
		butExtractClipped = new JButton("Extract");
		this.add(butExtractClipped,cd);


		RangeSliderPanel.Listener bbListener = new RangeSliderPanel.Listener() {
			@Override
			public void sliderChanged() 
			{
				if(!blockUpdates)
				{
					long [][] new_box = new long [2][3];
					for(int d = 0; d < 3; d++)
					{
						new_box [0][d] = bbAxes[d].getMin();
						new_box [1][d] = bbAxes[d].getMax();						
					}
					fireBoundingBoxChanged(new_box);
				}
			}
		};
		for(int d = 0; d < 3; d++)
		{
			bbAxes[d].addSliderChangeListener(bbListener);
		}
	}

	public void setBoundingBox(int bbx0, int bby0, int bbz0, int bbx1, int bby1, int bbz1) 
	{
		bbAxes[0].setMinAndMax(bbx0, bbx1);
		bbAxes[1].setMinAndMax(bby0, bby1);
		bbAxes[2].setMinAndMax(bbz0, bbz1);
	}
	
	public void setBoundingBox(final long [][] box) 
	{
		for(int d = 0; d < 3; d++)
		{
			bbAxes[d].setMinAndMax((int)box[0][d], (int)box[1][d]);
		}
	}
	
	public void setBoundingBox(final Interval interval) 
	{
		long [][] box = new long[2][3];
		box[0] = interval.minAsLongArray();
		box[1] = interval.maxAsLongArray();
		setBoundingBox(box);
	}
	
	public void setBounds(long [] maxDim)
	{
		blockUpdates = true;

		for(int d = 0; d < 3; d ++)
		{
			bbAxes[d].setBounds( new int[] {0, (int) maxDim[d]}) ;
		}
		blockUpdates = false;

	}
	
	public long [][] getBoundingBox()
	{
		long [][] boxout = new long[2][3];
		for(int d = 0; d < 3; d++)
		{
			boxout[0][d] = bbAxes[d].getMin();
			boxout[1][d] = bbAxes[d].getMax();			
		}	
		return boxout;
	}


	public void addClipPanelListener(Listener l) {
        listeners.add(l);
    }

	private void fireBoundingBoxChanged(long [][] box) {
		for(Listener l : listeners)
			l.boundingBoxChanged(box);
	}

}
