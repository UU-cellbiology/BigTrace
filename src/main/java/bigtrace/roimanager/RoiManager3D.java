package bigtrace.roimanager;

import java.awt.Color;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.joml.Matrix4fc;
import com.jogamp.opengl.GL3;

import ij.Prefs;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import bigtrace.BigTrace;
import bigtrace.geometry.Line3D;
import bigtrace.gui.ColorUserSettings;
import bigtrace.gui.TaskBT;
import bigtrace.gui.tracing.PanelFullAutoTrace;
import bigtrace.measure.RoiMeasure3D;
import bigtrace.rois.Box3D;
import bigtrace.rois.CrossSection3D;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Point3D;
import bigtrace.rois.PolyLine3D;
import bigtrace.rois.Roi3D;
import bigtrace.tracks.TrackingPanel;


public class RoiManager3D < T extends RealType< T > & NativeType< T > > 
{
	final BigTrace <T> bt;

	public final ArrayList<Roi3D> rois = new ArrayList<>();
	
	public AtomicInteger activeRoi = new AtomicInteger (-1);// = -1;
	
	public final Object roiLock = new Object();   // not 'this', so the EDT never blocks on the monitor
	
	public boolean bSyncingList = false;           // EDT-only flag

	public ArrayList<Roi3DGroup> groups = new ArrayList<>();

	public final static String sUndefinedGroupName = "*undefined*";

	public int nActiveGroup = 0;

	public Color activePointColor = new Color((int)Prefs.get( "BigTrace.activePointColor", Color.YELLOW.getRGB() ));
	public Color activeLineColor = new Color((int)Prefs.get( "BigTrace.activeLineColor", Color.RED.getRGB() ));

	public ColorUserSettings selectColors = new ColorUserSettings(); 
		
	//MAIN PANEL
	final public RoiManager3DPanel<T> rmPanel;
	
	//ROI list
	public final JList<String> jlist;
	
	//MEASURE OBJECT/PANEL
	public RoiMeasure3D<T> roiMeasure = null;
	
	//TRACKS PANEL
	public TrackingPanel<T> btTracksPanel = null;
	
	public final PanelFullAutoTrace< T > panelFullAutoTrace;
	
	//dialogs
	public final RoiManager3DDialogs rmDialogs;
	
	
	public static interface Listener {
		public void activeRoiChanged(int nRoi);				
	}

	public RoiManager3D(final BigTrace<T> bt)
	{

		this.bt = bt;
		rmDialogs = new RoiManager3DDialogs(bt);
		panelFullAutoTrace = new PanelFullAutoTrace<>(bt);

		//initialize new *undefined* ROI group
		groups.add(new Roi3DGroup(sUndefinedGroupName, bt.btData.undefPointSize, new Color(bt.btData.undefPointColor), bt.btData.undefLineThickness, new Color(bt.btData.undefLineColor), bt.btData.undefRenderType) );
		nActiveGroup = 0;
		
		rmPanel = new RoiManager3DPanel<>(this.bt, this);
		jlist = rmPanel.jlist;

	}
	
	 /** Posts a ListModel/JList update to the EDT.
	  *  MUST be called while holding roiLock: this keeps the EDT queue
	  *  in the same order as the changes to rois. Always uses invokeLater
	  *  (even on the EDT), so updates queued earlier by a worker run first. **/
	 private void postListUpdate(final Runnable r)
	 {
		 if(bt.btMacro.bMacroMode)
			 return; // the list is rebuilt once at the end of the macro command
		 SwingUtilities.invokeLater( () ->
		 {
			 bSyncingList = true;
			 try { r.run(); }
			 finally { bSyncingList = false; }
		 });
	 }
	 
	 /** EDT-only: selects nIndex in all three lists, or clears the selection **/
	 private void syncSelectionOnEDT(final int nIndex)
	 {
		 if(nIndex >= 0 && nIndex < rmPanel.listModel.getSize())
		 {
			 jlist.setSelectedIndex( nIndex );
			 jlist.ensureIndexIsVisible( nIndex );
			 if(roiMeasure != null) roiMeasure.jlist.setSelectedIndex( nIndex );
			 if(btTracksPanel != null) btTracksPanel.jlist.setSelectedIndex( nIndex );
		 }
		 else
		 {
			 jlist.clearSelection();
			 if(roiMeasure != null) roiMeasure.jlist.clearSelection();
			 if(btTracksPanel != null) btTracksPanel.jlist.clearSelection();
		 }
	 }
	 
	 /** thread-safe copy of the ROI list, for drawing/iterating from other threads **/
	 public ArrayList<Roi3D> getRoisSnapshot()
	 {
		 synchronized(roiLock) { return new ArrayList<>(rois); }
	 }
	 
	 public boolean containsRoi(final Roi3D roi)
	 {
		 synchronized(roiLock) { return rois.contains( roi ); }
	 }
	
	 /** adds provided ROI to the end of the list **/
	 public int addRoi(final Roi3D newRoi)
	 {		
		 synchronized(roiLock)
		 {
			 rois.add(newRoi);
			 final int newIndex = rois.size() - 1;
			 activeRoi.set( newIndex );
			 final String sName = getFullDisplayedRoiName(newRoi); // captured now, not on the EDT later
			 postListUpdate( () ->
			 {
				 rmPanel.listModel.addElement( sName );
				 syncSelectionOnEDT( newIndex );
			 });
			 return newIndex;
		 }
	 }

	 
	 public void updateRoiListModel()
	 {
		 final ArrayList<String> names = new ArrayList<>();
		 final int nActive;
		 synchronized(roiLock)
		 {
			 for (final Roi3D roi : rois)
				 names.add( getFullDisplayedRoiName(roi) );
			 nActive = activeRoi.get();
		 }
		 TaskBT.runOnEDT( () ->
		 {
			 bSyncingList = true;
			 try
			 {
				 rmPanel.listModel.clear();
				 for(final String s : names)
					 rmPanel.listModel.addElement( s );
				 syncSelectionOnEDT( nActive );
			 }
			 finally { bSyncingList = false; }
		 });
	 }

	 /** inserts ROI to the ROI list with the provided index **/
	 public int insertRoi(final Roi3D newRoi, final int nInsertN)
	 {		
		 synchronized(roiLock)
		 {
			 if(nInsertN < 0 || nInsertN > (rois.size()-1))
			 {
				 System.err.println("ERROR! Index of ROI insertion is out of the roi list range.");
				 System.err.println("Appending ROI to the end of the list.");
				 return addRoi(newRoi);   // reentrant lock, fine
			 }
			 rois.add(nInsertN, newRoi);
			 activeRoi.set(nInsertN);
			 final String sName = getFullDisplayedRoiName(newRoi);
			 postListUpdate( () ->
			 {
				 rmPanel.listModel.insertElementAt( sName, nInsertN );
				 syncSelectionOnEDT( nInsertN );
			 });
			 return nInsertN;
		 }

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
			 return "T" + String.format("%0" + sTimeFormat + "d", 
					 roi.getTimePoint()) + "_" + bt.roiManager.getGroupPrefixRoiNameBase(roi);
		 }
		 
		 //single time point, skip time
		 return bt.roiManager.getGroupPrefixRoiNameBase(roi);
	 }

	 public Roi3D getActiveRoi()
	 {
		 synchronized (roiLock) 
		 {
			 final int i = activeRoi.get();                          // read once
			 return (i >= 0 && i < rois.size()) ? rois.get(i) : null;
		 }
	 }
	 
	 /** removes ROI and updates ListModel
	  * does update activeRoi index! **/
	 public void removeRoi(int roiIndex)
	 {
		 synchronized(roiLock)
		 {
			 if(roiIndex < 0 || roiIndex >= rois.size())
				 return;
			 rois.remove(roiIndex);
			 final int nOld = activeRoi.get();
			 final int nNew;
			 if(roiIndex < nOld)       nNew = nOld - 1;
			 else if(roiIndex == nOld) nNew = roiIndex - 1;   // previous one, -1 if none
			 else                      nNew = nOld;
			 activeRoi.set( nNew );
			 postListUpdate( () ->
			 {
				 rmPanel.listModel.remove( roiIndex );
				 syncSelectionOnEDT( nNew );
			 });
		 }
	 }
	 
	 /** removes the given ROI (by reference); returns false if it is not in the list **/
	 public boolean removeRoi(final Roi3D roi)
	 {
		 synchronized(roiLock)
		 {
			 final int nInd = rois.indexOf( roi );
			 if(nInd < 0)
				 return false;
			 removeRoi( nInd );
			 return true;
		 }
	 }
	 
	 /** removes all ROIs (use this instead of rois.clear() + listModel.clear()) **/
	 public void clearRois()
	 {
		 synchronized(roiLock)
		 {
			 rois.clear();
			 activeRoi.set(-1);
			 postListUpdate( () ->
			 {
				 rmPanel.listModel.clear();
				 syncSelectionOnEDT( -1 );
			 });
		 }
	 } 
	 
	 /** replaces all ROIs at once (bulk import), keeping the same listModel instance **/
	 public void replaceAllRois(final List<Roi3D> newRois)
	 {
		 synchronized(roiLock)
		 {
			 rois.clear();
			 rois.addAll( newRois );
			 activeRoi.set(-1);
		 }
		 updateRoiListModel();
	 }
	 
	 
	 /** Draw all ROIS **/
	 public void draw(final GL3 gl, final Matrix4fc pvm,  Matrix4fc vm, final int[] screen_size, final boolean bWeightedOIT)
	 {
		 
		   final ArrayList<Roi3D> roisSnap;
	       final int nActive;
	       synchronized(roiLock)
	       {
	    	   roisSnap = new ArrayList<>(rois);
	    	   nActive = activeRoi.get();
	       }
	       Roi3D roi;
	       Color savePointColor = null;
	       Color saveLineColor = null;
	       int i;
	       int nShift;
	       float fOpacityScale = 1.0f;
	       float fOpacitySave = 1.0f;
	       int nMinF = Math.min(0, bt.btData.timeFade);
	       int nMaxF = Math.max(0, bt.btData.timeFade);
	       if(bt.btData.timeRender == 0)
	       {
		       nMinF = 0;
		       nMaxF = 0;
	       }
	       
	       for (i = 0; i < roisSnap.size(); i++) 
	       {
	    	   roi = roisSnap.get(i);
	    	   
	    	   nShift = roi.getTimePoint() - bt.btData.nCurrTimepoint;
	    	   
	    	   if(nShift >= nMinF && nShift <= nMaxF)
	    	   {
		    	   //see in which render run (opaque or transparent) ROI belongs
	    		   roi.defineTransparency();
		    	   if (bWeightedOIT && !roi.isTransparent())
		    		   continue;

		    	   if (!bWeightedOIT && roi.isTransparent())
		    		   continue;
		    	   
		    	   //save colors in case ROI is active
		    	   if(i == nActive)
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
		    	   if(rmPanel.bShowAll)
		    	   {
		    		   if(groups.get(roi.getGroupInd()).bVisible)
		    		   {
		    			   roi.draw(gl, pvm, vm, screen_size, bWeightedOIT);
		    		   }
		    		   else
		    		   {
		    			   //still draw active ROI
			    		   if(i == nActive)
			    		   {
			    			   roi.draw(gl, pvm, vm, screen_size, bWeightedOIT);
			    		   }	    			   
		    		   }
		    	   }
		    	   else
		    	   {
		    		   if(i == nActive)
		    		   {
		    			   roi.draw(gl, pvm, vm, screen_size, bWeightedOIT);
		    		   }
		    	   }
		    	  
		    	   //restore colors in case ROI is active
		    	   if(i == nActive)
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
		 if(activeRoi.intValue() < 0 || getActiveRoi().getType() != Roi3D.LINE_TRACE)
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
		 tracing.addPointAndSegment(point_, segments_);
		 bt.repaintBVV();
	 }

	 
	 public RealPoint getLastTracePoint()
	 { 
		 LineTrace3D tracing;
		 tracing = (LineTrace3D) getActiveRoi();
		 return tracing.vertices.get(tracing.vertices.size() - 1);
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
		 if(jlist.getSelectedIndex() < 0 || getActiveRoi().getType() != Roi3D.POLYLINE)
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
		 if(jlist.getSelectedIndex() < 0 || getActiveRoi().getType() != Roi3D.PLANE)
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
	 public void removePointLinePlane()
	 {
		 
		 final Roi3D activeR = getActiveRoi();
		 if(activeR == null)
			 return;
		 
		 final int nRoiType = activeR.getType();
		 boolean bPointRemoved = false;	 

		 switch (nRoiType)
		 {
		 case Roi3D.POLYLINE:
			 bPointRemoved = ((PolyLine3D) activeR).removeEndPoint();
			 break;
		 case Roi3D.PLANE:
			 bPointRemoved = ((CrossSection3D) activeR).removePoint();
			 break;
		 case Roi3D.LINE_TRACE:
			 bPointRemoved = ((LineTrace3D) activeR).removeLastSegment();
			 break;
		 }

		 if(!bPointRemoved)
		 {
			 synchronized(roiLock)
			 {
				 removeRoi( activeR );
				 final Roi3D prev = getActiveRoi();
				 // only keep the previous ROI active if it is the same type
				 if(prev != null && prev.getType() != nRoiType)
				 {
					 activeRoi.set(-1);
					 postListUpdate( () -> syncSelectionOnEDT( -1 ) );
				 }
			 }
		 }
		 bt.repaintBVV();
		 
	 }

	 public void unselect()
	 {
		 synchronized(roiLock)
		 {
			 activeRoi.set(-1);
			 postListUpdate( () -> syncSelectionOnEDT( -1 ) );
		 }
		 bt.repaintBVV();
	 }

	 public void deleteActiveROI()
	 {
		 synchronized(roiLock)
		 {
			 final int nInd = activeRoi.get();
			 if(nInd < 0)
				 return;
			 removeRoi( nInd );   // also posts the selection update
		 }
		 bt.repaintBVV();	 
	 }
	 
	 public void dialRenameActiveROI()
	 {
		 final Roi3D currROI = getActiveRoi();
		 if(currROI == null)
			 return;
			String s = (String)JOptionPane.showInputDialog(
					this.rmPanel,
					"New name:",
					"Rename ROI",
					JOptionPane.PLAIN_MESSAGE,
					null,
					null,
					currROI.getName());

			//If a string was returned, rename
			 if ((s != null) && (s.length() > 0))
			 {
				 currROI.setName(s);
				 synchronized(roiLock)
				 {
					 final int nInd = rois.indexOf( currROI );
					 if(nInd >= 0)
					 {
						 final String sName = getFullDisplayedRoiName( currROI );
						 postListUpdate( () -> rmPanel.listModel.set( nInd, sName ) );
					 }
				 }
			 }
	 }
	
	
	/** sets active channel for tracing and measurements (0 based) **/
	public void setActiveChannel(int nChannel)
	{
		bt.btData.nChAnalysis = nChannel;
		
		if(rmPanel.cbActiveChannel.getSelectedIndex() != nChannel)
		{
			rmPanel.cbActiveChannel.setSelectedIndex(nChannel);
		}
		
		if(roiMeasure.cbActiveChannel.getSelectedIndex() != nChannel)
		{
			roiMeasure.cbActiveChannel.setSelectedIndex(nChannel);
		}
	}

	/** updates ROIs image for a specific group **/
	void updateROIsGroupDisplay(int nGroupN)
	{
		Roi3DGroup updateGroup = groups.get(nGroupN);
		for (Roi3D roi : rois)
		{
			if(roi.getGroupInd() == nGroupN)
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
	    
		final int nMinF = Math.min(0, bt.btData.timeFade);
	    final int nMaxF = Math.max(0, bt.btData.timeFade);
		int nShift;
		final ArrayList< Roi3D > roiSnap = getRoisSnapshot();
		for (int i = 0; i < roiSnap.size(); i++)
		{
			//if ROI is visible at the current time frame
			nShift =  roiSnap.get(i).getTimePoint() - bt.btData.nCurrTimepoint;
			if(nShift >= nMinF && nShift <= nMaxF)
			{
				dCurrDist = roiSnap.get(i).getMinDist(clickLine);
				if(dCurrDist < dDistMin)
				{
					dDistMin = dCurrDist;
					dInd = i;
				}
			}
		}
		
		if(Math.abs(dDistMin - Double.MAX_VALUE) > 0.1)
		{
			jlist.setSelectedIndex(dInd);
			bt.repaintBVV();
		}

	}
	
	/** moves ROIs from nGroupFrom index to group with nGroupTo index **/
	public void moveROIsGroups(final int nGroupFrom, final int nGroupTo)
	{
		for (int i = 0; i < rois.size(); i++)
		{
			if(rois.get(i).getGroupInd() == nGroupFrom)
			{
				rois.get(i).setGroupInd(nGroupTo);
				rmPanel.listModel.setElementAt(getFullDisplayedRoiName(rois.get(i)), i);
			}
		}
	}
	
	public void addGroup(Roi3DGroup group_in)
	{
		groups.add( group_in );		
		rmPanel.updateGroupSelector();	
	}
	
	/** apply group to roi **/	
	public void applyGroupToROI(int nRoiIndex, int nGroupIndex)
	{

		if(nRoiIndex < 0 && nRoiIndex>rois.size() - 1)
			return;
		if(nGroupIndex < 0 && nGroupIndex > groups.size() - 1)
			return;
		final Roi3D currROI = rois.get( nRoiIndex );
		currROI.setGroup(groups.get(nGroupIndex));
		currROI.setGroupInd(nGroupIndex);
		rmPanel.listModel.setElementAt(getFullDisplayedRoiName(currROI), nRoiIndex);
	}
	
	public void dialShowGroups()
	{
		final Roi3DGroupManager dialGroup = new Roi3DGroupManager(this);
		dialGroup.show();
		rmPanel.updateGroupSelector();
	}
	
	public void applyGroupToROI(Roi3D roiIn, int nGroupIndex)
	{
		for (int i = 0; i < rois.size(); i++)
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
		for (int i = 0; i < groups.size(); i++)
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
		synchronized(roiLock)
		{
			rois.removeIf( roi -> roi.getGroupInd() == nGroupN );
			activeRoi.set(-1);
		}
		updateRoiListModel();
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
