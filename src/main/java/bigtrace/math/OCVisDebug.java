package bigtrace.math;

import com.jogamp.opengl.GL3;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.joml.Matrix4fc;

import bigtrace.BigTrace;
import bigtrace.BigTraceData;
import bigtrace.gui.BCsettings;
import bigtrace.rois.Roi3D;
import bigtrace.scene.VisCone;
import bigtrace.scene.VisLineAndPoints;
import bigtrace.scene.VisPointsScaled;
import bigtrace.scene.VisPolygonFlat;
import bvvpg.vistools.Bvv;
import bvvpg.vistools.BvvFunctions;
import bvvpg.vistools.BvvStackSource;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/** Debug visualization of one-click tracing (BigTrace.bOCVisDebugEnabled).
 * After every step of live one-click tracing, two cones are shown at the new point:
 * - cones.get(CONE_LAST): last direction (last eigenvector / step direction),
 *   opening = directionality constrain: half angle acos(dAngleThreshold), candidates outside it are skipped;
 * - cones.get(CONE_HISTORY): history (momentum) direction, only with stiffness > 0, opening from stiffness:
 *   half angle at which the step score factor cos^p (p = dScorePowStep = 4*stiffness) drops to 1/2.
 * Directions come in physical (scaled) space, positions in voxel coordinates.
 * Junction passages: the elongated line filter map of the junction box is shown as a BVV source 
 * while the passage is traced, the raw data (analysis channel) is hidden (display and alpha range 0-0), 
 * as in the semi-auto trace box mode. After the passage the map is removed and the raw data restored.
 * Gap bridging: polylines.get(POLY_BRIDGE_SEARCH) shows the search direction (heading, up to the search reach),
 * polylines.get(POLY_BRIDGE_JUMP) the jump to the landing point (if found), with a message in BVV. 
 * They are hidden at the next step after a bridge, after a failed one they stay until the next tracing.
 * Branch-first junction passage (JunctionBranchPassage): the junction box saliency is shown instead of the map,
 * polylines.get(POLY_BRANCH_HEADING) is the extrapolated heading (up to the cross-section of the target),
 * polylines.get(POLY_BRANCH_TARGET) the target with its line direction (+-1 SD), 
 * polylines.get(POLY_BRANCH_PATH) the planned passage curve,
 * polygons.get(POLYGON_BRANCH_SECTION) the searched region of the cross-section with the target (or the last searched one),
 * points.get(POINTS_BRANCH_CANDIDATES) the candidates found there, points.get(POINTS_BRANCH_REJECTED) the saliency maxima
 * rejected there (saliency below the threshold or orientation outside the constrain). They stay while the passage is traced.
 * Updates come from the tracing thread, drawing from the render thread: both are synchronized.
 * BVV sources are added/removed on the event dispatch thread. **/
public class OCVisDebug
{
	final BigTrace<?> bt;
	
	//cones for visualization
	final public List<VisCone> cones = new ArrayList<>();
	
	//lines for visualization
	final public List<VisLineAndPoints> polylines = new ArrayList<>();

	//points for visualization
	final public List<VisPointsScaled> points = new ArrayList<>();
	
	//cross-sections (planes) for visualization
	final public List<VisPolygonFlat> polygons = new ArrayList<>();

	/** index in cones: last direction, opening = directionality constrain **/
	public static final int CONE_LAST = 0;

	/** index in cones: history (momentum) direction, opening from stiffness **/
	public static final int CONE_HISTORY = 1;

	/** number of step cones **/
	static final int N_CONES = 2;
	
	/** index in polylines: gap bridging search direction (heading, up to the search reach) **/
	public static final int POLY_BRIDGE_SEARCH = 0;
	
	/** index in polylines: gap bridging jump (current point to the landing point) **/
	public static final int POLY_BRIDGE_JUMP = 1;
	
	/** index in polylines: branch-first passage, extrapolated heading **/
	public static final int POLY_BRANCH_HEADING = 2;
	
	/** index in polylines: branch-first passage, chosen target and its direction **/
	public static final int POLY_BRANCH_TARGET = 3;
	
	/** index in polylines: branch-first passage, planned curve **/
	public static final int POLY_BRANCH_PATH = 4;
	
	/** number of polylines **/
	static final int N_POLYLINES = 5;
	
	/** visibility of polylines (same indices) **/
	final boolean [] bPolylineVisible = new boolean[N_POLYLINES];
	
	/** index in polygons: branch-first passage, searched region of the cross-section with the target **/
	public static final int POLYGON_BRANCH_SECTION = 0;
	
	/** number of polygons **/
	static final int N_POLYGONS = 1;
	
	/** visibility of polygons (same indices) **/
	final boolean [] bPolygonVisible = new boolean[N_POLYGONS];
	
	/** index in points: branch-first passage, candidates (possible branches) in the cross-section **/
	public static final int POINTS_BRANCH_CANDIDATES = 0;
	
	/** index in points: branch-first passage, rejected saliency maxima in the cross-section **/
	public static final int POINTS_BRANCH_REJECTED = 1;
	
	/** number of point sets **/
	static final int N_POINTS = 2;
	
	/** visibility of point sets (same indices) **/
	final boolean [] bPointsVisible = new boolean[N_POINTS];

	//int nConeSurfaceRender = BigTraceData.SURFACE_SHADE;

	int nConeSurfaceRender = BigTraceData.SURFACE_SILHOUETTE;

	boolean initialized = false;

	/** color of the last direction cone **/
	public Color colorLast = Color.YELLOW;

	/** color of the history direction cone **/
	public Color colorHistory = Color.CYAN;

	/** color of the gap bridging search direction **/
	public Color colorBridgeSearch = Color.ORANGE;
	
	/** color of the gap bridging jump **/
	public Color colorBridgeJump = Color.GREEN;
	
	/** colors of the branch-first passage: heading, target, planned curve **/
	public Color colorBranchHeading = Color.WHITE;
	public Color colorBranchTarget = Color.RED;
	public Color colorBranchPath = Color.MAGENTA;
	
	/** pause when the branch-first plan is shown (ms); 0 = no pause **/
	public int nBranchPlanDelayMs = 1500;
	
	/** color, line thickness and render type (Roi3D.OUTLINE, WIRE, SURFACE) of the branch-first cross-section **/
	public Color colorBranchSection = new Color(100, 180, 255);
	public float fSectionThickness = 2.0f;
	public int nSectionRenderType = Roi3D.WIRE;
	
	/** colors and size (in smallest voxel sizes) of the branch-first candidates (filled) and rejected maxima (rings) **/
	public Color colorBranchCandidates = Color.YELLOW;
	public Color colorBranchRejected = Color.GRAY;
	public float fBranchPointSize = 6.0f;
	
	/** thickness of the debug lines **/
	public float fLineThickness = 3.0f;

	/** cone length in units of the largest tracing SD (physical) **/
	public double dConeLengthSD = 3.0;

	/** pause after every step (ms), to watch the tracing step by step; 0 = no pause **/
	public int nStepDelayMs = 100;
	
	/** pause when a junction map is shown (ms), before the passage is traced; 0 = no pause **/
	public int nJunctionMapDelayMs = 1500;
	
	/** pause when gap bridging is shown (ms); 0 = no pause **/
	public int nGapBridgeDelayMs = 1500;
	
	/** gap bridging was shown during the current step (lines are kept until the next one) **/
	boolean bBridgeJustShown = false;

	/** junction map source in BVV, null if not shown (changed on the event dispatch thread) **/
	volatile BvvStackSource<UnsignedByteType> bvvMap = null;
	
	/** the map was shown during the current step (kept for one more step if there is no passage) **/
	boolean bMapJustShown = false;
	
	/** brightness/alpha of the junction map, kept between junctions **/
	final BCsettings bcMap = new BCsettings();
	
	/** brightness/alpha of the raw data (analysis channel) before it was hidden **/
	final BCsettings bcRaw = new BCsettings();

	public OCVisDebug(final BigTrace<?> bt)
	{
		this.bt = bt;

	}

	/** creates the step cones and lines (hidden until used) **/
	void init()
	{
		for(int i = 0; i < N_POLYLINES; i++)
		{
			final VisLineAndPoints line = new VisLineAndPoints();
			line.setThickness(fLineThickness);
			polylines.add(line);
			bPolylineVisible[i] = false;
		}
		polylines.get(POLY_BRIDGE_SEARCH).setLineColor(colorBridgeSearch);
		polylines.get(POLY_BRIDGE_SEARCH).setPointColor(colorBridgeSearch);
		polylines.get(POLY_BRIDGE_JUMP).setLineColor(colorBridgeJump);
		polylines.get(POLY_BRIDGE_JUMP).setPointColor(colorBridgeJump);
		polylines.get(POLY_BRANCH_HEADING).setLineColor(colorBranchHeading);
		polylines.get(POLY_BRANCH_HEADING).setPointColor(colorBranchHeading);
		polylines.get(POLY_BRANCH_TARGET).setLineColor(colorBranchTarget);
		polylines.get(POLY_BRANCH_TARGET).setPointColor(colorBranchTarget);
		polylines.get(POLY_BRANCH_PATH).setLineColor(colorBranchPath);
		polylines.get(POLY_BRANCH_PATH).setPointColor(colorBranchPath);
		for(int i = 0; i < N_CONES; i++)
		{
			final VisCone cone = new VisCone(bt.btData);
			cone.surfaceRender = nConeSurfaceRender;
			cone.bVisible = false;
			cones.add(cone);
		}
		cones.get(CONE_LAST).setColor(colorLast);
		cones.get(CONE_HISTORY).setColor(colorHistory);
		for(int i = 0; i < N_POLYGONS; i++)
		{
			final VisPolygonFlat polygon = new VisPolygonFlat(bt.btData);
			polygon.setRenderType(nSectionRenderType);
			polygon.setThickness(fSectionThickness);
			polygon.setColor(colorBranchSection);
			polygons.add(polygon);
			bPolygonVisible[i] = false;
		}
		for(int i = 0; i < N_POINTS; i++)
		{
			final VisPointsScaled pts = new VisPointsScaled();
			pts.bLastSquared = false;
			pts.setSize(fBranchPointSize);
			pts.setRenderType(Roi3D.WIRE);
			points.add(pts);
			bPointsVisible[i] = false;
		}
		//candidates: filled disks, rejected maxima: rings
		points.get(POINTS_BRANCH_CANDIDATES).setColor(colorBranchCandidates);
		points.get(POINTS_BRANCH_CANDIDATES).setRenderType(Roi3D.SURFACE);
		points.get(POINTS_BRANCH_REJECTED).setColor(colorBranchRejected);
		initialized = true;
	}

	/** hides the step cones and lines (start of a new tracing) **/
	public synchronized void clear()
	{
		for(final VisCone cone : cones)
		{
			cone.bVisible = false;
		}
		hidePolylines();
		for(int i = 0; i < bPolygonVisible.length; i++)
		{
			bPolygonVisible[i] = false;
		}
		for(int i = 0; i < bPointsVisible.length; i++)
		{
			bPointsVisible[i] = false;
		}
		bBridgeJustShown = false;
	}
	
	/** hides all polylines **/
	void hidePolylines()
	{
		for(int i = 0; i < bPolylineVisible.length; i++)
		{
			bPolylineVisible[i] = false;
		}
	}
	
	/** hides the gap bridging lines **/
	void hideBridgeLines()
	{
		bPolylineVisible[POLY_BRIDGE_SEARCH] = false;
		bPolylineVisible[POLY_BRIDGE_JUMP] = false;
	}
	
	/** hides the branch-first passage lines, cross-section and points **/
	synchronized void hideBranchLines()
	{
		bPolylineVisible[POLY_BRANCH_HEADING] = false;
		bPolylineVisible[POLY_BRANCH_TARGET] = false;
		bPolylineVisible[POLY_BRANCH_PATH] = false;
		bPolygonVisible[POLYGON_BRANCH_SECTION] = false;
		bPointsVisible[POINTS_BRANCH_CANDIDATES] = false;
		bPointsVisible[POINTS_BRANCH_REJECTED] = false;
	}
	
	/** true if a branch-first line (or cross-section, points) is shown **/
	synchronized boolean isBranchShown()
	{
		return bPolylineVisible[POLY_BRANCH_HEADING] || bPolylineVisible[POLY_BRANCH_TARGET] || bPolylineVisible[POLY_BRANCH_PATH]
				|| bPolygonVisible[POLYGON_BRANCH_SECTION] || bPointsVisible[POINTS_BRANCH_CANDIDATES] || bPointsVisible[POINTS_BRANCH_REJECTED];
	}
	
	/**
	 * Branch-first junction passage plan, then pause (nBranchPlanDelayMs). All in voxel coordinates.
	 * @param entryVoxel current point (junction entry)
	 * @param headingEndVoxel end of the extrapolated heading (cross-section of the target or the last searched one)
	 * @param curveVoxel planned passage curve; null = no target found
	 * @param targetA target - 1 SD along its direction (null = no target)
	 * @param targetB target + 1 SD along its direction
	 * @param sectionVoxel outline of the searched region in the cross-section of the target 
	 * (or the last searched one); null = none
	 * @param candidatesVoxel candidates (possible branches) in that cross-section
	 * @param rejectedVoxel saliency maxima rejected there (saliency or orientation)
	 */
	public void showBranchPlan(final double [] entryVoxel, final double [] headingEndVoxel, final ArrayList<double[]> curveVoxel,
			final double [] targetA, final double [] targetB, final ArrayList<double[]> sectionVoxel,
			final ArrayList<double[]> candidatesVoxel, final ArrayList<double[]> rejectedVoxel)
	{
		synchronized(this)
		{
			if(!initialized)
			{
				init();
			}
			setLine(POLY_BRANCH_HEADING, entryVoxel, headingEndVoxel);
			if(curveVoxel != null && targetA != null)
			{
				setLine(POLY_BRANCH_TARGET, targetA, targetB);
				polylines.get(POLY_BRANCH_PATH).setVertices(toRealPoints(curveVoxel));
				bPolylineVisible[POLY_BRANCH_PATH] = true;
			}
			else
			{
				bPolylineVisible[POLY_BRANCH_TARGET] = false;
				bPolylineVisible[POLY_BRANCH_PATH] = false;
			}
			//cross-section
			if(sectionVoxel != null && sectionVoxel.size() >= 3)
			{
				final VisPolygonFlat polygon = polygons.get(POLYGON_BRANCH_SECTION);
				polygon.setRenderType(nSectionRenderType);
				polygon.setVertices(toRealPoints(sectionVoxel));
				bPolygonVisible[POLYGON_BRANCH_SECTION] = true;
			}
			else
			{
				bPolygonVisible[POLYGON_BRANCH_SECTION] = false;
			}
			setPoints(POINTS_BRANCH_CANDIDATES, candidatesVoxel);
			setPoints(POINTS_BRANCH_REJECTED, rejectedVoxel);
			//kept for the step of the junction (as the map)
			bMapJustShown = true;
		}
		bt.repaintBVV();
		sleep(nBranchPlanDelayMs);
	}
	
	/** point set nIndex: the points (voxel coordinates), visible if there are any **/
	void setPoints(final int nIndex, final ArrayList<double[]> pointsVoxel)
	{
		if(pointsVoxel == null || pointsVoxel.isEmpty())
		{
			bPointsVisible[nIndex] = false;
			return;
		}
		points.get(nIndex).setVertices(toRealPoints(pointsVoxel));
		bPointsVisible[nIndex] = true;
	}
	
	/** double [] positions to RealPoints **/
	static ArrayList<RealPoint> toRealPoints(final ArrayList<double[]> in)
	{
		final ArrayList<RealPoint> out = new ArrayList<>();
		for(final double [] p : in)
		{
			out.add(new RealPoint(p));
		}
		return out;
	}
	
	/**
	 * Gap bridging: search direction and the jump (if found), message in BVV, pause.
	 * @param startVoxel current point (where the gap starts), voxel coordinates
	 * @param searchEndVoxel end of the search along the heading (search reach), voxel coordinates
	 * @param landingVoxel landing point, voxel coordinates; null = gap not bridged
	 */
	public void showGapBridge(final double [] startVoxel, final double [] searchEndVoxel, final double [] landingVoxel)
	{
		synchronized(this)
		{
			if(!initialized)
			{
				init();
			}
			setLine(POLY_BRIDGE_SEARCH, startVoxel, searchEndVoxel);
			if(landingVoxel != null)
			{
				setLine(POLY_BRIDGE_JUMP, startVoxel, landingVoxel);
			}
			else
			{
				bPolylineVisible[POLY_BRIDGE_JUMP] = false;
			}
			bBridgeJustShown = true;
		}
		showMessage(landingVoxel != null ? "gap bridged" : "gap not bridged");
		bt.repaintBVV();
		sleep(nGapBridgeDelayMs);
	}
	
	/** polyline nIndex: a segment between two points (voxel coordinates), made visible **/
	void setLine(final int nIndex, final double [] p1, final double [] p2)
	{
		final ArrayList<RealPoint> points = new ArrayList<>();
		points.add(new RealPoint(p1));
		points.add(new RealPoint(p2));
		polylines.get(nIndex).setVertices(points);
		bPolylineVisible[nIndex] = true;
	}

	/**
	 * Cones for one tracing step.
	 * @param posVoxel new point, voxel coordinates
	 * @param lastDirPhys last direction, physical space, unit
	 * @param dAngleThreshold directionality constrain (cosine)
	 * @param memDirPhys history (momentum) direction, physical space, unit; null = history is off
	 * @param dScorePowStep power of the step direction score (4 * stiffness)
	 * @param sigmaTrace tracing SDs, voxels
	 */
	public synchronized void showStep(final double [] posVoxel, final double [] lastDirPhys, final double dAngleThreshold,
			final double [] memDirPhys, final double dScorePowStep, final double [] sigmaTrace)
	{
		if(!initialized)
		{
			init();
		}
		final double [] posPhys = new double[3];
		double dMaxSD = 0;
		for(int d = 0; d < 3; d++)
		{
			posPhys[d] = posVoxel[d] * bt.btData.globCal[d];
			dMaxSD = Math.max(dMaxSD, sigmaTrace[d] * bt.btData.globCal[d]);
		}
		final double dLength = dConeLengthSD * dMaxSD;
		
		//gap bridging lines: kept for the step of the bridge, hidden after
		if(bBridgeJustShown)
		{
			bBridgeJustShown = false;
		}
		else
		{
			hideBridgeLines();
		}

		//last direction: directionality constrain
		final double dHalfLast = Math.acos(Math.min(1.0, Math.max(0.0, dAngleThreshold)));
		updateCone(CONE_LAST, posPhys, lastDirPhys, dLength, dHalfLast, colorLast);

		//history direction: angle where cos^p = 1/2
		if(memDirPhys != null && dScorePowStep > 0)
		{
			final double dHalfHistory = Math.acos(Math.pow(0.5, 1.0 / dScorePowStep));
			updateCone(CONE_HISTORY, posPhys, memDirPhys, dLength, dHalfHistory, colorHistory);
		}
		else
		{
			cones.get(CONE_HISTORY).bVisible = false;
		}
	}

	/** updates cone nIndex: apex at posPhys, axis along dirPhys (unit) of length dLength,
	 * half opening angle dHalfAngle (radians) **/
	void updateCone(final int nIndex, final double [] posPhys, final double [] dirPhys, final double dLength, final double dHalfAngle, final Color color)
	{
		final double [] axis = new double[3];
		for(int d = 0; d < 3; d++)
		{
			axis[d] = dirPhys[d] * dLength;
		}
		final VisCone cone = cones.get(nIndex);
		cone.setColor(color);
		//VisCone takes the full opening angle
		cone.setVertices(axis, posPhys, 2.0 * dHalfAngle);
		cone.bVisible = true;
	}

	/** Shows the junction map (full resolution voxel coordinates, 0-255) in BVV, 
	 * replacing the previous one. The first time the raw data (analysis channel) is hidden **/
	public void showJunctionMap(final RandomAccessibleInterval<UnsignedByteType> map)
	{
		showJunctionMap(map, "junction map");
	}
	
	/** as showJunctionMap(map), with the name of the BVV source **/
	public void showJunctionMap(final RandomAccessibleInterval<UnsignedByteType> map, final String sName)
	{
		runOnEDT(() -> 
		{
			if(bvvMap != null)
			{
				bcMap.storeBC(bvvMap);
				bvvMap.removeFromBdv();
			}
			else
			{
				final BvvStackSource<?> raw = getRawSource();
				if(raw != null)
				{
					bcRaw.storeBC(raw);
					raw.setDisplayRange(0.0, 0.0);
					raw.setAlphaRange(0.0, 0.0);
				}
			}
			final BvvStackSource<UnsignedByteType> src = BvvFunctions.show(map, sName, Bvv.options().addTo(bt.bvvHandle));
			src.setRenderType(bt.btData.nRenderMethod);
			src.setLUT( "Fire" );
			src.setDisplayRangeBounds(0, 255);
			src.setAlphaRangeBounds(0, 255);
			if(bcMap.bInit)
			{
				bcMap.setBC(src);
			}
			else
			{
				src.setDisplayRange(0.0, 255.0);
				src.setAlphaRange(0.0, 255.0);
			}
			bvvMap = src;
		});
		bMapJustShown = true;
		bt.repaintBVV();
		sleep(nJunctionMapDelayMs);
	}
	
	/** message in the BVV window (e.g. "passage rejected") **/
	public void showMessage(final String sMessage)
	{
		if(bt.bvvViewer != null)
		{
			bt.bvvViewer.showMessage(sMessage);
		}
	}
	
	/** called every tracing step with junction handling: during a passage the map stays,
	 * after it (or one step after a junction without passage) it is removed **/
	public void junctionMapStep(final boolean bInPassage)
	{
		if(bvvMap == null && !isBranchShown())
			return;
		if(bInPassage || bMapJustShown)
		{
			bMapJustShown = false;
			return;
		}
		hideJunctionMap();
	}
	
	/** removes the junction map from BVV and restores the raw data (and the branch-first lines) **/
	public void hideJunctionMap()
	{
		bMapJustShown = false;
		hideBranchLines();
		runOnEDT(() ->
		{
			if(bvvMap == null)
				return;
			bcMap.storeBC(bvvMap);
			bvvMap.removeFromBdv();
			bvvMap = null;
			final BvvStackSource<?> raw = getRawSource();
			if(raw != null)
			{
				bcRaw.setBC(raw);
			}
		});
		bt.repaintBVV();
	}
	
	/** BVV source of the analysis (traced) channel **/
	BvvStackSource<?> getRawSource()
	{
		final int nCh = bt.btData.nChAnalysis;
		if(nCh < 0 || nCh >= bt.bvv_sources.size())
			return null;
		return bt.bvv_sources.get(nCh);
	}
	
	/** runs r on the event dispatch thread and waits for it **/
	static void runOnEDT(final Runnable r)
	{
		if(SwingUtilities.isEventDispatchThread())
		{
			r.run();
			return;
		}
		try
		{
			SwingUtilities.invokeAndWait(r);
		}
		catch ( InterruptedException e )
		{
			Thread.currentThread().interrupt();
		}
		catch ( InvocationTargetException e )
		{
			e.printStackTrace();
		}
	}

	/** pause of the tracing thread after a step (nStepDelayMs) **/
	public void stepDelay()
	{
		sleep(nStepDelayMs);
	}
	
	/** pause of the calling (tracing) thread, nothing if nMs is 0 **/
	static void sleep(final int nMs)
	{
		if(nMs > 0)
		{
			try
			{
				Thread.sleep(nMs);
			}
			catch ( InterruptedException ignore )
			{
			}
		}
	}

	public synchronized void draw(final GL3 gl, final Matrix4fc pvm,  Matrix4fc vm, final int [] screen_size, final boolean bWeightedOIT)
	{
		for(final VisCone cone : cones)
		{
			if(cone.isTransparent() == bWeightedOIT)
			{
				cone.draw( gl, pvm, vm, bt.btData, bWeightedOIT );
			}
		}
		

		if(!bWeightedOIT)
		{
			for(int i = 0; i < polygons.size(); i++)
			{
				if(i >= bPolygonVisible.length || bPolygonVisible[i])
				{
					polygons.get(i).draw( gl, pvm, bt.btData, bWeightedOIT );
				}
			}
			
			for(int i = 0; i < points.size(); i++)
			{
				if(i >= bPointsVisible.length || bPointsVisible[i])
				{
					points.get(i).draw( gl, pvm, screen_size, bt.btData, bWeightedOIT );
				}
			}
			
			for(int i = 0; i < polylines.size(); i++)
			{
				if(i >= bPolylineVisible.length || bPolylineVisible[i])
				{
					polylines.get(i).draw( gl, pvm, screen_size, bt.btData, bWeightedOIT );
				}
			}
		}
	}
}
