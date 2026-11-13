package bigtrace.math;

import com.jogamp.opengl.GL3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4fc;

import bigtrace.BigTrace;
import bigtrace.BigTraceData;
import bigtrace.scene.VisCone;
import bigtrace.scene.VisLineAndPoints;

/** Debug visualization of one-click tracing (BigTrace.bOCVisDebugEnabled).
 * After every step of live one-click tracing, two cones are shown at the new point:
 * - cones.get(CONE_LAST): last direction (last eigenvector / step direction),
 *   opening = directionality constrain: half angle acos(dAngleThreshold), candidates outside it are skipped;
 * - cones.get(CONE_HISTORY): history (momentum) direction, only with stiffness > 0, opening from stiffness:
 *   half angle at which the step score factor cos^p (p = dScorePowStep = 4*stiffness) drops to 1/2.
 * Directions come in physical (scaled) space, positions in voxel coordinates.
 * Updates come from the tracing thread, drawing from the render thread: both are synchronized. **/
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
	public int nStepDelayMs = 0;

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

	/** pause of the tracing thread after a step (nStepDelayMs) **/
	public void stepDelay()
	{
		if(nStepDelayMs > 0)
		{
			try
			{
				Thread.sleep(nStepDelayMs);
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
