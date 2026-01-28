package bigtrace.rois;

import java.awt.Color;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.joml.Matrix4fc;
import com.jogamp.opengl.GL3;

import bdv.tools.brightness.ColorIcon;
import ij.Prefs;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import bigtrace.BigTrace;
import bigtrace.geometry.Line3D;
import bigtrace.gui.NumberField;
import bigtrace.gui.PanelFullAutoTrace;
import bigtrace.gui.PanelTitle;
import bigtrace.measure.RoiMeasure3D;
import bigtrace.tracks.TrackingPanel;


public class RoiManager3D < T extends RealType< T > & NativeType< T > > extends JPanel implements ListSelectionListener, ActionListener 
{
	final BigTrace <T> bt;

	private static final long serialVersionUID = -2843907862066423151L;

	public static final int ADD_POINT = 0, 
							ADD_POINT_LINE = 1, 
							ADD_POINT_SEMIAUTOLINE = 2, 
							ADD_POINT_ONECLICKLINE = 3, 
							ADD_POINT_PLANE = 4;

	public final ArrayList<Roi3D> rois = new ArrayList<>();
	
	public AtomicInteger activeRoi = new AtomicInteger (-1);// = -1;

	public ArrayList<Roi3DGroup> groups = new ArrayList<>();

	final static String sUndefinedGroupName = "*undefined*";

	public int nActiveGroup = 0;

	public Color activePointColor = new Color((int)Prefs.get( "BigTrace.activePointColor", Color.YELLOW.getRGB() ));
	public Color activeLineColor = new Color((int)Prefs.get( "BigTrace.activeLineColor", Color.RED.getRGB() ));

	public ColorUserSettings selectColors = new ColorUserSettings(); 
	public static int mode = (int) Prefs.get("BigTrace.RoiManagerMode", ADD_POINT_LINE);
	public boolean bShowAll = true;
	
	//MEASURE OBJECT/PANEL
	public RoiMeasure3D<T> roiMeasure = null;
	
	//TRACKS PANEL
	public TrackingPanel<T> btTracksPanel = null;
	
	//dialogs
	public final RoiManager3DDialogs<T> rmDiag;

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
	
	public final PanelFullAutoTrace< T > panelFullAutoTrace;

	final ImageIcon tabIconOCTrace;
	final ImageIcon tabIconCancel;
	
	private final ArrayList<Listener> listeners = new ArrayList<>();

		
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public RoiManager3D(BigTrace<T> bt)
	{

		this.bt = bt;
		rmDiag = new RoiManager3DDialogs(bt);
		panelFullAutoTrace = new PanelFullAutoTrace<>(bt);
		int nButtonSize = 40;

		JPanel panTracing = new JPanel(new GridBagLayout());  
		panTracing.setBorder(new PanelTitle(" Tracing type "));

		ButtonGroup roiTraceMode = new ButtonGroup();

		//initialize new *undefined* ROI group
		groups.add(new Roi3DGroup(sUndefinedGroupName, bt.btData.undefPointSize, new Color(bt.btData.undefPointColor), bt.btData.undefLineThickness, new Color(bt.btData.undefLineColor), bt.btData.undefRenderType) );
		nActiveGroup = 0;

		URL icon_path = this.getClass().getResource("/bt_icons/dot.png");
		ImageIcon tabIcon = new ImageIcon(icon_path);
		roiPointMode = new JToggleButton(tabIcon);
		roiPointMode.setToolTipText("Trace single point");
		roiPointMode.setPreferredSize(new Dimension(nButtonSize , nButtonSize ));
		if(mode == RoiManager3D.ADD_POINT)
			{roiPointMode.setSelected(true);}


		icon_path = this.getClass().getResource("/bt_icons/polyline.png");
		tabIcon = new ImageIcon(icon_path);
		roiPolyLineMode = new JToggleButton(tabIcon);
		roiPolyLineMode.setToolTipText("Trace polyline");
		roiPolyLineMode.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		if(mode == RoiManager3D.ADD_POINT_LINE)
			{roiPolyLineMode.setSelected(true);}

		icon_path = this.getClass().getResource("/bt_icons/semiauto.png");
		tabIcon = new ImageIcon(icon_path);
		roiPolySemiAMode = new JToggleButton(tabIcon);
		roiPolySemiAMode.setToolTipText("Semi auto trace");
		roiPolySemiAMode.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		if(mode == RoiManager3D.ADD_POINT_SEMIAUTOLINE)
			{roiPolySemiAMode.setSelected(true);}
		//add properties listener
		roiPolySemiAMode.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() == 2) {

					// Double-click detected
					rmDiag.dialSemiAutoProperties();
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
		if(mode == RoiManager3D.ADD_POINT_ONECLICKLINE)
			{roiPolyOneClickMode.setSelected(true);}		
		
		roiPolyOneClickMode.addMouseListener(new MouseAdapter() 
		{
			@Override
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() == 2) 
				{
					// Double-click detected
					rmDiag.dialOneClickProperties();
				} 
			}
		});

		icon_path = this.getClass().getResource("/bt_icons/plane.png");
		tabIcon = new ImageIcon(icon_path);
		roiPlaneMode = new JToggleButton(tabIcon);
		roiPlaneMode.setToolTipText("Cross-section");
		roiPlaneMode.setPreferredSize(new Dimension(nButtonSize, nButtonSize));
		if(mode == RoiManager3D.ADD_POINT_PLANE)
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
		panelFullAutoTrace.initButton(butAutoTrace);
		
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
		GridBagConstraints ct = new GridBagConstraints();
		ct.gridx = 0;
		ct.gridy = 0;
		panTracing.add(roiPointMode,ct);
		ct.gridx++;
		panTracing.add(roiPolyLineMode,ct);
		ct.gridx++;
		panTracing.add(roiPolySemiAMode,ct);
		ct.gridx++;
		panTracing.add(roiPolyOneClickMode,ct);
		ct.gridx++;
		panTracing.add(roiPlaneMode,ct);
		ct.gridx++;
		JSeparator sp = new JSeparator(SwingConstants.VERTICAL);
		sp.setPreferredSize(new Dimension((int) (nButtonSize*0.5),nButtonSize));
		panTracing.add(sp,ct);
		ct.gridx++;
		
		panTracing.add(butAutoTrace,ct);
		ct.gridx++;
		
		//filler
		ct.weightx = 0.01;
		panTracing.add(new JLabel(), ct);
		ct.weightx = 0.0;
		ct.gridx++;

		panTracing.add(roiImport,ct);
		ct.gridx++;
		panTracing.add(roiSettings,ct);

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
					focusOnRoi(rois.get(index));
				} 
				if (SwingUtilities.isRightMouseButton(evt))
				{
					if(activeRoi.intValue()>=0)
					{
						dialProperties();
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

		cr.weightx=0.0;
		cr.weighty=0.0;
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
		for(int i=0;i<nCh.length;i++)
		{
			nCh[i] = "channel "+Integer.toString(i+1);
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

		String[] nGroupNames = new String[groups.size()];
		for(int i=0;i<nGroupNames.length;i++)
		{
			nGroupNames[i] = groups.get(i).getName();
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
		c.insets=new Insets(4,4,2,2);
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
	
	 /** adds provided ROI to the end of the list **/
	 public synchronized void addRoi(final Roi3D newRoi)
	 {		
		 rois.add(newRoi);		 
		 listModel.addElement(getFullDisplayedRoiName(newRoi));
		 jlist.setSelectedIndex( rois.size() - 1 );
		 activeRoi.set( rois.size() - 1 );
	 }

	 /** inserts ROI to the ROI list with the provided index **/
	 public synchronized void insertRoi(final Roi3D newRoi, final int nInsertN)
	 {		
		 if(nInsertN < 0 || nInsertN > (rois.size()-1))
		 {
			 System.err.println("ERROR! Index of ROI insertion is out of the roi list range.");
			 System.err.println("Appending ROI to the end of the list.");
			 addRoi(newRoi);
			 return;
		 }
		 rois.add(nInsertN, newRoi);
		 listModel.insertElementAt(newRoi.getName(), nInsertN);
		 jlist.setSelectedIndex( nInsertN );
		 activeRoi.set(nInsertN);

	 }

	 public void focusOnRoi(Roi3D roi)
	 {	
		 final Interval roiBoundingBox = roi.getBoundingBox(); 
		 if(roiBoundingBox != null)
		 {
			 final Interval zoomInterval = Intervals.intersect(Intervals.expand(roiBoundingBox, bt.btData.nROIDoubleClickClipExpand), bt.btData.getDataCurrentSourceFull());
			 bt.focusOnInterval(zoomInterval);
			 if(bt.btData.bROIDoubleClickClip)
			 {
				 bt.btPanel.clipPanel.setBoundingBox(zoomInterval);
			 }			 			 
		 }
		 else
		 {
			 System.out.println("NOT IMPLEMENTED YET!");
		 }
	 }
	 
	 public Roi3D makeRoi(int nRoiType, int nTimePoint)
	 {
		 Roi3D newRoi;
		 
		 switch (nRoiType)
		 {
		 case Roi3D.POINT:
			 newRoi = new Point3D(bt.btData, groups.get(nActiveGroup), nTimePoint);
			 break;
		 case Roi3D.POLYLINE:
			 newRoi = new PolyLine3D(bt.btData, groups.get(nActiveGroup),nTimePoint);
			 break;
		 case Roi3D.LINE_TRACE:
			 newRoi = new LineTrace3D(bt.btData, groups.get(nActiveGroup),nTimePoint);
			 break;
		 case Roi3D.PLANE:
			 newRoi = new CrossSection3D(bt.btData, groups.get(nActiveGroup),bt.btData.nDimIni,nTimePoint);
			 break;
		 case Roi3D.BOX:
			 newRoi = new Box3D(bt.btData, groups.get(nActiveGroup),nTimePoint);
			 break;
		 default:
			 newRoi = new Point3D(bt.btData, groups.get(nActiveGroup),nTimePoint);
		 }
		 newRoi.setGroupInd(nActiveGroup);
		 return newRoi;
	 }
	 
	 /** returns the full name of the group for the ROI**/
	 public String getGroupName(final Roi3D nRoi)
	 {
		 final int nInd = nRoi.getGroupInd(); 
		 
		 if(nInd == 0 || (nInd > groups.size()-1))
		 {
			 return sUndefinedGroupName;
		 }
		 
		 return groups.get(nInd).getName();
	 }
	 
	 /** returns ROI name with a short 3 letters group prefix in squared brackets**/
	 public String getGroupPrefixRoiNameBase(final Roi3D nRoi)
	 {
		 String nFullName;
		 final int nInd = nRoi.getGroupInd(); 
		 if(nInd == 0 || (nInd > groups.size()-1))
		 {
			 nFullName = nRoi.getName();
		 }
		 else
		 {
			 int nGNameLength = groups.get(nInd).getName().length();
			 nGNameLength = Math.min(nGNameLength, 3);
			 nFullName = "["+groups.get(nInd).getName().substring(0, nGNameLength)+"]"+ nRoi.getName();
		 }
		 return nFullName;
	 }
	 
	 /** returns ROI name with a TXXX time point + short 3 letters group prefix in squared brackets**/
	 public String getFullDisplayedRoiName(final Roi3D roi)
	 {
		 final String sTimeFormat = Integer.toString(String.valueOf(bt.btData.nNumTimepoints).length());

		 if(bt.btData.nNumTimepoints > 1)
		 {
			 return "T"+String.format("%0"+sTimeFormat+"d", roi.getTimePoint())+"_"+bt.roiManager.getGroupPrefixRoiNameBase(roi);
		 }
		 
		 //single time point, skip time
		 return bt.roiManager.getGroupPrefixRoiNameBase(roi);
	 }

	 public Roi3D getActiveRoi()
	 {
		 if(activeRoi.intValue()>-1)
		 {
			 return rois.get(activeRoi.intValue());
		 }
		 return null;
	 }
	 /** removes ROI and updates ListModel
	  * does not update activeRoi index! **/
	 public synchronized void removeRoi(int roiIndex)
	 {
		 int nVal = activeRoi.intValue();
		 if(roiIndex<rois.size())
		 {
			 rois.remove(roiIndex);
			 listModel.removeElementAt(roiIndex);
			 //activeRoi = -1;
		 }
		 //not sure what is going on here (why activeRoi becomes -1),
		 //but workaround for now
		 activeRoi.set( nVal-1 );
		 if(activeRoi.intValue()<0)
		 {
			 jlist.clearSelection();
		 }
	 }
	 
	 
	 /** removes active ROI and updates ListModel
	  * and activeRoi index **/
	 public synchronized void removeActiveRoi()
	 {
		 
		 if(activeRoi.intValue()>=0)
		 {
			 rois.remove(activeRoi.intValue());
			 listModel.removeElementAt(activeRoi.intValue());
			 activeRoi.decrementAndGet();
			 if(activeRoi.intValue()<0)
			 {
				 jlist.clearSelection();
			 }
			 fireActiveRoiChanged(activeRoi.intValue());
		 }

	 }
	 
	 @Override
	 public synchronized void removeAll()
	 {
		 rois.clear();
	 }
	 
	 /** Draw all ROIS **/
	 public void draw(final GL3 gl, final Matrix4fc pvm,  Matrix4fc vm, final int[] screen_size)
	 {
	       Roi3D roi;
	       Color savePointColor = null;
	       Color saveLineColor = null;
	       int i;
	       int nShift;
	       float fOpacityScale = 1.0f;
	       float fOpacitySave = 1.0f;
	       int nMinF = Math.min(0,bt.btData.timeFade);
	       int nMaxF = Math.max(0,bt.btData.timeFade);
	       if(bt.btData.timeRender == 0)
	       {
		       nMinF = 0;
		       nMaxF = 0;
	       }
	       
	       for (i = 0; i < rois.size(); i++) 
	       {
	    	   roi = rois.get(i);
	    	   nShift = roi.getTimePoint() - bt.btData.nCurrTimepoint;
	    	   if(nShift >= nMinF && nShift <= nMaxF)
	    	   {

		    	   //save colors in case ROI is active
		    	   if(i == activeRoi.intValue())
		    	   {
		    		   savePointColor = roi.getPointColor();
		    		   saveLineColor = roi.getLineColor();
		    		   roi.setPointColorRGB(activePointColor);
		    		   roi.setLineColorRGB(activeLineColor);
		    	   }
		    	   nShift = Math.abs(nShift);
	    		   if(nShift > 0)
	    		   {
	    			   fOpacityScale = 1.0f - (float)nShift / (float)(Math.abs(bt.btData.timeFade) + 1);
	    			   fOpacitySave = roi.getOpacity();
	    			   roi.setOpacity(roi.getOpacity() * fOpacityScale);
	    		   }
		    	   if(bShowAll)
		    	   {
		    		   if(groups.get(roi.getGroupInd()).bVisible)
		    		   {
		    			   roi.draw(gl, pvm, vm, screen_size);
		    		   }
		    		   else
		    		   {
		    			   //still draw active ROI
			    		   if(i == activeRoi.intValue())
			    		   {
			    			   roi.draw(gl, pvm, vm, screen_size);
			    		   }	    			   
		    		   }
		    	   }
		    	   else
		    	   {
		    		   if(i == activeRoi.intValue())
		    		   {
		    			   roi.draw(gl, pvm, vm, screen_size);
		    		   }
		    	   }
		    	  
		    	   //restore colors in case ROI is active
		    	   if(i == activeRoi.intValue())
		    	   {
		    		   roi.setPointColor(savePointColor);
		    		   roi.setLineColor(saveLineColor);
		    	   }
		    	   //restore opacity
	    		   if(nShift > 0)
	    		   {
	    			   roi.setOpacity(fOpacitySave);
	    		   }
	    	   }//show only current time point
	       }
	 }
	 
	 /**adds new point to Point3D, Polyline3D or Plane3D ROI **/
	 public synchronized void addPoint(RealPoint point_)
	 {
		 
		 switch (mode){
		 
		 case RoiManager3D.ADD_POINT:
			 addPoint3D(point_);
			 break;
		 case RoiManager3D.ADD_POINT_LINE:
			 addPointToLine(point_);
			 break;
		 case RoiManager3D.ADD_POINT_PLANE:
			 addPointToPlane(point_);
			 break;

		 }
		 bt.repaintBVV();
		 
	 }
	 
	 public synchronized void addPoint3D(RealPoint point_)
	 {
		 Point3D pointROI = (Point3D)makeRoi( Roi3D.POINT, bt.btData.nCurrTimepoint); 
		 pointROI.setVertex(point_);
		 addRoi(pointROI);
	 }
	 
	 public synchronized void addSegment(RealPoint point_, ArrayList<RealPoint> segments_)
	 {
		 LineTrace3D tracing;
		 //new Line
		 if(jlist.getSelectedIndex()<0 || getActiveRoi().getType() != Roi3D.LINE_TRACE)
		 {
			 tracing = (LineTrace3D) makeRoi(Roi3D.LINE_TRACE, bt.btData.nCurrTimepoint);
			 if(bt.btData.bEstimateROIThicknessFromParams)
			 {
				 tracing.setLineThickness( bt.btData.estimateROIThicknessFromTracing() );
			 }
			 tracing.addFirstPoint(point_);
			 addRoi(tracing);
			 //activeRoi = rois.size()-1; 
			 return;
		 }
		 
		 //add point to line
		 tracing = (LineTrace3D) getActiveRoi();
		 tracing.addPointAndSegment(point_,segments_);
		 bt.repaintBVV();
	 }
	 public RealPoint getLastTracePoint()
	 { 
		 LineTrace3D tracing;
		 tracing = (LineTrace3D) getActiveRoi();
		 return tracing.vertices.get(tracing.vertices.size()-1);
	 }

	 
	 public synchronized boolean removeSegment()
	 {
		 LineTrace3D tracing;
		 tracing = (LineTrace3D) getActiveRoi();
		 boolean bRemove =tracing.removeLastSegment();
		 bt.repaintBVV();
		 return bRemove;
	 }

	 /** adds point to active Polyline3D ROI
	  *  if active ROI is not a polyline, does nothing
	  *  if there are no active ROIS, starts new polyline **/
	 public synchronized void addPointToLine(RealPoint point_)
	 {

		 PolyLine3D polyline;
		 
		 //new Line
		 if(jlist.getSelectedIndex()<0 || getActiveRoi().getType()!=Roi3D.POLYLINE)
		 {
			 polyline  = (PolyLine3D) makeRoi(Roi3D.POLYLINE, bt.btData.nCurrTimepoint);
			 polyline.addPointToEnd(point_);
			 addRoi(polyline);
			 //activeRoi = rois.size()-1; 
			 return;
		 }

		 //add point to line
		 polyline = (PolyLine3D) getActiveRoi();
		 polyline.addPointToEnd(point_);			
	 
	 }
	 
	 /** adds point to active plane3D ROI
	  *  if active ROI is not a plane, does nothing
	  *  if there are no active ROIS, starts new polyline **/
	 public synchronized void addPointToPlane(RealPoint point_)
	 {

		 CrossSection3D plane;
		 
		 //new Plane
		 if(jlist.getSelectedIndex()<0 || getActiveRoi().getType()!=Roi3D.PLANE)
		 {	
			 plane  = (CrossSection3D) makeRoi(Roi3D.PLANE, bt.btData.nCurrTimepoint);
			 plane.addPoint(point_);
			 addRoi(plane);
			 //activeRoi = rois.size()-1; 
			 return;
		 }

		 //add point to plane
		 plane = (CrossSection3D) getActiveRoi();
		 plane.addPoint(point_);			
	 
	 }
	 
	 /** removes point from the active polyline3D/plane3D ROIS
	  *  if active ROI is not that type, does nothing
	  *  if it is a last point, removes ROI object
	  *  and activates previous Roi in the list (if any of the same type) 
	  * **/
	 public synchronized void removePointLinePlane()
	 {
		 boolean bPointRemoved =false;
		 
		 if(jlist.getSelectedIndex()<0)
			 return;
		 final int nRoiType = getActiveRoi().getType();
		 
		 //active ROI is not a line or none ROI selected
		 //if(nRoiType==Roi3D.POLYLINE || nRoiType==Roi3D.PLANE)
		 {
			 switch (nRoiType)
			 {
			 case Roi3D.POLYLINE:
				 bPointRemoved = ((PolyLine3D) getActiveRoi()).removeEndPoint();
				 break;
			 case Roi3D.PLANE:
				 bPointRemoved = ((CrossSection3D) getActiveRoi()).removePoint();
				 break;
			 case Roi3D.LINE_TRACE:
				 bPointRemoved = ((LineTrace3D) getActiveRoi()).removeLastSegment();
				 break;
			 }
			
			 if(!bPointRemoved)
			 {
				 removeRoi(activeRoi.intValue());
				 if(activeRoi.intValue()>=0)
				 {
					 if(getActiveRoi().getType()!=nRoiType)
					 {
						 activeRoi.set(-1);
						 jlist.clearSelection();
					 }
					 else
					 {
						 jlist.setSelectedIndex(activeRoi.intValue());
					 }
				 }
			 }
			 bt.repaintBVV();
		 }
	 }

	 public synchronized void unselect()
	 {
		 activeRoi.set(-1);
		 jlist.clearSelection();
		 roiMeasure.jlist.clearSelection();
		 btTracksPanel.jlist.clearSelection();
		 fireActiveRoiChanged(activeRoi.intValue());

	 }
	 
	 public synchronized void deleteActiveROI()
	 {
		 if(activeRoi.intValue()<0)
			 return;
		 removeRoi(activeRoi.intValue());
		 if(activeRoi.intValue()>=0)
		 {
			 jlist.setSelectedIndex(activeRoi.intValue());
		 }
		 else
		 {
			 jlist.clearSelection();
		 }
		 fireActiveRoiChanged(activeRoi.intValue()); 
	 }
	 
	 public void dialRenameActiveROI()
	 {
			String s = (String)JOptionPane.showInputDialog(
					this,
					"New name:",
					"Rename ROI",
					JOptionPane.PLAIN_MESSAGE,
					null,
					null,
					getActiveRoi().getName());

			//If a string was returned, rename
			if ((s != null) && (s.length() > 0)) 
			{
				final Roi3D currROI = getActiveRoi();
				currROI.setName(s);
				listModel.set(activeRoi.intValue(),getFullDisplayedRoiName(currROI));
				return;
			}
	 }
	 
	 public void addRoiManager3DListener(Listener l) 
	 {
		 listeners.add(l);
	 }
	 
	 private void fireActiveRoiChanged(int nRoi) 
	 {
		bt.repaintBVV();
		for(Listener l : listeners)
			l.activeRoiChanged(nRoi);
	}

	@Override
	public void valueChanged(ListSelectionEvent e) 
	{
		
		if (e.getValueIsAdjusting() == false) 
		{
			bt.repaintBVV();
			roiMeasure.jlist.setSelectedIndex(jlist.getSelectedIndex());
			btTracksPanel.jlist.setSelectedIndex( jlist.getSelectedIndex() );
			//No selection:
			if (jlist.getSelectedIndex() == -1) 
            {
            	activeRoi.set(-1);
            //Multiple selection:disabled right now		
            } else if (jlist.getSelectedIndices().length > 1) {
            
            //Single selection
            } else {

            	activeRoi.set(jlist.getSelectedIndex());
            	//update the timepoint
            	if(getActiveRoi().getTimePoint()!=bt.viewer.state().getCurrentTimepoint())
            	{
            		bt.btData.bDeselectROITime = false;
            		bt.viewer.setTimepoint(getActiveRoi().getTimePoint());
            	}
            	//jlist.setSelectedIndex(activeRoi);
            	//update the timepoint
            	fireActiveRoiChanged(jlist.getSelectedIndex()); 
            }
        }
    }
	
	/** sets active channel for tracing and measurements (0 based) **/
	public void setActiveChannel(int nChannel)
	{
		bt.btData.nChAnalysis = nChannel;
		
		if(this.cbActiveChannel.getSelectedIndex() != nChannel)
		{
			this.cbActiveChannel.setSelectedIndex(nChannel);
		}
		
		if(roiMeasure.cbActiveChannel.getSelectedIndex() != nChannel)
		{
			roiMeasure.cbActiveChannel.setSelectedIndex(nChannel);
		}
	}

	//buttons
	@Override
	public void actionPerformed(ActionEvent e) 
	{
		//TRACING TYPE
		if(e.getSource() == roiPointMode)
		{
			if(RoiManager3D.mode != RoiManager3D.ADD_POINT)
			{
				RoiManager3D.mode = RoiManager3D.ADD_POINT;
				Prefs.set("BigTrace.RoiManagerMode", RoiManager3D.mode);
				unselect();
			}
		}
		if(e.getSource() == roiPolyLineMode)
		{
			if(RoiManager3D.mode != RoiManager3D.ADD_POINT_LINE)
			{
				RoiManager3D.mode = RoiManager3D.ADD_POINT_LINE;
				Prefs.set("BigTrace.RoiManagerMode", RoiManager3D.mode);
				unselect();
			}
		}
		if(e.getSource() == roiPolySemiAMode)
		{
			if(RoiManager3D.mode != RoiManager3D.ADD_POINT_SEMIAUTOLINE)
			{
				RoiManager3D.mode = RoiManager3D.ADD_POINT_SEMIAUTOLINE;
				Prefs.set("BigTrace.RoiManagerMode", RoiManager3D.mode);
				unselect();
			}
		}
		if(e.getSource() == roiPolyOneClickMode)
		{
			if(!bt.bInputLock)
			{
				if(RoiManager3D.mode != RoiManager3D.ADD_POINT_ONECLICKLINE)
				{
					RoiManager3D.mode = RoiManager3D.ADD_POINT_ONECLICKLINE;
					Prefs.set("BigTrace.RoiManagerMode", RoiManager3D.mode);
					unselect();
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
			if(RoiManager3D.mode != RoiManager3D.ADD_POINT_PLANE)
			{
				RoiManager3D.mode = RoiManager3D.ADD_POINT_PLANE;
				Prefs.set("BigTrace.RoiManagerMode", RoiManager3D.mode);
				unselect();
			}
		}
		//SETTINGS
		if(e.getSource() == roiSettings)
		{
			rmDiag.dialRenderSettings();
		}
		//ACTIVE CHANNEL
		if(e.getSource() == cbActiveChannel)
		{
			setActiveChannel(cbActiveChannel.getSelectedIndex());
		}
		
		//ACTIVE PRESET
		if(e.getSource() == cbActiveGroup)
		{
			//Dimension size= cbActiveGroup.getSize();
			if(nActiveGroup!=cbActiveGroup.getSelectedIndex())
			{
				nActiveGroup=cbActiveGroup.getSelectedIndex();
				//unselect();
				
			}
		}	

		//SHOW ALL BUTTON
		if(e.getSource() == butShowAll)
		{
			this.bShowAll=butShowAll.isSelected();
			fireActiveRoiChanged(activeRoi.intValue()); 
		}
		
		//SAVE ROIS
		if(rois.size()>0)
		{
			
			if(e.getSource() == butSaveROIs)
			{
				rmDiag.diagSaveROIs();
			}
		}
		//LOAD ROIS
		if(e.getSource() == butLoadROIs)
		{
			rmDiag.diagLoadROIs();
		}
		
		//IMPORT ROIS
		if(e.getSource() == roiImport)
		{
			rmDiag.diagImportROIs();
		}
		
		//Groups Manager
		if(e.getSource() == butROIGroups)
		{
			dialShowGroups();
			
		}
		//GROUP VISIBILITY
		if(e.getSource() == butDisplayGroup)
		{
			rmDiag.dialGroupVisibility();
		}
		
		///SIDE ROI SPECIFIC LIST BUTTONS
		if(activeRoi.intValue()>=0)
		{
			//DELETE
			if(e.getSource() == butDelete)
			{
				deleteActiveROI();
			}
			//RENAME
			if(e.getSource() == butRename)
			{
				dialRenameActiveROI();

			}
			//DESELECT
			if(e.getSource() == butDeselect)
			{
				unselect();
				 
			}
			//PROPERTIES
			if(e.getSource() == butProperties)
			{
				dialProperties();
			}
			//APPLY GROUP
			
			if(e.getSource() == butApplyGroup)
			{
				applyGroupToROI(activeRoi.intValue(), nActiveGroup);
			}	
			

		}

		
	}
	
	/** show ROI Properties dialog**/
	public void dialProperties()
	{
		JPanel dialProperties = new JPanel(new GridBagLayout());
		GridBagConstraints cd = new GridBagConstraints();
		NumberField nfTimePoint = new NumberField(4);
		NumberField nfPointSize = new NumberField(4);
		NumberField nfLineThickness = new NumberField(4);
		NumberField nfOpacity = new NumberField(4);
		
		Roi3D currentROI = getActiveRoi();

		String[] sRenderType = { "Outline", "Wire", "Surface" };
		JComboBox<String> renderTypeList = new JComboBox<>(sRenderType);
		nfTimePoint.setIntegersOnly(true);
		nfTimePoint.setText(Integer.toString(currentROI.getTimePoint()));
		nfPointSize.setText(Float.toString(currentROI.getPointSize()));
		nfLineThickness.setText(Float.toString(currentROI.getLineThickness()));
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.00", decimalFormatSymbols);
		nfOpacity.setText(df.format(currentROI.getOpacity()));
		
		nfOpacity.setLimits(0.0, 1.0);
		

		JButton butPointColor = new JButton( new ColorIcon( currentROI.getPointColor() ) );
		
		butPointColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose point color", currentROI.getPointColor() );
			if (newColor != null)
			{
				selectColors.setColor(newColor, 0);
				butPointColor.setIcon(new ColorIcon(newColor));
			}
			
		});
		
		JButton butLineColor  = new JButton( new ColorIcon( currentROI.getLineColor()) );

		
		butLineColor.addActionListener( e -> {
				Color newColor = JColorChooser.showDialog(bt.btPanel.finFrame, "Choose line color", currentROI.getPointColor() );
				if (newColor!=null)
				{	
					selectColors.setColor(newColor, 1);							
					butLineColor.setIcon(new ColorIcon(newColor));
				}
				
		});
		
		cd.gridx=0;
		cd.gridy=0;
		dialProperties.add(new JLabel("ROI Type: "),cd);
		cd.gridx++;
		dialProperties.add(new JLabel(Roi3D.intTypeToString(currentROI.getType())),cd);

		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Group: "),cd);
		cd.gridx++;
		//just in case
		if(currentROI.getGroupInd()> groups.size()-1)
		{
			dialProperties.add(new JLabel(RoiManager3D.sUndefinedGroupName),cd);
		}
		else
		{
			dialProperties.add(new JLabel(groups.get(currentROI.getGroupInd()).getName()),cd);	
		}

		
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Time point: "),cd);
		cd.gridx++;
		dialProperties.add(nfTimePoint,cd);

		
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Point size: "),cd);
		cd.gridx++;
		dialProperties.add(nfPointSize,cd);
		
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Point color: "),cd);
		cd.gridx++;
		dialProperties.add(butPointColor,cd);
		
		if(currentROI.getType()>Roi3D.POINT)
		{
			cd.gridx=0;
			cd.gridy++;
			dialProperties.add(new JLabel("Line thickness: "),cd);
			cd.gridx++;
			dialProperties.add(nfLineThickness,cd);
			cd.gridx=0;
			cd.gridy++;
			dialProperties.add(new JLabel("Line color: "),cd);
			cd.gridx++;
			dialProperties.add(butLineColor,cd);
		}
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Opacity: "),cd);
		cd.gridx++;
		dialProperties.add(nfOpacity,cd);
		

		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Render as: "),cd);
		renderTypeList.setSelectedIndex(currentROI.getRenderType());
		cd.gridx++;
		dialProperties.add(renderTypeList,cd);

		
		
		int reply = JOptionPane.showConfirmDialog(null, dialProperties, "ROI Properties", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		
		
		if (reply == JOptionPane.OK_OPTION) 
		{
			
			//time point
			currentROI.setTimePoint(Math.min(Math.max(0, Integer.parseInt(nfTimePoint.getText())), bt.btData.nNumTimepoints - 1));
			//point size 
			currentROI.setPointSize(Float.parseFloat(nfPointSize.getText()));
			
			//point color
			if(selectColors.getColor(0)!=null)
			{
				currentROI.setPointColorRGB(selectColors.getColor(0));
				selectColors.setColor(null, 0);
				//newPointColor = null;
			}
			//opacity
			float fNewOpacity= Float.parseFloat(nfOpacity.getText());
			if(fNewOpacity<0.0f)
				{fNewOpacity=0.0f;}
			if(fNewOpacity>1.0f)
				{fNewOpacity=1.0f;}
			currentROI.setOpacity(fNewOpacity);
			
			//render type
			if(renderTypeList.getSelectedIndex()!=currentROI.getRenderType())
			{
				currentROI.setRenderType(renderTypeList.getSelectedIndex());
			}
			//line
			if(currentROI.getType()>Roi3D.POINT)
			{
				//line thickness
				float fNewLineThickess = Float.parseFloat(nfLineThickness.getText());
				if(Math.abs(fNewLineThickess-currentROI.getLineThickness())>0.00001)
				{
					currentROI.setLineThickness(fNewLineThickess );
				}
				//line color
				if(selectColors.getColor(1)!=null)
				{				
					currentROI.setLineColorRGB(selectColors.getColor(1));
					selectColors.setColor(null, 1);
				
				}

			}
			
			fireActiveRoiChanged(activeRoi.intValue()); 
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
	

	/** updates ROIs image for a specific group **/
	void updateROIsGroupDisplay(int nGroupN)
	{
		Roi3DGroup updateGroup =groups.get(nGroupN);
		for (Roi3D roi : rois)
		{
			if(roi.getGroupInd()==nGroupN)
			{
				roi.setGroup(updateGroup);
			}
		}
	}
	
	/** updates all ROIs images**/
	public void updateROIsDisplay()
	{
		for (Roi3D roi : rois)
		{
			roi.updateRenderVertices();
		}
		
		bt.repaintBVV();
	}
	
	/** function used to select ROI on canvas **/
	public void selectClosestToLineRoi(Line3D clickLine)
	{
		double dDistMin = Double.MAX_VALUE; 
		int dInd = -1;
		double dCurrDist = 0.0;
	    
		final int nMinF = Math.min(0,bt.btData.timeFade);
	    final int nMaxF = Math.max(0,bt.btData.timeFade);
		int nShift;
		
		for (int i=0;i<rois.size();i++)
		{
			//if ROI is visible at the current time frame
			nShift =  rois.get(i).getTimePoint() - bt.btData.nCurrTimepoint;
			if(nShift >= nMinF && nShift <= nMaxF)
			{
				dCurrDist= rois.get(i).getMinDist(clickLine);
				if(dCurrDist<dDistMin)
				{
					dDistMin = dCurrDist;
					dInd=i;
				}
			}

		}
		if(Math.abs(dDistMin-Double.MAX_VALUE) > 0.1)
		{
			jlist.setSelectedIndex(dInd);
			fireActiveRoiChanged(jlist.getSelectedIndex()); 
		}

	}
	
	/** moves ROIs from nGroupFrom index to group with nGroupTo index **/
	public void moveROIsGroups(final int nGroupFrom, final int nGroupTo)
	{
		for (int i=0;i<rois.size();i++)
		{
			if(rois.get(i).getGroupInd() == nGroupFrom)
			{
				rois.get(i).setGroupInd(nGroupTo);
				listModel.setElementAt(getFullDisplayedRoiName(rois.get(i)), i);
			}
		}
	}
	
	void updateGroupSelector()
	{
		int nGroupSave = nActiveGroup;
		cbActiveGroup.removeAllItems();
		for(int i=0;i<groups.size();i++)
		{
			//cbActiveGroup.addItem(groups.get(i).getName());
			cbActiveGroup.insertItemAt(groups.get(i).getName(),i);
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
	
	public void addGroup(Roi3DGroup group_in)
	{
		groups.add( group_in );		
		updateGroupSelector();	
	}
	
	/** apply group to roi **/	
	public void applyGroupToROI(int nRoiIndex, int nGroupIndex)
	{

		if(nRoiIndex<0 && nRoiIndex>rois.size()-1)
			return;
		if(nGroupIndex<0 && nGroupIndex>groups.size()-1)
			return;
		final Roi3D currROI = rois.get( nRoiIndex );
		currROI.setGroup(groups.get(nGroupIndex));
		currROI.setGroupInd(nGroupIndex);
		listModel.setElementAt(getFullDisplayedRoiName(currROI), nRoiIndex);
	}
	
	public void dialShowGroups()
	{
		Roi3DGroupManager<T> dialGroup = new Roi3DGroupManager<>(this);
		dialGroup.initGUI();
		dialGroup.show();
		updateGroupSelector();
	}
	
	public void applyGroupToROI(Roi3D roiIn, int nGroupIndex)
	{
		for (int i =0;i<rois.size();i++)
		{
			if(roiIn.equals( rois.get( i ) ))
			{
				applyGroupToROI(i,nGroupIndex);
				return;
			}
		}
	}

	public void applyGroupToROI(Roi3D roiIn, Roi3DGroup rGroup)
	{
		for (int i = 0;i<groups.size();i++)
		{
			if(rGroup.equals( groups.get( i ) ))
			{
				applyGroupToROI(roiIn, i);
				return;
			}
		}
	}

	
	/** deletes ROIs of specific group **/
	public void deleteROIsBelongingToGroup(int nGroupN)
	{
		for (int i=(rois.size()-1);i>=0;i--)
		{
			if(rois.get(i).getGroupInd()==nGroupN)
			{
				 rois.remove(i);
				 listModel.removeElementAt(i);
			}
		}
		jlist.clearSelection();
	}
	
	
	public void updateGroupsList()
	{
		 
		 cbActiveGroup.removeAllItems();
		 for(int i=0;i<groups.size();i++)
		 {
			 cbActiveGroup.addItem(groups.get(i).getName());
		 }
		 cbActiveGroup.setSelectedIndex(0);
		 nActiveGroup = 0;

	}
	public void setRoiMeasure3D( RoiMeasure3D<T> roiMeasure_)
	{
		 this.roiMeasure = roiMeasure_;
	}
	
	public void setTracksPanel( TrackingPanel<T> btTracksPanel_)
	{
		 this.btTracksPanel = btTracksPanel_;
	}

	public void repaintBVV()
	{
		bt.repaintBVV();
	}
}
