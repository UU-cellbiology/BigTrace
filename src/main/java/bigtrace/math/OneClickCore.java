package bigtrace.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;

/** One-click tracing algorithm, without any UI (OneClickTrace builds the ROI from it).
 * <p>
 * Plain stepping by Steger's line points and linking (C. Steger, An unbiased detector of curvilinear structures, 1998;
 * dissertation 1998, 3.1.x and 4.3.2-4.3.3 for 3D):
 * <ul>
 * <li>A voxel gives a line point if the maximum of the second order Taylor polynomial of the smoothed intensity
 * in the plane across the line lies close to it (linePoint). Its line direction is the eigenvector
 * of the smallest absolute eigenvalue of the scale-normalised hessian (MathBox), so that the elongation
 * of the PSF does not tilt it.</li>
 * <li>From the current line point, the neighbours of its voxel around the line direction
 * (NeighborsHashMap, Steger's restricted set) are tested; of the line points ahead the one with the smallest
 * d + beta (distance in the smallest voxel size, angle between the line directions in radians) is the next point.</li>
 * <li>Tracing stops when there is none (stopReason tells which test removed the last candidates).</li>
 * </ul>
 * Units: positions are voxel coordinates of the input (sub-voxel), directions are unit vectors in physical
 * space (calibrated voxel size). The hessian is scale-normalised by the tracing sigma (curve thickness SD)
 * per axis: its anisotropy is taken as the anisotropy of the filament profile (MathBox). **/
public class OneClickCore < T extends RealType< T > >
{
	/** reasons for the end of tracing **/
	public enum Stop { NONE, NO_LINE_POINT, DIRECTION, INTENSITY, MASK, SELF_INTERSECTION, BORDER }

	/** box of the hessian around the current point: +- BOX_SD tracing sigmas **/
	static final double BOX_SD = 3.0;

	/** the box is recomputed when the point is further than INNER_SD sigmas from its centre **/
	static final double INNER_SD = 1.0;

	/** search of the start point: saliency maximum within +- START_SD sigmas of the click **/
	static final double START_SD = 2.0;

	/** the line point of a voxel (the maximum across the line) may be at most LINE_POINT_TOL voxels away
	 * from it per axis. Steger uses half a voxel (the point lies in the voxel), which in 2D finds a line point
	 * in every pixel the line passes through. In 3D it does not: a line through the corner region of a voxel has
	 * its closest point outside of it (e.g. x offset 0.55 with y, z 0.3), and lines on voxel boundaries give 0.51
	 * from both sides, so the chain broke (harness: 0.5 reached 71% of the synthetic cases, 0.55 74%,
	 * replays 0.6 45%, 1.0 47%; the same wrong-branch events). The estimate is still close to the expansion
	 * point, the linking cost then prefers the nearest. **/
	static final double LINE_POINT_TOL = 1.0;

	/** weight of the angle (radians) against the distance (smallest voxels) in the linking cost **/
	static final double LINK_ANGLE_WEIGHT = 1.0;

	/** the next point is at least LINK_MIN_FORWARD smallest voxels ahead **/
	static final double LINK_MIN_FORWARD = 0.25;

	/** and within the cone of LINK_MIN_STEP_COS around the line direction **/
	static final double LINK_MIN_STEP_COS = 0.5;

	/** a candidate closer than SELF_DIST smallest voxels to a traced point is a self-intersection **/
	static final double SELF_DIST = 0.5;

	//------------------ settings (set before init)

	/** tracing (smoothing) sigma, SD in voxels per axis **/
	public final double [] sigma = new double [] {3.0, 3.0, 3.0};

	/** voxel size per axis (physical units) **/
	public final double [] voxelSize = new double [] {1.0, 1.0, 1.0};

	/** directionality constrain: min |cos| of the angle between the line directions of consecutive points **/
	public double dDirectionality = 0.6;

	/** if not NaN, line points where the input is below it are not used **/
	public double dIntensityThreshold = Double.NaN;

	/** if not null, voxels where it is true are not used (existing ROIs) **/
	public Predicate<long[]> occupied = null;

	/** if not null, the candidates of each step are reported to it (debugging, the harness) **/
	public java.util.function.Consumer<String> log = null;

	/** number of threads for the convolutions **/
	public int nThreads = Runtime.getRuntime().availableProcessors();

	//------------------ state

	RandomAccessibleInterval< T > input;
	RandomAccessible< T > inputExtended;
	RandomAccess< T > raInput;

	MathBox box;
	@SuppressWarnings( "rawtypes" )
	Convolution [] conv;
	ExecutorService es = null;

	/** half size of the box (voxels) **/
	final long [] boxHalf = new long[3];
	/** half size of the inner box (voxels) **/
	final long [] innerHalf = new long[3];
	/** centre of the box (voxels) **/
	final long [] boxCentre = new long[3];
	boolean bBoxValid = false;
	/** true after the box was recomputed (cleared by the caller, e.g. to show it) **/
	public boolean bBoxMoved = false;

	double dMinVox = 1.0;

	/** current point (voxels) and line direction (physical, unit, oriented along the tracing) **/
	final double [] pos = new double[3];
	final double [] dir = new double[3];

	/** why the last step returned null **/
	public Stop stopReason = Stop.NONE;

	/** restricted neighbour sets (NeighborsHashMap) indexed by the rounded direction **/
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	final List<int[]> [] neighborsArr = new ArrayList[27];

	/** traced points (voxels) in a spatial hash (rounded positions) for the self-intersection test **/
	final HashMap<Long, ArrayList<double[]>> traced = new HashMap<>();

	/** result of linePoint **/
	public static class LinePoint
	{
		/** position (voxels, sub-voxel) **/
		public final double [] x = new double[3];
		/** line direction (physical, unit, sign arbitrary) **/
		public final double [] t = new double[3];
		/** line saliency (normalised hessian) **/
		public double dSal;
	}

	/** sets up the box and the convolutions for the current settings **/
	public void init()
	{
		final double [] scale = sigma;
		final long [] dims = new long[3];
		dMinVox = Double.MAX_VALUE;
		for (int d = 0; d < 3; d++)
		{
			boxHalf[d] = (long) Math.ceil(BOX_SD * sigma[d]);
			innerHalf[d] = Math.max(1, (long) Math.ceil(INNER_SD * sigma[d]));
			dims[d] = 2 * boxHalf[d] + 1;
			dMinVox = Math.min(dMinVox, voxelSize[d]);
		}
		box = new MathBox(dims);
		box.setScale(scale);
		box.enableGradient();
		release();
		es = Executors.newFixedThreadPool( Math.max(1, nThreads) );
		conv = MathBox.derivativeConvolutions(sigma, scale, es);
		NeighborsHashMap.fill( neighborsArr );
		bBoxValid = false;
	}

	/** the image to trace (voxel coordinates) **/
	public void setInput(final RandomAccessibleInterval< T > input_)
	{
		input = input_;
		inputExtended = Views.extendBorder(input);
		raInput = input.randomAccess();
		bBoxValid = false;
	}

	public void release()
	{
		if(es != null)
		{
			es.shutdown();
			es = null;
		}
	}

	/** the box of the hessian (voxels) **/
	public FinalInterval getBoxInterval()
	{
		final long [] max = new long[3];
		for (int d = 0; d < 3; d++)
		{
			max[d] = box.boxMin[d] + box.dims[d] - 1;
		}
		return new FinalInterval(box.boxMin.clone(), max);
	}

	/** the box is computed around x if x is not in its inner part **/
	void ensureBox(final double [] x)
	{
		final long [] c = new long[3];
		boolean bInside = bBoxValid;
		for (int d = 0; d < 3; d++)
		{
			c[d] = Math.round(x[d]);
			if(Math.abs(c[d] - boxCentre[d]) > innerHalf[d])
				bInside = false;
		}
		if(bInside)
			return;
		final long [] min = new long[3];
		for (int d = 0; d < 3; d++)
		{
			boxCentre[d] = c[d];
			//inside of the image if it fits
			min[d] = Math.max(input.min(d), Math.min(input.max(d) - box.dims[d] + 1, c[d] - boxHalf[d]));
		}
		box.setMin(min);
		box.compute(inputExtended, conv, nThreads, es);
		bBoxValid = true;
		bBoxMoved = true;
	}

	boolean insideInput(final long [] v)
	{
		for (int d = 0; d < 3; d++)
		{
			if(v[d] < input.min(d) || v[d] > input.max(d))
				return false;
		}
		return true;
	}

	//------------------ start

	/** start of a trace near the click (voxels): the saliency maximum within +- START_SD sigmas,
	 * its line point if it is one (otherwise the voxel); its line direction to dirOut (physical, unit).
	 * Null if there is no line-like voxel around. **/
	public double [] findStart(final double [] click, final double [] dirOut)
	{
		ensureBox(click);
		final long [] min = new long[3];
		final long [] max = new long[3];
		for (int d = 0; d < 3; d++)
		{
			final long r = (long) Math.ceil(START_SD * sigma[d]);
			min[d] = Math.max(input.min(d), Math.round(click[d]) - r);
			max[d] = Math.min(input.max(d), Math.round(click[d]) + r);
		}
		final long [] v = new long[3];
		final long [] best = new long[3];
		double dBest = 0;
		for (v[2] = min[2]; v[2] <= max[2]; v[2]++)
			for (v[1] = min[1]; v[1] <= max[1]; v[1]++)
				for (v[0] = min[0]; v[0] <= max[0]; v[0]++)
				{
					final double s = box.saliencyAtVoxel(v);
					if(s > dBest)
					{
						dBest = s;
						System.arraycopy(v, 0, best, 0, 3);
					}
				}
		if(!(dBest > 0))
			return null;
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = best[d];
		}
		final double [] lp = nearestLinePoint(out, dirOut);
		if(lp != null)
			return lp;
		final double [] tg = new double[3];
		box.saliencyAndVectorAtVoxel(best, tg);
		System.arraycopy(gridToPhys(tg), 0, dirOut, 0, 3);
		return out;
	}

	/** the line point nearest to x (voxels) among the voxel of x and its 26 neighbours, its line direction
	 * (physical, unit) to dirOut; null if there is none. A start (a click, the end of a trace) is often
	 * not exactly on the line, and stepping goes from line point to line point. **/
	public double [] nearestLinePoint(final double [] x, final double [] dirOut)
	{
		ensureBox(x);
		final long [] n = new long[3];
		final LinePoint lp = new LinePoint();
		double [] out = null;
		double dBest = Double.MAX_VALUE;
		for (int k = -1; k <= 1; k++)
			for (int j = -1; j <= 1; j++)
				for (int i = -1; i <= 1; i++)
				{
					n[0] = Math.round(x[0]) + i;
					n[1] = Math.round(x[1]) + j;
					n[2] = Math.round(x[2]) + k;
					if(!insideInput(n) || !linePoint(n, lp))
						continue;
					double dd = 0;
					for (int d = 0; d < 3; d++)
					{
						final double r = (lp.x[d] - x[d]) * voxelSize[d];
						dd += r * r;
					}
					if(dd < dBest)
					{
						dBest = dd;
						out = lp.x.clone();
						System.arraycopy(lp.t, 0, dirOut, 0, 3);
					}
				}
		return out;
	}

	//------------------ tracing

	/** forgets the traced points (self-intersection test) **/
	public void clearTrace()
	{
		traced.clear();
	}

	/** adds a traced point (voxels) for the self-intersection test **/
	public void addTracePoint(final double [] x)
	{
		traced.computeIfAbsent( packKey(Math.round(x[0]), Math.round(x[1]), Math.round(x[2])), k -> new ArrayList<>() ).add( x.clone() );
	}

	/** tracing continues from x (voxels) along the direction direction (physical); x is added to the traced points **/
	public void begin(final double [] x, final double [] direction)
	{
		System.arraycopy(x, 0, pos, 0, 3);
		System.arraycopy(direction, 0, dir, 0, 3);
		LinAlgHelpers.normalize(dir);
		addTracePoint(x);
		stopReason = Stop.NONE;
	}

	/** current point (voxels) **/
	public double [] getPosition()
	{
		return pos.clone();
	}

	/** current line direction (physical, unit) **/
	public double [] getDirection()
	{
		return dir.clone();
	}

	/** Steger's linking step from the current point: the next point (voxels, also added to the traced points),
	 * or null (stopReason) **/
	public double [] step()
	{
		ensureBox(pos);
		final long [] gc = new long[3];
		for (int d = 0; d < 3; d++)
		{
			gc[d] = Math.round(pos[d]);
		}
		//Steger's set of neighbours around the line direction
		final double [] tg = physToGrid(dir);
		int nIndex = 0;
		for (int d = 0; d < 3; d++)
		{
			nIndex = nIndex * 3 + ((int) Math.round(tg[d]) + 1);
		}
		final List<int[]> set = neighborsArr[nIndex];
		if(set == null)
		{
			stopReason = Stop.NO_LINE_POINT;
			return null;
		}
		final long [] n = new long[3];
		final long [] nr = new long[3];
		final LinePoint lp = new LinePoint();
		final LinePoint best = new LinePoint();
		double dBestCost = Double.MAX_VALUE;
		//the most specific reason why the candidates were rejected
		Stop reason = Stop.NO_LINE_POINT;
		boolean bAnyInside = false;
		for (final int [] o : set)
		{
			for (int d = 0; d < 3; d++)
			{
				n[d] = gc[d] + o[d];
			}
			if(!insideInput(n))
				continue;
			bAnyInside = true;
			if(!(box.saliencyAtVoxel(n) > 0))
			{
				report(n, null, "saliency");
				continue;
			}
			if(!linePoint(n, lp))
			{
				report(n, log == null ? null : lp, "not a line point");
				continue;
			}
			final double dCos = Math.abs(LinAlgHelpers.dot(lp.t, dir));
			if(dCos < dDirectionality)
			{
				report(n, lp, "direction");
				reason = later(reason, Stop.DIRECTION);
				continue;
			}
			double dForward = 0;
			double dDist = 0;
			for (int d = 0; d < 3; d++)
			{
				final double s = (lp.x[d] - pos[d]) * voxelSize[d];
				dForward += s * dir[d];
				dDist += s * s;
			}
			dDist = Math.sqrt(dDist);
			if(dForward < LINK_MIN_FORWARD * dMinVox || dForward < LINK_MIN_STEP_COS * dDist)
			{
				report(n, lp, "not ahead");
				continue;
			}
			for (int d = 0; d < 3; d++)
			{
				nr[d] = Math.round(lp.x[d]);
			}
			if(!Double.isNaN(dIntensityThreshold) && insideInput(nr)
					&& raInput.setPositionAndGet(nr).getRealDouble() < dIntensityThreshold)
			{
				reason = later(reason, Stop.INTENSITY);
				continue;
			}
			if(occupied != null && occupied.test(nr))
			{
				reason = later(reason, Stop.MASK);
				continue;
			}
			if(isTraced(lp.x))
			{
				reason = later(reason, Stop.SELF_INTERSECTION);
				continue;
			}
			final double dCost = dDist / dMinVox + LINK_ANGLE_WEIGHT * Math.acos(Math.min(1.0, dCos));
			if(log != null)
			{
				report(n, lp, String.format("cost %.2f", dCost));
			}
			if(dCost < dBestCost)
			{
				dBestCost = dCost;
				System.arraycopy(lp.x, 0, best.x, 0, 3);
				System.arraycopy(lp.t, 0, best.t, 0, 3);
				best.dSal = lp.dSal;
			}
		}
		if(dBestCost == Double.MAX_VALUE)
		{
			stopReason = bAnyInside ? reason : Stop.BORDER;
			return null;
		}
		if(LinAlgHelpers.dot(best.t, dir) < 0)
		{
			LinAlgHelpers.scale(best.t, -1.0, best.t);
		}
		System.arraycopy(best.x, 0, pos, 0, 3);
		System.arraycopy(best.t, 0, dir, 0, 3);
		addTracePoint(pos);
		return pos.clone();
	}

	void report(final long [] n, final LinePoint lp, final String sWhat)
	{
		if(log == null)
			return;
		if(lp == null)
			log.accept(String.format("  (%d,%d,%d) %s", n[0], n[1], n[2], sWhat));
		else
			log.accept(String.format("  (%d,%d,%d) x (%.2f,%.2f,%.2f) t (%.2f,%.2f,%.2f) sal %.3g: %s", n[0], n[1], n[2],
					lp.x[0], lp.x[1], lp.x[2], lp.t[0], lp.t[1], lp.t[2], lp.dSal, sWhat));
	}

	/** of two rejection reasons the one of the later test (the candidate got further) **/
	static Stop later(final Stop a, final Stop b)
	{
		return b.ordinal() > a.ordinal() ? b : a;
	}

	/** Steger's line point test of the voxel n (in the box): the line direction is the eigenvector of the
	 * smallest absolute eigenvalue of the normalised hessian (both other eigenvalues negative: a bright line);
	 * the maximum of the second order Taylor polynomial (normalised hessian and gradient, converted to voxel units)
	 * in the plane through n perpendicular to it must lie within LINE_POINT_TOL voxels of n per axis.
	 * The plane is perpendicular in voxel units, so its point on the line is the one closest to the voxel centre
	 * in the units of the test (in physical units, with anisotropic voxels the point of a steep line moves along
	 * the line and out of the voxel: with z voxels 3 times larger a helix climbing at 45 degrees stopped after 2 steps).
	 * The result goes to lp. **/
	public boolean linePoint(final long [] n, final LinePoint lp)
	{
		final double [] h = new double[6];
		final double [] g = new double[3];
		if(!box.hessianAtVoxel(n, h) || !box.gradientAtVoxel(n, g))
			return false;
		final double [] tn = new double[3];
		lp.dSal = SteerableLineFilter.saliency(h, tn);
		for (int d = 0; d < 3; d++)
		{
			lp.x[d] = Double.NaN;
		}
		if(!(lp.dSal > 0))
			return false;
		box.normToGridDir(tn, tn);
		System.arraycopy(gridToPhys(tn), 0, lp.t, 0, 3);
		//to voxel units
		final double [] sc = box.scale;
		h[0] /= sc[0] * sc[0];
		h[1] /= sc[0] * sc[1];
		h[2] /= sc[0] * sc[2];
		h[3] /= sc[1] * sc[1];
		h[4] /= sc[1] * sc[2];
		h[5] /= sc[2] * sc[2];
		for (int d = 0; d < 3; d++)
		{
			g[d] /= sc[d];
		}
		final double [] u = new double[3];
		final double [] v = new double[3];
		crossSectionBasis(tn, u, v);
		final double [] hu = new double[3];
		final double [] hv = new double[3];
		hessianTimes(h, u, hu);
		hessianTimes(h, v, hv);
		final double a11 = LinAlgHelpers.dot(u, hu);
		final double a12 = LinAlgHelpers.dot(u, hv);
		final double a22 = LinAlgHelpers.dot(v, hv);
		final double b1 = LinAlgHelpers.dot(u, g);
		final double b2 = LinAlgHelpers.dot(v, g);
		final double det = a11 * a22 - a12 * a12;
		if(!(det > 0) || a11 + a22 >= 0)
			return false;
		final double ca = (a12 * b2 - a22 * b1) / det;
		final double cb = (a12 * b1 - a11 * b2) / det;
		boolean bInside = true;
		for (int d = 0; d < 3; d++)
		{
			final double dOff = ca * u[d] + cb * v[d];
			if(Math.abs(dOff) > LINE_POINT_TOL)
				bInside = false;
			lp.x[d] = n[d] + dOff;
		}
		return bInside;
	}

	boolean isTraced(final double [] x)
	{
		final long [] c = new long[3];
		for (int d = 0; d < 3; d++)
		{
			c[d] = Math.round(x[d]);
		}
		final double dLim2 = (SELF_DIST * dMinVox) * (SELF_DIST * dMinVox);
		//(SELF_DIST is at most half of a voxel: neighbouring hash cells are enough)
		for (long dz = -1; dz <= 1; dz++)
			for (long dy = -1; dy <= 1; dy++)
				for (long dx = -1; dx <= 1; dx++)
				{
					final ArrayList<double[]> cell = traced.get( packKey(c[0] + dx, c[1] + dy, c[2] + dz) );
					if(cell == null)
						continue;
					for (final double [] p : cell)
					{
						double dd = 0;
						for (int d = 0; d < 3; d++)
						{
							final double r = (p[d] - x[d]) * voxelSize[d];
							dd += r * r;
						}
						if(dd < dLim2)
							return true;
					}
				}
		return false;
	}

	/** packs 3 coordinates (each within +-2^20) into one long **/
	static long packKey(final long x, final long y, final long z)
	{
		final long nOff = 1L << 20;
		final long nMask = (1L << 21) - 1;
		return (((x + nOff) & nMask) << 42) | (((y + nOff) & nMask) << 21) | ((z + nOff) & nMask);
	}

	//------------------ units

	/** direction in voxel units -> unit direction in physical space **/
	public double [] gridToPhys(final double [] v)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = v[d] * voxelSize[d];
		}
		LinAlgHelpers.normalize(out);
		return out;
	}

	/** direction in physical space -> unit direction in voxel units **/
	public double [] physToGrid(final double [] v)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = v[d] / voxelSize[d];
		}
		LinAlgHelpers.normalize(out);
		return out;
	}

	static void hessianTimes(final double [] h, final double [] x, final double [] out)
	{
		out[0] = h[0] * x[0] + h[1] * x[1] + h[2] * x[2];
		out[1] = h[1] * x[0] + h[3] * x[1] + h[4] * x[2];
		out[2] = h[2] * x[0] + h[4] * x[1] + h[5] * x[2];
	}

	/** two unit vectors u, v perpendicular to the unit vector t and to each other **/
	static void crossSectionBasis(final double [] t, final double [] u, final double [] v)
	{
		//the axis least aligned with t
		int nAxis = 0;
		for (int d = 1; d < 3; d++)
		{
			if(Math.abs(t[d]) < Math.abs(t[nAxis]))
				nAxis = d;
		}
		final double [] a = new double[3];
		a[nAxis] = 1.0;
		LinAlgHelpers.cross(t, a, u);
		LinAlgHelpers.normalize(u);
		LinAlgHelpers.cross(t, u, v);
		LinAlgHelpers.normalize(v);
	}
}
