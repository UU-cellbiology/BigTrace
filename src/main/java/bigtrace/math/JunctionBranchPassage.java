package bigtrace.math;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;

import bigtrace.geometry.Pipe3D;
import bigtrace.volume.ConeCursor;

/** "Branch-first" junction passage of one-click tracing (OneClickTrace.bJunctionBranchFirst).
 * The elongated line filter map used by the coasting (JunctionHandler) follows the strongest response,
 * so at a shallow crossing (or filaments overlapping in z) the trace goes onto the brighter branch.
 * Here the branch is chosen by geometry, brightness is only a threshold:
 * 1) heading: slope of a straight line fit of the trace's own recent path (dFitLengthSD), blended with
 *    the smoothed (momentum) direction of the trace (dHeadingMomentumWeight);
 * 2) target: cross-sections perpendicular to the straight extrapolation of the heading at
 *    dSearchStartSD, +dSearchStepSD, ... up to dSearchMaxSD; in each, local maxima of line saliency
 *    (junction box hessian) with saliency >= dRelSaliency x running saliency of the trace (OneClickTrace.dSalTrace),
 *    orientation within dCandidateMinCos of the heading (looser than the directionality constrain: a turning filament
 *    crosses the cross-section obliquely) and offset <= dTargetMaxOffsetSD tracing SDs
 *    (Mahalanobis, per axis) from the extrapolated point are candidates (in the first cross-section with any;
 *    if there is none up to dSearchMaxSD, going back from dSearchStartSD towards dSearchMinSD);
 * 3) a cubic Hermite curve from the current point (heading) to each candidate is planned; its end direction 
 *    is fitted along the candidate's ridge (followed dT1FitSD each way, ridgeDirection), not the eigenvector at 
 *    the candidate alone (biased near the junction or a gap end),
 *    the target is the candidate whose curve turns the least in total (sum of the turning angles along it),
 *    e.g. a parallel filament nearby needs an S-shaped curve and turns more than the traced one;
 * 4) passage: the curve to the target is followed
 *    step by step: in each cross-section the saliency maximum (within dFollowSD SDs of the curve, orientation within
 *    dFollowMinCos of the curve) closest to the curve
 *    shifted by the current offset of the followed ridge from it (continuity: it does not switch between maxima)
 *    is taken; only points moving forward, at least dMinPointSpacing apart, are added;
 *    with none the step is "blind" on the curve; more than dBlindFraction of the curve length (at least dMinBlindSD)
 *    of blind steps in a row: passage lost, the curve to the next candidate (in the order of turning, if it turns
 *    at most dRetryMaxExtraTurnDeg more) is followed;
 *    all lost: the trace stops (JunctionHandler.bStopOnLostPassage).
 * Without enough trace history (NOFIT) or a target (NOTARGET) the map coasting is used.
 * After the map coasting, planToCoastEnd() refines its end point (nearest ridge centre in its 3D vicinity, dRefineSD)
 * and the passage to it is planned and followed the same way (3-4): the map coasting tends to cut bends
 * and end beside the filament.
 * Geometry is done in physical space; lengths are in units of the largest tracing SD in grid voxels times
 * the smallest grid voxel size (as the coasting distance of JunctionHandler), offsets per axis in tracing SDs. **/
class JunctionBranchPassage < T extends RealType< T > & NativeType< T > >
{
	final JunctionHandler<T> jh;
	final OneClickTrace<T> oc;

	/** length of the trace history for the heading fit (in tracing SDs) **/
	double dFitLengthSD = 6.0;

	/** weight of the smoothed (momentum) direction in the heading (0 = only the fit, 1 = only the momentum) **/
	double dHeadingMomentumWeight = 0.5;

	/** maximum number of candidates with a planned curve (the lowest cost ones) **/
	int nMaxPlanned = 8;

	/** end of the map coasting: radius of the vicinity (ellipsoid, tracing SDs per axis) searched for the ridge **/
	double dRefineSD = 3.0;

	/** end of the map coasting: minimum |cos| between the ridge direction and the passage chord **/
	double dRefineMinCos = 0.5;

	/** end of the map coasting: the ridge must be ahead, its projection on the passage chord at least
	 * this fraction of the chord length **/
	double dRefineMinAhead = 0.5;

	/** the target is searched in cross-sections from this distance (in tracing SDs) ... **/
	double dSearchStartSD = 9.0;

	/** ... every dSearchStepSD ... **/
	double dSearchStepSD = 1.0;

	/** ... up to this distance (limited by the junction box); if none of these has candidates,
	 * the cross-sections closer than dSearchStartSD are searched, going back down to dSearchMinSD 
	 * (a filament turning at the junction leaves the cross-sections far ahead) **/
	double dSearchMaxSD = 12.0;

	/** closest cross-section searched (in tracing SDs), see dSearchMaxSD **/
	double dSearchMinSD = 2.0;

	/** maximum offset of the target from the extrapolated heading (tracing SDs, per axis) **/
	double dTargetMaxOffsetSD = 4.5;

	/** candidates: minimum |cos| between their line direction and the heading (60 deg); the least turning
	 * curve decides among them. Following the curve uses the directionality constrain vs the curve direction **/
	double dCandidateMinCos = 0.5;

	/** weight of the orientation difference in the candidate cost (order of the candidates to plan) **/
	double dTargetAngleWeight = 4.0;

	/** minimum saliency of candidates, fraction of the running saliency of the trace **/
	double dRelSaliency = 0.3;

	/** while following the passage curve: maximum offset of the ridge from it (tracing SDs, per axis) **/
	double dFollowSD = 1.5;

	/** while following the passage curve (and the target's ridge for t1): minimum |cos| between the ridge 
	 * direction and the curve direction (looser than the directionality constrain: the filament can bend
	 * more sharply than the curve, e.g. a short S-bend right after the junction; the continuity of the offset 
	 * keeps it on the same ridge) **/
	double dFollowMinCos = 0.5;

	/** minimum distance between successive passage points (full resolution voxels) **/
	double dMinPointSpacing = 0.5;

	/** (debug visualization) bending cone: the filament can turn by the directionality constrain angle 
	 * every dBendStepSD tracing SDs **/
	double dBendStepSD = 1.0;

	/** (debug visualization) bending cone: maximum total turn of the maximally bending path (degrees) **/
	double dMaxTotalBendDeg = 90.0;

	/** (debug visualization) number of maximally bending paths shown (rotated around the heading) **/
	int nBendPathsShown = 4;

	/** cone search: candidates closer than this (tracing SDs, per axis) to a stronger one are merged
	 * (a filament crosses the cone surface over several voxels) **/
	double dCandidateMergeSD = 1.5;

	/** end direction of the passage curve (t1): the target's ridge is followed this far each way (tracing SDs)
	 * and the direction is fitted to it (the eigenvector at a single point is biased near a junction or a gap);
	 * 0 = the eigenvector at the target **/
	double dT1FitSD = 1.5;

	/** while following the target's ridge for t1: maximum offset from the predicted point (tracing SDs, per axis) **/
	double dT1FollowSD = 1.0;

	/** (debug) change of t1 of the chosen target by the ridge fit (degrees) **/
	double dT1FitDeg = 0;

	/** bending cone of the last computeBendCone (physical): apex (end of the straight heading), 
	 * vector from the apex to the base center, full opening angle (0 = no bending), 
	 * axial and radial position of the maximally bent end relative to p0 **/
	final double [] coneApex = new double[3];
	final double [] coneAxis = new double[3];
	double coneFullAngle = 0;
	double coneAxial = 0;
	double coneRadial = 0;

	/** the candidates were searched on the bending cone (BRANCH_SEARCH_CONE or BOTH), for the debug visualization **/
	boolean bConeSearch = false;

	/** length of the bending cone where the candidates were searched (physical), for the debug visualization **/
	double dConeDist = 0;

	/** junction passage: maximum length of blind steps in a row, fraction of the length of the passage curve
	 * (passages that found their branch went at most ~0.2 of the curve blind; a wrong one 0.4) ... **/
	double dBlindFraction = 0.3;

	/** ... but at least this (tracing SDs: the junction itself may have no clean ridge in the cross-sections).
	 * Gap bridging: the reach of the bridge (blind steps over the gap are its purpose) **/
	double dMinBlindSD = 1.0;

	/** a planned passage (chooseLeastTurning): target, its end direction, curve, total turning and the change
	 * of the end direction by the ridge fit (degrees) **/
	static class Plan
	{
		double [] p1;
		double [] t1;
		double [][] curve;
		double [] curveS;
		double dTurnDeg;
		double dFitDeg;
	}

	/** a lost passage: the next candidates are tried only if their curve turns at most this much more (degrees)
	 * than the least turning one (otherwise it is a different path, e.g. onto another filament: the trace stops) **/
	double dRetryMaxExtraTurnDeg = 30.0;

	/** passages planned to the candidates, the least turning first (the next ones are tried if it is lost) **/
	final ArrayList<Plan> plans = new ArrayList<>();

	/** minimum number of trace points for the heading fit **/
	int nMinFitPoints = 4;

	/** junction box center ahead of the current point (tracing SDs), the box covers the search **/
	double dBoxAheadSD = 4.0;

	/** ridge centre test (isRidgeCentre): distance of the compared points in the plane perpendicular to the line
	 * (in smallest voxels) **/
	double dRidgeTestStep = 1.0;

	/** gap bridging (planGap): candidates at least this far ahead (tracing SDs) of the most advanced 
	 * recent trace point (along the heading) **/
	double dGapMinAheadSD = 1.0;

	/** gap bridging (planGap): the last part of the trace (tracing SDs, path length) left out of the heading fit:
	 * before a gap the stepping often drifts to the side, off the end of the filament **/
	double dGapTailSD = 1.5;

	/** gap bridging (planGap): weight of the momentum in the heading (the momentum follows the drift at the end) **/
	double dGapHeadingMomentumWeight = 0.0;

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

	/** total turning of the chosen curve (degrees) **/
	double dChosenTurnDeg = 0;

	/** (debug visualization) curves planned to the other candidates and their total turning (degrees) **/
	final ArrayList<double[][]> altCurves = new ArrayList<>();
	final ArrayList<Double> altTurnDeg = new ArrayList<>();

	/** (debug visualization) end point of the map coasting (physical), null if not refined **/
	double [] coastEnd = null;

	//---------- debug visualization: the cross-section of the target (or the last one searched)

	/** center of the cross-section (physical), null if none was searched **/
	double [] secCenter = null;

	/** searched radius in the cross-section (tracing SDs, per axis) **/
	double secRadiusSD = 0;

	/** basis of the cross-section plane **/
	final double [] secU = new double[3];
	final double [] secV = new double[3];

	/** accepted candidates (positions, physical) in the cross-section **/
	final ArrayList<double[]> secCandidates = new ArrayList<>();

	/** saliency maxima in the cross-section rejected by saliency or orientation (positions, physical) **/
	final ArrayList<double[]> secRejected = new ArrayList<>();

	JunctionBranchPassage(final JunctionHandler<T> jh_)
	{
		jh = jh_;
		oc = jh_.oc;
	}

	/** grid voxel size, SDs, length unit and step for the current tracing parameters, clears the plan **/
	void setupGeometry()
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
		curveS = null;
		altCurves.clear();
		altTurnDeg.clear();
		coastEnd = null;
		secCenter = null;
		secCandidates.clear();
		secRejected.clear();
	}

	/** Plans and follows the passage from currpoint, its points (full resolution) are added to outPath.
	 * Returns GEO_OK (heading of OneClickTrace set to the passage end), GEO_NOFIT/GEO_NOTARGET 
	 * (nothing done, use the map coasting) or GEO_LOST (no passage). **/
	int plan(final RealPoint currpoint, final RandomAccess< T > raInput, final ArrayList<RealPoint> outPath)
	{
		setupGeometry();

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

		//2) candidates
		final ArrayList<double[]> cands = findCandidates(dMinSal);
		if(cands.isEmpty())
		{
			jh.visDebugMessage("branch-first: no branch found, map coasting");
			visDebugPlan(false);
			return GEO_NOTARGET;
		}
		//3) curves to the candidates, the least turning one
		chooseLeastTurning(cands, dMinSal);
		final String sMode = (OneClickTrace.BRANCH_SEARCH_MODE == OneClickTrace.BRANCH_SEARCH_SPHERE) ? 
				String.format("half sphere %.0f SD", secRadiusSD) :
				(OneClickTrace.BRANCH_SEARCH_MODE == OneClickTrace.BRANCH_SEARCH_BOTH) ? 
				String.format("cone %.0f SD + section %.0f SD", dConeDist / dLenSD, dTargetDist / dLenSD) 
				: (bConeSearch ? String.format("cone %.0f SD", dConeDist / dLenSD) : String.format("section %.0f SD", dTargetDist / dLenSD));
		jh.visDebugMessage(String.format("branch-first (%s): %d candidate(s), chosen turns %.0f deg, end direction fit %.0f deg", 
				sMode, altCurves.size() + 1, dChosenTurnDeg, dT1FitDeg));
		visDebugPlan(true);

		//4) follow the curve (if lost, the next ones)
		return followPlans(raInput, outPath, dMinSal, "branch-first", 0.0);
	}

	/** After the map coasting (ending at endFull, full resolution, with direction endDirGrid, grid space):
	 * the end is refined to the nearest ridge centre in its vicinity (ridgesInVicinity), then the passage from 
	 * currpoint to it is planned and followed as in plan(). Points (full resolution) are added to outPath.
	 * Returns GEO_OK (heading of OneClickTrace set to the passage end), GEO_NOTARGET (no ridge at the end,
	 * nothing done) or GEO_LOST (the curve could not be followed, nothing changed in OneClickTrace). **/
	int planToCoastEnd(final RealPoint currpoint, final double [] endFull, final double [] endDirGrid, 
			final RandomAccess< T > raInput, final ArrayList<RealPoint> outPath)
	{
		setupGeometry();
		bConeSearch = false;
		//heading: fit, or the momentum if the trace is too short
		if(!fitHeading(currpoint))
		{
			System.arraycopy(oc.memDirectionVector, 0, t0, 0, 3);
			LinAlgHelpers.normalize(t0);
		}
		final double dOwnSal = oc.dSalTrace;
		if(!(dOwnSal > 0))
		{
			return GEO_NOTARGET;
		}
		final double dMinSal = dRelSaliency * dOwnSal;
		final double [] endPhys = new double[3];
		for (int d = 0; d < 3; d++)
		{
			endPhys[d] = oc.toGridDouble(endFull[d], d) * vox[d];
		}
		final double [] endDir = oc.gridToPhysDir(endDirGrid);
		coastEnd = endPhys.clone();
		//junction box between the current point and the end (closer to the end: covers the vicinity search)
		final long [] center = new long[3];
		double dDist = 0;
		for (int d = 0; d < 3; d++)
		{
			center[d] = Math.round((p0[d] + 0.6 * (endPhys[d] - p0[d])) / vox[d]);
			dDist += (endPhys[d] - p0[d]) * (endPhys[d] - p0[d]);
		}
		dTargetDist = Math.sqrt(dDist);
		jh.computeJunctionHessian(center);

		//chord of the passage (ahead direction), the coasting direction if degenerate
		final double [] chord = new double[3];
		for (int d = 0; d < 3; d++)
		{
			chord[d] = endPhys[d] - p0[d];
		}
		if(!(LinAlgHelpers.dot(chord, chord) > 0))
		{
			System.arraycopy(endDir, 0, chord, 0, 3);
		}
		LinAlgHelpers.normalize(chord);

		//ridge centres in the vicinity of the end
		final ArrayList<double[]> rejected = new ArrayList<>();
		final ArrayList<double[]> cands = ridgesInVicinity(endPhys, dRefineSD, dMinSal, chord, dRefineMinAhead * dTargetDist, dRefineMinCos, rejected);
		//(debug visualization: middle cross-section of the searched ellipsoid)
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(chord, u, v);
		secCenter = endPhys.clone();
		secRadiusSD = dRefineSD;
		System.arraycopy(u, 0, secU, 0, 3);
		System.arraycopy(v, 0, secV, 0, 3);
		for(final double [] c : cands)
		{
			secCandidates.add(new double [] {c[0], c[1], c[2]});
		}
		secRejected.addAll(rejected);
		if(cands.isEmpty())
		{
			jh.visDebugMessage("map coasting: no ridge near the end, map path kept");
			visDebugPlan(false);
			return GEO_NOTARGET;
		}
		//the nearest one
		double [] best = null;
		for(final double [] c : cands)
		{
			if(best == null || c[3] < best[3])
				best = c;
		}
		dT1FitDeg = fitCandidateDirection(best, dMinSal);
		for (int d = 0; d < 3; d++)
		{
			p1[d] = best[d];
			t1[d] = best[5 + d];
		}
		buildCurve();
		dChosenTurnDeg = Math.toDegrees(totalTurning(curve));
		jh.visDebugMessage(String.format("map coasting: end refined (%.1f SD), passage turns %.0f deg, end direction fit %.0f deg", 
				best[3], dChosenTurnDeg, dT1FitDeg));
		visDebugPlan(true);
		return follow(raInput, outPath, dMinSal, "map coasting", junctionBlindLength());
	}

	/** Gap bridging by a planned passage (OneClickTrace.bridgeGapPassage: normal stepping found no continuation),
	 * the same steps as plan() with a different candidate search:
	 * 1) heading: line fit of the trace without its last dGapTailSD (drift to the side before the gap), 
	 *    blended with the momentum by dGapHeadingMomentumWeight (if too short: as in plan(), or the reference 
	 *    direction of the stepping);
	 * 2) candidates: ridge centres (ridgesInVicinity) in the ellipsoid of dGapReachSD tracing SDs (per axis)
	 *    around the current point, at least dGapMinAheadSD ahead (along the heading) of the most advanced 
	 *    recent trace point within the reach, saliency
	 *    >= dRelSaliency x running saliency of the trace, |cos| of the line direction vs the heading
	 *    >= dCandidateMinCos (no cone: any position ahead), not on existing ROIs (traced mask);
	 *    of those closer than dCandidateMergeSD the strongest one is kept. The junction box is placed
	 *    half way into the reach (covers the search). Planning order: offset from the straight heading line;
	 * 3) the least turning curve to them (chooseLeastTurning, end direction fitted along the ridge);
	 * 4) the curve is followed (follow: blind steps over the gap, then on the ridge).
	 * Returns GEO_OK (points, full resolution, added to outPath; heading of OneClickTrace set to the passage end),
	 * GEO_NOTARGET (no candidate) or GEO_LOST (could not be followed); nothing changed in OneClickTrace then. **/
	int planGap(final RealPoint currpoint, final RandomAccess< T > raInput, final ArrayList<RealPoint> outPath, final double dGapReachSD)
	{
		setupGeometry();
		bConeSearch = false;
		//1) heading: fit, or the reference direction of the stepping
		final double [] ref = (oc.bUseHistory ? oc.memDirectionVector : oc.lastDirectionVector).clone();
		LinAlgHelpers.normalize(ref);
		if(!oc.bUseHistory)
		{
			//(fitHeading orients the fit along the momentum, not updated without history)
			System.arraycopy(ref, 0, oc.memDirectionVector, 0, 3);
		}
		if(!fitHeading(currpoint, dGapTailSD, dGapHeadingMomentumWeight) && !fitHeading(currpoint))
		{
			System.arraycopy(ref, 0, t0, 0, 3);
		}
		final double dOwnSal = oc.dSalTrace;
		if(!(dOwnSal > 0) || !(dGapReachSD > 0))
		{
			return GEO_NOTARGET;
		}
		//the most advanced recent trace point along the heading (within the reach): candidates must be ahead of it
		double dFront = 0;
		for(final double [] full : oc.dirHistory)
		{
			double dAlong = 0;
			double dR = 0;
			for (int d = 0; d < 3; d++)
			{
				final double x = oc.toGridDouble(full[d], d) * vox[d] - p0[d];
				dAlong += x * t0[d];
				dR += (x / sd[d]) * (x / sd[d]);
			}
			if(Math.sqrt(dR) <= dGapReachSD)
			{
				dFront = Math.max(dFront, dAlong);
			}
		}
		final double dMinSal = dRelSaliency * dOwnSal;
		//reach along the heading (physical)
		double dNorm = 0;
		for (int d = 0; d < 3; d++)
		{
			dNorm += (t0[d] / sd[d]) * (t0[d] / sd[d]);
		}
		dTargetDist = dGapReachSD / Math.sqrt(dNorm);

		//junction box half way into the reach
		final long [] center = new long[3];
		for (int d = 0; d < 3; d++)
		{
			center[d] = Math.round((p0[d] + 0.5 * dTargetDist * t0[d]) / vox[d]);
		}
		jh.computeJunctionHessian(center);
		if(jh.isVisDebug())
		{
			oc.bt.ocVisDebug.showJunctionMap(jh.box.getSaliencyForDisplay(oc.ds), "gap saliency");
		}

		//2) ridge centres ahead within the reach
		final ArrayList<double[]> rejected = new ArrayList<>();
		final ArrayList<double[]> ridges = ridgesInVicinity(p0, dGapReachSD, dMinSal, t0, dFront + dGapMinAheadSD * dLenSD, dCandidateMinCos, rejected);
		//(debug visualization: the ridge centres behind the front, e.g. the traced filament itself, are not shown 
		//as rejected, only those ahead failing the orientation)
		final double dMinAhead = dFront + dGapMinAheadSD * dLenSD;
		rejected.removeIf(x -> (x[0] - p0[0]) * t0[0] + (x[1] - p0[1]) * t0[1] + (x[2] - p0[2]) * t0[2] < dMinAhead);
		//not on existing ROIs; offset: from the straight heading line (SDs, per axis); saliency
		final long [] posFull = new long[3];
		final double [] vecGrid = new double[3];
		final ArrayList<double[]> valid = new ArrayList<>();
		for(final double [] c : ridges)
		{
			final double [] full = physToFull(c);
			for (int d = 0; d < 3; d++)
			{
				posFull[d] = Math.round(full[d]);
			}
			if(oc.bUseMask && oc.traceMask.isOccupied(posFull))
			{
				rejected.add(new double [] {c[0], c[1], c[2]});
				continue;
			}
			double dAlong = 0;
			for (int d = 0; d < 3; d++)
			{
				dAlong += (c[d] - p0[d]) * t0[d];
			}
			double dLat = 0;
			for (int d = 0; d < 3; d++)
			{
				final double r = (c[d] - p0[d] - dAlong * t0[d]) / sd[d];
				dLat += r * r;
			}
			final double sal = jh.box.saliencyAtVoxel(Math.round(c[0] / vox[0]), Math.round(c[1] / vox[1]), Math.round(c[2] / vox[2]), vecGrid);
			valid.add(new double [] {c[0], c[1], c[2], Math.sqrt(dLat), c[4], c[5], c[6], c[7], sal});
		}
		//one per filament (piece): the strongest of the close ones
		valid.sort((a, b) -> Double.compare(b[8], a[8]));
		final ArrayList<double[]> cands = new ArrayList<>();
		for(final double [] c : valid)
		{
			if(!isCloseTo(c, cands, dCandidateMergeSD))
				cands.add(c);
		}
		//(debug visualization: the base of the searched half ellipsoid, at the current point)
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(t0, u, v);
		secCenter = p0.clone();
		secRadiusSD = dGapReachSD;
		System.arraycopy(u, 0, secU, 0, 3);
		System.arraycopy(v, 0, secV, 0, 3);
		for(final double [] c : cands)
		{
			secCandidates.add(new double [] {c[0], c[1], c[2]});
		}
		secRejected.addAll(rejected);
		if(cands.isEmpty())
		{
			jh.visDebugMessage("gap bridge: no continuation found");
			visDebugPlan(false);
			return GEO_NOTARGET;
		}
		//3) curves to the candidates, the least turning one
		chooseLeastTurning(cands, dMinSal);
		jh.visDebugMessage(String.format("gap bridge (%.1f SD): %d candidate(s), chosen turns %.0f deg, end direction fit %.0f deg", 
				dGapReachSD, altCurves.size() + 1, dChosenTurnDeg, dT1FitDeg));
		visDebugPlan(true);

		//4) follow the curve over the gap (if lost, the next ones), blind up to the reach of the bridge
		return followPlans(raInput, outPath, dMinSal, "gap bridge", dGapReachSD * dLenSD);
	}

	/** Follows the planned curve step by step: the nearest ridge in the cross-section (within dFollowSD),
	 * otherwise a blind step on the curve. Points (full resolution) are added to outPath;
	 * GEO_OK: heading of OneClickTrace set to the curve direction at the end, GEO_LOST: nothing changed. **/
	int follow(final RandomAccess< T > raInput, final ArrayList<RealPoint> outPath, final double dMinSal, final String sName,
			final double dMaxBlindLength)
	{
		final double dCurveEnd = curveS[curveS.length - 1];
		final double [] p = new double[3];
		final double [] t = new double[3];
		final double [] q = new double[3];
		final double [] pPred = new double[3];
		//offset of the followed ridge from the curve (continuity: the ridge closest to curve + offset is taken)
		final double [] off = new double[3];
		//last output point (physical), for the forward progress
		double [] qLast = p0.clone();
		final ArrayList<RealPoint> pts = new ArrayList<>();
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
				jh.visDebugMessage(sName + ": passage lost");
				return GEO_LOST;
			}
			curvePoint(s, p, t);
			for (int d = 0; d < 3; d++)
			{
				pPred[d] = p[d] + off[d];
			}
			if(nearestRidge(p, t, dFollowSD, dMinSal, dFollowMinCos, pPred, q))
			{
				bLastOk = true;
				dBlind = 0;
				for (int d = 0; d < 3; d++)
				{
					off[d] = q[d] - p[d];
				}
			}
			else
			{
				System.arraycopy(pPred, 0, q, 0, 3);
				bLastOk = false;
				dBlind += dStep;
				if(dBlind > dMaxBlindLength)
				{
					jh.visDebugMessage(sName + ": passage lost");
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
			System.arraycopy(t, 0, tEnd, 0, 3);
			//only points moving forward along the curve and at least dMinPointSpacing voxels from the previous one
			double dAdvance = 0;
			double dSpacing = 0;
			for (int d = 0; d < 3; d++)
			{
				dAdvance += (q[d] - qLast[d]) * t[d];
				final double dv = (q[d] - qLast[d]) / vox[d] * oc.ds[d];
				dSpacing += dv * dv;
			}
			if(dAdvance <= 0 || Math.sqrt(dSpacing) < dMinPointSpacing)
				continue;
			pts.add(new RealPoint(outPos));
			qLast = q.clone();
		}
		if(pts.isEmpty())
			return GEO_LOST;
		outPath.addAll(pts);
		//heading after the passage
		System.arraycopy(tEnd, 0, oc.memDirectionVector, 0, 3);
		System.arraycopy(tEnd, 0, oc.lastDirectionVector, 0, 3);
		final double [] tGrid = oc.physToGridDir(tEnd);
		System.arraycopy(tGrid, 0, jh.coastLineDir, 0, 3);
		System.arraycopy(tGrid, 0, jh.coastDir, 0, 3);
		jh.bJunctionArmed = false;
		return GEO_OK;
	}

	/** plans the curves to the candidates (up to nMaxPlanned with the lowest cost), the target (p1, t1, curve)
	 * is the one turning the least; the other curves are kept for the debug visualization.
	 * The end direction of each curve is fitted along the candidate's ridge (fitCandidateDirection) **/
	void chooseLeastTurning(final ArrayList<double[]> cands, final double dMinSal)
	{
		//candidate: position (3), offset (1), |cos| (1), oriented direction (3); order by cost
		cands.sort((a, b) -> Double.compare(a[3] + dTargetAngleWeight * (1.0 - a[4]), b[3] + dTargetAngleWeight * (1.0 - b[4])));
		final int nPlan = Math.min(cands.size(), Math.max(1, nMaxPlanned));
		final ArrayList<double[][]> curves = new ArrayList<>();
		final ArrayList<double[]> curvesS = new ArrayList<>();
		final ArrayList<Double> turns = new ArrayList<>();
		final double [] fitDeg = new double[nPlan];
		int nBest = 0;
		for (int i = 0; i < nPlan; i++)
		{
			final double [] c = cands.get(i);
			fitDeg[i] = fitCandidateDirection(c, dMinSal);
			for (int d = 0; d < 3; d++)
			{
				p1[d] = c[d];
				t1[d] = c[5 + d];
			}
			buildCurve();
			final double turn = totalTurning(curve);
			curves.add(curve);
			curvesS.add(curveS);
			turns.add(turn);
			if(turn < turns.get(nBest))
				nBest = i;
		}
		final double [] c = cands.get(nBest);
		for (int d = 0; d < 3; d++)
		{
			p1[d] = c[d];
			t1[d] = c[5 + d];
		}
		curve = curves.get(nBest);
		curveS = curvesS.get(nBest);
		dChosenTurnDeg = Math.toDegrees(turns.get(nBest));
		dT1FitDeg = fitDeg[nBest];
		altCurves.clear();
		altTurnDeg.clear();
		for (int i = 0; i < nPlan; i++)
		{
			if(i != nBest)
			{
				altCurves.add(curves.get(i));
				altTurnDeg.add(Math.toDegrees(turns.get(i)));
			}
		}
		//all plans, the least turning first
		plans.clear();
		for (int i = 0; i < nPlan; i++)
		{
			final Plan pl = new Plan();
			final double [] ci = cands.get(i);
			pl.p1 = new double [] {ci[0], ci[1], ci[2]};
			pl.t1 = new double [] {ci[5], ci[6], ci[7]};
			pl.curve = curves.get(i);
			pl.curveS = curvesS.get(i);
			pl.dTurnDeg = Math.toDegrees(turns.get(i));
			pl.dFitDeg = fitDeg[i];
			plans.add(pl);
		}
		plans.sort((a, b) -> Double.compare(a.dTurnDeg, b.dTurnDeg));
	}

	/** makes plans[nIndex] the current one (target, curve; the others are the alternative curves of the debug visualization) **/
	void usePlan(final int nIndex)
	{
		final Plan pl = plans.get(nIndex);
		System.arraycopy(pl.p1, 0, p1, 0, 3);
		System.arraycopy(pl.t1, 0, t1, 0, 3);
		curve = pl.curve;
		curveS = pl.curveS;
		dChosenTurnDeg = pl.dTurnDeg;
		dT1FitDeg = pl.dFitDeg;
		altCurves.clear();
		altTurnDeg.clear();
		for (int i = 0; i < plans.size(); i++)
		{
			if(i != nIndex)
			{
				altCurves.add(plans.get(i).curve);
				altTurnDeg.add(plans.get(i).dTurnDeg);
			}
		}
	}

	/** maximum length of blind steps in a row of a junction passage along the current curve (physical) **/
	double junctionBlindLength()
	{
		return Math.max(dMinBlindSD * dLenSD, dBlindFraction * curveS[curveS.length - 1]);
	}

	/** Follows the planned passages (plans) in the order of turning until one is not lost
	 * (only those turning at most dRetryMaxExtraTurnDeg more than the first one).
	 * dBlindLength: maximum length of blind steps in a row (physical), 0 or less: junctionBlindLength of each curve.
	 * Returns GEO_OK (see follow) or GEO_LOST (all lost, nothing changed) **/
	int followPlans(final RandomAccess< T > raInput, final ArrayList<RealPoint> outPath, final double dMinSal, final String sName, 
			final double dBlindLength)
	{
		for (int i = 0; i < plans.size(); i++)
		{
			if(i > 0 && plans.get(i).dTurnDeg > plans.get(0).dTurnDeg + dRetryMaxExtraTurnDeg)
				break;
			usePlan(i);
			if(i > 0)
			{
				jh.visDebugMessage(String.format("%s: passage lost, next candidate (turns %.0f deg)", sName, dChosenTurnDeg));
				visDebugPlan(true);
			}
			final double dMaxBlind = dBlindLength > 0 ? dBlindLength : junctionBlindLength();
			if(follow(raInput, outPath, dMinSal, sName, dMaxBlind) == GEO_OK)
				return GEO_OK;
		}
		return GEO_LOST;
	}

	/** total turning of a dense polyline: sum of the angles between successive segments (radians) **/
	static double totalTurning(final double [][] pts)
	{
		double sum = 0;
		final double [] a = new double[3];
		final double [] b = new double[3];
		boolean bHasPrev = false;
		for (int k = 1; k < pts.length; k++)
		{
			for (int d = 0; d < 3; d++)
			{
				b[d] = pts[k][d] - pts[k - 1][d];
			}
			if(!(LinAlgHelpers.dot(b, b) > 0))
				continue;
			LinAlgHelpers.normalize(b);
			if(bHasPrev)
			{
				sum += Math.acos(Math.max(-1.0, Math.min(1.0, LinAlgHelpers.dot(a, b))));
			}
			System.arraycopy(b, 0, a, 0, 3);
			bHasPrev = true;
		}
		return sum;
	}

	/** heading: slope b of a straight line fit X(s) = a + b s of the trace history (physical) over the last
	 * dFitLengthSD of arc length (s <= 0 at the current point), blended with the smoothed (momentum) direction 
	 * (dHeadingMomentumWeight). Sets p0 (current point) also when it fails (too short history). **/
	boolean fitHeading(final RealPoint currpoint)
	{
		return fitHeading(currpoint, 0.0, dHeadingMomentumWeight);
	}

	/** as fitHeading(currpoint), the last dSkipSD tracing SDs of the trace (path length) are left out of the fit
	 * (e.g. the end of a trace drifting to the side before a gap), the fit is blended with the momentum
	 * with the weight dMomentumWeight **/
	boolean fitHeading(final RealPoint currpoint, final double dSkipSD, final double dMomentumWeight)
	{
		for (int d = 0; d < 3; d++)
		{
			p0[d] = oc.toGridDouble(currpoint.getDoublePosition(d), d) * vox[d];
		}
		final ArrayDeque<double[]> hist = oc.dirHistory;
		if(hist.size() < nMinFitPoints)
			return false;
		final double dSkipLen = Math.max(0.0, dSkipSD) * dLenSD;
		final double dMaxLen = dSkipLen + dFitLengthSD * dLenSD;
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
			prev = x;
			if(-s > dMaxLen)
				break;
			//(the end of the trace left out)
			if(-s < dSkipLen)
				continue;
			pts.add(x);
			sArr.add(s);
		}
		final int n = pts.size();
		if(n < nMinFitPoints || sArr.get(0) - sArr.get(n - 1) < 2.0 * dStep)
			return false;
		//least squares slope
		double sMean = 0;
		final double [] xMean = new double[3];
		for (int i = 0; i < n; i++)
		{
			sMean += sArr.get(i);
			for (int d = 0; d < 3; d++)
			{
				xMean[d] += pts.get(i)[d];
			}
		}
		sMean /= n;
		for (int d = 0; d < 3; d++)
		{
			xMean[d] /= n;
		}
		double sVar = 0;
		final double [] cov = new double[3];
		for (int i = 0; i < n; i++)
		{
			final double ds = sArr.get(i) - sMean;
			sVar += ds * ds;
			for (int d = 0; d < 3; d++)
			{
				cov[d] += ds * (pts.get(i)[d] - xMean[d]);
			}
		}
		if(!(sVar > 0))
			return false;
		for (int d = 0; d < 3; d++)
		{
			t0[d] = cov[d] / sVar;
		}
		final double len = Math.sqrt(LinAlgHelpers.dot(t0, t0));
		if(!(len > 0))
			return false;
		for (int d = 0; d < 3; d++)
		{
			t0[d] /= len;
		}
		//forward (along the momentum)
		final double [] mem = oc.memDirectionVector.clone();
		LinAlgHelpers.normalize(mem);
		if(LinAlgHelpers.dot(t0, mem) < 0)
		{
			for (int d = 0; d < 3; d++)
			{
				t0[d] = -t0[d];
			}
		}
		//blend with the momentum
		if(dMomentumWeight > 0 && LinAlgHelpers.dot(mem, mem) > 0)
		{
			for (int d = 0; d < 3; d++)
			{
				t0[d] = (1.0 - dMomentumWeight) * t0[d] + dMomentumWeight * mem[d];
			}
			LinAlgHelpers.normalize(t0);
		}
		return true;
	}

	/** candidates: in the first cross-section (perpendicular to the straight heading) with any, 
	 * from dSearchStartSD to dSearchMaxSD; if there is none, the closer cross-sections from dSearchStartSD 
	 * back to dSearchMinSD (the farthest one with candidates); 
	 * each: position (3), offset (1), |cos| (1), oriented direction (3), see ridgesInSection **/
	ArrayList<double[]> findCandidates(final double dMinSal)
	{
		final int nMode = OneClickTrace.BRANCH_SEARCH_MODE;
		//on the half sphere ahead: outwards, then inwards
		if(nMode == OneClickTrace.BRANCH_SEARCH_SPHERE)
		{
			bConeSearch = false;
			ArrayList<double[]> sphereCands = findCandidatesSphere(dMinSal, dSearchStartSD, dSearchMaxSD, dSearchStepSD);
			if(sphereCands.isEmpty())
			{
				sphereCands = findCandidatesSphere(dMinSal, dSearchStartSD - dSearchStepSD, dSearchMinSD, -dSearchStepSD);
			}
			return sphereCands;
		}
		bConeSearch = (nMode == OneClickTrace.BRANCH_SEARCH_CONE || nMode == OneClickTrace.BRANCH_SEARCH_BOTH);
		final boolean bSectionSearch = (nMode != OneClickTrace.BRANCH_SEARCH_CONE);
		//on the bending cone: ahead, then closer
		ArrayList<double[]> coneCands = new ArrayList<>();
		final ArrayList<double[]> coneCandPos = new ArrayList<>();
		final ArrayList<double[]> coneRejected = new ArrayList<>();
		if(bConeSearch)
		{
			coneCands = findCandidatesCone(dMinSal, dSearchStartSD, dSearchMaxSD, dSearchStepSD);
			if(coneCands.isEmpty())
			{
				coneCands = findCandidatesCone(dMinSal, dSearchStartSD - dSearchStepSD, dSearchMinSD, -dSearchStepSD);
			}
			dConeDist = dTargetDist;
			coneCandPos.addAll(secCandidates);
			coneRejected.addAll(secRejected);
			if(!bSectionSearch)
				return coneCands;
		}
		//cross-sections: ahead, then closer (going back towards the junction)
		ArrayList<double[]> secCands = findCandidates(dMinSal, dSearchStartSD, dSearchMaxSD, dSearchStepSD);
		if(secCands.isEmpty())
		{
			secCands = findCandidates(dMinSal, dSearchStartSD - dSearchStepSD, dSearchMinSD, -dSearchStepSD);
		}
		if(!bConeSearch)
			return secCands;
		//both: pooled, a cross-section candidate close to a cone candidate is dropped
		final ArrayList<double[]> out = new ArrayList<>(coneCands);
		final ArrayList<double[]> secPos = new ArrayList<>();
		for(final double [] c : secCands)
		{
			boolean bClose = false;
			for(final double [] o : coneCands)
			{
				double dd = 0;
				for (int d = 0; d < 3; d++)
				{
					final double r = (c[d] - o[d]) / sd[d];
					dd += r * r;
				}
				if(dd < dCandidateMergeSD * dCandidateMergeSD)
				{
					bClose = true;
					break;
				}
			}
			if(!bClose)
			{
				out.add(c);
				secPos.add(new double [] {c[0], c[1], c[2]});
			}
		}
		//(debug visualization) candidates and rejected maxima of both
		final ArrayList<double[]> secRej = new ArrayList<>(secRejected);
		secCandidates.clear();
		secCandidates.addAll(coneCandPos);
		secCandidates.addAll(secPos);
		secRejected.clear();
		secRejected.addAll(coneRejected);
		secRejected.addAll(secRej);
		return out;
	}

	/** Candidates on the surface of the half sphere ahead (ridgesOnHalfSphere) of radius dFromSD, +dStepSD, ... 
	 * to dToSD (tracing SDs), the first radius with any. Same format as findCandidates 
	 * (offset = from the straight heading line) **/
	ArrayList<double[]> findCandidatesSphere(final double dMinSal, final double dFromSD, final double dToSD, final double dStepSD)
	{
		secCenter = null;
		secRadiusSD = 0;
		final ArrayList<double[]> rejected = new ArrayList<>();
		if(dStepSD == 0)
			return new ArrayList<>();
		double dNorm = 0;
		for (int d = 0; d < 3; d++)
		{
			dNorm += (t0[d] / sd[d]) * (t0[d] / sd[d]);
		}
		final double dSDAlong = 1.0 / Math.sqrt(dNorm);
		final double [] pole = new double[3];
		for (double dR = dFromSD; (dStepSD > 0) ? (dR <= dToSD + 1e-6) : (dR >= dToSD - 1e-6); dR += dStepSD)
		{
			dTargetDist = dR * dSDAlong;
			for (int d = 0; d < 3; d++)
			{
				pole[d] = p0[d] + dTargetDist * t0[d];
			}
			if(!insideBox(pole))
			{
				if(dStepSD > 0)
					break;
				continue;
			}
			rejected.clear();
			final ArrayList<double[]> cands = ridgesOnHalfSphere(dR, dMinSal, rejected);
			//(debug visualization: the rim of the half sphere, candidates, rejected)
			final double [] u = new double[3];
			final double [] v = new double[3];
			Pipe3D.crossSectionBasis(t0, u, v);
			secCenter = p0.clone();
			secRadiusSD = dR;
			System.arraycopy(u, 0, secU, 0, 3);
			System.arraycopy(v, 0, secV, 0, 3);
			secCandidates.clear();
			for(final double [] c : cands)
			{
				secCandidates.add(new double [] {c[0], c[1], c[2]});
			}
			secRejected.clear();
			secRejected.addAll(rejected);
			if(!cands.isEmpty())
				return cands;
		}
		return new ArrayList<>();
	}

	/** Candidates on the surface of the half sphere ahead of p0 (along the heading t0): voxels of the junction box
	 * at the distance dRadiusSD tracing SDs (per axis, an ellipsoid) from p0, within a shell of half a voxel 
	 * (the largest in SDs), with (x - p0).t0 >= 0. Ridge centres (isRidgeCentre) with saliency >= dMinSal and
	 * |cos| of the line direction vs the heading >= dCandidateMinCos; of those closer than dCandidateMergeSD 
	 * the strongest one is kept. A filament crosses the surface once (the incoming one behind it). 
	 * Each: position (3, physical), offset from the straight heading line (SDs), |cos|, line direction oriented
	 * along the heading (3), saliency. rejectedOut (may be null) gets the ridge centres with the orientation 
	 * outside the limit. Limited by the junction box (lateral reach). **/
	ArrayList<double[]> ridgesOnHalfSphere(final double dRadiusSD, final double dMinSal, final ArrayList<double[]> rejectedOut)
	{
		//shell half thickness (SDs): half of the largest voxel step in SDs, so the shell has no holes
		double dHalf = 0;
		for (int d = 0; d < 3; d++)
		{
			dHalf = Math.max(dHalf, 0.5 * vox[d] / sd[d]);
		}
		final long [] lo = new long[3];
		final long [] hi = new long[3];
		for (int d = 0; d < 3; d++)
		{
			lo[d] = Math.max(jh.box.boxMin[d], (long) Math.floor((p0[d] - (dRadiusSD + dHalf) * sd[d]) / vox[d]));
			hi[d] = Math.min(jh.box.boxMin[d] + jh.box.dims[d] - 1, (long) Math.ceil((p0[d] + (dRadiusSD + dHalf) * sd[d]) / vox[d]));
		}
		final ArrayList<double[]> ridges = new ArrayList<>();
		final double [] vecGrid = new double[3];
		final long [] g = new long[3];
		final double [] pos = new double[3];
		for (g[2] = lo[2]; g[2] <= hi[2]; g[2]++)
			for (g[1] = lo[1]; g[1] <= hi[1]; g[1]++)
				for (g[0] = lo[0]; g[0] <= hi[0]; g[0]++)
				{
					double dR = 0;
					double dAhead = 0;
					for (int d = 0; d < 3; d++)
					{
						pos[d] = g[d] * vox[d];
						final double r = (pos[d] - p0[d]) / sd[d];
						dR += r * r;
						dAhead += (pos[d] - p0[d]) * t0[d];
					}
					if(dAhead < 0 || Math.abs(Math.sqrt(dR) - dRadiusSD) > dHalf)
						continue;
					final double val = jh.box.saliencyAtVoxel(g[0], g[1], g[2], vecGrid);
					if(!(val >= dMinSal) || val <= 0)
						continue;
					final double [] dir = oc.gridToPhysDir(vecGrid);
					if(!isRidgeCentre(g, val, dir))
						continue;
					double cs = LinAlgHelpers.dot(dir, t0);
					final double sign = cs < 0 ? -1.0 : 1.0;
					cs = Math.abs(cs);
					if(cs < dCandidateMinCos)
					{
						if(rejectedOut != null)
						{
							rejectedOut.add(pos.clone());
						}
						continue;
					}
					double dLat = 0;
					for (int d = 0; d < 3; d++)
					{
						final double r = (pos[d] - p0[d] - dAhead * t0[d]) / sd[d];
						dLat += r * r;
					}
					ridges.add(new double [] {pos[0], pos[1], pos[2], Math.sqrt(dLat), cs, sign * dir[0], sign * dir[1], sign * dir[2], val});
				}
		//one per filament crossing the surface: the strongest of the close ones
		ridges.sort((a, b) -> Double.compare(b[8], a[8]));
		final ArrayList<double[]> out = new ArrayList<>();
		for(final double [] c : ridges)
		{
			if(!isCloseTo(c, out, dCandidateMergeSD))
				out.add(c);
		}
		return out;
	}

	/** Candidates on the surface of the bending cone (computeBendCone) of length dFromSD, +dStepSD, ... to dToSD
	 * (tracing SDs), the first length with any. Same format as findCandidates (offset = from the cone apex) **/
	ArrayList<double[]> findCandidatesCone(final double dMinSal, final double dFromSD, final double dToSD, final double dStepSD)
	{
		secCenter = null;
		secRadiusSD = 0;
		final ArrayList<double[]> rejected = new ArrayList<>();
		if(dStepSD == 0)
			return new ArrayList<>();
		for (double dDist = dFromSD; (dStepSD > 0) ? (dDist <= dToSD + 1e-6) : (dDist >= dToSD - 1e-6); dDist += dStepSD)
		{
			computeBendCone(dDist * dLenSD);
			if(!insideBox(coneApex))
			{
				if(dStepSD > 0)
					break;
				continue;
			}
			dTargetDist = dDist * dLenSD;
			rejected.clear();
			final ArrayList<double[]> cands = ridgesOnCone(dMinSal, rejected);
			//(debug visualization)
			secCandidates.clear();
			for(final double [] c : cands)
			{
				secCandidates.add(new double [] {c[0], c[1], c[2]});
			}
			secRejected.clear();
			secRejected.addAll(rejected);
			if(!cands.isEmpty())
				return cands;
		}
		return new ArrayList<>();
	}

	/** Candidates on the surface (shell) of the current bending cone (computeBendCone), voxels of the junction box
	 * visited by ConeCursor: ridge centres (isRidgeCentre: maximum of the line saliency across the line among
	 * the 26 neighbours; saliency and direction of the box voxels, JunctionBox.computeSaliency) with saliency >= dMinSal and |cos|
	 * of the line direction vs the heading >= dCandidateMinCos; of those closer than dCandidateMergeSD the strongest
	 * one is kept. Each: position (3, physical), offset from the cone apex (SDs), |cos|, line direction oriented
	 * along the heading (3), saliency. rejectedOut (may be null) gets positions of the ridge centres with the orientation
	 * outside the limit. Without bending (zero opening angle) the cross-section at the apex is used. **/
	ArrayList<double[]> ridgesOnCone(final double dMinSal, final ArrayList<double[]> rejectedOut)
	{
		if(!(coneFullAngle > 1e-6))
		{
			final double [] u = new double[3];
			final double [] v = new double[3];
			Pipe3D.crossSectionBasis(t0, u, v);
			return ridgesInSection(coneApex, t0, u, v, dTargetMaxOffsetSD, dMinSal, dCandidateMinCos, rejectedOut);
		}
		//cursor over the shell, inside the junction box (grid voxels, voxel i at i * vox)
		final RandomAccess<FloatType> ra = Views.translate(jh.box.salImg, jh.box.boxMin).randomAccess();
		final double [] bbMin = new double[3];
		final double [] bbMax = new double[3];
		double dMaxVox = 0;
		for (int d = 0; d < 3; d++)
		{
			bbMin[d] = jh.box.boxMin[d] * vox[d];
			bbMax[d] = (jh.box.boxMin[d] + jh.box.nDim - 1) * vox[d];
			dMaxVox = Math.max(dMaxVox, vox[d]);
		}
		final ConeCursor<FloatType> cc = new ConeCursor<>(ra, coneApex, coneAxis, coneFullAngle, vox,
				new FinalRealInterval(bbMin, bbMax), true, 0.5 * dMaxVox);
		//shell voxels that are ridge centres (saliency maxima across the line among the +-1 voxel neighbours)
		final ArrayList<double[]> ridges = new ArrayList<>();
		final double [] vecGrid = new double[3];
		final long [] g = new long[3];
		while(cc.hasNext())
		{
			cc.fwd();
			for (int d = 0; d < 3; d++)
			{
				g[d] = cc.getLongPosition(d);
			}
			final double val = jh.box.saliencyAtVoxel(g[0], g[1], g[2], vecGrid);
			if(!(val >= dMinSal) || val <= 0)
				continue;
			final double [] dir = oc.gridToPhysDir(vecGrid);
			if(!isRidgeCentre(g, val, dir))
				continue;
			final double [] pos = new double [] {g[0] * vox[0], g[1] * vox[1], g[2] * vox[2]};
			double cs = LinAlgHelpers.dot(dir, t0);
			final double sign = cs < 0 ? -1.0 : 1.0;
			cs = Math.abs(cs);
			if(cs < dCandidateMinCos)
			{
				if(rejectedOut != null)
				{
					rejectedOut.add(pos);
				}
				continue;
			}
			double dOff = 0;
			for (int d = 0; d < 3; d++)
			{
				final double r = (pos[d] - coneApex[d]) / sd[d];
				dOff += r * r;
			}
			ridges.add(new double [] {pos[0], pos[1], pos[2], Math.sqrt(dOff), cs, sign * dir[0], sign * dir[1], sign * dir[2], val});
		}
		//one per filament crossing the surface: the strongest of the close ones
		ridges.sort((a, b) -> Double.compare(b[8], a[8]));
		final ArrayList<double[]> out = new ArrayList<>();
		for(final double [] c : ridges)
		{
			if(!isCloseTo(c, out, dCandidateMergeSD))
				out.add(c);
		}
		return out;
	}

	/** Ridge centre test: true if the saliency val at the grid voxel g (line direction dir, physical, unit)
	 * is not smaller than the saliency (interpolated, MathBox.saliencyAt) at dRidgeTestStep smallest voxels 
	 * away in 8 directions in the plane perpendicular to the line (physical): a maximum across the line,
	 * the saliency along it is not compared (it changes smoothly along a filament, a strict 3D maximum 
	 * is found only every few microns). In physical units, so it works for anisotropic voxels; 
	 * on the flank of a filament (where the eigenvector tilts towards z) the plane still points to the centre.
	 * False at the border of the junction box **/
	boolean isRidgeCentre(final long [] g, final double val, final double [] dir)
	{
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(dir, u, v);
		final double r = dRidgeTestStep * dStep;
		final double [] x = new double[3];
		for (int k = 0; k < 8; k++)
		{
			final double phi = k * Math.PI / 4.0;
			final double c = Math.cos(phi);
			final double sn = Math.sin(phi);
			for (int d = 0; d < 3; d++)
			{
				x[d] = g[d] + r * (c * u[d] + sn * v[d]) / vox[d];
			}
			final double nb = jh.box.saliencyAt(x);
			if(Double.isNaN(nb) || nb > val)
				return false;
		}
		return true;
	}

	/** true if the point x (first 3 values, physical) is closer than dRadiusSD (tracing SDs, per axis)
	 * to any of the points in list (first 3 values) **/
	boolean isCloseTo(final double [] x, final ArrayList<double[]> list, final double dRadiusSD)
	{
		for(final double [] o : list)
		{
			double dd = 0;
			for (int d = 0; d < 3; d++)
			{
				final double r = (x[d] - o[d]) / sd[d];
				dd += r * r;
			}
			if(dd < dRadiusSD * dRadiusSD)
				return true;
		}
		return false;
	}

	/** Direction of the ridge at c (physical): the ridge is followed both ways from c (dT1FitSD each,
	 * cross-sections perpendicular to dirInit every dStep, ridge closest to the predicted point (previous one
	 * + dStep along dirInit) within dT1FollowSD, stops at a gap) and the principal axis of the ridge points
	 * (incl. c) is fitted. The cross-sections are not turned with the ridge: over the short fit length the continuity
	 * keeps them on it, while a direction from the short chords is dominated by the coarse z steps.
	 * Robust to the bias of the single eigenvector near a junction or a gap end.
	 * dirInit: initial direction (unit, physical); the result (to out) is oriented along it.
	 * Returns false (out not changed) if the ridge is too short (less than 3 points or 0.5 tracing SD extent). **/
	boolean ridgeDirection(final double [] c, final double [] dirInit, final double dMinSal, final double [] out)
	{
		final ArrayList<double[]> pts = new ArrayList<>();
		pts.add(c.clone());
		final double [] p = new double[3];
		final double [] t = new double[3];
		final double [] pPred = new double[3];
		final double [] q = new double[3];
		final double dMaxS = dT1FitSD * dLenSD;
		for (int side = 1; side >= -1; side -= 2)
		{
			System.arraycopy(c, 0, p, 0, 3);
			for (int d = 0; d < 3; d++)
			{
				t[d] = side * dirInit[d];
			}
			for (double s = dStep; s <= dMaxS + 1e-9; s += dStep)
			{
				for (int d = 0; d < 3; d++)
				{
					pPred[d] = p[d] + dStep * t[d];
				}
				if(!insideBox(pPred) || !nearestRidge(pPred, t, dT1FollowSD, dMinSal, dFollowMinCos, pPred, q))
					break;
				pts.add(q.clone());
				System.arraycopy(q, 0, p, 0, 3);
			}
		}
		final int n = pts.size();
		if(n < 3)
			return false;
		//principal axis: power iteration on the covariance, from dirInit
		final double [] mean = new double[3];
		for(final double [] x : pts)
		{
			for (int d = 0; d < 3; d++)
			{
				mean[d] += x[d] / n;
			}
		}
		final double [][] cov = new double[3][3];
		for(final double [] x : pts)
		{
			for (int i = 0; i < 3; i++)
				for (int j = 0; j < 3; j++)
				{
					cov[i][j] += (x[i] - mean[i]) * (x[j] - mean[j]);
				}
		}
		final double [] a = dirInit.clone();
		final double [] b = new double[3];
		for (int it = 0; it < 50; it++)
		{
			for (int i = 0; i < 3; i++)
			{
				b[i] = cov[i][0] * a[0] + cov[i][1] * a[1] + cov[i][2] * a[2];
			}
			final double len = Math.sqrt(LinAlgHelpers.dot(b, b));
			if(!(len > 0))
				return false;
			for (int i = 0; i < 3; i++)
			{
				a[i] = b[i] / len;
			}
		}
		//extent along the axis
		double sMin = Double.MAX_VALUE;
		double sMax = -Double.MAX_VALUE;
		for(final double [] x : pts)
		{
			double s = 0;
			for (int d = 0; d < 3; d++)
			{
				s += (x[d] - mean[d]) * a[d];
			}
			sMin = Math.min(sMin, s);
			sMax = Math.max(sMax, s);
		}
		if(sMax - sMin < 0.5 * dLenSD)
			return false;
		final double sign = LinAlgHelpers.dot(a, dirInit) < 0 ? -1.0 : 1.0;
		for (int d = 0; d < 3; d++)
		{
			out[d] = sign * a[d];
		}
		return true;
	}

	/** t1 of a candidate (c[5..7], oriented line direction) replaced by the direction fitted along its ridge
	 * (ridgeDirection, if successful); returns the change in degrees (0 if not fitted) **/
	double fitCandidateDirection(final double [] c, final double dMinSal)
	{
		if(!(dT1FitSD > 0))
			return 0;
		final double [] pos = new double [] {c[0], c[1], c[2]};
		final double [] dir = new double [] {c[5], c[6], c[7]};
		final double [] fit = new double[3];
		if(!ridgeDirection(pos, dir, dMinSal, fit))
			return 0;
		final double cs = Math.max(-1.0, Math.min(1.0, LinAlgHelpers.dot(fit, dir)));
		for (int d = 0; d < 3; d++)
		{
			c[5 + d] = fit[d];
		}
		return Math.toDegrees(Math.acos(cs));
	}

	/** candidates in the first cross-section with any, at distances dFromSD, +dStepSD, ... to dToSD (tracing SDs) **/
	ArrayList<double[]> findCandidates(final double dMinSal, final double dFromSD, final double dToSD, final double dStepSD)
	{
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(t0, u, v);
		final double [] c0 = new double[3];
		secRadiusSD = dTargetMaxOffsetSD;
		final ArrayList<double[]> rejected = new ArrayList<>();
		if(dStepSD == 0)
			return new ArrayList<>();
		for (double dDist = dFromSD; (dStepSD > 0) ? (dDist <= dToSD + 1e-6) : (dDist >= dToSD - 1e-6); dDist += dStepSD)
		{
			for (int d = 0; d < 3; d++)
			{
				c0[d] = p0[d] + dDist * dLenSD * t0[d];
			}
			if(!insideBox(c0))
				break;
			dTargetDist = dDist * dLenSD;
			rejected.clear();
			final ArrayList<double[]> cands = ridgesInSection(c0, t0, u, v, dTargetMaxOffsetSD, dMinSal, dCandidateMinCos, rejected);
			//(debug visualization) this cross-section
			secCenter = c0.clone();
			System.arraycopy(u, 0, secU, 0, 3);
			System.arraycopy(v, 0, secV, 0, 3);
			secCandidates.clear();
			for(final double [] c : cands)
			{
				secCandidates.add(new double [] {c[0], c[1], c[2]});
			}
			secRejected.clear();
			secRejected.addAll(rejected);
			if(!cands.isEmpty())
				return cands;
		}
		return new ArrayList<>();
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
		return ridgesInSection(c0, t, u, v, dMaxOffsetSD, dMinSal, oc.dAngleThreshold, null);
	}

	/** as ridgesInSection above, with the minimum |cos| of the line orientation vs t dMinCos; 
	 * rejectedOut (may be null) gets positions (physical) of the local maxima 
	 * within dMaxOffsetSD rejected by saliency (below dMinSal) or orientation **/
	ArrayList<double[]> ridgesInSection(final double [] c0, final double [] t, final double [] u, final double [] v,
			final double dMaxOffsetSD, final double dMinSal, final double dMinCos, final ArrayList<double[]> rejectedOut)
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
				if(!(val > 0) || off[i][j] > dMaxOffsetSD || (rejectedOut == null && val < dMinSal))
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
				if(val < dMinSal)
				{
					rejectedOut.add(x.clone());
					continue;
				}
				jh.box.hessBox.saliencyAt(xg, vecGrid);
				final double [] dir = oc.gridToPhysDir(vecGrid);
				double cs = LinAlgHelpers.dot(dir, t);
				if(cs < 0)
				{
					cs = -cs;
					for (int d = 0; d < 3; d++)
					{
						dir[d] = -dir[d];
					}
				}
				if(cs < dMinCos)
				{
					if(rejectedOut != null)
					{
						rejectedOut.add(x.clone());
					}
					continue;
				}
				out.add(new double [] {x[0], x[1], x[2], off[i][j], cs, dir[0], dir[1], dir[2]});
			}
		}
		return out;
	}

	/** Ridge centre voxels (grid voxels of the junction box) in the vicinity of cPhys: ellipsoid of dRadiusSD 
	 * tracing SDs (per axis). A ridge centre has saliency >= dMinSal and not smaller than any of its 26 neighbours 
	 * lying across its own line direction (isRidgeCentre). Accepted: ahead of p0
	 * (projection of (x - p0) on dirAhead >= dMinAhead) and |cos| of the line direction vs dirAhead >= dMinCos.
	 * Each result: position (3, physical), offset in SDs, |cos|, line direction oriented along dirAhead (3, physical);
	 * rejectedOut (may be null) gets the positions of the ridge centres failing "ahead" or the orientation **/
	ArrayList<double[]> ridgesInVicinity(final double [] cPhys, final double dRadiusSD, final double dMinSal, 
			final double [] dirAhead, final double dMinAhead, final double dMinCos, final ArrayList<double[]> rejectedOut)
	{
		final ArrayList<double[]> out = new ArrayList<>();
		//grid range (+1 voxel for the neighbours)
		final long [] lo = new long[3];
		final int [] dim = new int[3];
		for (int d = 0; d < 3; d++)
		{
			lo[d] = (long) Math.floor((cPhys[d] - dRadiusSD * sd[d]) / vox[d]) - 1;
			final long hi = (long) Math.ceil((cPhys[d] + dRadiusSD * sd[d]) / vox[d]) + 1;
			dim[d] = (int) (hi - lo[d] + 1);
		}
		final int nVol = dim[0] * dim[1] * dim[2];
		final double [] sal = new double[nVol];
		final double [][] dirs = new double[nVol][];
		final double [] vecGrid = new double[3];
		int n = 0;
		for (int z = 0; z < dim[2]; z++)
			for (int y = 0; y < dim[1]; y++)
				for (int x = 0; x < dim[0]; x++)
				{
					final double val = jh.box.saliencyAtVoxel(lo[0] + x, lo[1] + y, lo[2] + z, vecGrid);
					sal[n] = Double.isNaN(val) ? Double.NEGATIVE_INFINITY : val;
					dirs[n] = Double.isNaN(val) ? null : oc.gridToPhysDir(vecGrid);
					n++;
				}
		final double [] pos = new double[3];
		for (int z = 1; z < dim[2] - 1; z++)
			for (int y = 1; y < dim[1] - 1; y++)
				for (int x = 1; x < dim[0] - 1; x++)
				{
					final int i = (z * dim[1] + y) * dim[0] + x;
					final double val = sal[i];
					if(!(val >= dMinSal) || val <= 0 || dirs[i] == null)
						continue;
					pos[0] = (lo[0] + x) * vox[0];
					pos[1] = (lo[1] + y) * vox[1];
					pos[2] = (lo[2] + z) * vox[2];
					double dOff = 0;
					for (int d = 0; d < 3; d++)
					{
						final double r = (pos[d] - cPhys[d]) / sd[d];
						dOff += r * r;
					}
					dOff = Math.sqrt(dOff);
					if(dOff > dRadiusSD)
						continue;
					//ridge centre: maximum across its own direction (isRidgeCentre)
					final double [] dir = dirs[i];
					if(!isRidgeCentre(new long [] {lo[0] + x, lo[1] + y, lo[2] + z}, val, dir))
						continue;
					//ahead and orientation
					double dAhead = 0;
					for (int d = 0; d < 3; d++)
					{
						dAhead += (pos[d] - p0[d]) * dirAhead[d];
					}
					double cs = LinAlgHelpers.dot(dir, dirAhead);
					final double sign = cs < 0 ? -1.0 : 1.0;
					cs = Math.abs(cs);
					if(dAhead < dMinAhead || cs < dMinCos)
					{
						if(rejectedOut != null)
						{
							rejectedOut.add(pos.clone());
						}
						continue;
					}
					out.add(new double [] {pos[0], pos[1], pos[2], dOff, cs, sign * dir[0], sign * dir[1], sign * dir[2]});
				}
		return out;
	}

	/** ridge in the cross-section at p perpendicular to t, within dMaxOffsetSD of p, |cos| of its direction vs t 
	 * at least dMinCos, closest to pRef (offset in SDs, per axis); its position to q, false if there is none **/
	boolean nearestRidge(final double [] p, final double [] t, final double dMaxOffsetSD, final double dMinSal, final double dMinCos,
			final double [] pRef, final double [] q)
	{
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(t, u, v);
		final ArrayList<double[]> cands = ridgesInSection(p, t, u, v, dMaxOffsetSD, dMinSal, dMinCos, null);
		double dBest = Double.MAX_VALUE;
		for(final double [] c : cands)
		{
			double dd = 0;
			for (int d = 0; d < 3; d++)
			{
				final double r = (c[d] - pRef[d]) / sd[d];
				dd += r * r;
			}
			if(dd < dBest)
			{
				dBest = dd;
				System.arraycopy(c, 0, q, 0, 3);
			}
		}
		return dBest < Double.MAX_VALUE;
	}

	/** nearest (offset in SDs) ridge in the cross-section at p perpendicular to t, within dMaxOffsetSD;
	 * its position to q, false if there is none **/
	boolean nearestRidge(final double [] p, final double [] t, final double dMaxOffsetSD, final double dMinSal, final double [] q)
	{
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(t, u, v);
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
		LinAlgHelpers.normalize(t);
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

	/** debug visualization: extrapolated heading (up to the target cross-section or the coasting end), 
	 * target with its direction, passage curve and the curves to the other candidates, 
	 * the cross-section with candidates and rejected maxima, the end of the map coasting
	 * (bTarget false: no target, curves) **/
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
		final ArrayList<ArrayList<double[]>> altFull = new ArrayList<>();
		double [] tgtA = null;
		double [] tgtB = null;
		if(bTarget)
		{
			//every ~half SD along the curve
			final int nStride = Math.max(1, (int) Math.round(0.5 * dLenSD / Math.max(curveS[1] - curveS[0], 1e-12)));
			curveFull = curveToFull(curve, nStride);
			final double [] a = new double[3];
			final double [] b = new double[3];
			for (int d = 0; d < 3; d++)
			{
				a[d] = p1[d] - dLenSD * t1[d];
				b[d] = p1[d] + dLenSD * t1[d];
			}
			tgtA = physToFull(a);
			tgtB = physToFull(b);
			for(final double [][] alt : altCurves)
			{
				altFull.add(curveToFull(alt, nStride));
			}
		}
		//cross-section: outline of the searched region (offset = dTargetMaxOffsetSD SDs, an ellipse in the plane),
		//accepted candidates and rejected saliency maxima
		ArrayList<double[]> sectionFull = null;
		final ArrayList<double[]> candFull = new ArrayList<>();
		final ArrayList<double[]> rejFull = new ArrayList<>();
		if(secCenter != null)
		{
			sectionFull = new ArrayList<>();
			final int nOutline = 48;
			final double [] w = new double[3];
			final double [] x = new double[3];
			for (int k = 0; k < nOutline; k++)
			{
				final double phi = 2.0 * Math.PI * k / nOutline;
				double dNorm = 0;
				for (int d = 0; d < 3; d++)
				{
					w[d] = Math.cos(phi) * secU[d] + Math.sin(phi) * secV[d];
					dNorm += (w[d] / sd[d]) * (w[d] / sd[d]);
				}
				final double r = secRadiusSD / Math.sqrt(dNorm);
				for (int d = 0; d < 3; d++)
				{
					x[d] = secCenter[d] + r * w[d];
				}
				sectionFull.add(physToFull(x));
			}
			for(final double [] c : secCandidates)
			{
				candFull.add(physToFull(c));
			}
			for(final double [] c : secRejected)
			{
				rejFull.add(physToFull(c));
			}
		}
		//branch-first, cone search: the bending cone searched (no cross-section); otherwise no cone
		if(coastEnd == null && bConeSearch)
		{
			visDebugBendCone(dConeDist);
		}
		else
		{
			oc.bt.ocVisDebug.setBendCone(physToFull(p0), new double[3], 0.0, null);
		}
		oc.bt.ocVisDebug.showBranchPlan(physToFull(p0), physToFull(hEnd), curveFull, tgtA, tgtB, sectionFull, candFull, rejFull,
				altFull, coastEnd != null ? physToFull(coastEnd) : null, boxFull());
	}

	/** debug visualization: the junction (hessian) box in full resolution voxels (whole grid voxels) **/
	FinalInterval boxFull()
	{
		final long [] min = new long[3];
		final long [] max = new long[3];
		for (int d = 0; d < 3; d++)
		{
			min[d] = jh.box.boxMin[d] * oc.ds[d];
			max[d] = (jh.box.boxMin[d] + jh.box.nDim) * oc.ds[d] - 1;
		}
		return new FinalInterval(min, max);
	}

	/** Maximally bending path of length dLength (physical) from p0: it turns by the directionality constrain angle
	 * acos(dAngleThreshold) every dBendStepSD tracing SDs, always to the same side in the plane of the heading t0 
	 * and the unit vector n (perpendicular to t0); the total turn is at most dMaxTotalBendDeg. Points (physical), from p0 **/
	ArrayList<double[]> maxBendPath(final double dLength, final double [] n)
	{
		final ArrayList<double[]> out = new ArrayList<>();
		final double dStepLen = Math.max(1e-12, dBendStepSD * dLenSD);
		final int nSteps = Math.max(1, (int) Math.round(dLength / dStepLen));
		final double dL = dLength / nSteps;
		final double dTurn = Math.acos(Math.max(-1.0, Math.min(1.0, oc.dAngleThreshold)));
		final double dMaxTurn = Math.toRadians(dMaxTotalBendDeg);
		final double [] x = p0.clone();
		out.add(x.clone());
		for (int k = 1; k <= nSteps; k++)
		{
			final double a = Math.min(k * dTurn, dMaxTurn);
			for (int d = 0; d < 3; d++)
			{
				x[d] += dL * (Math.cos(a) * t0[d] + Math.sin(a) * n[d]);
			}
			out.add(x.clone());
		}
		return out;
	}

	/** Bending cone for the length dLength (physical) ahead of p0: apex at the end of the straight heading,
	 * base circle through the ends of the maximally bending paths (maxBendPath) rotated around the heading.
	 * Its surface is where the filament can be after dLength with a limited bending, from straight on (apex)
	 * to the maximal bend (base). Sets coneApex, coneAxis, coneFullAngle, coneAxial, coneRadial **/
	void computeBendCone(final double dLength)
	{
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(t0, u, v);
		final ArrayList<double[]> path = maxBendPath(dLength, u);
		final double [] end = path.get(path.size() - 1);
		//axial and radial position of the maximally bent end
		double dAxial = 0;
		for (int d = 0; d < 3; d++)
		{
			dAxial += (end[d] - p0[d]) * t0[d];
		}
		double dRadial = 0;
		for (int d = 0; d < 3; d++)
		{
			final double r = end[d] - p0[d] - dAxial * t0[d];
			dRadial += r * r;
		}
		dRadial = Math.sqrt(dRadial);
		//cone: apex at the straight end, axis (apex -> base center) back along the heading
		final double dHeight = dLength - dAxial;
		for (int d = 0; d < 3; d++)
		{
			coneApex[d] = p0[d] + dLength * t0[d];
			coneAxis[d] = -dHeight * t0[d];
		}
		coneFullAngle = (dHeight > 1e-12 && dRadial > 1e-12) ? 2.0 * Math.atan2(dRadial, dHeight) : 0.0;
		coneAxial = dAxial;
		coneRadial = dRadial;
	}

	/** Debug visualization of the bending cone of length dLength (computeBendCone) and nBendPathsShown 
	 * of the maximally bending paths **/
	void visDebugBendCone(final double dLength)
	{
		computeBendCone(dLength);
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(t0, u, v);
		final ArrayList<ArrayList<double[]>> pathsFull = new ArrayList<>();
		for (int i = 0; i < nBendPathsShown; i++)
		{
			final double phi = 2.0 * Math.PI * i / nBendPathsShown;
			final double [] n = new double[3];
			for (int d = 0; d < 3; d++)
			{
				n[d] = Math.cos(phi) * u[d] + Math.sin(phi) * v[d];
			}
			final ArrayList<double[]> pathFull = new ArrayList<>();
			for(final double [] p : maxBendPath(dLength, n))
			{
				pathFull.add(physToFull(p));
			}
			pathsFull.add(pathFull);
		}
		oc.bt.ocVisDebug.setBendCone(physToFull(coneApex), coneAxis.clone(), coneFullAngle, pathsFull);
		jh.visDebugMessage(String.format("bending cone: %.0f deg per %.1f SD over %.0f SD, max. bent end %.1f SD ahead, %.1f SD aside, cone half angle %.0f deg",
				Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, oc.dAngleThreshold)))), dBendStepSD, dLength / dLenSD,
				coneAxial / dLenSD, coneRadial / dLenSD, Math.toDegrees(0.5 * coneFullAngle)));
	}

	/** every nStride-th point of a dense curve (and its last one) in full resolution voxel coordinates **/
	ArrayList<double[]> curveToFull(final double [][] c, final int nStride)
	{
		final ArrayList<double[]> out = new ArrayList<>();
		for (int k = 0; k < c.length; k += nStride)
		{
			out.add(physToFull(c[k]));
		}
		out.add(physToFull(c[c.length - 1]));
		return out;
	}
}
