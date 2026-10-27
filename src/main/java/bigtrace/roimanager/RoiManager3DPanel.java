package bigtrace.roimanager;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.BigTrace;
import bigtrace.gui.PanelTitle;
import ij.Prefs;

public class RoiManager3DPanel < T extends RealType< T > & NativeType< T > > extends JPanel implements ListSelectionListener, ActionListener 
{
	private static final long serialVersionUID = -2843907862066423151L;
	
	final BigTrace<T> bt;
	final RoiManager3D<T> rm;
	
	public static final int ADD_POINT = 0, 
			ADD_POINT_LINE = 1, 
			ADD_POINT_SEMIAUTOLINE = 2, 
			ADD_POINT_ONECLICKLINE = 3, 
			ADD_POINT_PLANE = 4;
	
	public static int mode = (int) Prefs.get("BigTrace.RoiManagerMode", ADD_POINT_LINE);
	
	//ROI LIST
	public DefaultListModel< String > listModel; 
	public final JList<String> jlist;
	final JScrollPane listScroller;
	
	public static interface Listener {
		public void activeRoiChanged(int nRoi);				
	}
	
	final JButton butDelete;
	final JButton butRename;
	final JButton butDeselect;
	final JButton butProperties;
	public final JToggleButton butShowAll;
	final JButton butSaveROIs;
	final JButton butLoadROIs;
	final JButton butROIGroups;
	final JButton butApplyGroup;
	final JButton butDisplayGroup;
	
	public boolean bShowAll = true;

	public final JComboBox<String> cbActiveChannel;
	final JComboBox<String> cbActiveGroup;

	final JToggleButton roiPointMode;
	final JToggleButton roiPolyLineMode;
	final JToggleButton roiPolySemiAMode;
	final JToggleButton roiPolyOneClickMode;
	final JToggleButton roiPlaneMode;
	final public JButton butAutoTrace;
	final JButton roiImport;
	final JButton roiSettings;
	final ImageIcon tabIconOCTrace;
	final ImageIcon tabIconCancel;
	
	public RoiManager3DPanel(final BigTrace<T> bt, final RoiManager3D<T> rm)
	{
		this.bt = bt;
		this.rm = rm;
		int nButtonSize = 40;
		
		JPanel panTracing = new JPanel(new GridBagLayout());  
		panTracing.setBorder(new PanelTitle(" Tracing type "));

		ButtonGroup roiTraceMode = new ButtonGroup();
		
		URL icon_path = this.getClass().getResource("/bt_icons/dot.png");
		ImageIcon tabIcon = new ImageIcon(icon_path);
		roiPointMode = new JToggleButton(tabIcon);
		roiPointMode.setToolTipText("Trace single point");
		roiPointMode.setPreferredSize(new Dimension(nButtonSize , nButtonSize ));
		if(mode == ADD_POINT)
			{roiPointMode.setSelected(true);}


		icon_path = this.getClass().getResource("/bt_icons/polyline.png");
		tabIcon = new ImageIcon(icon_path);
		roiPolyLineMode = new JToggleButton(tabIcon);
		roiPolyLineMode.setToolTipText("Trace polyline");
		roiPolyLineMode.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		if(mode == ADD_POINT_LINE)
			{roiPolyLineMode.setSelected(true);}

		icon_path = this.getClass().getResource("/bt_icons/semiauto.png");
		tabIcon = new ImageIcon(icon_path);
		roiPolySemiAMode = new JToggleButton(tabIcon);
		roiPolySemiAMode.setToolTipText("Semi auto trace");
		roiPolySemiAMode.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		if(mode == ADD_POINT_SEMIAUTOLINE)
			{roiPolySemiAMode.setSelected(true);}
		
		//add properties listener
		roiPolySemiAMode.addMouseListener(new MouseAdapter() 
		{
			@Override
			public void mouseClicked(MouseEvent evt) 
			{
				if (evt.getClickCount() == 2 || SwingUtilities.isRightMouseButton(evt) ) 
				{
					// Double-click detected
					rm.rmDialogs.dialSemiAutoProperties();
				} 
			}
		});	
		icon_path = this.getClass().getResource("/bt_icons/oneclicktrace.png");
		tabIconOCTrace = new ImageIcon(icon_path);
		icon_path = this.getClass().getResource("/bt_icons/cancel.png");
		tabIconCancel = new ImageIcon(icon_path);
		roiPolyOneClickMode = new JToggleButton(tabIconOCTrace);
		roiPolyOneClickMode.setToolTipText("One click trace");
		roiPolyOneClickMode.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		if(mode == ADD_POINT_ONECLICKLINE)
			{roiPolyOneClickMode.setSelected(true);}		
		
		roiPolyOneClickMode.addMouseListener(new MouseAdapter() 
		{
			@Override
			public void mouseClicked(MouseEvent evt) 
			{
				if (evt.getClickCount() == 2 || SwingUtilities.isRightMouseButton(evt)) 
				{
					// Double-click detected
					rm.rmDialogs.dialOneClickProperties();
				} 
			}
		});
		
		icon_path = this.getClass().getResource("/bt_icons/plane.png");
		tabIcon = new ImageIcon(icon_path);
		roiPlaneMode = new JToggleButton(tabIcon);
		roiPlaneMode.setToolTipText("Cross-section");
		roiPlaneMode.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		if(mode == ADD_POINT_PLANE)
			{roiPlaneMode.setSelected(true);}

		icon_path = this.getClass().getResource("/bt_icons/file_import.png");
		tabIcon = new ImageIcon(icon_path);
		roiImport = new JButton(tabIcon);
		roiImport.setToolTipText("Import ROIs");
		roiImport.setPreferredSize(new Dimension(nButtonSize, nButtonSize));

		icon_path = this.getClass().getResource("/bt_icons/autotrace.png");
		tabIcon = new ImageIcon(icon_path);
		butAutoTrace = new JButton(tabIcon);
		butAutoTrace.setToolTipText("Full auto tracing");
		butAutoTrace.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		rm.panelFullAutoTrace.initButton(butAutoTrace);
		
		icon_path = this.getClass().getResource("/bt_icons/settings.png");
		tabIcon = new ImageIcon(icon_path);
		roiSettings = new JButton(tabIcon);
		roiSettings.setToolTipText("Settings");
		roiSettings.setPreferredSize(new Dimension(nButtonSize, nButtonSize));

		//button group	 
		roiTraceMode.add(roiPointMode);
		roiTraceMode.add(roiPolyLineMode);
		roiTraceMode.add(roiPolySemiAMode);
		roiTraceMode.add(roiPolyOneClickMode);
		roiTraceMode.add(roiPlaneMode);

		roiPointMode.addActionListener(this);
		roiPolyLineMode.addActionListener(this);
		roiPolySemiAMode.addActionListener(this);
		roiPolyOneClickMode.addActionListener(this);
		roiPlaneMode.addActionListener(this);

		roiImport.addActionListener(this);
		roiSettings.addActionListener(this);

		//add to the panel
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		panTracing.add(roiPointMode,gbc);
		gbc.gridx++;
		panTracing.add(roiPolyLineMode,gbc);
		gbc.gridx++;
		panTracing.add(roiPolySemiAMode,gbc);
		gbc.gridx++;
		panTracing.add(roiPolyOneClickMode,gbc);
		gbc.gridx++;
		panTracing.add(roiPlaneMode,gbc);
		gbc.gridx++;
		JSeparator sp = new JSeparator(SwingConstants.VERTICAL);
		sp.setPreferredSize(new Dimension((int) (nButtonSize*0.5),nButtonSize));
		panTracing.add(sp,gbc);
		gbc.gridx++;
		
		panTracing.add(butAutoTrace,gbc);
		gbc.gridx++;
		
		//filler
		gbc.weightx = 0.01;
		panTracing.add(new JLabel(), gbc);
		gbc.weightx = 0.0;
		gbc.gridx++;

		panTracing.add(roiImport,gbc);
		gbc.gridx++;
		panTracing.add(roiSettings,gbc);

		///RoiLIST and buttons
		listModel = new DefaultListModel<>();
		jlist = new JList<>( listModel );
		jlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		jlist.setLayoutOrientation(JList.VERTICAL);
		jlist.setVisibleRowCount(-1);
		jlist.addListSelectionListener(this);
		jlist.addMouseListener(new MouseAdapter() 
		{
			@Override
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() == 2) {

					// Double-click detected
					int index = jlist.locationToIndex(evt.getPoint());
					rm.focusOnRoi(rm.rois.get(index));
				} 
				if (SwingUtilities.isRightMouseButton(evt))
				{
					if(rm.activeRoi.intValue() >= 0)
					{
						rm.rmDialogs.dialProperties();
					}
				}
			}
		});

		listScroller = new JScrollPane(jlist);
		// listScroller.setPreferredSize(new Dimension(400, 500));
		listScroller.setMinimumSize(new Dimension(170, 250));

		JPanel roiList = new JPanel(new GridBagLayout());
		roiList.setBorder(new PanelTitle(" ROI Manager "));

		GridBagConstraints cr = new GridBagConstraints();
		cr.gridx=0;
		cr.gridy=0;

		cr.gridheight = GridBagConstraints.REMAINDER;

		cr.fill  = GridBagConstraints.BOTH;
		cr.weightx = 0.99;
		cr.weighty = 0.99;

		roiList.add(listScroller,cr);

		cr.weightx = 0.0;
		cr.weighty = 0.0;
		cr.fill = GridBagConstraints.NONE;
		//cr.weighty=0.0;
		butDelete = new JButton("Delete");
		butDelete.addActionListener(this);
		cr.gridx++;
		cr.gridy++;
		cr.gridheight = 1;
		roiList.add(butDelete,cr);
		
		butRename = new JButton("Rename");
		butRename.addActionListener(this);
		cr.gridy++;
		roiList.add(butRename,cr);

		butDeselect = new JButton("Deselect");
		butDeselect.addActionListener(this);
		cr.gridy++;
		roiList.add(butDeselect,cr);

		butProperties = new JButton("Properties");
		butProperties.addActionListener(this);
		cr.gridy++;
		roiList.add(butProperties ,cr);

		butShowAll = new JToggleButton("Show all");
		butShowAll.addActionListener(this);
		butShowAll.setSelected(true);
		cr.gridy++;
		roiList.add(butShowAll ,cr);

		butSaveROIs = new JButton("Save ROIs");
		butSaveROIs.addActionListener(this);
		cr.gridy++;
		roiList.add(butSaveROIs ,cr);

		butLoadROIs = new JButton("Load ROIs");
		butLoadROIs.addActionListener(this);
		cr.gridy++;
		roiList.add(butLoadROIs ,cr);

		butROIGroups = new JButton("Groups");
		butROIGroups.addActionListener(this);
		cr.gridy++;
		roiList.add(butROIGroups ,cr);

		// Blank/filler component
		cr.gridx++;
		cr.gridy++;
		cr.weightx = 0.01;
		cr.weighty = 0.01;
		roiList.add(new JLabel(), cr);		 

		// a solution for now
		Dimension butDim = butProperties.getPreferredSize();
		butDelete.setMinimumSize(butDim);
		butDelete.setPreferredSize(butDim);
		butRename.setMinimumSize(butDim);		 
		butRename.setPreferredSize(butDim);
		butDeselect.setMinimumSize(butDim);		 
		butDeselect.setPreferredSize(butDim);
		butShowAll.setMinimumSize(butDim);		 
		butShowAll.setPreferredSize(butDim);
		butROIGroups.setMinimumSize(butDim);
		butROIGroups.setPreferredSize(butDim);

		JPanel panChannel = new JPanel(new GridBagLayout());
		panChannel.setBorder(new PanelTitle(""));

		String[] nCh = new String[bt.btData.nTotalChannels];
		for(int i = 0; i < nCh.length; i++)
		{
			nCh[i] = "channel " + Integer.toString(i+1);
		}
		cbActiveChannel = new JComboBox<>(nCh);
		cbActiveChannel.setSelectedIndex(0);
		cbActiveChannel.addActionListener(this);


		cr = new GridBagConstraints();
		cr.gridx = 0;
		cr.gridy = 0;
		panChannel.add(new JLabel("Active"),cr);
		cr.gridx++;
		panChannel.add(cbActiveChannel,cr);

		JPanel panGroup = new JPanel(new GridBagLayout());
		panGroup.setBorder(new PanelTitle(" Groups "));

		String[] nGroupNames = new String[rm.groups.size()];
		for(int i = 0; i < nGroupNames.length; i++)
		{
			nGroupNames[i] = rm.groups.get(i).getName();
		}
		cbActiveGroup = new JComboBox<>(nGroupNames);
		cbActiveGroup.setSelectedIndex(0);
		cbActiveGroup.setPrototypeDisplayValue("tyrosinated");
		cbActiveGroup.addActionListener(this);
		butApplyGroup = new JButton("Apply");
		butApplyGroup.addActionListener(this);
		icon_path = this.getClass().getResource("/bt_icons/group_visibility.png");
		tabIcon = new ImageIcon(icon_path);
		butDisplayGroup = new JButton(tabIcon);
		butDisplayGroup.setToolTipText("Toggle ROI groups visibility");
		butDisplayGroup.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		butDisplayGroup.addActionListener(this);


		cr = new GridBagConstraints();
		cr.gridx = 0;
		cr.gridy = 0;
		panGroup.add(butDisplayGroup,cr);
		cr.gridx++;
		panGroup.add(cbActiveGroup,cr);
		cr.gridx++;
		panGroup.add(butApplyGroup,cr);

		GridBagConstraints c = new GridBagConstraints();
		setLayout(new GridBagLayout());
		c.insets = new Insets(4,4,2,2);
		c.gridx = 0;
		c.gridy = 0;

		c.fill = GridBagConstraints.HORIZONTAL;

		//tracing
		add(panTracing,c);
		//roi list
		c.gridy++;
		c.weighty = 0.99;
		c.fill = GridBagConstraints.BOTH;
		add(roiList,c);
		c.gridy++;
		c.weighty = 0.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		add(panChannel,c);
		c.gridy++;
		add(panGroup,c);
		// Blank/filler component
		c.gridy++;
		c.weightx = 0.01;
		c.weighty = 0.01;
		add(new JLabel(), c);   
	}
	
	@Override
	public void valueChanged(ListSelectionEvent e) 
	{
		
		if (e.getValueIsAdjusting() == false) 
		{
			bt.repaintBVV();
			rm.roiMeasure.jlist.setSelectedIndex(jlist.getSelectedIndex());
			rm.btTracksPanel.jlist.setSelectedIndex( jlist.getSelectedIndex() );
			//No selection:
			if (jlist.getSelectedIndex() == -1) 
            {
            	rm.activeRoi.set(-1);
            //Multiple selection:disabled right now		
            } else if (jlist.getSelectedIndices().length > 1) {
            
            //Single selection
            } else 
            {

            	rm.activeRoi.set(jlist.getSelectedIndex());
            	//update the timepoint
            	if(rm.getActiveRoi().getTimePoint()!=bt.bvvViewer.state().getCurrentTimepoint())
            	{
            		bt.btData.bDeselectROITime = false;
            		bt.bvvViewer.setTimepoint(rm.getActiveRoi().getTimePoint());
            	}
            	//jlist.setSelectedIndex(activeRoi);
            	//update the timepoint
            	bt.repaintBVV();           
            }
        }
    }
	 @Override
	 public synchronized void removeAll()
	 {
		 rm.rois.clear();
	 }
	
	//buttons
	@Override
	public void actionPerformed(ActionEvent e) 
	{
		//TRACING TYPE
		if(e.getSource() == roiPointMode)
		{
			if(mode != ADD_POINT)
			{
				mode = ADD_POINT;
				Prefs.set("BigTrace.RoiManagerMode", mode);
				rm.unselect();
			}
		}
		if(e.getSource() == roiPolyLineMode)
		{
			if(mode != ADD_POINT_LINE)
			{
				mode = ADD_POINT_LINE;
				Prefs.set("BigTrace.RoiManagerMode", mode);
				rm.unselect();
			}
		}
		if(e.getSource() == roiPolySemiAMode)
		{
			if(mode != ADD_POINT_SEMIAUTOLINE)
			{
				mode = ADD_POINT_SEMIAUTOLINE;
				Prefs.set("BigTrace.RoiManagerMode", mode);
				rm.unselect();
			}
		}
		if(e.getSource() == roiPolyOneClickMode)
		{
			if(!bt.bInputLock)
			{
				if(mode != ADD_POINT_ONECLICKLINE)
				{
					mode = ADD_POINT_ONECLICKLINE;
					Prefs.set("BigTrace.RoiManagerMode", mode);
					rm.unselect();
				}
			}
			else
			{
				if(roiPolyOneClickMode.isEnabled())
				{
					bt.cancelOneClickTrace();
				}
			}
		}
		if(e.getSource() == roiPlaneMode)
		{
			if(mode != ADD_POINT_PLANE)
			{
				mode = ADD_POINT_PLANE;
				Prefs.set("BigTrace.RoiManagerMode", mode);
				rm.unselect();
			}
		}
		//SETTINGS
		if(e.getSource() == roiSettings)
		{
			rm.rmDialogs.dialRenderSettings();
		}
		
		//ACTIVE CHANNEL
		if(e.getSource() == cbActiveChannel)
		{
			rm.setActiveChannel(cbActiveChannel.getSelectedIndex());
		}
		
		//ACTIVE PRESET
		if(e.getSource() == cbActiveGroup)
		{
			//Dimension size= cbActiveGroup.getSize();
			if(rm.nActiveGroup != cbActiveGroup.getSelectedIndex())
			{
				rm.nActiveGroup = cbActiveGroup.getSelectedIndex();
				//unselect();
				
			}
		}	

		//SHOW ALL BUTTON
		if(e.getSource() == butShowAll)
		{
			this.bShowAll = butShowAll.isSelected();
			bt.repaintBVV();
		}
		
		//SAVE ROIS
		if(rm.rois.size()>0)
		{
			
			if(e.getSource() == butSaveROIs)
			{
				rm.rmDialogs.diagSaveROIs();
			}
		}
		
		//LOAD ROIS
		if(e.getSource() == butLoadROIs)
		{
			rm.rmDialogs.diagLoadROIs();
		}
		
		//IMPORT ROIS
		if(e.getSource() == roiImport)
		{
			rm.rmDialogs.diagImportROIs();
		}
		
		//Groups Manager
		if(e.getSource() == butROIGroups)
		{
			rm.dialShowGroups();
			
		}
		
		//GROUP VISIBILITY
		if(e.getSource() == butDisplayGroup)
		{
			rm.rmDialogs.dialGroupVisibility();
		}
		
		///SIDE ROI SPECIFIC LIST BUTTONS
		if(rm.activeRoi.intValue() >= 0)
		{
			//DELETE
			if(e.getSource() == butDelete)
			{
				rm.deleteActiveROI();
			}
			//RENAME
			if(e.getSource() == butRename)
			{
				rm.dialRenameActiveROI();

			}
			//DESELECT
			if(e.getSource() == butDeselect)
			{
				rm.unselect();				 
			}
			
			//PROPERTIES
			if(e.getSource() == butProperties)
			{
				rm.rmDialogs.dialProperties();
			}
			
			//APPLY GROUP		
			if(e.getSource() == butApplyGroup)
			{
				rm.applyGroupToROI(rm.activeRoi.intValue(), rm.nActiveGroup);
			}	
			
		}
		
	}
	
	/** adjusts button icons for one-click tracing mode **/
	public void setOneClickTracing(boolean bBegin)
	{
		if(bBegin)
		{
			roiPolyOneClickMode.setIcon( tabIconCancel );
			roiPolyOneClickMode.setToolTipText( "Stop tracing" );
			roiPolyOneClickMode.setEnabled( true );
		}
		else
		{
			roiPolyOneClickMode.setIcon(tabIconOCTrace);
			roiPolyOneClickMode.setToolTipText("One click trace");			
		}
	}
	
	void updateGroupSelector()
	{
		int nGroupSave = rm.nActiveGroup;
		cbActiveGroup.removeAllItems();
		for(int i = 0; i < rm.groups.size(); i++)
		{
			//cbActiveGroup.addItem(groups.get(i).getName());
			cbActiveGroup.insertItemAt(rm.groups.get(i).getName(),i);
		}
		if(nGroupSave>(cbActiveGroup.getItemCount()-1))
		{
			cbActiveGroup.setSelectedIndex(0);
		}
		else
		{
			cbActiveGroup.setSelectedIndex(nGroupSave);
		}
	}
	
	public void updateGroupsList()
	{		 
		 cbActiveGroup.removeAllItems();
		 for(int i = 0; i < rm.groups.size(); i++)
		 {
			 cbActiveGroup.addItem(rm.groups.get(i).getName());
		 }
		 cbActiveGroup.setSelectedIndex(0);
		 rm.nActiveGroup = 0;

	}
	
	 /**a dds new point to Point3D, Polyline3D or Plane3D ROI **/
	 public synchronized void addPoint(RealPoint point_)
	 {
		 switch (mode)
		 {
		 case ADD_POINT:
			 rm.addPoint3D(point_);
			 break;
		 case ADD_POINT_LINE:
			 rm.addPointToLine(point_);
			 break;
		 case ADD_POINT_PLANE:
			 rm.addPointToPlane(point_);
			 break;
		 }
		 
		 bt.repaintBVV();		 
	 }
	
}
