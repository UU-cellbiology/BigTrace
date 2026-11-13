package bigtrace.math;

import java.util.ArrayDeque;
import java.util.ArrayList;

import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;

/** Junction (crossing) handling of OneClickTrace (OneClickTrace.bJunctions, uses its momentum direction as heading):
 * - detection of a junction ahead (orientation coherence along the momentum, with hysteresis);
 * - planned junction passage: coasting along the elongated steerable line filter map
 *   of a JunctionBox, computed in a buffer and committed only if it is a crossing;
 * - in auto-tracing: crossing existing ROIs (traced mask), stopping when merging onto them,
 *   merging with traces that continue across the junction (TraceEndMerger);
 * - in auto-tracing: moving a start that is at a junction out along one of its arms.
 * Positions and coasting directions here are in grid coordinates; the heading of OneClickTrace 
 * (memDirectionVector, lastDirectionVector), eigenvectors and all angle comparisons are in physical space. **/
class JunctionHandler < T extends RealType< T > & NativeType< T > >
{
	final OneClickTrace<T> oc;

	/** orientation coherence ahead (see getPathCoherence) below this value marks a junction **/
	final double dJunctionCoherenceIn = 0.7;

	/** coherence above this value re-arms the detection after a junction (hysteresis) **/
	final double dJunctionCoherenceOut = 0.9;

	/** voxels with saliency below this fraction of the maximum in the ball are ignored in the coherence **/
	final double dCoherenceMinSalFraction = 0.3;

	/** length (SD, in tracing sigmas) of the elongated line filter along the passage direction
	 * (long enough to resolve ~15-20 deg crossings) **/
	final double dAlongSigmaFactor = 3.0;

	/** coasting distance through a junction (in tracing sigmas) **/
	final double dCoastDistanceFactor = 6.0;

	/** half size of the junction box (in tracing sigmas) **/
	final double dJunctionBoxFactor = 6.0;

	/** an accepted passage must end at least this distance (in tracing sigmas) away
	 * from existing ROIs (traced mask), otherwise normal stepping runs along their edge **/
	final double dPassageClearFactor = 1.0;

	/** a passage along which the map drops below this fraction of its value at the first step
	 * is rejected: it runs into the void (e.g. straight on at a kink of the filament).
	 * Tested: overshoots at kinks dropped to 0.05-0.12, real crossings stayed >= 0.2 **/
	final double dPassageFadeFraction = 0.15;
	
	/** arms of the elongated line filter (start relocation, kinks): number of tested directions,
	 * minimum response (fraction of the strongest) and minimum angle between arms **/
	final int nArmDirections = 128;
	final double dArmFraction = 0.35;
	final double dArmSepDeg = 40.0;

	/** a start at a junction is moved this far (in tracing sigmas) out along an arm **/
	final double [] dRelocateFactors = new double [] {4.0, 6.0};

	/** results of step() **/
	static final int STEP_NORMAL = 0, STEP_POINT = 1, STEP_STOP = 2;

	/** results of planPassage() **/
	static final int PASSAGE_NONE = 0, PASSAGE_OK = 1, PASSAGE_MERGE = 2;

	/** max tracing sigma in grid units **/
	double dMaxSigmaGrid = 1.0;

	/** radius of the coherence ball and look-ahead distance (grid voxels), ~sigma **/
	int nJunctionRadius = 1;

	/** SD of the elongated line filter along the passage direction (grid voxels) **/
	double dAlongSigma = 3.0;

	/** coasting distance through a junction (grid voxels) **/
	double dCoastDistance = 6.0;

	/** safety limit of coasting steps **/
	int nCoastMaxSteps = 9;

	JunctionBox box = null;

	double [][] armDirections = null;

	final TraceEndMerger merger = new TraceEndMerger();

	//---------- state

	/** true while coasting (only inside planPassage) **/
	boolean bCoasting = false;

	int nCoastSteps = 0;

	/** detection is armed (see dJunctionCoherenceOut) **/
	boolean bJunctionArmed = true;

	/** the last coasting ended by reaching the coasting distance (not by a wall/limits) **/
	boolean bCoastEndedByDistance = false;

	/** stop tracing in this direction once the passage queue is empty (merge found) **/
	boolean bStopAfterQueue = false;

	/** heading when entering the junction (unit, grid space) **/
	final double [] coastLineDir = new double[3];

	/** current coasting direction (unit, grid space): chord from the coasting start **/
	final double [] coastDir = new double[3];

	/** first point of the current coasting **/
	final double [] coastStartPos = new double[3];
	
	/** map value at the first coasting step and the lowest ratio to it along the passage **/
	double dMapFirst = 1.0;
	double dMapMinRatio = 1.0;

	/** points of an accepted junction passage, returned one by one by step() **/
	final ArrayDeque<RealPoint> passageQueue = new ArrayDeque<>();

	/** point returned by step() with STEP_POINT **/
	RealPoint stepPoint = null;

	JunctionHandler(final OneClickTrace<T> oc_)
	{
		oc = oc_;
	}

	/** sizes from the max tracing sigma (grid units), allocates the junction box **/
	void init(final double dMaxSigmaGrid_, final boolean bDownsample)
	{
		dMaxSigmaGrid = dMaxSigmaGrid_;
		nJunctionRadius = Math.max(1, (int) Math.ceil(dMaxSigmaGrid));
		dAlongSigma = dAlongSigmaFactor * dMaxSigmaGrid;
		dCoastDistance = dCoastDistanceFactor * dMaxSigmaGrid;
		nCoastMaxSteps = Math.max((int) Math.ceil(5.0 * dMaxSigmaGrid), (int) Math.ceil(1.5 * dCoastDistance));
		box = new JunctionBox(Math.max(3, (int) Math.ceil(dJunctionBoxFactor * dMaxSigmaGrid)), bDownsample);
		armDirections = SteerableLineFilter.sphereDirections(nArmDirections);
	}

	/** before a new runTracing() **/
	void resetTrace()
	{
		merger.clear();
		passageQueue.clear();
	}

	/** before tracing in one direction **/
	void resetDirection()
	{
		bCoasting = false;
		nCoastSteps = 0;
		bJunctionArmed = true;
		passageQueue.clear();
		bStopAfterQueue = false;
	}

	/** merges the traces found at the ends of junction passages into the current trace **/
	void applyMerges()
	{
		merger.applyMerges(oc.bt, oc.getCurrentTracing());
	}

	/** Junction part of OneClickTrace.getNextPoint, before the normal step:
	 * returns STEP_POINT (next point in stepPoint: from an accepted passage),
	 * STEP_STOP (stop tracing in this direction) or STEP_NORMAL (normal stepping). **/
	int step(final RealPoint currpoint, final RandomAccess< T > raInput)
	{
		stepPoint = null;
		//points of an accepted junction passage
		if(!passageQueue.isEmpty())
		{
			stepPoint = passageQueue.poll();
			return STEP_POINT;
		}
		//passage ended at the end of another trace (merged at the end of runTracing)
		if(bStopAfterQueue)
		{
			return STEP_STOP;
		}
		//junction ahead? checked before the normal step, so the passage starts
		//from the current (still centered) point, before normal stepping can be
		//pulled to the crossing filament.
		//Hysteresis: after a junction, detection is armed again only when
		//coherence ahead is high (> dJunctionCoherenceOut)
		boolean bJunction = false;
		final double dCoherenceAhead = getCoherenceAhead(currpoint);
		if(!bJunctionArmed)
		{
			if(dCoherenceAhead > dJunctionCoherenceOut)
			{
				bJunctionArmed = true;
			}
		}
		else
		{
			//crossing filaments ahead,
			//or about to hit an existing ROI (auto-tracing): try to pass it
			bJunction = dCoherenceAhead < dJunctionCoherenceIn || isTracedAhead(currpoint);
		}
		if(bJunction)
		{
			final int nPassage = planPassage(currpoint, raInput);
			if(nPassage == PASSAGE_OK)
			{
				stepPoint = passageQueue.poll();
				return (stepPoint == null) ? STEP_STOP : STEP_POINT;
			}
			if(nPassage == PASSAGE_MERGE)
			{
				//the trace would continue on an existing ROI: stop here
				return STEP_STOP;
			}
			//no passage: continue with normal stepping
		}
		return STEP_NORMAL;
	}

	//---------- junction detection

	/** orientation coherence along the momentum in the ball centered ~sigma ahead of the current point **/
	double getCoherenceAhead(final RealPoint currpoint)
	{
		final double [] memGrid = oc.physToGridDir(oc.memDirectionVector);
		final long [] ahead = new long[3];
		for (int d = 0; d < 3; d++)
		{
			ahead[d] = Math.round(oc.toGridDouble(currpoint.getDoublePosition(d), d) + nJunctionRadius * memGrid[d]);
		}
		return getPathCoherence(ahead, oc.memDirectionVector);
	}

	/** Junction (crossing) detector using the eigenvectors of the math box.
	 * Saliency weighted orientation coherence along the heading m in a ball of radius ~sigma:
	 *   C = sum( w * (v.m)^2 ) / sum( w ), w = saliency, v = local eigenvector
	 * (= m'Tm/tr(T) of the local orientation tensor T, the sign of v does not matter).
	 * v and m are in physical space, gridPos and the ball are in the grid.
	 * Single filament along m: C ~ 0.9-1, crossing at 45 deg: ~0.75, at 90 deg: ~0.5.
	 * Voxels below dCoherenceMinSalFraction of the max saliency in the ball are ignored. **/
	double getPathCoherence(final long [] gridPos, final double [] m)
	{
		final int nR = nJunctionRadius;
		//sparse sampling for large sigma
		final int nStride = Math.max(1, nR / 2);
		final long [] minB = new long[3];
		final long [] maxB = new long[3];
		for (int d = 0; d < 3; d++)
		{
			minB[d] = Math.max(oc.salWeights.min(d), gridPos[d] - nR);
			maxB[d] = Math.min(oc.salWeights.max(d), gridPos[d] + nR);
		}
		final long nR2 = (long) nR * nR;
		final long [] pos = new long[3];
		float wMax = 0;
		for (pos[2] = minB[2]; pos[2] <= maxB[2]; pos[2] += nStride)
		{
			for (pos[1] = minB[1]; pos[1] <= maxB[1]; pos[1] += nStride)
			{
				for (pos[0] = minB[0]; pos[0] <= maxB[0]; pos[0] += nStride)
				{
					if(dist2(pos, gridPos) <= nR2)
					{
						wMax = Math.max(wMax, oc.raW.setPositionAndGet(pos).get());
					}
				}
			}
		}
		final double wMin = Math.max(0.0, dCoherenceMinSalFraction * wMax);
		double sumW = 0;
		double sumWC = 0;
		for (pos[2] = minB[2]; pos[2] <= maxB[2]; pos[2] += nStride)
		{
			for (pos[1] = minB[1]; pos[1] <= maxB[1]; pos[1] += nStride)
			{
				for (pos[0] = minB[0]; pos[0] <= maxB[0]; pos[0] += nStride)
				{
					if(dist2(pos, gridPos) > nR2)
						continue;
					final float w = oc.raW.setPositionAndGet(pos).get();
					if(w <= 0 || w < wMin)
						continue;
					final double c = LinAlgHelpers.dot(oc.getVectorAtGrid(pos), m);
					sumW += w;
					sumWC += w * c * c;
				}
			}
		}
		//nothing line-like around, not a junction
		if(sumW <= 0)
			return 1.0;
		return sumWC / sumW;
	}

	static long dist2(final long [] a, final long [] b)
	{
		long out = 0;
		for (int d = 0; d < 3; d++)
		{
			out += (a[d] - b[d]) * (a[d] - b[d]);
		}
		return out;
	}

	//---------- planned junction passage

	/** Plans the whole junction passage in a buffer: junction map, coasting through the junction
	 * (existing ROIs of the traced mask are crossed), then decides:
	 * PASSAGE_OK: coasting reached its distance and ended clear of existing ROIs (a crossing),
	 *             the points are queued and returned by the next step() calls; or the passage
	 *             ends at the end of another trace continuing it (merge, auto-tracing);
	 * PASSAGE_MERGE: the passage ends on an existing ROI (the trace merges onto it), stop;
	 * PASSAGE_NONE: no passage (wall etc.), continue with normal stepping. **/
	int planPassage(final RealPoint currpoint, final RandomAccess< T > raInput)
	{
		passageQueue.clear();
		visDebugMessage("entering junction");
		final ArrayList<RealPoint> path = new ArrayList<>();
		//heading before the junction (restored if there is no passage)
		final double [] memBefore = oc.memDirectionVector.clone();
		final double [] lastBefore = oc.lastDirectionVector.clone();
		dMapMinRatio = 1.0;
		RealPoint pt = enterJunction(currpoint, raInput);
		while(pt != null)
		{
			path.add(pt);
			if(!bCoasting)
				break;
			pt = coastStep(pt, raInput);
		}
		if(bCoasting)
		{
			stopCoasting();
		}
		if(path.isEmpty())
		{
			visDebugMessage("passage rejected");
			noPassageHeading(currpoint, memBefore, lastBefore);
			return PASSAGE_NONE;
		}
		final double [] lastGrid = toGrid(path.get(path.size() - 1));
		//safeguard: the map faded along the passage, it runs into the void
		final boolean bFaded = dMapMinRatio < dPassageFadeFraction;
		if(bCoastEndedByDistance && !bFaded && !isNearTraced(lastGrid))
		{
			passageQueue.addAll(path);
			exitPassageHeading(path.get(path.size() - 1));
			return PASSAGE_OK;
		}
		if(isTraced(lastGrid))
		{
			//passage ends on an existing ROI: at the end of another trace
			//continuing in the same direction? then go there and merge them
			if(oc.bMergeTraceEnds)
			{
				double dMaxSigma = 0;
				for(int d = 0; d < 3; d++)
				{
					dMaxSigma = Math.max(dMaxSigma, oc.bt.btData.sigmaTrace[d]);
				}
				final int nK = Math.max(2, (int) Math.ceil(2.0 * dMaxSigma));
				final double dR = 0.5 * (oc.getCurrentTracing() != null ? oc.getCurrentTracing().getLineThickness() : oc.fEstimatedThickness) + dMaxSigma;
				final int nTP = (oc.nTimePoint >= 0) ? oc.nTimePoint : oc.bt.btData.nCurrTimepoint;
				final int nKeep = merger.findMergeEnd(oc.bt, oc.getCurrentTracing(), nTP, dR, nK, currpoint, path);
				if(nKeep >= 0)
				{
					for(int k = 0; k < nKeep; k++)
					{
						passageQueue.add(path.get(k));
					}
					bStopAfterQueue = true;
					return PASSAGE_OK;
				}
			}
			return PASSAGE_MERGE;
		}
		visDebugMessage("passage rejected");
		noPassageHeading(currpoint, memBefore, lastBefore);
		return PASSAGE_NONE;
	}
	
	/** debug visualization: message in BVV (entering a junction, passage rejected) **/
	void visDebugMessage(final String sMessage)
	{
		if(oc.bLiveUpdate && oc.bt.bOCVisDebugEnabled && oc.bt.ocVisDebug != null)
		{
			oc.bt.ocVisDebug.showMessage(sMessage);
		}
	}
	
	/** No passage (e.g. a kink of the filament detected as a junction): the heading from before
	 * the junction is restored and turned to the outgoing arm of the elongated line filter 
	 * at the detection point (~sigma ahead): the arm closest to the heading, other than the incoming one.
	 * Otherwise a stiff momentum keeps pointing straight on and normal stepping stops at the kink. **/
	void noPassageHeading(final RealPoint currpoint, final double [] memBefore, final double [] lastBefore)
	{
		System.arraycopy(memBefore, 0, oc.memDirectionVector, 0, 3);
		System.arraycopy(lastBefore, 0, oc.lastDirectionVector, 0, 3);
		if(box.hessBox == null)
			return;
		final double [] outArm = findOutgoingArm(toGrid(currpoint), memBefore);
		if(outArm != null)
		{
			System.arraycopy(outArm, 0, oc.memDirectionVector, 0, 3);
			System.arraycopy(outArm, 0, oc.lastDirectionVector, 0, 3);
		}
	}
	
	/** Exit of an accepted passage: a kink (bend) of the filament can be detected as a junction,
	 * since it lowers the coherence ahead like a shallow crossing. The passage then coasts straight 
	 * and ends at/after the bend with the heading of the incoming part, 
	 * so normal stepping (and gap bridging) can not turn into the outgoing part and tracing stops.
	 * Here the heading is turned to the outgoing arm of the elongated line filter ~sigma ahead of the exit 
	 * (hessian of a junction box centered at the exit): the arm closest to the heading, other than 
	 * the incoming one, if it is within the directionality constrain.
	 * At the exit of a real crossing it is the straight continuation, the heading barely changes.
	 * The heading is turned only half way to the arm: the arm is a long-range average 
	 * (rays of ~2 elongated filter lengths), on a filament that curves after the crossing 
	 * it points further than the local direction, and right after the crossing (passage 
	 * ends ~2.5 sigma past it) a full turn can lean the heading to the other half 
	 * of the crossing filament (tested: full turn went onto it, half way followed the filament). **/
	void exitPassageHeading(final RealPoint exitPoint)
	{
		final double [] heading = oc.memDirectionVector.clone();
		final double [] pExit = toGrid(exitPoint);
		final long [] center = new long[3];
		for (int d = 0; d < 3; d++)
		{
			center[d] = Math.round(pExit[d]);
		}
		computeJunctionHessian(center);
		final double [] outArm = findOutgoingArm(pExit, heading);
		if(outArm == null)
			return;
		if(SteerableLineFilter.dot(outArm, heading) < oc.dAngleThreshold)
			return;
		//half way between the passage heading and the arm
		final double [] newHeading = new double[3];
		for (int d = 0; d < 3; d++)
		{
			newHeading[d] = outArm[d] + heading[d];
		}
		LinAlgHelpers.normalize(newHeading);
		System.arraycopy(newHeading, 0, oc.memDirectionVector, 0, 3);
		System.arraycopy(newHeading, 0, oc.lastDirectionVector, 0, 3);
	}
	
	/** Arms of the elongated line filter (current junction box) at ~sigma ahead of pGrid along heading:
	 * returns the outgoing arm, closest to the heading other than the incoming one 
	 * (the one pointing back along the heading), or null if there are fewer than 2 arms
	 * or none points forward. 
	 * heading and the returned arm are in physical space (the arm search itself runs in the grid) **/
	double [] findOutgoingArm(final double [] pGrid, final double [] heading)
	{
		final double [] headingGrid = oc.physToGridDir(heading);
		final double [] pAhead = pGrid.clone();
		for (int d = 0; d < 3; d++)
		{
			pAhead[d] += nJunctionRadius * headingGrid[d];
		}
		final ArrayList<double[]> armsGrid = box.hessBox.findArms(pAhead, armDirections, dAlongSigma, (int) Math.ceil(2.0 * dAlongSigma), dArmFraction, dArmSepDeg);
		if(armsGrid.size() < 2)
			return null;
		//arm directions to physical space
		final ArrayList<double[]> arms = new ArrayList<>();
		for(final double [] arm : armsGrid)
		{
			arms.add(oc.gridToPhysDir(arm));
		}
		//incoming arm: pointing back along the heading
		int nIncoming = 0;
		for (int i = 1; i < arms.size(); i++)
		{
			if(SteerableLineFilter.dot(arms.get(i), heading) < SteerableLineFilter.dot(arms.get(nIncoming), heading))
				nIncoming = i;
		}
		double [] outArm = null;
		double dBest = 0.0;
		for (int i = 0; i < arms.size(); i++)
		{
			if(i == nIncoming)
				continue;
			final double c = SteerableLineFilter.dot(arms.get(i), heading);
			if(c > dBest)
			{
				dBest = c;
				outArm = arms.get(i);
			}
		}
		return outArm;
	}

	/** starts coasting at the current point: heading = momentum, junction map, first coasting step **/
	RealPoint enterJunction(final RealPoint currpoint, final RandomAccess< T > raInput)
	{
		bCoasting = true;
		nCoastSteps = 0;
		bCoastEndedByDistance = false;
		//coasting runs in the grid: heading to grid space
		final double [] memGrid = oc.physToGridDir(oc.memDirectionVector);
		System.arraycopy(memGrid, 0, coastLineDir, 0, 3);
		System.arraycopy(memGrid, 0, coastDir, 0, 3);
		final long [] entryGrid = new long[3];
		for (int d = 0; d < 3; d++)
		{
			entryGrid[d] = oc.toGrid(currpoint.getDoublePosition(d), d);
		}
		computeJunctionMap(entryGrid);
		return coastStep(currpoint, raInput);
	}

	/** junction box centered ~2 sigma ahead of the entry along the entry direction,
	 * its hessian (full convolution) and the elongated line filter map **/
	void computeJunctionMap(final long [] entryGrid)
	{
		final long [] center = new long[3];
		for (int d = 0; d < 3; d++)
		{
			center[d] = Math.round(entryGrid[d] + 2.0 * dMaxSigmaGrid * coastLineDir[d]);
		}
		computeJunctionHessian(center);
		box.computeMap(coastLineDir, dAlongSigma);
		//debug visualization: the map in BVV instead of the raw data
		if(oc.bLiveUpdate && oc.bt.bOCVisDebugEnabled && oc.bt.ocVisDebug != null)
		{
			oc.bt.ocVisDebug.showJunctionMap(box.getMapForDisplay(oc.ds));
		}
		if(oc.bShowJunctionMap && oc.bLiveUpdate)
		{
			box.showMap("junction map at " + Long.toString(entryGrid[0]) + "," + Long.toString(entryGrid[1])
					+ "," + Long.toString(entryGrid[2]) + (oc.bDownsample ? " (grid)" : ""));
		}
	}

	/** junction box at center (grid) and its hessian **/
	void computeJunctionHessian(final long [] center)
	{
		box.setCenter(center);
		box.computeHessian(oc.getJunctionInput(box), oc.convObjects);
	}

	/** One coasting step along the elongated line filter map: one grid voxel along the entry
	 * direction, then sub-voxel parabolic re-centering of the map in the cross-section
	 * (this also follows a filament that bends slightly). Coasting continues for dCoastDistance
	 * along the entry direction, and past it until clear of existing ROIs (auto-tracing).
	 * The intensity threshold still applies, the traced mask does not (planPassage decides).
	 * Returns null if coasting can not continue (no positive response ahead, leaving the data
	 * or the junction box, intensity stop); coasting is then switched off. **/
	RealPoint coastStep(final RealPoint currpoint, final RandomAccess< T > raInput)
	{
		final double [] p = toGrid(currpoint);
		final double [] u = new double[3];
		final double [] v = new double[3];
		SteerableLineFilter.crossSectionBasis(coastLineDir, u, v);
		//step one grid voxel along e and re-center in the cross-section (sub-voxel)
		final double [] q = new double[3];
		for (int d = 0; d < 3; d++)
		{
			q[d] = p[d] + coastLineDir[d];
		}
		double m0 = box.mapValue(q);
		if(nCoastSteps == 0)
		{
			//fallback only: the start is off the ridge of the map (no response ahead),
			//climb to the nearest map maximum in the cross-section (up to ~sigma);
			//the map is specific to the entry direction, so it leads back onto the same filament.
			//Otherwise the offset is corrected gradually (no jump to the side)
			if(!(m0 > 0))
			{
				snapToMapRidge(p, u, v);
				for (int d = 0; d < 3; d++)
				{
					q[d] = p[d] + coastLineDir[d];
				}
				m0 = box.mapValue(q);
			}
			System.arraycopy(p, 0, coastStartPos, 0, 3);
		}
		//no line ahead: "wall"
		if(!(m0 > 0))
		{
			stopCoasting();
			return null;
		}
		//fading of the map along the passage
		if(nCoastSteps == 0)
		{
			dMapFirst = m0;
			dMapMinRatio = 1.0;
		}
		else
		{
			dMapMinRatio = Math.min(dMapMinRatio, m0 / dMapFirst);
		}
		final double ou = getMapParabolicOffset(q, u, m0);
		final double ov = getMapParabolicOffset(q, v, m0);
		final long [] gridPos = new long[3];
		for (int d = 0; d < 3; d++)
		{
			q[d] += ou * u[d] + ov * v[d];
			gridPos[d] = Math.round(q[d]);
			//(the math box is not updated while a passage is planned, the junction map is used)
			if(gridPos[d] < oc.gridMin[d] || gridPos[d] > oc.gridMax[d])
			{
				stopCoasting();
				return null;
			}
		}
		final long [] posFull = oc.toFullLong(gridPos);
		//intensity stop; existing ROIs (traced mask) are crossed while planning,
		//planPassage decides afterwards whether it was a crossing or a merge
		if(oc.bt.btData.bOCIntensityStop && raInput.setPositionAndGet( posFull ).getRealDouble() < oc.bt.btData.dOCIntensityThreshold)
		{
			stopCoasting();
			return null;
		}

		//coasting direction: chord from the coasting start (follows slight bends,
		//single re-centering offsets are averaged out), entry direction while too short
		double dLen = 0;
		for (int d = 0; d < 3; d++)
		{
			coastDir[d] = q[d] - coastStartPos[d];
			dLen += coastDir[d] * coastDir[d];
		}
		if(dLen >= 4.0)
		{
			LinAlgHelpers.normalize(coastDir);
		}
		else
		{
			System.arraycopy(coastLineDir, 0, coastDir, 0, 3);
		}
		System.arraycopy(oc.gridToPhysDir(coastDir), 0, oc.lastDirectionVector, 0, 3);
		nCoastSteps++;

		//leave the junction after a fixed distance along e (the map is valid for the whole
		//junction box, local eigenvectors/coherence are not: near a junction, especially a shallow
		//one, they look "aligned" while the crossing filament is still within reach of normal stepping)
		double dAlong = 0;
		for (int d = 0; d < 3; d++)
		{
			dAlong += (q[d] - coastStartPos[d]) * coastLineDir[d];
		}
		//passage across an existing ROI: keep coasting until clear of it
		final boolean bKeepClear = oc.bUseMask && dAlong >= dCoastDistance && nCoastSteps < nCoastMaxSteps && isNearTraced(q);
		if(!bKeepClear && (dAlong >= dCoastDistance || nCoastSteps >= nCoastMaxSteps))
		{
			bCoastEndedByDistance = dAlong >= dCoastDistance;
			stopCoasting();
		}

		final double [] outPos = new double[3];
		for (int d = 0; d < 3; d++)
		{
			//continuous grid -> full resolution (same as toFull for integer grid positions)
			outPos[d] = q[d] * oc.ds[d] + 0.5 * (oc.ds[d] - 1);
		}
		return new RealPoint(outPos);
	}

	/** leaves the junction mode, momentum continues along the coasting direction **/
	void stopCoasting()
	{
		bCoasting = false;
		bJunctionArmed = false;
		//grid -> physical space
		final double [] coastPhys = oc.gridToPhysDir(coastDir);
		System.arraycopy(coastPhys, 0, oc.memDirectionVector, 0, 3);
		System.arraycopy(coastPhys, 0, oc.lastDirectionVector, 0, 3);
	}

	/** moves p in the cross-section plane (u,v) to the nearest local maximum of the map:
	 * hill climbing with half voxel steps, up to nJunctionRadius away **/
	void snapToMapRidge(final double [] p, final double [] u, final double [] v)
	{
		final double [] test = new double[3];
		final double [] start = p.clone();
		double dBest = box.mapValue(p);
		if(Double.isNaN(dBest))
			return;
		final int nMaxIter = 4 * nJunctionRadius;
		for (int nIter = 0; nIter < nMaxIter; nIter++)
		{
			final double [] center = p.clone();
			boolean bMoved = false;
			for (int a = -1; a <= 1; a++)
			{
				for (int b = -1; b <= 1; b++)
				{
					if(a == 0 && b == 0)
						continue;
					double distSq = 0;
					for (int d = 0; d < 3; d++)
					{
						test[d] = center[d] + 0.5 * (a * u[d] + b * v[d]);
						distSq += (test[d] - start[d]) * (test[d] - start[d]);
					}
					if(distSq > nJunctionRadius * nJunctionRadius)
						continue;
					final double val = box.mapValue(test);
					if(val > dBest)
					{
						dBest = val;
						System.arraycopy(test, 0, p, 0, 3);
						bMoved = true;
					}
				}
			}
			if(!bMoved)
				break;
		}
	}

	/** sub-voxel offset of the maximum of the map along unit vector w through q,
	 * parabolic fit of M(q-w), M(q), M(q+w). Limited to [-0.5,0.5] per step,
	 * so an offset is corrected over a few steps instead of a visible jump to the side **/
	double getMapParabolicOffset(final double [] q, final double [] w, final double m0)
	{
		final double [] x = new double[3];
		for (int d = 0; d < 3; d++)
		{
			x[d] = q[d] - w[d];
		}
		final double mm = box.mapValue(x);
		for (int d = 0; d < 3; d++)
		{
			x[d] = q[d] + w[d];
		}
		final double mp = box.mapValue(x);
		if(Double.isNaN(mm) || Double.isNaN(mp))
			return 0.0;
		final double denom = mm - 2.0 * m0 + mp;
		//not a maximum (on a flank): move half a voxel uphill
		if(denom >= 0)
		{
			return mp > mm ? 0.5 : (mp < mm ? -0.5 : 0.0);
		}
		return Math.max(-0.5, Math.min(0.5, 0.5 * (mm - mp) / denom));
	}

	//---------- traced mask (auto-tracing)

	/** true if the grid position (continuous) is occupied in the traced mask **/
	boolean isTraced(final double [] gridPos)
	{
		if(!oc.bUseMask)
			return false;
		final long [] g = new long[3];
		for (int d = 0; d < 3; d++)
		{
			g[d] = Math.round(gridPos[d]);
		}
		return oc.traceMask.isOccupied( oc.toFullLong(g) );
	}

	/** true if the traced mask is occupied at the grid position or ~sigma around it
	 * in the cross-section of the coasting direction **/
	boolean isNearTraced(final double [] gridPos)
	{
		if(!oc.bUseMask)
			return false;
		final double [] u = new double[3];
		final double [] v = new double[3];
		SteerableLineFilter.crossSectionBasis(coastLineDir, u, v);
		final double r = dPassageClearFactor * dMaxSigmaGrid;
		final double [] x = new double[3];
		for (int k = 0; k < 5; k++)
		{
			final double a = (k == 1) ? r : ((k == 2) ? -r : 0.0);
			final double b = (k == 3) ? r : ((k == 4) ? -r : 0.0);
			for (int d = 0; d < 3; d++)
			{
				x[d] = gridPos[d] + a * u[d] + b * v[d];
			}
			if(isTraced(x))
				return true;
		}
		return false;
	}

	/** true if an existing ROI (traced mask) is 1-2 grid voxels ahead along the momentum **/
	boolean isTracedAhead(final RealPoint currpoint)
	{
		if(!oc.bUseMask)
			return false;
		final double [] x = new double[3];
		final double [] memGrid = oc.physToGridDir(oc.memDirectionVector);
		for (int s = 1; s <= 2; s++)
		{
			for (int d = 0; d < 3; d++)
			{
				x[d] = oc.toGridDouble(currpoint.getDoublePosition(d), d) + s * memGrid[d];
			}
			if(isTraced(x))
				return true;
		}
		return false;
	}

	//---------- start at a junction (auto-tracing)

	/** Auto-tracing seeds are the brightest voxels, often at junctions, where the start direction
	 * is ambiguous. If the (refined) start is at a junction, i.e. the elongated line filter has
	 * 3 or more arms around it, returns a start ~4-6 sigma out along one of the arms
	 * (strongest first) on a plain filament (at most 2 arms, not traced yet); otherwise null.
	 * Two stage test: short rays in the math box of the start (cheap), then long rays
	 * in a junction box (full convolution) to confirm.
	 * Changes the math box (OneClickTrace.getMathForCurrentPoint), the caller recomputes it. **/
	RealPoint relocateStart(final RealPoint start)
	{
		final double [] p = toGrid(start);
		final double dShortAlong = 1.5 * dMaxSigmaGrid;
		final int nShortRay = Math.max(2, (int) Math.ceil(3.0 * dMaxSigmaGrid));
		//1) cheap test in the math box of the start (short rays)
		if(new HessianBox(oc.hessFloat, oc.minV).findArms(p, armDirections, dShortAlong, nShortRay, dArmFraction, dArmSepDeg).size() < 3)
			return null;
		//2) confirm with long rays in a junction box centered at the start
		final long [] center = new long[3];
		for (int d = 0; d < 3; d++)
		{
			center[d] = Math.round(p[d]);
		}
		computeJunctionHessian(center);
		final ArrayList<double[]> arms = box.hessBox.findArms(p, armDirections, dAlongSigma, (int) Math.ceil(2.0 * dAlongSigma), dArmFraction, dArmSepDeg);
		if(arms.size() < 3)
			return null;
		//3) candidate starts out along the arms (strongest first)
		final double [] q = new double[3];
		for(final double [] arm : arms)
		{
			for(final double dFactor : dRelocateFactors)
			{
				boolean bInside = true;
				for (int d = 0; d < 3; d++)
				{
					q[d] = p[d] + dFactor * dMaxSigmaGrid * arm[d];
					if(q[d] < oc.gridMin[d] || q[d] > oc.gridMax[d])
						bInside = false;
				}
				if(!bInside)
					continue;
				final RealPoint qFull = toFull(q);
				oc.getMathForCurrentPoint(qFull);
				final RealPoint q2 = oc.refinePointUsingSaliency(qFull, 1.0f);
				if(oc.bUseMask && oc.traceMask.isOccupied(q2))
					continue;
				oc.getMathForCurrentPoint(q2);
				if(new HessianBox(oc.hessFloat, oc.minV).findArms(toGrid(q2), armDirections, dShortAlong, nShortRay, dArmFraction, dArmSepDeg).size() <= 2)
				{
					return q2;
				}
			}
		}
		return null;
	}

	//---------- coordinates

	/** full resolution point -> continuous grid coordinates **/
	double [] toGrid(final RealPoint pt)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = oc.toGridDouble(pt.getDoublePosition(d), d);
		}
		return out;
	}

	/** continuous grid coordinates -> full resolution point **/
	RealPoint toFull(final double [] g)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = g[d] * oc.ds[d] + 0.5 * (oc.ds[d] - 1);
		}
		return new RealPoint(out);
	}
}
