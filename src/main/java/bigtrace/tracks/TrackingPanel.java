package bigtrace.tracks;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;

import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.NumberField;
import bigtrace.gui.PanelTitle;
import bigtrace.gui.RangeSliderPanel;
import bigtrace.measure.MeasureValues;
import bigtrace.rois.AbstractCurve3D;
import bigtrace.rois.Roi3D;
import ij.IJ;
import ij.Prefs;

public class TrackingPanel < T extends RealType< T > & NativeType< T > > extends JPanel implements ListSelectionListener, ActionListener
{
	final BigTrace<T> bt;

	JButton butTrack;
	JButton butSettings;
	JButton butAlign;
	JButton butDelete;
	JButton butRename;
	JButton butGroups;
	public JList<String> jlist;
	JScrollPane listScroller;
	CurveTracker<T> btTracker;
	
	ImageIcon tabIconTrain;
	ImageIcon tabIconCancel;
	
	public TrackingPanel(final BigTrace<T> bt)
	{
		this.bt = bt;
		this.btTracker = null;// = new CurveTracker< >(bt);
		JPanel panTrackTools = new JPanel(new GridBagLayout());  
		panTrackTools.setBorder(new PanelTitle(" Tracking "));
		int nButtonSize = 40;
		
		URL icon_path = this.getClass().getResource("/bt_icons/train.png");
		tabIconTrain = new ImageIcon(icon_path);
		icon_path = this.getClass().getResource("/bt_icons/cancel.png");
		tabIconCancel = new ImageIcon(icon_path);
		butTrack = new JButton(tabIconTrain);
		butTrack.setToolTipText("Track");
		butTrack.setPreferredSize(new Dimension(nButtonSize , nButtonSize ));
		
		icon_path = this.getClass().getResource("/bt_icons/settings.png");
		ImageIcon tabIcon = new ImageIcon(icon_path);
		butSettings = new JButton(tabIcon);
		butSettings.setToolTipText("Settings");
		butSettings.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		
		butTrack.addActionListener(this);
		butSettings.addActionListener(this);
		
		GridBagConstraints cr = new GridBagConstraints();
		cr.gridx=0;
		cr.gridy=0;
		panTrackTools.add(butTrack,cr);
		
		cr.gridx++;
		JSeparator sp = new JSeparator(SwingConstants.VERTICAL);
		sp.setPreferredSize(new Dimension((int) (nButtonSize*0.5),nButtonSize));
		panTrackTools.add(sp,cr);
		//filler
		cr.gridx++;
		cr.weightx = 0.01;
		panTrackTools.add(new JLabel(), cr);
		cr.weightx = 0.0;
		cr.gridx++;
		panTrackTools.add(butSettings,cr);
		
		JPanel panTracksChange = new JPanel(new GridBagLayout());
		panTracksChange.setBorder(new PanelTitle(" Tracks "));
		cr = new GridBagConstraints();
		
		//synchronized ROI list
		jlist = new JList<>(bt.roiManager.listModel);
		listScroller = new JScrollPane(jlist);
		jlist.addListSelectionListener(this);
		listScroller.setPreferredSize(new Dimension(300, 400));
		listScroller.setMinimumSize(new Dimension(170, 250));
		
		jlist.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() == 2) 
				{
					// Double-click detected
					int index = jlist.locationToIndex(evt.getPoint());
					bt.roiManager.focusOnRoi(bt.roiManager.rois.get(index));
				}
		        //right click
		    	if (SwingUtilities.isRightMouseButton(evt))
				{
					if(bt.roiManager.activeRoi.intValue()>=0)
					{
						bt.roiManager.dialProperties();
					}
				}
			}
		});

		cr.gridx=0;
		cr.gridy=0;
		cr.gridheight=GridBagConstraints.REMAINDER;

		cr.fill  = GridBagConstraints.BOTH;
		cr.weightx=0.99;
		cr.weighty=0.99;
		panTracksChange.add(listScroller,cr);
		cr.weightx=0.0;
		cr.weighty=0.0;
		cr.fill = GridBagConstraints.NONE;

		butAlign = new JButton("Align");
		butAlign.addActionListener(this);
		cr.gridx++;
		cr.gridy++;
		cr.gridheight=1;
		panTracksChange.add(butAlign,cr);

		butDelete = new JButton("Delete");
		butDelete.addActionListener(this);
		cr.gridy++;
		panTracksChange.add(butDelete,cr);
		
		butRename = new JButton("Rename");
		butRename.addActionListener(this);
		cr.gridy++;
		panTracksChange.add(butRename,cr);
		
		butGroups = new JButton("Groups");
		butGroups.addActionListener(this);
		cr.gridy++;
		panTracksChange.add(butGroups,cr);

		// a solution for now
		butAlign.setMinimumSize(butRename.getPreferredSize());
		butAlign.setPreferredSize(butRename.getPreferredSize());
		butDelete.setMinimumSize(butRename.getPreferredSize());
		butDelete.setPreferredSize(butRename.getPreferredSize());
		butRename.setMinimumSize(butRename.getPreferredSize());		 
		butRename.setPreferredSize(butRename.getPreferredSize());
		butGroups.setMinimumSize(butRename.getPreferredSize());		 
		butGroups.setPreferredSize(butRename.getPreferredSize());


		// Blank/filler component
		cr.gridx++;
		cr.gridy++;
		cr.weightx = 0.01;
		cr.weighty = 0.01;
		panTracksChange.add(new JLabel(), cr);		

		// a solution for now
		panTracksChange.setMinimumSize(butGroups.getPreferredSize());
		panTracksChange.setPreferredSize(butGroups.getPreferredSize()); 


		//put all panels together
		cr = new GridBagConstraints();
		setLayout(new GridBagLayout());
		cr.insets = new Insets(4,4,2,2);
		cr.gridx = 0;
		cr.gridy = 0;
		cr.fill = GridBagConstraints.HORIZONTAL;

		//Line Tools
		add(panTrackTools,cr);
		//roi list
		cr.gridy++;
		cr.weighty = 0.99;
		cr.fill = GridBagConstraints.BOTH;
		add(panTracksChange,cr);

		// Blank/filler component
		cr.gridy++;
		cr.weightx = 0.01;
		cr.weighty = 0.01;
		add(new JLabel(), cr);    
	}
	
	void dialogAlign()
	{
		JPanel dialAlign = new JPanel(new GridBagLayout());
		GridBagConstraints cd = new GridBagConstraints();
		String [] sGroupSelection = new String [bt.roiManager.groups.size()];
		for (int i = 0; i < bt.roiManager.groups.size(); i++)
		{
			sGroupSelection[i] = bt.roiManager.groups.get( i ).getName();
		}
		JComboBox<String> cbGroup = new JComboBox<>(sGroupSelection);
		cd.gridx=0;
		cd.gridy=0;
		dialAlign.add(new JLabel("Group: "),cd);
		cd.gridy++;
		dialAlign.add(cbGroup,cd);
		int reply = JOptionPane.showConfirmDialog(null, dialAlign, "Align trace ROIs in a group", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);		
		
		if (reply == JOptionPane.OK_OPTION) 
		{
			alignROIsInGroup(cbGroup.getSelectedIndex());
		}
	}
	
	void alignROIsInGroup(final int indGroup)
	{
		class ROIsT
		{
			public final AbstractCurve3D sRoi;
			public final int nTimeFrame;
			public ROIsT(final AbstractCurve3D sRoi_, final int nTimeFrame_)
			{
				sRoi = sRoi_;
				nTimeFrame = nTimeFrame_;
			}
		}
		// creates the comparator for comparing stock value 
		class ROIsTComparator implements Comparator<ROIsT> 
		{ 
		  
		    @Override
			public int compare(ROIsT s1, ROIsT s2) 
		    { 
		        if (s1.nTimeFrame == s2.nTimeFrame) 
		            return 0; 
		        else if (s1.nTimeFrame > s2.nTimeFrame) 
		            return 1; 
		        else
		            return -1; 
		    } 
		} 
		final ArrayList<ROIsT> groupROIs = new ArrayList<>();
		Roi3D roi;
		//fill the list
		for(int i=0; i<bt.roiManager.rois.size();i++)
		{
			roi = bt.roiManager.rois.get( i );
			if(roi.getGroupInd() == indGroup)
			{
				if(roi.getType()==Roi3D.LINE_TRACE || roi.getType()==Roi3D.POLYLINE)
				{
					groupROIs.add( new ROIsT(( AbstractCurve3D ) roi,roi.getTimePoint()) );
				}
			}
		}
		if(groupROIs.size()<1)
		{
			IJ.log( "Cannot find traces to align in this group." );
			return;
		}
		
		Collections.sort(groupROIs, new ROIsTComparator()); 
		
		MeasureValues oldVect = new MeasureValues();
		MeasureValues newVect = new MeasureValues();
		int nFrameN = groupROIs.get( 0 ).nTimeFrame;
		(groupROIs.get( 0 ).sRoi).getEndsDirection(oldVect, bt.btData.globCal);
		for(int i = 1; i<groupROIs.size();i++)
		{
			AbstractCurve3D newRoi = groupROIs.get( i ).sRoi;
			if(nFrameN - newRoi.getTimePoint() == 0)
			{
				IJ.log("There are multiple ROIs in the same frame number #" +Integer.toString( nFrameN ));
			}
			nFrameN = newRoi.getTimePoint();
			newRoi.getEndsDirection(newVect, bt.btData.globCal);
			//if not looking at the same direction
			if(LinAlgHelpers.dot( oldVect.direction.positionAsDoubleArray(),  newVect.direction.positionAsDoubleArray())<0)
			{
				newRoi.reversePoints();
				for(int d=0;d<3;d++)
				{
					newVect.direction.setPosition((-1)*newVect.direction.getDoublePosition( d ) , d );
				}
				//System.out.println("swapped");

			}
			for(int d=0;d<3;d++)
			{
				oldVect.direction.setPosition(newVect.direction.getDoublePosition( d ) , d );
			}
		}
		IJ.log( "Aligned "+Integer.toString( groupROIs.size() )+ " ROIs in the " +bt.roiManager.groups.get( indGroup ).getName()+" group.");
		
	}
	
	/** tracking of LineTrace over time**/
	void simpleTracking()
	{
		JPanel dialogTrackSettings = new JPanel();
		
		dialogTrackSettings.setLayout(new GridBagLayout());
		
		GridBagConstraints cd = new GridBagConstraints();

		GBCHelper.alighLeft(cd);
		
		NumberField nfBoxExpand = new NumberField(4);
		
		final JCheckBox cbTraceMask = new JCheckBox("Avoid (mask) existing ROIs (experimental)");
		
		cd.gridx = 0;
		cd.gridy = 0;	
		String[] sTrackDirection = { "all timepoints", "forward in time", "backward in time", "range below" };
		JComboBox<String> trackDirectionList = new JComboBox<>(sTrackDirection);
		dialogTrackSettings.add(new JLabel("Tracking:"),cd);
		trackDirectionList.setSelectedIndex((int)Prefs.get("BigTrace.trackDirection", 0));
		cd.gridx++;
		dialogTrackSettings.add(trackDirectionList,cd);
		cd.gridy++;
		
		
		int [] nRange = new int [2];
		nRange[0] = 0;
		nRange[1] = bt.btData.nNumTimepoints - 1;
		RangeSliderPanel timeRange = new RangeSliderPanel(nRange, nRange);
		timeRange.makeConstrained( bt.btData.nCurrTimepoint, bt.btData.nCurrTimepoint );
		
		
		cd.gridx = 0;
		cd.gridwidth = 2;
		dialogTrackSettings.add(timeRange,cd);
		cd.gridwidth = 1;
		cd.gridy++;
		
		String[] sTrackNextFrame = { "ROI bounding box", "ROI's shape"};
		JComboBox<String> trackNextFrame = new JComboBox<>(sTrackNextFrame);
		
		cd.gridx = 0;	
		dialogTrackSettings.add(new JLabel("Search next curve inside:"),cd);
		trackNextFrame.setSelectedIndex((int)Prefs.get("BigTrace.trackNextFrame", 0));
		cd.gridx++;
		dialogTrackSettings.add(trackNextFrame,cd);
		cd.gridy++;
		
		cd.gridx = 0;	
		dialogTrackSettings.add(new JLabel("Expand ROI box search by (px):"),cd);
		cd.gridx++;
		nfBoxExpand.setIntegersOnly( true );
		nfBoxExpand.setText(Integer.toString((int)(Prefs.get("BigTrace.nTrackExpandBox", 0))));
		dialogTrackSettings.add(nfBoxExpand,cd);
		cd.gridy++;
		
		cbTraceMask.setSelected(bt.btData.bTrackingUseMask );
		cd.gridx = 0;
		cd.gridwidth = 2;
		dialogTrackSettings.add(cbTraceMask,cd);
	
		int reply = JOptionPane.showConfirmDialog(null, dialogTrackSettings, "Track settings", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			int nTrackDirection = trackDirectionList.getSelectedIndex();
			Prefs.set("BigTrace.trackDirection", nTrackDirection);
			
			bt.btData.bTrackingUseMask = cbTraceMask.isSelected();
			Prefs.set( "BigTrace.bTrackingUseMask", bt.btData.bTrackingUseMask  );
			
			this.btTracker = new CurveTracker< >(bt);
			
			//tracking range
			switch(nTrackDirection)
			{
				//all timepoints
				case 0:
					btTracker.nFirstTP = 0;
					btTracker.nLastTP = bt.btData.nNumTimepoints - 1;
					break;
				//forward 
				case 1:
					btTracker.nFirstTP = bt.btData.nCurrTimepoint;
					btTracker.nLastTP = bt.btData.nNumTimepoints - 1;
					break;
				//backwards
				case 2:
					btTracker.nFirstTP = 0;
					btTracker.nLastTP = bt.btData.nCurrTimepoint;
					break;
				//range
				case 3:
					btTracker.nFirstTP = timeRange.getMin();
					btTracker.nLastTP = timeRange.getMax();
					break;

					
			}
			
			int nNextFrame = trackNextFrame.getSelectedIndex();
			Prefs.set("BigTrace.trackNextFrame", nNextFrame);
			
			// box expand
			int nBoxExpand = Integer.parseInt(nfBoxExpand.getText());
			
			bt.bInputLock = true;
			bt.setLockMode(true);
			butTrack.setEnabled( true );
			Prefs.set("BigTrace.nTrackExpandBox", nBoxExpand);	
			btTracker.nNextFrame = nNextFrame;
			btTracker.nBoxExpand = nBoxExpand;
			butTrack.setIcon( tabIconCancel );
			butTrack.setToolTipText( "Stop tracking" );
			btTracker.addPropertyChangeListener( bt.btPanel );
			btTracker.butTrack = butTrack;
			btTracker.tabIconTrain = tabIconTrain; 
			btTracker.execute();
		}

				
	}
	@Override
	public void actionPerformed( ActionEvent e )
	{
		// RUN TRACKING
		if(e.getSource() == butTrack)
			if(jlist.getSelectedIndex()>-1)
			{

				if(!bt.bInputLock && bt.btData.nNumTimepoints > 1 && bt.roiManager.getActiveRoi().getType()==Roi3D.LINE_TRACE)
				{
					simpleTracking();
				}
				else
				{
					if(bt.bInputLock && butTrack.isEnabled() && btTracker!=null && !btTracker.isCancelled() && !btTracker.isDone())
					{
						btTracker.cancel( false );
					}
				}
			}
			else
			{
				bt.btPanel.progressBar.setString( "Please select a start ROI first." );
			}
		
		//Groups Manager
		if(e.getSource() == butGroups)
		{
			bt.roiManager.dialShowGroups();
			
		}
		//ALIGN ROIs in a GROUP
		if(e.getSource() == butAlign)
		{
			dialogAlign();
		}
		//DELETE
		if(e.getSource() == butDelete)
		{
			bt.roiManager.deleteActiveROI();
		}
		//RENAME
		if(e.getSource() == butRename)
		{
			bt.roiManager.dialRenameActiveROI();
		}
		
		//Settings
		if(e.getSource() == butSettings)
		{
			bt.roiManager.rmDiag.dialOneClickProperties();
		}
	}
	@Override
	public void valueChanged( ListSelectionEvent e )
	{
		if (e.getValueIsAdjusting() == false) 
		{
			bt.roiManager.jlist.setSelectedIndex(jlist.getSelectedIndex());
        }
		
	}

}
