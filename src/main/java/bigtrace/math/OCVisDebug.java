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
import bigtrace.scene.VisCone;
import bigtrace.scene.VisLineAndPoints;
import bvvpg.vistools.Bvv;
import bvvpg.vistools.BvvFunctions;
import bvvpg.vistools.BvvStackSource;
import net.imglib2.RandomAccessibleInterval;
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
 * Updates come from the tracing thread, drawing from the render thread: both are synchronized.
 * BVV sources are added/removed on the event dispatch thread. **/
public class OCVisDebug
{
	final BigTrace<?> bt;
	//cones for visualization
	final public List<VisCone> cones = new ArrayList<>();
	//lines for visualization
	final public List<VisLineAndPoints> polylines = new ArrayList<>();

	/** index in cones: last direction, opening = directionality constrain **/
	public static final int CONE_LAST = 0;

	/** index in cones: history (momentum) direction, opening from stiffness **/
	public static final int CONE_HISTORY = 1;

	/** number of step cones **/
	static final int N_CONES = 2;

	//int nConeSurfaceRender = BigTraceData.SURFACE_SHADE;

	int nConeSurfaceRender = BigTraceData.SURFACE_SILHOUETTE;

	boolean initialized = false;

	/** color of the last direction cone **/
	public Color colorLast = Color.YELLOW;

	/** color of the history direction cone **/
	public Color colorHistory = Color.CYAN;

	/** cone length in units of the largest tracing SD (physical) **/
	public double dConeLengthSD = 3.0;

	/** pause after every step (ms), to watch the tracing step by step; 0 = no pause **/
	public int nStepDelayMs = 100;
	
	/** pause when a junction map is shown (ms), before the passage is traced; 0 = no pause **/
	public int nJunctionMapDelayMs = 1500;

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

	/** creates the step cones (hidden until the first step) **/
	void init()
	{
		for(int i = 0; i < N_CONES; i++)
		{
			final VisCone cone = new VisCone(bt.btData);
			cone.surfaceRender = nConeSurfaceRender;
			cone.bVisible = false;
			cones.add(cone);
		}
		cones.get(CONE_LAST).setColor(colorLast);
		cones.get(CONE_HISTORY).setColor(colorHistory);
		initialized = true;
	}

	/** hides the step cones (start of a new tracing) **/
	public synchronized void clear()
	{
		for(final VisCone cone : cones)
		{
			cone.bVisible = false;
		}
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
			final BvvStackSource<UnsignedByteType> src = BvvFunctions.show(map, "junction map", Bvv.options().addTo(bt.bvvHandle));
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
		if(bvvMap == null)
			return;
		if(bInPassage || bMapJustShown)
		{
			bMapJustShown = false;
			return;
		}
		hideJunctionMap();
	}
	
	/** removes the junction map from BVV and restores the raw data **/
	public void hideJunctionMap()
	{
		bMapJustShown = false;
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
			for(final VisLineAndPoints line:polylines)
			{
				line.draw( gl, pvm, screen_size, bt.btData, bWeightedOIT );
			}
		}
	}
}
