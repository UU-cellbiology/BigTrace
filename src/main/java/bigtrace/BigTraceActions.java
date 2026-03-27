package bigtrace;

import java.awt.Component;
import java.awt.KeyboardFocusManager;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JTextField;

import net.imglib2.FinalInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;


import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;

import bigtrace.geometry.Line3D;
import bigtrace.gui.Rotate3DViewerStyle;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Roi3D;
import bigtrace.rois.RoiManager3D;
import bigtrace.volume.VolumeMisc;
import bvvpg.vistools.BvvHandle;


public class BigTraceActions < T extends RealType< T > & NativeType< T > > 
{
	/** plugin instance **/
	BigTrace<T> bt;
	final Actions actions;
	
	public BigTraceActions(final BigTrace<T> bt_)
	{		
		bt = bt_;
		actions = new Actions( new InputTriggerConfig() );
		installActions();
		installBehaviors();
	}
	
	
	public void installActions()
	{
		//final Actions actions = new Actions( new InputTriggerConfig() );
		actions.runnableAction(() -> actionAddPoint(),	            "add point", "F" );
		actions.runnableAction(() -> actionNewRoiTraceMode(),	    "new trace", "V" );		
		actions.runnableAction(() -> actionRemovePoint(),       	"remove point",	"G" );
		actions.runnableAction(() -> actionDeleteROI(),       		"delete ROI", "DELETE" );
		actions.runnableAction(() -> actionDeselect(),	            "deselect", "H" );
		actions.runnableAction(() -> actionReversePoints(),         "reverse curve point order","Y" );
		actions.runnableAction(() -> actionAdvanceTraceBox(),       "advance trace box", "T" );
		actions.runnableAction(() -> actionSemiTraceStraightLine(),	"straight line semitrace", "R" );
		actions.runnableAction(() -> actionZoomIn(),			    "zoom in to click", "D" );
		actions.runnableAction(() -> actionZoomOut(),				"center view (zoom out)", "C" );
		actions.runnableAction(() -> actionResetClip(),				"reset clipping", "X" );
		actions.runnableAction(() -> actionToggleRender(),			"toggle render mode", "O" );
		actions.runnableAction(() -> actionSelectRoi(),	            "select ROI", "E" );
		
		actions.runnableAction(
				() -> {
					Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
					if(!(c instanceof JTextField))
						bt.resetViewXY();
					
				},
				"reset view XY",
				"1" );
			actions.runnableAction(
				() -> {
					Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
					if(!(c instanceof JTextField))
						bt.resetViewYZ();
				},
				"reset view YZ",
				"2" );
			actions.runnableAction(
					() -> {
						Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
						if(!(c instanceof JTextField))
							bt.resetViewXZ();
					},
					"reset view XZ",
					"3" );			

		//actions.namedAction(action, defaultKeyStrokes);
		actions.install( bt.bvv_main.getBvvHandle().getKeybindings(), "BigTrace actions" );


	}
	
	/** install smoother rotation **/
	void installBehaviors()
	{
		final BvvHandle handle = bt.bvv_main.getBvvHandle();
		//change drag rotation for navigation "3D Viewer" style
		final Rotate3DViewerStyle dragRotate = new Rotate3DViewerStyle( 0.75, handle);
		final Rotate3DViewerStyle dragRotateFast = new Rotate3DViewerStyle( 2.0, handle);
		final Rotate3DViewerStyle dragRotateSlow = new Rotate3DViewerStyle( 0.1, handle);
		
		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.behaviour( dragRotate, "drag rotate", "button1" );
		behaviours.behaviour( dragRotateFast, "drag rotate fast", "shift button1" );
		behaviours.behaviour( dragRotateSlow, "drag rotate slow", "ctrl button1" );
		behaviours.install( handle.getTriggerbindings(), "BigTrace Behaviours" );
	}
	
	
	/** find a brightest pixel in the direction of a click
	 *  and add a new 3D point to active ROI OR
	 *  start a new ROI (if none selected)
	 **/ 
	public void actionAddPoint()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
								
			RealPoint target = new RealPoint(3);
			if(!bt.bTraceMode)
			{
				if(bt.findPointLocationFromClick(bt.btData.getDataCurrentSourceClipped(),target))
				{
					
//					System.out.println(target.getDoublePosition(0));
//					System.out.println(target.getDoublePosition(1));
//					System.out.println(target.getDoublePosition(2));
					
					switch (RoiManager3D.mode)
					{
						case RoiManager3D.ADD_POINT_SEMIAUTOLINE:
							
							bt.setTraceBoxMode(true);
							
							//nothing selected, make a new tracing
							if(bt.roiManager.activeRoi.intValue()==-1)
							{
								//make a temporary ROI to calculate TraceBox
								LineTrace3D tracing_for_box = (LineTrace3D) bt.roiManager.makeRoi(Roi3D.LINE_TRACE, bt.btData.nCurrTimepoint);
								if(bt.btData.bEstimateROIThicknessFromParams)
								{
									tracing_for_box.setLineThickness( bt.btData.estimateROIThicknessFromTracing() );
								}
								tracing_for_box.addFirstPoint(target);
								//calculate a box around maximum intensity point
								bt.calcShowTraceBox(tracing_for_box, true);

							}
							else
							{
								final int nRoiType = bt.roiManager.getActiveRoi().getType();
								//continue tracing for the selected tracing
								if(nRoiType == Roi3D.LINE_TRACE)
								{
									bt.calcShowTraceBox((LineTrace3D)bt.roiManager.getActiveRoi(),false);
								}
								//otherwise make a new tracing
								else
								{
									bt.roiManager.addSegment(target, null);																
									bt.calcShowTraceBox((LineTrace3D)bt.roiManager.getActiveRoi(),false);
								}
							}
							break;
						case RoiManager3D.ADD_POINT_ONECLICKLINE:
							
							boolean bMakeNewTrace = false;
							
							if(bt.roiManager.activeRoi.intValue()==-1)
							{
								bMakeNewTrace = true;
							}
							else
							{
								if(bt.roiManager.getActiveRoi().getType() != Roi3D.LINE_TRACE)
								{
									bt.roiManager.unselect();
									bMakeNewTrace = true;
								}
							}	
							
							bt.runOneClickTrace(target, bMakeNewTrace);
							break;
						default:
							bt.roiManager.addPoint(target);
					}
					
				}
			}
			//we are in the tracebox mode,
			//continue to trace within the trace box
			else
			{
				if(RoiManager3D.mode==RoiManager3D.ADD_POINT_SEMIAUTOLINE)
				{
					if(bt.findPointLocationFromClick(bt.btData.trace_weights, target))
					{
						//run trace finding in a separate thread
						bt.getSemiAutoTrace(target);
						
					}
				}
			}
		}
		
	}
	/** works only in trace mode, deselects current tracing
	 * and starts a new one in the trace mode**/
	public void actionNewRoiTraceMode()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
								
			RealPoint target = new RealPoint(3);
			if(bt.bTraceMode)
			{
				if(bt.findPointLocationFromClick(bt.btData.trace_weights, target))
				{
					bt.roiManager.unselect();
					bt.roiManager.addSegment(target, null);																
					bt.calcShowTraceBox((LineTrace3D)bt.roiManager.getActiveRoi(),false);
				}				
			}
		}
	}
	/** remove last added point from ROI
	 * (and delete ROI if it is the last point in it)
	 * **/
	public void actionRemovePoint()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(!bt.bTraceMode)
			{
				bt.roiManager.removePointLinePlane();
			}
			else
			{
				//if the last point in the tracing, leave tracing mode
				if(!bt.roiManager.removeSegment())
				{
					bt.btData.nPointsInTraceBox--;
					bt.roiManager.removeActiveRoi();
					bt.roiManager.activeRoi.set(-1);
					bt.setTraceBoxMode(false);						
					bt.removeTraceBox();
					
				}
				//not the last point, see if we need to move trace box back
				else
				{
					bt.btData.nPointsInTraceBox--;
					
					if(bt.btData.nPointsInTraceBox==0)
					{
						bt.calcShowTraceBox((LineTrace3D)bt.roiManager.getActiveRoi(),false);
					}
				}
				
			}
			bt.bvvViewer.showMessage("Point removed");

		}					
		
	}
	
	public void actionDeleteROI()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			bt.roiManager.deleteActiveROI();
		}
		
	}
	/** deselects current ROI (and finishes tracing)
	 *   
	 * **/
	public void actionDeselect()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(!bt.bTraceMode)
			{
				bt.roiManager.unselect();
			}
			else
			{
				bt.roiManager.unselect();
				bt.setTraceBoxMode(false);
				//bTraceMode= false;
				//roiManager.setLockMode(bTraceMode);	
				bt.removeTraceBox();
			}
		}
	}
	
	/** reverses order of points/segments in PolyLine and LineTrace,
	 * so the active end (where point addition happens) is reversed **/
	public void actionReversePoints() 
	{
		
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(bt.roiManager.activeRoi.intValue()>=0)
			{
				int nRoiType = bt.roiManager.getActiveRoi().getType();
				//continue tracing for the selected tracing
				if(nRoiType == Roi3D.POLYLINE)
				{
					bt.roiManager.getActiveRoi().reversePoints();					
				}
				
				if(nRoiType == Roi3D.LINE_TRACE)
				{
					bt.roiManager.getActiveRoi().reversePoints();
					if(bt.bTraceMode)
					{
						bt.calcShowTraceBox((LineTrace3D)bt.roiManager.getActiveRoi(),false);
						bt.btData.nPointsInTraceBox=1;
					}
				}
				bt.repaintBVV();
			}

		}
	}
	/** move trace box to a position around current last added point **/
	public void actionAdvanceTraceBox()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(bt.bTraceMode && bt.btData.nPointsInTraceBox>1)
			{
				bt.calcShowTraceBox((LineTrace3D)bt.roiManager.getActiveRoi(),false);
				bt.btData.nPointsInTraceBox=1;
			}
		}
	}
	/** in a trace mode build a straight (not a curved trace) segment 
	 * connecting clicked and last point (to overcome semi-auto errors)**/
	public void actionSemiTraceStraightLine()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(bt.bTraceMode)
			{
				//make a straight line
				RealPoint target = new RealPoint(3);							
				if(bt.findPointLocationFromClick(bt.btData.trace_weights, target))
				{								
					bt.roiManager.addSegment(target, 
							VolumeMisc.BresenhamWrap(bt.roiManager.getLastTracePoint(),target));
					bt.btData.nPointsInTraceBox++;
				}
			}
			else
			{	
				if(RoiManager3D.mode == RoiManager3D.ADD_POINT_ONECLICKLINE)
				{
					if(bt.roiManager.activeRoi.intValue()>=0)
					{
						if(bt.roiManager.getActiveRoi().getType() == Roi3D.LINE_TRACE)
						{
							RealPoint target = new RealPoint(3);							
							if(bt.findPointLocationFromClick(bt.btData.getDataCurrentSourceClipped(), target))
							{								
								bt.roiManager.addSegment(target, 
										VolumeMisc.BresenhamWrap(bt.roiManager.getLastTracePoint(),target));
							}
						}
					}
				}

			}
		}
	}
	
	/** find a brightest pixel in the direction of a click
	 *  zoom main view to it, limiting to nZoomBoxSize
	 **/ 
	public void actionZoomIn()
	{
		
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			//addPoint();
			RealPoint target = new RealPoint(3);
			if(!bt.bTraceMode)
			{
				if(bt.findPointLocationFromClick(bt.btData.getDataCurrentSourceClipped(),target))
				{
					
					final FinalInterval zoomInterval = VolumeMisc.getTraceBoxCentered(bt.getTraceInterval(!bt.btData.bZoomClip),bt.btData.nZoomBoxSize, target);

					if(bt.btData.bZoomClip)
					{
						bt.btPanel.clipPanel.setBoundingBox(zoomInterval);
					}
	
					//animate
					bt.bvvViewer.setTransformAnimator(bt.getCenteredViewAnim(zoomInterval,bt.btData.dZoomBoxScreenFraction));
				}
			}
			else
			{
				if(bt.findPointLocationFromClick(bt.btData.trace_weights,target))
				{
					FinalInterval zoomInterval = VolumeMisc.getZoomBoxCentered((long)(bt.btData.nTraceBoxSize*0.5), target);
			
					bt.bvvViewer.setTransformAnimator(bt.getCenteredViewAnim(zoomInterval,bt.btData.dZoomBoxScreenFraction));
				}
			}

		}
	}
	
	/** zoom out to get full overview of current active volume view
	 **/ 
	public void actionZoomOut()
	{
		
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(!bt.bTraceMode)
			{		
				bt.bvvViewer.setTransformAnimator(bt.getCenteredViewAnim(bt.btData.getDataCurrentSourceClipped(),1.0));
			}
			else
			{
				bt.bvvViewer.setTransformAnimator(bt.getCenteredViewAnim(bt.btData.trace_weights,0.8));
			}

		}
	}
	
	public void actionResetClip()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(!bt.bTraceMode)
			{
				bt.btPanel.clipPanel.setBoundingBox(bt.btData.nDimIni);				
			}
		}
	}
	
	public void actionToggleRender()
	{
		if(bt.btData.nRenderMethod == BigTraceData.DATA_RENDER_MAX_INT)
		{
			bt.btPanel.renderMethodPanel.cbRenderMethod.setSelectedIndex(BigTraceData.DATA_RENDER_VOLUMETRIC);
		}
		else
		{
			bt.btPanel.renderMethodPanel.cbRenderMethod.setSelectedIndex(BigTraceData.DATA_RENDER_MAX_INT);			
		}
	}
	/** selects ROI upon user click **/
	public void actionSelectRoi()
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		//solution for now, to not interfere with typing
		if(!bt.bInputLock && !(c instanceof JTextField))
		{
			if(!bt.bTraceMode)
			{
				Line3D clickLine = bt.findClickLine();
				if(clickLine!=null)
					bt.roiManager.selectClosestToLineRoi(bt.findClickLine());
				
			}
		}
		
	}
	public ActionMap getActionMap()
	{		
		return actions.getActionMap();
	}
	public InputMap getInputMap()
	{
		return actions.getInputMap();
	}
}
