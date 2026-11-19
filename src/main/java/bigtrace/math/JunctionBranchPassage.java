package bigtrace.math;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/** "Branch-first" junction passage of one-click tracing (OneClickTrace.bJunctionBranchFirst).
 * The elongated line filter map used by the coasting (JunctionHandler) follows the strongest response,
 * so at a shallow crossing (or filaments overlapping in z) the trace goes onto the brighter branch.
 * Here the branch is chosen by geometry, brightness is only a threshold:
 * 1) heading: quadratic fit of the trace's own recent path (dFitLengthSD), its tangent at the current point;
 * 2) target: cross-sections perpendicular to the straight extrapolation of the heading at
 *    dSearchStartSD, +dSearchStepSD, ... up to dSearchMaxSD; in each, local maxima of line saliency
 *    (junction box hessian) with saliency >= dRelSaliency x running saliency of the trace (OneClickTrace.dSalTrace),
 *    orientation within the directionality constrain of the heading and offset <= dTargetMaxOffsetSD tracing SDs
 *    (Mahalanobis, per axis) from the extrapolated point are candidates. The first cross-section with candidates gives
 *    the target: the lowest cost = offset (SDs) + dTargetAngleWeight x (1 - |cos| orientation vs heading);
 * 3) passage: cubic Hermite curve from the current point (heading) to the target (its orientation), followed
 *    step by step: in each cross-section the saliency maximum closest to the curve (within dFollowSD SDs) is taken,
 *    with none the step is "blind" on the curve; more than dMaxBlindSD of blind steps in a row: passage lost.
 * Without enough trace history (NOFIT) or a target (NOTARGET) the map coasting is used.
 * Geometry is done in physical space; lengths are in units of the largest tracing SD in grid voxels times
 * the smallest grid voxel size (as the coasting distance of JunctionHandler), offsets per axis in tracing SDs. **/
class JunctionBranchPassage < T extends RealType< T > & NativeType< T > >
{
	final JunctionHandler<T> jh;
	final OneClickTrace<T> oc;

	/** length of the trace history for the heading fit (in tracing SDs) **/
	double dFitLengthSD = 8.0;

	/** the target is searched in cross-sections from this distance (in tracing SDs) ... **/
	double dSearchStartSD = 6.0;

	/** ... every dSearchStepSD ... **/
	double dSearchStepSD = 1.0;

	/** ... up to this distance (limited by the junction box) **/
	double dSearchMaxSD = 12.0;

	/** maximum offset of the target from the extrapolated heading (tracing SDs, per axis) **/
	double dTargetMaxOffsetSD = 4.5;

	/** weight of the orientation difference in the target cost **/
	double dTargetAngleWeight = 4.0;

	/** minimum saliency of candidates, fraction of the running saliency of the trace **/
	double dRelSaliency = 0.3;

	/** while following the passage curve: maximum offset of the ridge from it (tracing SDs, per axis) **/
	double dFollowSD = 1.5;

	/** maximum length of blind steps in a row (tracing SDs) **/
	double dMaxBlindSD = 5.0;

	/** minimum number of trace points for the heading fit **/
	int nMinFitPoints = 4;

	/** junction box center ahead of the current point (tracing SDs), the box covers the search **/
	double dBoxAheadSD = 4.0;

	/** results of plan() **/
	static final int GEO_OK = 0, GEO_NOFIT = 1, GEO_NOTARGET = 2, GEO_LOST = 3;

	//---------- geometry of the current plan (physical space)

	/** grid voxel size and tracing SD per axis (physical) **/
	final double [] vox = new double[3];
	final double [] sd = new double[3];

	/** length unit (one tracing SD along the path) and step length **/
	double dLenSD = 1.0;
	double dStep = 1.0;

	/** current point and the fitted heading **/
	final double [] p0 = new double[3];
	final double [] t0 = new double[3];

	/** target point and its (oriented) line direction **/
	final double [] p1 = new double[3];
	final double [] t1 = new double[3];

	/** distance of the cross-section where the target was found (or the last one searched) **/
	double dTargetDist = 0;

	/** dense passage curve and its arc length **/
	double [][] curve = null;
	double [] curveS = null;

	/** tangent at the end of the followed passage **/
	final double [] tEnd = new double[3];

	JunctionBranchPassage(final JunctionHandler<T> jh_)
	{
		jh = jh_;
		oc = jh_.oc;
	}

	/** Plans and follows the passage from currpoint, its points (full resolution) are added to outPath.
	 * Returns GEO_OK (heading of OneClickTrace set to the passage end), GEO_NOFIT/GEO_NOTARGET
	 * (nothing done, use the map coasting) or GEO_LOST (no passage). **/
	int plan(final RealPoint currpoint, final RandomAccess< T > raInput, final ArrayList<RealPoint> outPath)
	{
		double dMinVox = Double.MAX_VALUE;
		for (int d = 0; d < 3; d++)
		{
			vox[d] = oc.gridVoxelSize[d];
			sd[d] = oc.sigmaGridV[d] * vox[d];
			dMinVox = Math.min(dMinVox, vox[d]);
		}
		dLenSD = jh.dMaxSigmaGrid * dMinVox;
		dStep = dMinVox;
		curve = null;

		//1) heading from the trace's own path
		if(!fitHeading(currpoint))
		{
			jh.visDebugMessage("branch-first: short trace, map coasting");
			return GEO_NOFIT;
		}
		final double dOwnSal = oc.dSalTrace;
		if(!(dOwnSal > 0))
		{
			return GEO_NOFIT;
		}
		final double dMinSal = dRelSaliency * dOwnSal;

		//junction box ahead (covers the search for the target)
		final long [] center = new long[3];
		for (int d = 0; d < 3; d++)
		{
			center[d] = Math.round((p0[d] + dBoxAheadSD * dLenSD * t0[d]) / vox[d]);
		}
		jh.computeJunctionHessian(center);
		if(jh.isVisDebug())
		{
			oc.bt.ocVisDebug.showJunctionMap(jh.box.getSaliencyForDisplay(oc.ds), "junction saliency");
		}

		//2) target
		if(!findTarget(dMinSal))
		{
			jh.visDebugMessage("branch-first: no branch found, map coasting");
			visDebugPlan(false);
			return GEO_NOTARGET;
		}
		buildCurve();
		jh.visDebugMessage("branch-first: branch chosen");
		visDebugPlan(true);

		//3) follow the curve
		final double dCurveEnd = curveS[curveS.length - 1];
		final double [] p = new double[3];
		final double [] t = new double[3];
		final double [] q = new double[3];
		double s = 0;
		double dBlind = 0;
		boolean bLastOk = true;
		while(true)
		{
			s += dStep;
			if(s > dCurveEnd && bLastOk)
				break;
			//past the end: a bit further to find the ridge again
			if(s > dCurveEnd + dLenSD)
			{
				jh.visDebugMessage("branch-first: passage lost");
				return GEO_LOST;
			}
			curvePoint(s, p, t);
			if(nearestRidge(p, t, dFollowSD, dMinSal, q))
			{
				bLastOk = true;
				dBlind = 0;
			}
			else
			{
				System.arraycopy(p, 0, q, 0, 3);
				bLastOk = false;
				dBlind += dStep;
				if(dBlind > dMaxBlindSD * dLenSD)
				{
					jh.visDebugMessage("branch-first: passage lost");
					return GEO_LOST;
				}
			}
			final double [] outPos = new double[3];
			final long [] posFull = new long[3];
			for (int d = 0; d < 3; d++)
			{
				final double g = q[d] / vox[d];
				if(Math.round(g) < oc.gridMin[d] || Math.round(g) > oc.gridMax[d])
				{
					return GEO_LOST;
				}
				outPos[d] = g * oc.ds[d] + 0.5 * (oc.ds[d] - 1);
				posFull[d] = Math.round(outPos[d]);
			}
			if(oc.bt.btData.bOCIntensityStop && raInput.setPositionAndGet( posFull ).getRealDouble() < oc.bt.btData.dOCIntensityThreshold)
			{
				return GEO_LOST;
			}
			outPath.add(new RealPoint(outPos));
			System.arraycopy(t, 0, tEnd, 0, 3);
		}
		if(outPath.isEmpty())
			return GEO_LOST;
		//heading after the passage
		System.arraycopy(tEnd, 0, oc.memDirectionVector, 0, 3);
		System.arraycopy(tEnd, 0, oc.lastDirectionVector, 0, 3);
		final double [] tGrid = oc.physToGridDir(tEnd);
		System.arraycopy(tGrid, 0, jh.coastLineDir, 0, 3);
		System.arraycopy(tGrid, 0, jh.coastDir, 0, 3);
		jh.bJunctionArmed = false;
		return GEO_OK;
	}

	/** heading: quadratic fit X(s) = a + b s + c s^2 of the trace history (physical) over the last
	 * dFitLengthSD of arc length (s <= 0 at the current point), tangent b at the current point **/
	boolean fitHeading(final RealPoint currpoint)
	{
		for (int d = 0; d < 3; d++)
		{
			p0[d] = oc.toGridDouble(currpoint.getDoublePosition(d), d) * vox[d];
		}
		final ArrayDeque<double[]> hist = oc.dirHistory;
		if(hist.size() < nMinFitPoints)
			return false;
		final double dMaxLen = dFitLengthSD * dLenSD;
		final ArrayList<double[]> pts = new ArrayList<>();
		final ArrayList<Double> sArr = new ArrayList<>();
		double s = 0;
		double [] prev = null;
		final Iterator<double[]> it = hist.descendingIterator();
		while(it.hasNext())
		{
			final double [] full = it.next();
			final double [] x = new double[3];
			for (int d = 0; d < 3; d++)
			{
				x[d] = oc.toGridDouble(full[d], d) * vox[d];
			}
			if(prev != null)
			{
				double dl = 0;
				for (int d = 0; d < 3; d++)
				{
					dl += (x[d] - prev[d]) * (x[d] - prev[d]);
				}
				s -= Math.sqrt(dl);
			}
			if(-s > dMaxLen)
				break;
			pts.add(x);
			sArr.add(s);
			prev = x;
		}
		final int n = pts.size();
		if(n < nMinFitPoints || -s < 2.0 * dStep)
			return false;
		//normal equations of the quadratic fit (shared matrix for the 3 coordinates)
		final double [][] A = new double[3][3];
		final double [][] B = new double[3][3];
		for (int i = 0; i < n; i++)
		{
			final double si = sArr.get(i);
			final double [] pw = new double [] {1.0, si, si * si};
			for (int r = 0; r < 3; r++)
			{
				for (int c = 0; c < 3; c++)
				{
					A[r][c] += pw[r] * pw[c];
				}
				for (int d = 0; d < 3; d++)
				{
					B[r][d] += pw[r] * pts.get(i)[d];
				}
			}
		}
		final double [][] coef = solve3(A, B);
		if(coef == null)
			return false;
		//tangent at s = 0 is the coefficient b
		for (int d = 0; d < 3; d++)
		{
			t0[d] = coef[1][d];
		}
		final double len = Math.sqrt(SteerableLineFilter.dot(t0, t0));
		if(!(len > 0))
			return false;
		for (int d = 0; d < 3; d++)
		{
			t0[d] /= len;
		}
		//forward (along the momentum)
		if(SteerableLineFilter.dot(t0, oc.memDirectionVector) < 0)
		{
			for (int d = 0; d < 3; d++)
			{
				t0[d] = -t0[d];
			}
		}
		return true;
	}

	/** solves A X = B (3x3, B with 3 columns) by Gaussian elimination with pivoting, null if singular **/
	static double [][] solve3(final double [][] Ain, final double [][] Bin)
	{
		final double [][] A = new double[3][];
		final double [][] B = new double[3][];
		for (int r = 0; r < 3; r++)
		{
			A[r] = Ain[r].clone();
			B[r] = Bin[r].clone();
		}
		for (int c = 0; c < 3; c++)
		{
			int piv = c;
			for (int r = c + 1; r < 3; r++)
			{
				if(Math.abs(A[r][c]) > Math.abs(A[piv][c]))
					piv = r;
			}
			if(Math.abs(A[piv][c]) < 1e-300)
				return null;
			double [] tmp = A[c]; A[c] = A[piv]; A[piv] = tmp;
			tmp = B[c]; B[c] = B[piv]; B[piv] = tmp;
			for (int r = 0; r < 3; r++)
			{
				if(r == c)
					continue;
				final double f = A[r][c] / A[c][c];
				for (int k = 0; k < 3; k++)
				{
					A[r][k] -= f * A[c][k];
					B[r][k] -= f * B[c][k];
				}
			}
		}
		final double [][] X = new double[3][3];
		for (int r = 0; r < 3; r++)
		{
			for (int k = 0; k < 3; k++)
			{
				X[r][k] = B[r][k] / A[r][r];
			}
		}
		final double dCond = Math.abs(A[0][0]) + Math.abs(A[1][1]) + Math.abs(A[2][2]);
		for (int r = 0; r < 3; r++)
		{
			if(Math.abs(A[r][r]) < 1e-12 * dCond)
				return null;
		}
		return X;
	}

	/** target: first cross-section (perpendicular to the straight heading) with candidates, lowest cost **/
	boolean findTarget(final double dMinSal)
	{
		final double [] u = new double[3];
		final double [] v = new double[3];
		SteerableLineFilter.crossSectionBasis(t0, u, v);
		final double [] c0 = new double[3];
		for (double dDist = dSearchStartSD; dDist <= dSearchMaxSD + 1e-6; dDist += dSearchStepSD)
		{
			for (int d = 0; d < 3; d++)
			{
				c0[d] = p0[d] + dDist * dLenSD * t0[d];
			}
			dTargetDist = dDist * dLenSD;
			if(!insideBox(c0))
				break;
			final ArrayList<double[]> cands = ridgesInSection(c0, t0, u, v, dTargetMaxOffsetSD, dMinSal);
			double dBestCost = Double.MAX_VALUE;
			for(final double [] c : cands)
			{
				//c: position (3), offset (1), |cos| (1), oriented direction (3)
				final double cost = c[3] + dTargetAngleWeight * (1.0 - c[4]);
				if(cost < dBestCost)
				{
					dBestCost = cost;
					for (int d = 0; d < 3; d++)
					{
						p1[d] = c[d];
						t1[d] = c[5 + d];
					}
				}
			}
			if(dBestCost < Double.MAX_VALUE)
				return true;
		}
		return false;
	}

	/** true if the physical point is inside the junction box **/
	boolean insideBox(final double [] x)
	{
		for (int d = 0; d < 3; d++)
		{
			final double g = x[d] / vox[d];
			if(g < jh.box.boxMin[d] || g > jh.box.boxMin[d] + jh.box.nDim - 1)
				return false;
		}
		return true;
	}

	/** Local maxima of line saliency in the cross-section through c0 perpendicular to t (basis u, v),
	 * within dMaxOffsetSD tracing SDs (per axis) of c0, saliency >= dMinSal, line orientation within
	 * the directionality constrain of t. Each result: position (3, physical), offset in SDs, |cos|,
	 * line direction oriented along t (3, physical) **/
	ArrayList<double[]> ridgesInSection(final double [] c0, final double [] t, final double [] u, final double [] v,
			final double dMaxOffsetSD, final double dMinSal)
	{
		final ArrayList<double[]> out = new ArrayList<>();
		double dMaxSD = 0;
		for (int d = 0; d < 3; d++)
		{
			dMaxSD = Math.max(dMaxSD, sd[d]);
		}
		final double h = 0.5 * dStep;
		final int n = (int) Math.ceil((dMaxOffsetSD + 0.5) * dMaxSD / h);
		final int nW = 2 * n + 1;
		final double [][] sal = new double[nW][nW];
		final double [][] off = new double[nW][nW];
		final double [] x = new double[3];
		final double [] xg = new double[3];
		for (int i = 0; i < nW; i++)
		{
			for (int j = 0; j < nW; j++)
			{
				final double a = (i - n) * h;
				final double b = (j - n) * h;
				double dOff = 0;
				for (int d = 0; d < 3; d++)
				{
					x[d] = c0[d] + a * u[d] + b * v[d];
					final double r = (x[d] - c0[d]) / sd[d];
					dOff += r * r;
					xg[d] = x[d] / vox[d];
				}
				off[i][j] = Math.sqrt(dOff);
				sal[i][j] = Double.NEGATIVE_INFINITY;
				//(a ring of samples beyond the limit for the local maximum test)
				if(off[i][j] > dMaxOffsetSD + 2.0 * h / dMaxSD + 0.5)
					continue;
				final double val = jh.box.hessBox.saliencyAt(xg, null);
				if(!Double.isNaN(val))
					sal[i][j] = val;
			}
		}
		final double [] vecGrid = new double[3];
		for (int i = 1; i < nW - 1; i++)
		{
			for (int j = 1; j < nW - 1; j++)
			{
				final double val = sal[i][j];
				if(!(val >= dMinSal) || val <= 0 || off[i][j] > dMaxOffsetSD)
					continue;
				boolean bMax = true;
				for (int di = -1; di <= 1 && bMax; di++)
				{
					for (int dj = -1; dj <= 1; dj++)
					{
						if((di != 0 || dj != 0) && sal[i + di][j + dj] > val)
						{
							bMax = false;
							break;
						}
					}
				}
				if(!bMax)
					continue;
				final double a = (i - n) * h;
				final double b = (j - n) * h;
				for (int d = 0; d < 3; d++)
				{
					x[d] = c0[d] + a * u[d] + b * v[d];
					xg[d] = x[d] / vox[d];
				}
				jh.box.hessBox.saliencyAt(xg, vecGrid);
				final double [] dir = oc.gridToPhysDir(vecGrid);
				double cs = SteerableLineFilter.dot(dir, t);
				if(cs < 0)
				{
					cs = -cs;
					for (int d = 0; d < 3; d++)
					{
						dir[d] = -dir[d];
					}
				}
				if(cs < oc.dAngleThreshold)
					continue;
				out.add(new double [] {x[0], x[1], x[2], off[i][j], cs, dir[0], dir[1], dir[2]});
			}
		}
		return out;
	}

	/** nearest (offset in SDs) ridge in the cross-section at p perpendicular to t, within dMaxOffsetSD;
	 * its position to q, false if there is none **/
	boolean nearestRidge(final double [] p, final double [] t, final double dMaxOffsetSD, final double dMinSal, final double [] q)
	{
		final double [] u = new double[3];
		final double [] v = new double[3];
		SteerableLineFilter.crossSectionBasis(t, u, v);
		final ArrayList<double[]> cands = ridgesInSection(p, t, u, v, dMaxOffsetSD, dMinSal);
		double dBest = Double.MAX_VALUE;
		for(final double [] c : cands)
		{
			if(c[3] < dBest)
			{
				dBest = c[3];
				System.arraycopy(c, 0, q, 0, 3);
			}
		}
		return dBest < Double.MAX_VALUE;
	}

	/** cubic Hermite curve from (p0, t0) to (p1, t1), tangent lengths = chord, dense with arc length **/
	void buildCurve()
	{
		double L = 0;
		for (int d = 0; d < 3; d++)
		{
			L += (p1[d] - p0[d]) * (p1[d] - p0[d]);
		}
		L = Math.sqrt(L);
		final int n = Math.max(2, (int) Math.ceil(4.0 * L / dStep)) + 1;
		curve = new double[n][3];
		curveS = new double[n];
		for (int k = 0; k < n; k++)
		{
			final double s = k / (double) (n - 1);
			final double h00 = 2 * s * s * s - 3 * s * s + 1;
			final double h10 = s * s * s - 2 * s * s + s;
			final double h01 = -2 * s * s * s + 3 * s * s;
			final double h11 = s * s * s - s * s;
			for (int d = 0; d < 3; d++)
			{
				curve[k][d] = h00 * p0[d] + h10 * L * t0[d] + h01 * p1[d] + h11 * L * t1[d];
			}
			if(k > 0)
			{
				double dl = 0;
				for (int d = 0; d < 3; d++)
				{
					dl += (curve[k][d] - curve[k - 1][d]) * (curve[k][d] - curve[k - 1][d]);
				}
				curveS[k] = curveS[k - 1] + Math.sqrt(dl);
			}
		}
	}

	/** point and unit tangent of the curve at arc length s (straight on along t1 past its end) **/
	void curvePoint(final double s, final double [] p, final double [] t)
	{
		final int n = curveS.length;
		if(s >= curveS[n - 1])
		{
			for (int d = 0; d < 3; d++)
			{
				p[d] = curve[n - 1][d] + t1[d] * (s - curveS[n - 1]);
				t[d] = t1[d];
			}
			return;
		}
		int k = 1;
		while(k < n - 1 && curveS[k] < s)
			k++;
		final double f = (s - curveS[k - 1]) / Math.max(curveS[k] - curveS[k - 1], 1e-12);
		for (int d = 0; d < 3; d++)
		{
			p[d] = curve[k - 1][d] + f * (curve[k][d] - curve[k - 1][d]);
			t[d] = curve[k][d] - curve[k - 1][d];
		}
		SteerableLineFilter.normalize(t);
	}

	/** physical -> full resolution voxel coordinates **/
	double [] physToFull(final double [] x)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = (x[d] / vox[d]) * oc.ds[d] + 0.5 * (oc.ds[d] - 1);
		}
		return out;
	}

	/** debug visualization: extrapolated heading (up to the target cross-section), target with its direction,
	 * passage curve (bTarget false: only the heading up to the last searched cross-section) **/
	void visDebugPlan(final boolean bTarget)
	{
		if(!jh.isVisDebug())
			return;
		final double [] hEnd = new double[3];
		for (int d = 0; d < 3; d++)
		{
			hEnd[d] = p0[d] + dTargetDist * t0[d];
		}
		ArrayList<double[]> curveFull = null;
		double [] tgtA = null;
		double [] tgtB = null;
		if(bTarget)
		{
			curveFull = new ArrayList<>();
			//every ~half SD along the curve
			final int nStride = Math.max(1, (int) Math.round(0.5 * dLenSD / Math.max(curveS[1] - curveS[0], 1e-12)));
			for (int k = 0; k < curve.length; k += nStride)
			{
				curveFull.add(physToFull(curve[k]));
			}
			curveFull.add(physToFull(curve[curve.length - 1]));
			final double [] a = new double[3];
			final double [] b = new double[3];
			for (int d = 0; d < 3; d++)
			{
				a[d] = p1[d] - dLenSD * t1[d];
				b[d] = p1[d] + dLenSD * t1[d];
			}
			tgtA = physToFull(a);
			tgtB = physToFull(b);
		}
		oc.bt.ocVisDebug.showBranchPlan(physToFull(p0), physToFull(hEnd), curveFull, tgtA, tgtB);
	}
}
