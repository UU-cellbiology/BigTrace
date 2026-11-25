package bigtrace.math;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bigtrace.BigTrace;
import bigtrace.BigTraceData;
import bigtrace.geometry.Pipe3D;
import bigtrace.BigTraceBGTask;
import bigtrace.volume.VolumeMisc;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Roi3D;

import net.imglib2.AbstractInterval;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.algorithm.convolution.kernel.Kernel1D;
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class OneClickTrace < T extends RealType< T > & NativeType< T > > extends BigTraceBGTask
{
	public BigTrace<T> bt;
	
	/** full dataset to trace **/
	public IntervalView<T> fullInput; 
	
	/** if a tracing leaving this box,
	 * new full box is recalculated at the current location **/
	public FinalInterval innerTraceBox; 
	
	public int nTimePoint = -1;
	
	public RealPoint startPoint;
	
	public boolean bUpdateProgressBar = true;
	
	/** if true (default, interactive one-click), the ROI render geometry is rebuilt 
	 * and the viewer repainted after every segment, with a short pause to let UI update.
	 * Set to false for batch tracing (full auto): curve geometry is built 
	 * once at the end of each tracing direction, no pauses. **/
	public boolean bLiveUpdate = true;
	
	public boolean bUnlockInTheEnd = true;
	
	public RealPoint currentVector;
	
	public long [] boxFullHalfRange;
	
	public long [] boxInnerHalfRange;

	/** dimensions of the box where saliency + vectors 
	 * will be calculated in rangeFullBoxDim * sigma of the axis **/
	final double rangeFullBoxDim = 3.0;
	
	/** dimensions of the box where tracing will happen
	 	(in sigmas) before new box needs to be calculated**/
	final double rangeInnerBoxDim = 1.0;
	
	final long [] minV = new long [4];
	
	public double dAngleThreshold;// = 0.8;
	
	int nNeighborsMethods = 0;
	
	/** after this amount of points new segment will be added to LineTrace ROI **/
	int nPointPerSegment;
	
	final int [][] nNeighborsIndexes = new int[26][3];
	
	final double [][] nNeighborsVectors = new double[26][3];

	
	/** same as neighborsMap, but indexed by (d1+1)*9+(d2+1)*3+(d3+1), avoids string building **/
	@SuppressWarnings( "unchecked" )
	private final ArrayList<int[]>[] neighborsArr = new ArrayList[27];
	
	/** vector of curve direction for the last point, physical space, unit length.
	 * All directions (eigenvectors from getVectorAtGrid, last, momentum) are kept in physical space,
	 * so angles (directionality constrain, stiffness scores, junction coherence) are real angles
	 * also for anisotropic voxels. They are converted to grid space (physToGridDir) only
	 * where they are used as displacements on the grid (neighbors, look-ahead positions, cross-sections) **/
	final double [] lastDirectionVector = new double[3];
	
	/** "history" stepping (effective stiffness > 0): the momentum direction is the reference
	 * for neighbor choice and directionality, steps are scored geometrically, 
	 * position is re-centered on the ridge. If false, original stepping (last eigenvector). **/
	boolean bUseHistory = false;
	
	/** junction (crossing) detection and passage, uses the momentum direction as heading **/
	boolean bJunctions = false;
	
	/** junction passage: branch-first (JunctionBranchPassage) instead of the map coasting **/
	boolean bJunctionBranchFirst = true;
	
	/** junction passage by the map coasting: refined end and planned passage (JunctionBranchPassage.planToCoastEnd) **/
	boolean bJunctionCoastRefine = true;
		
	/** recent points (full resolution) of the trace in the current direction, newest last
	 * (heading fit of the branch-first junction passage) **/
	final ArrayDeque<double[]> dirHistory = new ArrayDeque<>();
	
	/** the last point returned by getNextPoint is a junction passage point (not checked for self intersection) **/
	boolean bLastFromPassage = false;
	
	/** maximum length of dirHistory **/
	static final int nDirHistoryMax = 1000;
	
	/** smoothed (momentum) direction of the traced path, physical space, unit length.
	 * Built mostly from actual step vectors, so a few bad Hessian eigenvectors
	 * at a crossing cannot flip it **/
	final double [] memDirectionVector = new double[3];
	
	/** path stiffness s (0-1) as set by the user, a single "summary" parameter **/
	double dStiffnessSet = 0.0;
	
	/** effective path stiffness, the four below are derived from it in updateStepping().
	 * s = 0: original stepping. With junction handling on, it is at least dJunctionMinStiffness **/
	double dStiffness = 0.0;
	
	/** scaling of the stiffness for the downsampled grid, 1/max(ds).
	 * Stiffness acts per step (heading memory, step/orientation scoring), 
	 * a grid step is ds times longer, so the same value would be ~ds times stiffer 
	 * per pixel and cut bends (tested on synthetic thick filaments: s 0.5 at ds 3 
	 * lost a tight bend, s 0.5/3 followed it and still passed a 35 degree crossing) **/
	double dStiffnessGridScale = 1.0;
	
	/** memory of momentum direction (0 = no memory, 1 = never changes) **/
	double dMomentumAlpha = 0.6;
	
	/** share of the eigenvector (vs step vector) in momentum update **/
	double dMomentumEigenShare = 0.25;
	
	/** power of |cos| between candidate eigenvector and momentum in score **/
	double dScorePowEig = 2.0;
	
	/** power of cos between candidate step and momentum in score **/
	double dScorePowStep = 2.0;
	
	/** maximum number of hill-climbing steps (grid voxels) in recenterOnRidge,
	 * about the filament radius = max tracing sigma in grid units **/
	int nRecenterMaxSteps = 1;
	
	/** minimum stiffness of stepping with junction handling on.
	 * With pure saliency stepping (stiffness 0) the trace turns onto a brighter crossing 
	 * filament before the junction is detected ahead; a little history keeps it straight 
	 * up to the junction (tested on ExM crossings: 0.1 is enough, kinks are traced 
	 * as with the original stepping) **/
	static final double dJunctionMinStiffness = 0.1;
	
	/** gap bridging (dotty lines): max look-ahead when stepping finds no candidate,
	 * in units of tracing sigma (per axis), 0 = off **/
	double dGapBridgeSigma = 0.0;
	
	/** gap bridging: landing voxel saliency must be at least this fraction 
	 * of the running average saliency of the trace (avoids jumping onto background) **/
	static final double dGapMinRelSal = 0.3;
	
	/** normal stepping found no continuation: with junction handling on, the junction passage is planned there
	 * first (a sharp kink or a junction not detected ahead), then the gap bridging **/
	boolean bStallJunctionPassage = true;
	
	/** branch-first junction passage: normal stepping is tried through the junction first (probeNormalStepping,
	 * JunctionBranchPassage.planByProbe); its path is used if it reaches a branch found on the half sphere
	 * that turns not much more than the least turning one. Off: in tests it often took the wrong filament **/
	boolean bJunctionProbe = false;

	/** branch-first junction passage: if the passages to all branches are lost and normal stepping through
	 * the junction ends inside the searched half sphere (the end of the filament), the trace follows it to its end
	 * (instead of stopping at the junction) **/
	boolean bJunctionProbeEnd = true;

	/** true during probeNormalStepping (normal stepping without junction handling, gap bridging, visualization) **/
	boolean bProbing = false;

	/** probeNormalStepping: the last point is beyond the radius (otherwise the stepping stopped before) **/
	boolean bProbeReached = false;

	/** probeNormalStepping: momentum, line direction (physical) and running saliency at its last point **/
	final double [] probeMem = new double[3];
	final double [] probeLast = new double[3];
	double dProbeSal = 0;

	/** gap bridging by a planned passage (JunctionBranchPassage.planGap: ridge centres ahead within the reach, 
	 * least turning curve to them, followed over the gap) instead of the jump to the best voxel in the 
	 * look-ahead cone (bridgeGap) **/
	boolean bGapBridgePassage = true;
	
	/** running (exponential) average of saliency along the current trace direction **/
	double dSalTrace = 0.0;
	
	/** stall (kink) recovery is on **/
	boolean bKinkRecovery = false;
	
	/** stall recovery: max look-ahead distance in tracing sigmas (per axis) **/
	static final double dRecoverReachSigma = 2.0;
	
	/** stall recovery: min |cos| between the line orientation at the landing voxel
	 * (and at the confirmation probe) and the direction of the jump **/
	static final double dRecoverAlignCos = 0.8;
	
	/** physical size of a grid voxel (calibration x downsampling factor).
	 * Used to convert directions between grid and physical space: 
	 * with anisotropic voxels, a direction in grid space makes a step in z look much straighter than it is **/
	final double [] gridVoxelSize = new double [] {1.0, 1.0, 1.0};
	
	/** sigma of the hessian kernels in grid voxels **/
	final double [] sigmaGridV = new double[3];
	
	/** gap bridging: cosine of the half-angle of the look-ahead cone (~37 deg),
	 * also the minimum agreement of the landing voxel orientation with the heading **/
	static final double dGapConeCos = 0.8;
	
	/** junction (crossing) handling (bJunctions) **/
	final JunctionHandler<T> junctions = new JunctionHandler<>(this);
	
	/** if true (auto-tracing), a junction passage that ends at the end of another line trace
	 * continuing in the same direction merges the two traces into one ROI **/
	public boolean bMergeTraceEnds = false;
	
	/** if true (auto-tracing), a start at a junction is moved out along one of its arms **/
	public boolean bRelocateJunctionStart = false;
	
	/** maximum sideways hill-climbing steps (grid voxels) of re-centering per tracing step.
	 * 1 = offsets are corrected gradually (larger values make visible jumps and can pull 
	 * the trace onto a neighboring bright structure, tested on ExM microtubule data) **/
	int nRecenterHillSteps = 1;
	
	/** re-centering on plain filaments may only move to voxels oriented within ~25 deg 
	 * of the momentum (same filament, not onto a crossing one) **/
	final double dRecenterAngleCos = 0.9;
	
	/** re-centering by Steger's method (recenterSteger: sub-voxel line centre from the gradient and hessian
	 * in the plane perpendicular to the local line direction) before the saliency hill-climbing (recenterOnRidge) **/
	boolean bStegerRecenter = true;
	
	/** Steger re-centering: maximum offset of the centre from the point one step ahead (tracing sigmas, per axis) **/
	double dStegerMaxSD = 1.0;

	/** stepping by Steger's linking of line points (stegerLinkStep) instead of the saliency choice of a neighbor
	 * followed by re-centering (history stepping only); the latter remains the fallback **/
	boolean bStegerLinking = true;

	/** Steger's linking: weight c of the angle difference (radians) in the cost d + c * beta,
	 * d is the distance between the line points in the smallest voxel size (Steger: c = 1, pixels) **/
	double dLinkAngleWeight = 1.0;

	/** Steger's linking: maximum offset of a line point from its voxel centre (grid voxels, per axis; Steger: 0.5) **/
	double dLinkVoxelTol = 0.5;

	/** Steger's linking: minimum progress of the next line point along the momentum (smallest voxel size) **/
	double dLinkMinForward = 0.25;

	/** Steger's linking: minimum cosine between the step to the next line point and the momentum
	 * (Steger, 2D: the neighbors within 67.5 deg of the line direction) **/
	double dLinkMinStepCos = 0.5;
	
	/** input of the convolutions of the current math box (border extended, grid coordinates), see getBoxInput **/
	RandomAccessible<?> mathBoxInput = null;
	
	/** Gaussian smoothing masks (tracing sigma on the grid, per axis; centered) for smoothedAt **/
	double [][] smoothKernels = null;
	
	/** if true, the junction map is shown in ImageJ when entering a junction 
	 * (interactive one-click only, not in batch/auto tracing) **/
	public boolean bShowJunctionMap = false;
	
	ArrayList<double[]> allPointsIntersection;
	
	/** spatial hash of allPointsIntersection: key is packed rounded voxel position,
	 * used for fast self-intersection check **/
	final HashMap<Long, ArrayList<double[]>> intersectionGrid = new HashMap<>();
	
	@SuppressWarnings( "rawtypes" )
	Convolution [] convObjects = new Convolution[6];
	
	/** margin (voxels of the convolution input, full resolution or grid) around a box 
	 * needed by the hessian convolutions (convObjects) **/
	final long [] convMargin = new long[3];
	ExecutorService es = null;// = Executors.newFixedThreadPool( nThreads );
	int nThreads;
	
	/** if running in auto-trace**/
	public boolean bUseMask = false;

	/** "trace mask" containing areas marked by existing ROIs **/
	public RoiTraceMask<T> traceMask = null;
	
	/** whether to initialize internally **/
	public boolean bInit = true;
	
	/** if the optimized location is already occupied in the tracemask **/
	boolean bStartLocationOccupied = false;
	
	float fEstimatedThickness = 5.0f;
	
	/** math box around the current point: hessian, saliency and direction at its voxels (grid) **/
	MathBox mathBox;
	
	/** line directions (grid space, unit; x, y, z, component) and saliency of the math box, grid coordinates **/
	IntervalView<FloatType> directionVectors;
	IntervalView<FloatType> salWeights;
	RandomAccess<FloatType> raV;
	RandomAccess<FloatType> raW;

	/** whether we are starting a new trace or continue with existing **/
	public boolean bNewTrace;
	
	/** if true, inserts ROI to the list with nInsertROIInd  index,
	 * otherwise just adds ROI to the end **/
	public boolean bInsertROI = false;
	
	public int nInsertROIInd = 0;
	
	ArrayImg<FloatType, FloatArray> gradFloat;
	
	/** Tracing math (hessian, saliency, vectors, stepping) is done on the "grid".
	 * Grid voxel g along axis d covers full resolution voxels [g*ds[d], g*ds[d]+ds[d]-1].
	 * With ds = {1,1,1} (default, downsampling off) grid == full resolution
	 * and tracing is identical to the original method. **/
	final long [] ds = new long [] {1, 1, 1};
	
	/** true if any of ds is >1 **/
	boolean bDownsample = false;
	
	/** half size of the math box in grid voxels (used only when downsampling) **/
	final long [] boxGridHalfRange = new long[3];
	
	/** bounds of fullInput in grid coordinates **/
	final long [] gridMin = new long[3];
	final long [] gridMax = new long[3];
	
	
	private LineTrace3D currentTracing;
	
	
	@Override
	protected Void doInBackground() throws Exception 
	{		
		runTracing();
		return null;
	}
	
	public void runTracing()
	{
		if(bLiveUpdate && bt.bOCVisDebugEnabled && bt.ocVisDebug != null)
		{
			bt.ocVisDebug.clear();
		}
		currentTracing = null;
		junctions.resetTrace();
		
		if(bUpdateProgressBar)
		{
			setProgress(0);
		}
		
		if(bInit)
		{
			init();
		}
		
		//long start1, end1;		
		//start1 = System.currentTimeMillis();
		
		bt.visualBoxes.bShowTraceBox = true;
		
		//in case we continue tracing
		if(!bNewTrace)
		{
			currentTracing  = (LineTrace3D)bt.roiManager.getActiveRoi();
			//get last point
			startPoint = new RealPoint(currentTracing.vertices.get(currentTracing.vertices.size() - 1));
		}
		//init math
		getMathForCurrentPoint(startPoint);
		if(bt.bOCVisDebugEnabled)
		{
			System.out.println("startPoint  " + startPoint .toString());
		}
		if(bNewTrace)
		{
			final RealPoint startRefinedPoint = refinePointUsingSaliency(startPoint);
			if(bt.bOCVisDebugEnabled)
			{
				System.out.println("startRefinedPoint  " + startRefinedPoint .toString());
			}
			//masked tracing
			//check if already traced
			if(bUseMask)
			{
				bStartLocationOccupied = false;
				if(traceMask.isOccupied( startRefinedPoint ))
				{
					traceMask.markInterval(  getLocalSearchArea(startPoint, 2.0f));
					bStartLocationOccupied = true;
					return;
				}	
			}
			//System.out.println("ini"+startPoint.toString() +" refined"+startRefinedPoint.toString());
			startPoint = startRefinedPoint;
			
			getMathForCurrentPoint(startPoint);
			
			//auto-tracing: a start at a junction is ambiguous, 
			//move it out along one of the arms of the junction
			if(bRelocateJunctionStart && bJunctions)
			{
				final RealPoint relocatedPoint = junctions.relocateStart(startPoint);
				if(relocatedPoint != null)
				{
					startPoint = relocatedPoint;
				}
				getMathForCurrentPoint(startPoint);
			}
		}
		
		double [] startDirectionVector = getVectorAtLocation(startPoint);
		
		for (int d = 0; d < 3; d++)
		{
			lastDirectionVector[d] = startDirectionVector[d];
		}
		
		//allPointsIntersection = new ArrayList<>();
		resetIntersectionPoints();
		
		int nTotPoints = 0;
		//we continue tracing, let's setup environment for that
		if(!bNewTrace)
		{
			//see so we do not trace in the same direction as existing curve
			//get the vector of the last direction at the end of the curve
			if(currentTracing.vertices.size() > 1)
			{
				final int lastSegmIndex = currentTracing.segments.size()-1;
				double [] prevDirection = new double[3];
				if(currentTracing.segments.get(lastSegmIndex).size() > 1)
				{
					final int prevPointIndex = currentTracing.segments.get(lastSegmIndex).size()-2;
					currentTracing.segments.get(lastSegmIndex).get(prevPointIndex).localize(prevDirection);
				}
				else
				{
					currentTracing.vertices.get(currentTracing.vertices.size()-2).localize(prevDirection);
				}
				LinAlgHelpers.subtract(startPoint.positionAsDoubleArray(), prevDirection, prevDirection);
				//direction vectors live in physical space (full resolution voxel -> physical)
				for(int d = 0; d < 3; d++)
				{
					prevDirection[d] *= gridVoxelSize[d] / ds[d];
				}
				LinAlgHelpers.normalize(prevDirection);
				final double sameDir = LinAlgHelpers.dot(prevDirection, lastDirectionVector);
				LinAlgHelpers.scale(prevDirection, -1.0, prevDirection);
				final double oppDir = LinAlgHelpers.dot(prevDirection, lastDirectionVector);
				//flip the lastDirectionVector
				if(oppDir>sameDir)
				{
					LinAlgHelpers.scale(lastDirectionVector, -1.0, lastDirectionVector);
				}				
			}
			//fill array of previous point
			resetIntersectionPoints();
			for(final double [] pt : currentTracing.makeJointSegmentDouble())
			{
				addIntersectionPoint( pt );
			}
			nTotPoints = allPointsIntersection.size();
		}	

		if(bNewTrace)
		{
			resetIntersectionPoints();
			//trace in one direction
			nTotPoints = traceOneDirection(true, 0);
			if(nTotPoints < 0)
			{
				setProgressState("Tracing interrupted by user.");
				return;
			}
			if(bUpdateProgressBar)
			{
				setProgress(50);
				setProgressState(Integer.toString(nTotPoints) + "points found.");
			}
			//look for another direction
			for (int d = 0; d < 3; d++)
			{
				lastDirectionVector[ d ] = (-1) * startDirectionVector[d];
			}
			
			//reverse ROI
			currentTracing.reversePoints();
			
			//init math at new point
			getMathForCurrentPoint(startPoint);
		}
		//trace in the other direction
		nTotPoints = traceOneDirection(false, nTotPoints);
		//could not find anything
		if(nTotPoints == 0 && bNewTrace)
		{
			bt.roiManager.removeRoi( currentTracing ); 
			if(bUseMask)
				bStartLocationOccupied = true;
		}
		if(nTotPoints < 0)
		{
			setProgressState("Tracing interrupted by user.");
			return;
		}
		//merge with traces found at the ends of junction passages
		junctions.applyMerges();
		//end1 = System.currentTimeMillis();
		//System.out.println("THREADED Elapsed Time in seconds: "+ 0.001*(end1-start1));
		
	
		if(bUpdateProgressBar)
		{
			setProgress(100);	
			setProgressState("trace with "+Integer.toString(nTotPoints)+" points. auto-tracing done.");
		}
		return ;
	}
	
	/** the ROI created by the last runTracing() (may have been removed again) **/
	public LineTrace3D getCurrentTracing()
	{
		return currentTracing;
	}
	
	/** traces line in one direction. Returns number of current found points
	 * or -1 if it got interrupted.
	 * @param bFirstTrace 
	 *        if it is a first trace or continuation of existing 
	 * @param nCountIn
	 *        if it is a continuation, how many vertices in ROI already **/
	public int traceOneDirection(boolean bFirstTrace, int nCountIn)
	{
		
		ArrayList<RealPoint> points = new ArrayList<>();
		
		RealPoint nextPoint;
		
		int nCountPoints = 0;
		//momentum starts from the (already oriented) direction
		for (int d = 0; d < 3; d++)
		{
			memDirectionVector[d] = lastDirectionVector[d];
		}
		points.add(startPoint);
		dirHistory.clear();
		addDirHistory(startPoint);
		
		//saliency reference for gap bridging
		final long [] startGrid = new long[3];
		for (int d = 0; d < 3; d++)
		{
			startGrid[d] = Math.min(salWeights.max(d), Math.max(salWeights.min(d), toGrid(startPoint.getDoublePosition(d), d)));
		}
		dSalTrace = raW.setPositionAndGet(startGrid).get();
		
		junctions.resetDirection();
		
		addIntersectionPoint(startPoint.positionAsDoubleArray());
		
		if(bFirstTrace)
		{
			//LineTrace3D newTracing;
			if(nTimePoint >= 0)
				currentTracing = (LineTrace3D) bt.roiManager.makeRoi(Roi3D.LINE_TRACE, nTimePoint);				
			else
				currentTracing = (LineTrace3D) bt.roiManager.makeRoi(Roi3D.LINE_TRACE, bt.btData.nCurrTimepoint);
			if(bt.btData.bEstimateROIThicknessFromParams)
			{
				currentTracing.setLineThickness( fEstimatedThickness );
			}
			currentTracing.addFirstPoint(points.get(0));
			if(!bInsertROI)
			{
				bt.roiManager.addRoi(currentTracing);
			}
			else
			{
				bt.roiManager.insertRoi(currentTracing, nInsertROIInd);
			}
		}
		else
		{
			nCountPoints = nCountIn;
		}
		
		nextPoint = getNextPoint(points.get(0));
		visDebugStep(nextPoint);
		
		//points inside the current math box
		while (nextPoint != null)
		//while (nextPoint != null && testCount<100)
		{
			if(points.size() == nPointPerSegment)
			{
				RealPoint lastSegmentPoint = points.get(points.size()-1).positionAsRealPoint();

				if(bLiveUpdate)
				{
					currentTracing.addPointAndSegment( points.get(points.size() - 1), points);
					bt.repaintBVV();
				}
				else
				{
					currentTracing.addPointAndSegmentNoUpdate( points.get(points.size() - 1), points);
				}

				points = new ArrayList<>();
				points.add( lastSegmentPoint );
				if(bUpdateProgressBar)
				{
					setProgressState(Integer.toString(nCountPoints)+"points found.");
					if(bFirstTrace)
					{
						setProgress(20);
					}
					else
					{
						setProgress(70);
					}
				}
				if(bLiveUpdate)
				{
					try {
						Thread.sleep(1);
					} catch (InterruptedException ignore) {}
				}
			}
			points.add(new RealPoint(nextPoint));
			addDirHistory(nextPoint);
			addIntersectionPoint(nextPoint.positionAsDoubleArray());
			nCountPoints++;
			//see if we are still inside reasonable 
			//values for math box
			if(!Intervals.contains(innerTraceBox, points.get(points.size()-1)) )
			{
				getMathForCurrentPoint(points.get(points.size()-1));
			}

			nextPoint = getNextPoint(points.get(points.size()-1));
			visDebugStep(nextPoint);
			
			//(passage points through a junction are not checked: a planned passage, e.g. through
			//a bend in z, can come close to the points just traced; they are still recorded)
			if(nextPoint != null && !bLastFromPassage)
			{
				if(checkIntersection(nextPoint))
				{
					nextPoint = null;
					if(!bUseMask)
						System.out.println("one-click tracing stopped, self intersection found.");
				}
			}

			if(isCancelled())
			{
				return -1;				
			}
		}
		//adding last part of the trace
		if(bLiveUpdate)
		{
			currentTracing.addPointAndSegment( points.get(points.size() - 1), points );
		}
		else
		{
			currentTracing.addPointAndSegmentNoUpdate( points.get(points.size() - 1), points );
			//build render geometry once for the whole direction
			currentTracing.updateRenderVertices();
		}
		bt.repaintBVV();
		return nCountPoints;
	}
	
	/** Dry run of the normal stepping (getNextPoint without junction handling and gap bridging) from currpoint,
	 * as the tracing loop does it (direction history, traced points, math box): until a point is farther than
	 * dRadiusSD SDs (sdPhys per axis, physical: an ellipsoid) from centre (physical), the stepping stops
	 * (no step, self intersection) or after nMaxSteps. Returns the points (full resolution); bProbeReached:
	 * the last one is beyond the radius; probeMem, probeLast, dProbeSal: the state at the last point.
	 * The state of the tracing is restored afterwards (the math box is recomputed at currpoint if it moved). **/
	ArrayList<RealPoint> probeNormalStepping(final RealPoint currpoint, final double [] centre, final double [] sdPhys,
			final double dRadiusSD, final int nMaxSteps)
	{
		final double [] memBefore = memDirectionVector.clone();
		final double [] lastBefore = lastDirectionVector.clone();
		final double dSalBefore = dSalTrace;
		final ArrayList<double[]> histBefore = new ArrayList<>(dirHistory);
		final long [] boxMinBefore = mathBox.boxMin.clone();
		final boolean bJunctionsBefore = bJunctions;
		final double dGapBefore = dGapBridgeSigma;
		final boolean bPassageBefore = bLastFromPassage;
		bJunctions = false;
		dGapBridgeSigma = 0;
		bProbing = true;

		final ArrayList<RealPoint> out = new ArrayList<>();
		final ArrayList<double[]> added = new ArrayList<>();
		bProbeReached = false;
		RealPoint cur = currpoint;
		try
		{
			for (int n = 0; n < nMaxSteps; n++)
			{
				final RealPoint next = getNextPoint(cur);
				if(next == null || checkIntersection(next))
					break;
				out.add(next);
				addDirHistory(next);
				final double [] q = next.positionAsDoubleArray();
				addIntersectionPoint(q);
				added.add(q);
				double dR = 0;
				for (int d = 0; d < 3; d++)
				{
					final double r = (toGridDouble(q[d], d) * gridVoxelSize[d] - centre[d]) / sdPhys[d];
					dR += r * r;
				}
				if(Math.sqrt(dR) >= dRadiusSD)
				{
					bProbeReached = true;
					break;
				}
				cur = next;
				if(!Intervals.contains(innerTraceBox, cur))
				{
					getMathForCurrentPoint(cur);
				}
			}
		}
		finally
		{
			System.arraycopy(memDirectionVector, 0, probeMem, 0, 3);
			System.arraycopy(lastDirectionVector, 0, probeLast, 0, 3);
			dProbeSal = dSalTrace;
			System.arraycopy(memBefore, 0, memDirectionVector, 0, 3);
			System.arraycopy(lastBefore, 0, lastDirectionVector, 0, 3);
			dSalTrace = dSalBefore;
			dirHistory.clear();
			dirHistory.addAll(histBefore);
			for (int i = added.size() - 1; i >= 0; i--)
			{
				removeIntersectionPoint(added.get(i));
			}
			bJunctions = bJunctionsBefore;
			dGapBridgeSigma = dGapBefore;
			bLastFromPassage = bPassageBefore;
			bProbing = false;
			boolean bMoved = false;
			for (int d = 0; d < 3; d++)
			{
				bMoved |= (mathBox.boxMin[d] != boxMinBefore[d]);
			}
			if(bMoved)
			{
				getMathForCurrentPoint(currpoint);
			}
		}
		return out;
	}

	/** adds a traced point to dirHistory (keeps at most nDirHistoryMax) **/
	void addDirHistory(final RealPoint pt)
	{
		dirHistory.addLast(pt.positionAsDoubleArray());
		if(dirHistory.size() > nDirHistoryMax)
		{
			dirHistory.removeFirst();
		}
	}
	
	/** initialization of parameters **/
	@SuppressWarnings("deprecation")
	public void init()
	{	
		
		setStiffness(bt.btData.dOCStiffness);
		setJunctions(bt.btData.bOCJunctions);
		bJunctionBranchFirst = bt.btData.bOCJunctionBranchFirst;
		bJunctionCoastRefine = bt.btData.bOCJunctionCoastRefine;

		dAngleThreshold = bt.btData.dDirectionalityOneClick;		
		
		//downsampling factors and sigma of the kernels on the grid
		final double [] sigmaGrid = new double[3];
		long dsMax = 1;
		bDownsample = false;
		for (int d = 0; d < 3; d++)
		{
			final double s = bt.btData.sigmaTrace[d];
			ds[d] = 1;
			sigmaGrid[d] = s;
			if(bt.btData.bTraceDownsample)
			{
				ds[d] = Math.max(1, Math.round(s / bt.btData.dDownsampleSigma));
				if(ds[d] > 1)
				{
					bDownsample = true;
					//block averaging by ds already blurs with variance (ds^2-1)/12 (full res px^2),
					//subtract it and convert to grid units
					sigmaGrid[d] = Math.sqrt(Math.max(0.25, s * s - (ds[d] * ds[d] - 1) / 12.0)) / ds[d];
				}
			}
			dsMax = Math.max(dsMax, ds[d]);

		}
		//re-centering can climb about one filament radius sideways
		double dMaxSigmaGrid = 0;
		for (int d = 0; d < 3; d++)
		{
			dMaxSigmaGrid = Math.max(dMaxSigmaGrid, sigmaGrid[d]);
		}
		nRecenterMaxSteps = Math.max(1, (int) Math.ceil(dMaxSigmaGrid));
		
		//same stiffness per pixel on the downsampled grid
		dStiffnessGridScale = 1.0 / dsMax;
		updateStepping();
		//gap bridging, distance is in sigmas
		dGapBridgeSigma = Math.min(BigTraceData.dOCGapBridgeMaxSigma, Math.max(0.0, bt.btData.dOCGapBridgeSigma));
		//junction box: junctions, gap bridging by a planned passage
		if(isJunctionBoxUsed())
		{
			junctions.init(dMaxSigmaGrid, bDownsample);
		}
		bKinkRecovery = bt.btData.bOCKinkRecovery;
		for (int d = 0; d < 3; d++)
		{
			final double cal = bt.btData.globCal[d];
			gridVoxelSize[d] = (cal > 0 ? cal : 1.0) * ds[d];
		}
		for (int d = 0; d < 3; d++)
		{
			sigmaGridV[d] = sigmaGrid[d];
		}
		//Gaussian masks (same sigma as the hessian) for the gradient of Steger re-centering
		smoothKernels = DerivConvolutionKernels.convolve_derive_kernel(sigmaGrid, new int [] {0, 0, 0});
		
		//vertex placement is in px, each step is now up to dsMax px
		nPointPerSegment = bt.btData.nVertexPlacementPointN;
		if(bDownsample)
		{
			nPointPerSegment = (int) Math.max(3, Math.round(bt.btData.nVertexPlacementPointN / (double) dsMax));
		}
				
		boxFullHalfRange = new long[3];
		boxInnerHalfRange = new long[3];
		final long [] boxFullRange = new long[3];

		
		for (int d = 0; d < 3; d++)
		{
			boxFullHalfRange[d] = (long) (Math.ceil(rangeFullBoxDim * bt.btData.sigmaTrace[d])); 
			boxInnerHalfRange[d] = (long) (Math.ceil(rangeInnerBoxDim * bt.btData.sigmaTrace[d])); 
			boxFullRange[d] = (long) (Math.ceil(rangeFullBoxDim * bt.btData.sigmaTrace[d]) * 2 + 1); 	
			if(bDownsample)
			{
				boxGridHalfRange[d] = (long) Math.ceil(rangeFullBoxDim * sigmaGrid[d]);
				boxFullRange[d] = boxGridHalfRange[d] * 2 + 1;
			}
		}

		mathBox = new MathBox(boxFullRange);

		//fill candidate voxels
		NeighborsHashMap.fill( neighborsArr );
		
		nThreads = Runtime.getRuntime().availableProcessors();
		es = Executors.newFixedThreadPool( nThreads );

		int count = 0;
		
		double [][] kernels;
		Kernel1D[] derivKernel;
		int [] nDerivOrder;
		for (int d1 = 0; d1 < 3; d1++)
		{
			for (int d2 = d1; d2 < 3; d2++ )
			{
				nDerivOrder = new int [3];
				nDerivOrder[d1]++;
				nDerivOrder[d2]++;
				//kernels = DerivConvolutionKernels.convolve_derive_kernel(bt.btData.sigmaTrace, nDerivOrder);
				kernels = DerivConvolutionKernels.convolve_derive_kernel(sigmaGrid, nDerivOrder);
				derivKernel = Kernel1D.centralAsymmetric(kernels);
				convObjects[count] = SeparableKernelConvolution.convolution( derivKernel );
				convObjects[count].setExecutor(es);
				count++;
			}
		}
		
		//input of the convolutions must include the margin around the box,
		//otherwise the hessian near the box faces is computed from border-replicated data
		computeConvMargin();
		//downsampled box (with the margin)
		if(bDownsample)
		{
			mathBox.setGridMargin(convMargin);
			if(isJunctionBoxUsed())
			{
				junctions.box.setGridMargin(convMargin);
			}
		}
		
		if(bt.btData.bEstimateROIThicknessFromParams)
		{
			fEstimatedThickness = bt.btData.estimateROIThicknessFromTracing();
		}
	}

	public void releaseMultiThread()
	{
		if(es != null)
			es.shutdown();
	}
	
	/** checks if the point intersects already traced part of the curve**/
	/** debug visualization (BigTrace.bOCVisDebugEnabled, live one-click tracing only):
	 * cones at the new point for the last direction (directionality constrain)
	 * and the history (momentum) direction (stiffness) **/
	void visDebugStep(final RealPoint newPoint)
	{
		if(newPoint == null || !bLiveUpdate || !bt.bOCVisDebugEnabled || bt.ocVisDebug == null)
			return;
		//directions are already in physical space
		bt.ocVisDebug.showStep(newPoint.positionAsDoubleArray(), lastDirectionVector, dAngleThreshold, 
				bUseHistory ? memDirectionVector : null, dScorePowStep, bt.btData.sigmaTrace);
		bt.repaintBVV();
		bt.ocVisDebug.stepDelay();
	}
	
	/** debug visualization of gap bridging: search direction from the current point along 
	 * the heading refGrid (grid space) up to dReachAlong grid voxels, and the jump to the landing 
	 * grid voxel (null = not bridged) **/
	void visDebugGapBridge(final RealPoint currpoint, final double [] refGrid, final double dReachAlong, final long [] landingGrid)
	{
		if(!bLiveUpdate || !bt.bOCVisDebugEnabled || bt.ocVisDebug == null)
			return;
		final double [] start = currpoint.positionAsDoubleArray();
		final double [] searchEnd = new double[3];
		final double [] landing = (landingGrid != null) ? new double[3] : null;
		for (int d = 0; d < 3; d++)
		{
			searchEnd[d] = start[d] + dReachAlong * refGrid[d] * ds[d];
			if(landing != null)
			{
				landing[d] = toFull(landingGrid[d], d);
			}
		}
		bt.ocVisDebug.showGapBridge(start, searchEnd, landing);
	}
	
	/** debug visualization: the junction map stays in BVV while the passage is traced,
	 * removed after it (raw data restored) **/
	void visDebugJunctionMap(final boolean bInPassage)
	{
		if(!bLiveUpdate || !bt.bOCVisDebugEnabled || bt.ocVisDebug == null)
			return;
		bt.ocVisDebug.junctionMapStep(bInPassage);
	}
	
	boolean checkIntersection(RealPoint currpoint)
	{
		
		final double [] currPos = currpoint.positionAsDoubleArray();
		//any point closer than 0.5 has rounded coordinates within +-1 of rounded currPos,
		//so it is enough to check 27 neighboring grid cells
		final long [] c = new long[3];
		for(int d = 0; d < 3; d++)
		{
			c[d] = Math.round( currPos[d] );
		}
		for(long dz = -1; dz <= 1; dz++)
			for(long dy = -1; dy <= 1; dy++)
				for(long dx = -1; dx <= 1; dx++)
				{
					final ArrayList<double[]> cell = intersectionGrid.get( packKey(c[0] + dx, c[1] + dy, c[2] + dz) );
					if(cell != null)
					{
						for(final double [] pt : cell)
						{
							if(LinAlgHelpers.distance(currPos, pt) < 0.5)
							{
								return true;
							}
						}
					}
				}
		return false;
	}
	
	@SuppressWarnings( "null" )
	public RealPoint getNextPoint(RealPoint currpoint)
	{
		RealPoint out = null;
		
		final RandomAccess< T > raInput = bt.btData.bOCIntensityStop ? fullInput.randomAccess() : null;

		//points of a planned gap bridge (without junction handling, see bridgeGapPassage)
		bLastFromPassage = false;
		if(!bJunctions)
		{
			if(!junctions.passageQueue.isEmpty())
			{
				bLastFromPassage = true;
				visDebugJunctionMap(true);
				return junctions.passageQueue.poll();
			}
			//(debug: the gap saliency map is removed after the passage)
			if(!bProbing)
			{
				visDebugJunctionMap(false);
			}
		}
		//junctions: passage points, stop, or normal stepping
		if(bJunctions)
		{
			final int nStep = junctions.step(currpoint, raInput);
			visDebugJunctionMap(nStep == JunctionHandler.STEP_POINT);
			bLastFromPassage = (nStep == JunctionHandler.STEP_POINT);
			if(nStep == JunctionHandler.STEP_POINT)
			{
				return junctions.stepPoint;
			}
			if(nStep == JunctionHandler.STEP_STOP)
			{
				return null;
			}
		}
		final double [] newDirection = new double[3];
		double [] candDirection;
		
		/** reference direction: momentum (smoothed path) or last eigenvector (original), physical space **/
		final double [] refDirection = bUseHistory  ? memDirectionVector : lastDirectionVector;
		/** the same in grid space, for the choice of neighbors **/
		final double [] refGrid = physToGridDir(refDirection);

		//history stepping by Steger's linking of line points
		//(if no neighbor ahead is a line point: the saliency choice of a neighbor and re-centering below)
		if(bUseHistory && bStegerLinking)
		{
			out = stegerLinkStep(currpoint, refDirection, raInput);
			if(out != null)
			{
				return out;
			}
		}
				
		/** normalized step vector of the current and of the best candidate **/
		final double [] candStep = new double[3];
		final double [] bestStep = new double[3];
		/** grid position of the best candidate **/
		final long [] bestGrid = new long[3];
		/** saliency of the best candidate **/
		float bestSal = 0.0f;
		
		//find a pixel in the neighborhood 
		//according to direction vector
		int nNeighborIndex = 0;
		
		for (int d = 0; d < 3; d++)
		{
			nNeighborIndex = nNeighborIndex * 3 + ((int)Math.round(refGrid[d]) + 1);
		}
		
		//get all relevant neighbors
		//float maxSal = (-1) * Float.MAX_VALUE;
		double maxScore = (-1) * Double.MAX_VALUE;
		float currSal = (-1) * Float.MAX_VALUE;
		
		ArrayList<int[]> scannedPos;
		
		if(nNeighborsMethods == 0)
		{
			scannedPos = neighborsArr[nNeighborIndex];
		}
		else
		{
			scannedPos = getNeighborPixels(refGrid);
		}
		int [] candidateNeighbor; 

		final long[] candPos = new long[3];
		
		final float[] finPos = new float[3];
		
		for(int nScan = 0; nScan < scannedPos.size(); nScan++)
		{
			candidateNeighbor = scannedPos.get(nScan);
			
			for (int d = 0; d < 3; d++)
			{
				candPos[d] = toGrid(currpoint.getDoublePosition(d), d) + candidateNeighbor[d];
			}		
		
			boolean bInside = true;
			
			for (int d = 0; d < 3; d++)
			{
				if(candPos[d] < gridMin[d] || candPos[d] > gridMax[d])
				{
					bInside = false;
					break;
				}
			}
			if(bInside)
			{
				currSal = raW.setPositionAndGet(candPos).get();			
				
				if(currSal > 0)
				{
					candDirection = getVectorAtGrid(candPos);
					
					//full resolution position for intensity and mask checks
					final long [] candPosFull = toFullLong(candPos);
					final double cosEig = Math.abs(LinAlgHelpers.dot(candDirection, refDirection));
					//check directionality
					//(inside a junction eigenvectors are unreliable, gate is relaxed)
					//if(cosEig < dAngleThreshold && !(bMomentum && bCoasting))
					if(cosEig < dAngleThreshold)
					{
						currSal = 0.0f;
					}
					
					// intensity stop rule
					if(bt.btData.bOCIntensityStop)
					{
						if(raInput.setPositionAndGet( candPosFull ).getRealDouble() < bt.btData.dOCIntensityThreshold)
						{
							currSal = 0.0f;
						}
					}
					
					//check if we hit the mask
					if(bUseMask)
					{
						if(traceMask.isOccupied( candPosFull ))
						{
							currSal = 0.0f;
						}
					}
					double currScore = currSal;
					if(bUseHistory)
					{
						//direction of the step itself (physical space)
						for (int d = 0; d < 3; d++)
						{
							candStep[d] = candidateNeighbor[d] * gridVoxelSize[d];
						}
						LinAlgHelpers.normalize(candStep);
						final double cosStep = Math.max(0.0, LinAlgHelpers.dot(candStep, refDirection));

						currScore = currSal * Math.pow(cosEig, dScorePowEig) * Math.pow(cosStep, dScorePowStep);
					}
					
					//see if we pass
					if(currScore > maxScore)
					{
						maxScore = currScore;
						bestSal = currSal;
						for (int d = 0; d < 3; d++)
						{
							finPos[d] = (float) toFull(candPos[d], d);
							newDirection[d] = candDirection[d];
							bestStep[d] = candStep[d];
							bestGrid[d] = candPos[d];
						}
					}
				}
			}
		}
		
		if(maxScore > 0.000001)
		{			
			//orient the eigenvector along the reference direction
			if(LinAlgHelpers.dot(newDirection, refDirection) < 0)
			{
				LinAlgHelpers.scale(newDirection, -1.0, newDirection);
			}
			for (int d = 0; d < 3; d++)
			{
				lastDirectionVector[d] = newDirection[d];
			}
			//history stepping: re-centering and momentum update
			if(bUseHistory)
			{
				//position is snapped back to the ridge: the sub-voxel line centre (Steger),
				//otherwise saliency hill-climbing on the grid
				final double [] subCentre = new double[3];
				final boolean bSub = bStegerRecenter && recenterSteger(currpoint, bestGrid, subCentre, raInput);
				if(bSub)
				{
					for (int d = 0; d < 3; d++)
					{
						finPos[d] = (float) (subCentre[d] * ds[d] + 0.5 * (ds[d] - 1));
						bestGrid[d] = Math.round(subCentre[d]);
					}
					//line direction at the centre (interpolated hessian), not at the stepped voxel (often on the flank)
					if(directionAtGrid(subCentre, newDirection))
					{
						if(LinAlgHelpers.dot(newDirection, refDirection) < 0)
						{
							LinAlgHelpers.scale(newDirection, -1.0, newDirection);
						}
						System.arraycopy(newDirection, 0, lastDirectionVector, 0, 3);
					}
				}
				else if(recenterOnRidge(currpoint, bestGrid, raInput))
				{
					for (int d = 0; d < 3; d++)
					{
						finPos[d] = (float) toFull(bestGrid[d], d);
					}
				}
	
				//momentum update: mostly from the actual displacement (after re-centering,
				//so heading errors get corrected), partly from the local orientation 
				for (int d = 0; d < 3; d++)
				{
					bestStep[d] = bSub ? (subCentre[d] - toGridDouble(currpoint.getDoublePosition(d), d)) * gridVoxelSize[d]
							: (bestGrid[d] - toGrid(currpoint.getDoublePosition(d), d)) * gridVoxelSize[d];
				}
				LinAlgHelpers.normalize(bestStep);
				for (int d = 0; d < 3; d++)
				{
					memDirectionVector[d] = dMomentumAlpha * memDirectionVector[d] 
							+ (1.0 - dMomentumAlpha) * ((1.0 - dMomentumEigenShare) * bestStep[d] + dMomentumEigenShare * newDirection[d]);
				}
				LinAlgHelpers.normalize(memDirectionVector);
				
			}
			out = new RealPoint(finPos);
			dSalTrace = 0.9 * dSalTrace + 0.1 * bestSal;
		}
		else
		{
			//no continuation: a sharp kink or a junction not detected ahead (e.g. detection not re-armed yet):
			//the junction passage first (branch-first: half sphere candidates, least turning)
			if(bJunctions && bStallJunctionPassage)
			{
				out = junctions.planAtStall(currpoint, raInput);
				if(out != null)
				{
					bLastFromPassage = true;
					return out;
				}
				if(junctions.nStallResult == JunctionHandler.PASSAGE_LOST || junctions.nStallResult == JunctionHandler.PASSAGE_MERGE)
				{
					return null;
				}
			}
			//ridge is interrupted, try to find its continuation ahead
			if(dGapBridgeSigma > 0)
			{
				if(bGapBridgePassage)
				{
					out = bridgeGapPassage(currpoint, raInput);
				}
				else
				{
					out = bridgeGap(currpoint, refDirection, raInput);
				}
			}
		}
		if(out == null && bKinkRecovery)
		{
			//still stuck (e.g. at a kink): relaxed look-ahead, momentum reset
			out = recoverStall(currpoint, refDirection, raInput);
		}
		
		return out;
		
	}
	
	/** Stall recovery, called when normal stepping (and gap bridging) found no continuation.
	 * At a kink of the filament (and right after a junction passage that coasted straight through it)
	 * the heading (momentum) still points along the incoming part, the corner itself is blob-like 
	 * (eigenvectors are ill-defined), so stepping and gap bridging (narrow cone around the heading) stop.
	 * Here the constraints are relaxed once:
	 * - look-ahead up to dRecoverReachSigma tracing sigmas (min 1.5 grid voxels),
	 *   in the cone of the directionality constrain (dAngleThreshold) around the heading;
	 * - the landing voxel's line orientation must agree with the jump direction (dRecoverAlignCos),
	 *   not with the heading (the line continues where we jump), saliency at least 
	 *   dGapMinRelSal of the running average, same intensity/mask/self-intersection checks;
	 * - confirmation: the line must continue ~sigma further along the landing orientation 
	 *   (a voxel in the 3x3x3 neighborhood of the probe passing the same saliency/orientation checks),
	 *   so the trace does not turn into a short side spur or a punctum;
	 * - on success the momentum is reset to the landing orientation (no memory of the old heading).
	 * Candidates are scored by saliency, alignment with the jump, straightness and distance.
	 * Returns null if nothing is found (trace ends there). **/
	RealPoint recoverStall(final RealPoint currpoint, final double [] refDirection, final RandomAccess< T > raInput)
	{
		//heading in physical space (angles) and in grid space (look-ahead)
		final double [] ref = refDirection.clone();
		LinAlgHelpers.normalize(ref);
		final double [] refGrid = physToGridDir(ref);
		final double G = dRecoverReachSigma;
		
		double dMaxSigmaGrid = 0;
		double dNormRef = 0;
		for (int d = 0; d < 3; d++)
		{
			dMaxSigmaGrid = Math.max(dMaxSigmaGrid, sigmaGridV[d]);
			dNormRef += (refGrid[d] / sigmaGridV[d]) * (refGrid[d] / sigmaGridV[d]);
		}
		final double dReachAlong = G / Math.sqrt(dNormRef);
		
		final long [] currGrid = new long[3];
		final double [] aheadFull = new double[3];
		final int [] L = new int[3];
		for (int d = 0; d < 3; d++)
		{
			currGrid[d] = toGrid(currpoint.getDoublePosition(d), d);
			aheadFull[d] = currpoint.getDoublePosition(d) + 0.5 * dReachAlong * refGrid[d] * ds[d];
			aheadFull[d] = Math.min(fullInput.max(d), Math.max(fullInput.min(d), aheadFull[d]));
			L[d] = (int) Math.ceil(G * sigmaGridV[d]);
		}
		getMathForCurrentPoint(new RealPoint(aheadFull));
		
		final double dMinSal = dGapMinRelSal * dSalTrace;
		//candidates: score, grid position, orientation (oriented along the jump), saliency, cos to heading
		final ArrayList<double[]> cands = new ArrayList<>();
		final long [] cand = new long[3];
		final double [] candFullPos = new double[3];
		final double [] jump = new double[3];
		for (int dz = -L[2]; dz <= L[2]; dz++)
			for (int dy = -L[1]; dy <= L[1]; dy++)
				for (int dx = -L[0]; dx <= L[0]; dx++)
				{
					final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if(dist < 1.5)
						continue;
					final double distSigma = Math.sqrt((dx / sigmaGridV[0]) * (dx / sigmaGridV[0])
							+ (dy / sigmaGridV[1]) * (dy / sigmaGridV[1]) 
							+ (dz / sigmaGridV[2]) * (dz / sigmaGridV[2]));
					if(distSigma > G)
						continue;
					jump[0] = dx / dist;
					jump[1] = dy / dist;
					jump[2] = dz / dist;
					final double [] jumpPhys = gridToPhysDir(jump);
					final double cosHeading = LinAlgHelpers.dot(jumpPhys, ref);
					if(cosHeading < dAngleThreshold)
						continue;
					cand[0] = currGrid[0] + dx;
					cand[1] = currGrid[1] + dy;
					cand[2] = currGrid[2] + dz;
					if(!isInsideMath(cand))
						continue;
					final float sal = raW.setPositionAndGet(cand).get();
					if(sal <= 0 || sal < dMinSal)
						continue;
					final double [] candVec = getVectorAtGrid(cand);
					final double dAlign = LinAlgHelpers.dot(candVec, jumpPhys);
					if(Math.abs(dAlign) < dRecoverAlignCos)
						continue;
					final long [] candFull = toFullLong(cand);
					if(bt.btData.bOCIntensityStop && raInput.setPositionAndGet( candFull ).getRealDouble() < bt.btData.dOCIntensityThreshold)
						continue;
					if(bUseMask && traceMask.isOccupied( candFull ))
						continue;
					for (int d = 0; d < 3; d++)
					{
						candFullPos[d] = toFull(cand[d], d);
					}
					if(checkIntersection(new RealPoint(candFullPos)))
						continue;
					final double sign = dAlign < 0 ? -1.0 : 1.0;
					final double score = sal * dAlign * dAlign * cosHeading / (1.0 + distSigma / G);
					cands.add(new double [] {score, cand[0], cand[1], cand[2], 
							sign * candVec[0], sign * candVec[1], sign * candVec[2], sal});
				}
		cands.sort((a, b) -> Double.compare(b[0], a[0]));
		
		final long [] probe = new long[3];
		final long [] nb = new long[3];
		final double [] vLand = new double[3];
		for(final double [] c : cands)
		{
			for (int d = 0; d < 3; d++)
			{
				vLand[d] = c[4 + d];
			}
			final double [] vLandGrid = physToGridDir(vLand);
			for (int d = 0; d < 3; d++)
			{
				probe[d] = Math.round(c[1 + d] + dMaxSigmaGrid * vLandGrid[d]);
			}
			//confirmation: the line continues ~sigma further
			boolean bConfirmed = false;
			for (int pz = -1; pz <= 1 && !bConfirmed; pz++)
				for (int py = -1; py <= 1 && !bConfirmed; py++)
					for (int px = -1; px <= 1 && !bConfirmed; px++)
					{
						nb[0] = probe[0] + px;
						nb[1] = probe[1] + py;
						nb[2] = probe[2] + pz;
						if(!isInsideMath(nb))
							continue;
						final float salNb = raW.setPositionAndGet(nb).get();
						if(salNb <= 0 || salNb < dMinSal)
							continue;
						if(Math.abs(LinAlgHelpers.dot(getVectorAtGrid(nb), vLand)) >= dRecoverAlignCos)
							bConfirmed = true;
					}
			if(!bConfirmed)
				continue;
			//reset momentum to the landing orientation
			for (int d = 0; d < 3; d++)
			{
				lastDirectionVector[d] = vLand[d];
				memDirectionVector[d] = vLand[d];
			}
			dSalTrace = 0.9 * dSalTrace + 0.1 * c[7];
			final float [] finPos = new float[3];
			for (int d = 0; d < 3; d++)
			{
				finPos[d] = (float) toFull((long) c[1 + d], d);
			}
			return new RealPoint(finPos);
		}
		return null;
	}
	
	/** direction in grid voxel units (eigenvector, step, jump) -> unit direction in physical space **/
	double [] gridToPhysDir(final double [] v)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = v[d] * gridVoxelSize[d];
		}
		return normalizeSafe(out);
	}
	
	/** direction in physical space -> unit direction in grid voxel units 
	 * (for displacements on the grid: neighbors, look-ahead positions, cross-sections) **/
	double [] physToGridDir(final double [] v)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = v[d] / gridVoxelSize[d];
		}
		return normalizeSafe(out);
	}
	
	/** normalizes v in place (a zero vector stays zero), returns it **/
	static double [] normalizeSafe(final double [] v)
	{
		final double len = LinAlgHelpers.length(v);
		if(len > 0)
		{
			LinAlgHelpers.scale(v, 1.0 / len, v);
		}
		return v;
	}
	
	/** true if the grid position is inside the dataset and inside the current math (saliency/vectors) box **/
	boolean isInsideMath(final long [] gridPos)
	{
		for (int d = 0; d < 3; d++)
		{
			if(gridPos[d] < gridMin[d] || gridPos[d] > gridMax[d]
					|| gridPos[d] < salWeights.min(d) || gridPos[d] > salWeights.max(d))
			{
				return false;
			}
		}
		return true;
	}
	
	/** Gap bridging by a planned passage (bGapBridgePassage), called when normal stepping finds no candidate:
	 * ridge centres ahead within dGapBridgeSigma tracing SDs, the least turning curve to them is followed
	 * over the gap (JunctionHandler.bridgeGap, JunctionBranchPassage.planGap). The first point of the passage
	 * is returned, the rest by the next getNextPoint calls (passage points: no self-intersection check).
	 * Returns null if there is no bridge. **/
	RealPoint bridgeGapPassage(final RealPoint currpoint, final RandomAccess< T > raInput)
	{
		final RealPoint out = junctions.bridgeGap(currpoint, raInput);
		if(out != null)
		{
			bLastFromPassage = true;
		}
		return out;
	}

	/** Gap bridging for "dotty" (punctate) lines, called when normal stepping finds no candidate.
	 * Looks ahead in a cone along the reference direction (half-angle acos(dGapConeCos)),
	 * up to dGapBridgeSigma tracing sigmas away. Distance is measured in sigma units per axis, 
	 * so the search region is an ellipsoid with the same anisotropy as the math box 
	 * (+-rangeFullBoxDim sigma), which the whole cone fits into for dGapBridgeSigma up to 4.5.
	 * It looks for a ridge voxel (saliency at least dGapMinRelSal of the running average 
	 * along the trace) whose orientation agrees with the heading and that passes the same intensity/mask/self-intersection checks.
	 * The best one (saliency weighted by alignment and distance) becomes the next point, 
	 * the gap is a straight piece of the trace.
	 * The math box is re-centered half way into the gap first. 
	 * Returns null if nothing is found (trace ends there). **/
	RealPoint bridgeGap(final RealPoint currpoint, final double [] refDirection, final RandomAccess< T > raInput)
	{
		//refDirection can be lastDirectionVector, which is updated below
		final double [] ref = refDirection.clone();
		LinAlgHelpers.normalize(ref);
		
		final double G = dGapBridgeSigma;
		
		//heading in grid space (look-ahead)
		final double [] refGrid = physToGridDir(ref);
		//grid distance along ref that corresponds to G sigmas
		double dNormRef = 0;
		for (int d = 0; d < 3; d++)
		{
			dNormRef += (refGrid[d] / sigmaGridV[d]) * (refGrid[d] / sigmaGridV[d]);
		}
		final double dReachAlong = G / Math.sqrt(dNormRef);
		
		final long [] currGrid = new long[3];
		final double [] aheadFull = new double[3];
		//search half-range per axis (grid voxels)
		final int [] L = new int[3];
		for (int d = 0; d < 3; d++)
		{
			currGrid[d] = toGrid(currpoint.getDoublePosition(d), d);
			aheadFull[d] = currpoint.getDoublePosition(d) + 0.5 * dReachAlong * refGrid[d] * ds[d];
			//keep it inside the dataset (otherwise the math box can be empty)
			aheadFull[d] = Math.min(fullInput.max(d), Math.max(fullInput.min(d), aheadFull[d]));
			L[d] = (int) Math.ceil(G * sigmaGridV[d]);
		}
		getMathForCurrentPoint(new RealPoint(aheadFull));
		
		final double dMinOrient = Math.max(dAngleThreshold, dGapConeCos);
		final double dMinSal = dGapMinRelSal * dSalTrace;
		final double [] jumpBr = new double[3];
		final long [] cand = new long[3];
		final long [] best = new long[3];
		final double [] candFullPos = new double[3];
		double [] bestVec = null;
		double bestScore = 0.0;
		float bestLandSal = 0.0f;
		
		for (int dz = -L[2]; dz <= L[2]; dz++)
			for (int dy = -L[1]; dy <= L[1]; dy++)
				for (int dx = -L[0]; dx <= L[0]; dx++)
				{
					//distance in grid voxels (for the cone and minimum jump)
					final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if(dist < 2.0)
						continue;
					//distance in sigmas
					final double distSigma = Math.sqrt((dx / sigmaGridV[0]) * (dx / sigmaGridV[0])
							+ (dy / sigmaGridV[1]) * (dy / sigmaGridV[1]) 
							+ (dz / sigmaGridV[2]) * (dz / sigmaGridV[2]));
					if(distSigma > G)
						continue;
					jumpBr[0] = dx;
					jumpBr[1] = dy;
					jumpBr[2] = dz;
					final double cosCone = LinAlgHelpers.dot(gridToPhysDir(jumpBr), ref);
					if(cosCone < dGapConeCos)
						continue;
					cand[0] = currGrid[0] + dx;
					cand[1] = currGrid[1] + dy;
					cand[2] = currGrid[2] + dz;
					boolean bInside = true;
					for (int d = 0; d < 3; d++)
					{
						if(cand[d] < gridMin[d] || cand[d] > gridMax[d]
								|| cand[d] < salWeights.min(d) || cand[d] > salWeights.max(d))
						{
							bInside = false;
							break;
						}
					}
					if(!bInside)
						continue;
					final float sal = raW.setPositionAndGet(cand).get();
					if(sal <= 0 || sal < dMinSal)
						continue;
					final double [] candVec = getVectorAtGrid(cand);
					if(Math.abs(LinAlgHelpers.dot(candVec, ref)) < dMinOrient)
						continue;
					final long [] candFull = toFullLong(cand);
					if(bt.btData.bOCIntensityStop && raInput.setPositionAndGet( candFull ).getRealDouble() < bt.btData.dOCIntensityThreshold)
						continue;
					if(bUseMask && traceMask.isOccupied( candFull ))
						continue;
					for (int d = 0; d < 3; d++)
					{
						candFullPos[d] = toFull(cand[d], d);
					}
					if(checkIntersection(new RealPoint(candFullPos)))
						continue;
					//prefer bright, straight ahead and close
					final double score = sal * cosCone * cosCone / (1.0 + distSigma / G);
					if(score > bestScore)
					{
						bestScore = score;
						bestLandSal = sal;
						bestVec = candVec;
						System.arraycopy(cand, 0, best, 0, 3);
					}
				}
		
		visDebugGapBridge(currpoint, refGrid, dReachAlong, bestVec != null ? best : null);
		if(bestVec == null)
		{
			return null;
		}
		dSalTrace = 0.9 * dSalTrace + 0.1 * bestLandSal;
		
		//orient and store the new direction
		if(LinAlgHelpers.dot(bestVec, ref) < 0)
		{
			LinAlgHelpers.scale(bestVec, -1.0, bestVec);
		}
		for (int d = 0; d < 3; d++)
		{
			lastDirectionVector[d] = bestVec[d];
		}
		if(bUseHistory)
		{
			final double [] jump = new double[3];
			for (int d = 0; d < 3; d++)
			{
				jump[d] = (best[d] - currGrid[d]) * gridVoxelSize[d];
			}
			LinAlgHelpers.normalize(jump);
			for (int d = 0; d < 3; d++)
			{
				memDirectionVector[d] = dMomentumAlpha * memDirectionVector[d] 
						+ (1.0 - dMomentumAlpha) * ((1.0 - dMomentumEigenShare) * jump[d] + dMomentumEigenShare * bestVec[d]);
			}
			LinAlgHelpers.normalize(memDirectionVector);
		}
		final float [] finPos = new float[3];
		for (int d = 0; d < 3; d++)
		{
			finPos[d] = (float) toFull(best[d], d);
		}
		return new RealPoint(finPos);
	}

	/** calculate hessian/eigen vectors around current point **/
	@SuppressWarnings( "unchecked" )
	public void getMathForCurrentPoint(final RealPoint currPoint)
	{
		updateGridBounds();
		//let's figure out the volume around the point
		IntervalView<T> currentBox = Views.interval(fullInput, getLocalTraceBox(fullInput,boxFullHalfRange,currPoint));
		innerTraceBox = getLocalTraceBox(fullInput,boxInnerHalfRange,currPoint);
		//currentBox.min(minV);
		
		//position of the math box (grid): the box around the point, inside of the data
		if(!bDownsample)
		{
			currentBox.min(minV);
		}
		else
		{
			//same logic as getLocalTraceBox, in grid coordinates
			for(int d = 0; d < 3; d++)
			{
				minV[d] = Math.max(gridMin[d], toGrid(currPoint.getDoublePosition(d), d) - boxGridHalfRange[d]);
			}
		}
		mathBox.setMin(minV);
		bt.visualBoxes.traceBox.setInterval( currentBox );

		//hessian (the data around the box are used too, convMargin), saliency and directions
		mathBoxInput = getBoxInput(mathBox);
		mathBox.computeHessian(mathBoxInput, convObjects, nThreads, es);
		directionVectors = mathBox.vecView();
		salWeights = mathBox.salView();
		raV = directionVectors.randomAccess();
		raW = salWeights.randomAccess();
		//calculate inner box
		
		
	}
	
	/** gets a box around "target" extending in each side according to range in each axis.
	crops the box so it is inside viewclick interval **/
	public FinalInterval getLocalTraceBox(final AbstractInterval fullInterval, final long [] range, final RealPoint target)
	{
		final long[][] rangeM = new long[2][3];

		for(int d = 0; d < 3; d++)
		{
			rangeM[0][d] = (long)(target.getDoublePosition(d)) - range[d];
			rangeM[1][d] = (long)(target.getDoublePosition(d)) + range[d];								
		}
		VolumeMisc.checkBoxInside(fullInterval, rangeM);
		
		return new FinalInterval(rangeM[0],rangeM[1]);							
	}
	
	/** returns local maximum of saliency (using trace_weights) 
	 * around target_in point. The search box is equal to SD of tracing x2 **/
	RealPoint refinePointUsingSaliency(final RealPoint target_in)
	{
		return refinePointUsingSaliency(target_in, 2.0f);
	}
	
	/** returns local maximum of saliency around target_in in the search box of +-fSDN SD of tracing
	 * (must be inside the current math box) **/
	RealPoint refinePointUsingSaliency(final RealPoint target_in, final float fSDN)
	{
		//get an box around the target
		final FinalInterval searchArea  = Intervals.intersect( fullInput, getLocalSearchArea(target_in, fSDN) );
		//convert to grid coordinates
		final long [][] gridArea = new long[2][3];
		for(int d = 0; d < 3; d++)
		{
				gridArea[0][d] = Math.floorDiv(searchArea.min(d), ds[d]);
				gridArea[1][d] = Math.floorDiv(searchArea.max(d), ds[d]);
		}
		RealPoint out = new RealPoint(3);
		VolumeMisc.findMaxLocation(Views.interval(salWeights, new FinalInterval(gridArea[0], gridArea[1])), out);
		for(int d = 0; d < 3; d++)
		{
			out.setPosition(toFull(Math.round(out.getDoublePosition(d)), d), d);
		}
		
		return out;
		
	}
	
	/** gets an interval around target_in with +-fSDN*sigma of tracing **/
	FinalInterval getLocalSearchArea(final RealPoint target_in, final float fSDN)
	{
		final long[][] rangeMax = new long[2][3];
		for(int d = 0; d < 3; d++)
		{
			rangeMax[0][d] = Math.round(target_in.getFloatPosition(d) - fSDN * bt.btData.sigmaTrace[d]);
			rangeMax[1][d] = Math.round(target_in.getFloatPosition(d) + fSDN * bt.btData.sigmaTrace[d]);
		}
		return new FinalInterval(rangeMax[0],rangeMax[1]);
	}
	
	/** returns orientation vector (unit, physical space) at the provided full resolution location **/
	public double[] getVectorAtLocation(final RealPoint point)
	{
		final long [] gridPos = new long [3];
		for (int d = 0; d < 3; d++)
		{
			gridPos[d] = toGrid(point.getDoublePosition(d), d);
		}
		return getVectorAtGrid(gridPos);
	}
	
	/** returns orientation vector (unit, physical space) at the provided grid location.
	 * The hessian eigenvector is a direction in grid voxel units (derivatives per voxel):
	 * for a straight line with physical direction t it is t/gridVoxelSize (normalized),
	 * so it is scaled back by the grid voxel size **/
	double[] getVectorAtGrid(final long[] gridPos)
	{
		final double [] currDirVector = new double[3];
		final long [] currpos = new long [4];

		for (int d = 0; d < 3; d++)
		{
			currpos[d] = gridPos[d];
			//currpos[d] = Math.round(point.getFloatPosition(d));
		}
		
		for(int d = 0; d < 3; d++)
		{
			currpos[3] = d;
			raV.setPosition(currpos);
			currDirVector[d] = raV.get().getRealDouble() * gridVoxelSize[d];
		}
		return normalizeSafe(currDirVector);
	}
	
	ArrayList<int[]> getNeighborPixels(final double [] directionVector)
	{
		final ArrayList<int[]> out = new ArrayList<>();
		for(int i = 0; i < 26; i++)
		{
			if(LinAlgHelpers.dot(directionVector, nNeighborsVectors[i]) > 0.5)
			{
				out.add(nNeighborsIndexes[i]);
			}
		}
		return out;
	}
	
	void initNeighbors()
	{
		int nCount = 0;
		final int [] dx = new int[3];
		for (dx[0]=-1; dx[0]<2; dx[0]++)
		{
			for (dx[1]=-1; dx[1]<2; dx[1]++)
			{
				for (dx[2]=-1; dx[2]<2; dx[2]++)
				{
					int nType = Math.abs(dx[0])+Math.abs(dx[1])+Math.abs(dx[2]);
					//remove central pixel
					if(nType!=0)
					{
						nNeighborsIndexes[nCount]= new int[3];
						nNeighborsVectors[nCount]= new double[3];
						for(int d=0;d<3;d++)
						{
							nNeighborsIndexes[nCount][d]= dx[d];
							nNeighborsVectors[nCount][d]= dx[d];
							LinAlgHelpers.normalize(nNeighborsVectors[nCount]);
						}
						nCount++;
						
					}
				}
			}
		}
		
	}
	
	/** clears the list (and spatial hash) of points used for intersection check **/
	void resetIntersectionPoints()
	{
		allPointsIntersection = new ArrayList<>();
		intersectionGrid.clear();
	}

	/** adds point to the list (and spatial hash) used for intersection check **/
	void addIntersectionPoint(final double [] pt)
	{
		allPointsIntersection.add( pt );
		intersectionGrid.computeIfAbsent( packKey(Math.round( pt[0] ), Math.round( pt[1] ), Math.round( pt[2] )), k -> new ArrayList<>() ).add( pt );
	}

	/** removes a point added by addIntersectionPoint (the same array; the last ones are the fastest) **/
	void removeIntersectionPoint(final double [] pt)
	{
		for (int i = allPointsIntersection.size() - 1; i >= 0; i--)
		{
			if(allPointsIntersection.get(i) == pt)
			{
				allPointsIntersection.remove(i);
				break;
			}
		}
		final long nKey = packKey(Math.round( pt[0] ), Math.round( pt[1] ), Math.round( pt[2] ));
		final ArrayList<double[]> cell = intersectionGrid.get( nKey );
		if(cell != null)
		{
			cell.removeIf(x -> x == pt);
			if(cell.isEmpty())
			{
				intersectionGrid.remove( nKey );
			}
		}
	}

	/** packs 3 coordinates (each within +-2^20) into one long **/
	static long packKey(final long x, final long y, final long z)
	{
		final long nOff = 1L << 20;
		final long nMask = (1L << 21) - 1;
		return (((x + nOff) & nMask) << 42) | (((y + nOff) & nMask) << 21) | ((z + nOff) & nMask);
	}
	
	
	/** full resolution coordinate -> grid voxel. For ds = 1 it is Math.round(x), as before **/
	long toGrid(final double x, final int d)
	{
			return Math.floorDiv(Math.round(x), ds[d]);
	}
	
	/** full resolution coordinate -> continuous grid coordinate (inverse of toFull) **/
	double toGridDouble(final double x, final int d)
	{
		return (x - 0.5 * (ds[d] - 1)) / ds[d];
	}
		
	/** grid voxel -> full resolution coordinate of its center. For ds = 1 it is identity **/
	double toFull(final long g, final int d)
	{
		return g * ds[d] + 0.5 * (ds[d] - 1);
	}
		
	/** grid voxel -> nearest full resolution voxel (clamped to fullInput) **/
	long [] toFullLong(final long [] g)
	{
		final long [] out = new long[3];
		for(int d = 0; d < 3; d++)
		{
			out[d] = Math.min(fullInput.max(d), Math.max(fullInput.min(d), Math.round(toFull(g[d], d))));
		}
		return out;
	}

	/** input (grid coordinates, border extended) for the hessian convolution of a box (math box, junction box)
	 * at its current position: the full dataset, or with downsampling its block averages 
	 * filled in the box with the convolution margin (border extension only at the borders of the data) **/
	RandomAccessible<?> getBoxInput(final MathBox box)
	{
		if(!bDownsample)
		{
			return Views.extendBorder(fullInput);
		}
		final long [] gridInputMin = box.gridInputMin();
		fillGridBox(box.gridImg, gridInputMin);
		return Views.extendBorder(Views.translate(box.gridImg, gridInputMin));
	}

	/** the junction box is needed: junction handling or gap bridging by a planned passage **/
	boolean isJunctionBoxUsed()
	{
		return bJunctions || (dGapBridgeSigma > 0 && bGapBridgePassage);
	}
	
	/** margin around a box needed by the hessian convolutions (max over the 6 derivative kernels) **/
	void computeConvMargin()
	{
		final FinalInterval probe = new FinalInterval(new long[] {0, 0, 0}, new long[] {0, 0, 0});
		for(int d = 0; d < 3; d++)
		{
			convMargin[d] = 0;
		}
		for(int c = 0; c < 6; c++)
		{
			final Interval req = convObjects[c].requiredSourceInterval(probe);
			for(int d = 0; d < 3; d++)
			{
				convMargin[d] = Math.max(convMargin[d], Math.max(-req.min(d), req.max(d)));
			}
		}
	}
	
	/** fills gridBox (positioned at gridMinPos in grid coordinates)
	 * with block averages of fullInput (border-extended) **/
	void fillGridBox(final ArrayImg<FloatType, FloatArray> target, final long [] gridMinPos)
	{
		final RandomAccess<T> raIn = Views.extendBorder(fullInput).randomAccess();
		final RandomAccess<FloatType> raOut = target.randomAccess();
		final long [] dims = Intervals.dimensionsAsLongArray( target );
		final long [] posOut = new long[3];
		final long [] posIn = new long[3];
		final double norm = 1.0 / (ds[0] * ds[1] * ds[2]);
		for(posOut[2] = 0; posOut[2] < dims[2]; posOut[2]++)
			for(posOut[1] = 0; posOut[1] < dims[1]; posOut[1]++)
				for(posOut[0] = 0; posOut[0] < dims[0]; posOut[0]++)
				{
					double sum = 0;
					for(long k = 0; k < ds[2]; k++)
						for(long j = 0; j < ds[1]; j++)
							for(long i = 0; i < ds[0]; i++)
							{
								posIn[0] = (gridMinPos[0] + posOut[0]) * ds[0] + i;
								posIn[1] = (gridMinPos[1] + posOut[1]) * ds[1] + j;
								posIn[2] = (gridMinPos[2] + posOut[2]) * ds[2] + k;
								sum += raIn.setPositionAndGet(posIn).getRealDouble();
							}
					raOut.setPositionAndGet(posOut).set((float)(sum * norm));
				}
	}
	
	/** bounds of fullInput in grid coordinates (for ds = 1 same as fullInput bounds) **/
	void updateGridBounds()
	{
		for(int d = 0; d < 3; d++)
		{
			gridMin[d] = Math.floorDiv(fullInput.min(d), ds[d]);
			gridMax[d] = Math.floorDiv(fullInput.max(d), ds[d]);
		}
	}
	
	/** Re-centering by Steger's method (C. Steger, An unbiased detector of curvilinear structures, 1998), in 3D,
	 * within the cross-section of the filament one step ahead: 
	 * the line direction t (eigenvector of the smallest absolute eigenvalue at the stepped voxel gridPos, physical, 
	 * oriented along the momentum) and the point o ahead of currpoint along t (by the projection of the step on t,
	 * at least the smallest voxel) define the plane through o perpendicular to t (physical units). 
	 * At the grid voxel g nearest to the current estimate, the hessian H (math box) and the gradient (central differences
	 * of the Gaussian smoothed input, smoothedAt) give the second order Taylor expansion of the smoothed intensity;
	 * its maximum within the plane (a bright line: negative definite in the plane) is the new estimate. 
	 * Repeated while it moves to another voxel (up to nRecenterMaxSteps + 1 times).
	 * The plane is fixed: the centre can not slide along the filament (towards a brighter spot of it, or back 
	 * behind the current point when the step went below a filament climbing in z).
	 * The centre must be within dStegerMaxSD tracing sigmas (per axis) of o, in front of currpoint (along the momentum), 
	 * its line direction within the directionality constrain of the momentum, not masked or below the intensity
	 * threshold. Its grid position (continuous) goes to subOut. Returns false (nothing changed) otherwise. **/
	boolean recenterSteger(final RealPoint currpoint, final long [] gridPos, final double [] subOut, final RandomAccess< T > raInput)
	{
		final double [] vx = gridVoxelSize;
		final double [] h = new double[6];
		final double [] t = new double[3];
		//line direction at the stepped voxel (physical, along the momentum)
		if(!mathBox.hessianAtVoxel(gridPos, h))
			return false;
		if(!(mathBox.saliencyAndVectorAtVoxel(gridPos, t) > 0))
			return false;
		final double [] tp = gridToPhysDir(t);
		if(LinAlgHelpers.dot(tp, memDirectionVector) < 0)
		{
			LinAlgHelpers.scale(tp, -1.0, tp);
		}
		//the plane: through o (one step ahead of the current point along the line), perpendicular to it
		final double [] cur = new double[3];
		double dAhead = 0;
		double dMinVox = Double.MAX_VALUE;
		for (int d = 0; d < 3; d++)
		{
			cur[d] = toGridDouble(currpoint.getDoublePosition(d), d);
			dAhead += (gridPos[d] - cur[d]) * vx[d] * tp[d];
			dMinVox = Math.min(dMinVox, vx[d]);
		}
		dAhead = Math.max(dAhead, dMinVox);
		final double [] o = new double[3];
		final long [] g = new long[3];
		for (int d = 0; d < 3; d++)
		{
			o[d] = cur[d] + dAhead * tp[d] / vx[d];
			g[d] = Math.round(o[d]);
		}
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(tp, u, v);
		final long [] q = new long[3];
		final double [] grad = new double[3];
		final double [] r = new double[3];
		final double [] hr = new double[3];
		final double [] hu = new double[3];
		final double [] hv = new double[3];
		final double [] x = new double[3];
		for (int nIter = 0; nIter <= nRecenterMaxSteps; nIter++)
		{
			if(!mathBox.hessianAtVoxel(g, h))
				return false;
			if(!(mathBox.saliencyAndVectorAtVoxel(g,t) > 0) )
				return false;
			//gradient of the smoothed input (per grid voxel)
			for (int d = 0; d < 3; d++)
			{
				System.arraycopy(g, 0, q, 0, 3);
				q[d] = g[d] + 1;
				final double dPlus = smoothedAt(q);
				q[d] = g[d] - 1;
				grad[d] = 0.5 * (dPlus - smoothedAt(q));
			}
			//physical units: with anisotropic voxels a plane perpendicular in grid units is tilted along the line
			h[0] /= vx[0] * vx[0];
			h[1] /= vx[0] * vx[1];
			h[2] /= vx[0] * vx[2];
			h[3] /= vx[1] * vx[1];
			h[4] /= vx[1] * vx[2];
			h[5] /= vx[2] * vx[2];
			for (int d = 0; d < 3; d++)
			{
				grad[d] /= vx[d];
				r[d] = (o[d] - g[d]) * vx[d];
			}
			//maximum of f(g) + grad.(y - g) + 1/2 (y - g)'H(y - g) for y = o + a u + b v
			hessianTimes(h, r, hr);
			hessianTimes(h, u, hu);
			hessianTimes(h, v, hv);
			final double a11 = LinAlgHelpers.dot(u, hu);
			final double a12 = LinAlgHelpers.dot(u, hv);
			final double a22 = LinAlgHelpers.dot(v, hv);
			final double b1 = LinAlgHelpers.dot(u, grad) + LinAlgHelpers.dot(u, hr);
			final double b2 = LinAlgHelpers.dot(v, grad) + LinAlgHelpers.dot(v, hr);
			final double det = a11 * a22 - a12 * a12;
			if(!(det > 0) || a11 + a22 >= 0)
				return false;
			final double ca = (a12 * b2 - a22 * b1) / det;
			final double cb = (a12 * b1 - a11 * b2) / det;
			boolean bSame = true;
			for (int d = 0; d < 3; d++)
			{
				x[d] = o[d] + (ca * u[d] + cb * v[d]) / vx[d];
				final long gn = Math.round(x[d]);
				if(gn != g[d])
				{
					bSame = false;
				}
				g[d] = gn;
			}
			if(bSame)
			{
				return acceptSteger(currpoint, o, x, t, subOut, raInput);
			}
		}
		return false;
	}
	
	/** line direction (unit, physical) at the continuous grid position x from the linearly interpolated hessian
	 * of the math box (MathBox.getHessianBox) to dirOut; false (dirOut not changed) outside of the box 
	 * or if it is not line-like (saliency not positive) **/
	boolean directionAtGrid(final double [] x, final double [] dirOut)
	{
		final double [] vecGrid = new double[3];
		final double dSal = mathBox.saliencyAndVectorAt(x, vecGrid);
		if(!(dSal > 0))
			return false;
		final double [] dir = gridToPhysDir(vecGrid);
		System.arraycopy(dir, 0, dirOut, 0, 3);
		return true;
	}
	
	/** Stepping by Steger's linking of line points (C. Steger, An unbiased detector of curvilinear structures, 1998;
	 * dissertation 1998, 3.1.x linking and 4.3.2-4.3.3 lines in 3D), history (momentum) stepping only.
	 * Each of the 26 neighbors of the voxel of the current (sub-voxel) point with its centre ahead along the momentum
	 * is tested as a line point on its own (stegerLinePoint): no re-centering, a voxel on the flank of the filament
	 * is not a line point (its extremum lies outside of it).
	 * Line points must have the orientation within the directionality constrain of the momentum,
	 * pass the intensity and mask checks and be ahead of the current point (dLinkMinForward).
	 * The next point is the line point minimizing Steger's d + c * beta: d is the distance between the sub-voxel
	 * points (smallest voxel size), beta the angle between their line directions (radians, 0 to pi/2),
	 * c = dLinkAngleWeight. The line direction, the momentum and the running saliency are updated as in getNextPoint.
	 * Returns null if no neighbor ahead is a line point. **/
	RealPoint stegerLinkStep(final RealPoint currpoint, final double [] refDirection, final RandomAccess< T > raInput)
	{
		final double [] vx = gridVoxelSize;
		final double dMinVox = Math.min(vx[0], Math.min(vx[1], vx[2]));
		final double [] cur = new double[3];
		final long [] gc = new long[3];
		for (int d = 0; d < 3; d++)
		{
			cur[d] = toGridDouble(currpoint.getDoublePosition(d), d);
			gc[d] = Math.round(cur[d]);
		}
		final SmoothedCache smoothed = new SmoothedCache(gc);
		final long [] n = new long[3];
		final double [] x = new double[3];
		final double [] t = new double[3];
		final double [] bestX = new double[3];
		final double [] bestT = new double[3];
		double bestCost = Double.MAX_VALUE;
		double bestSal = 0;
		for (int k = -1; k <= 1; k++)
			for (int j = -1; j <= 1; j++)
				for (int i = -1; i <= 1; i++)
				{
					if(i == 0 && j == 0 && k == 0)
						continue;
					n[0] = gc[0] + i;
					n[1] = gc[1] + j;
					n[2] = gc[2] + k;
					boolean bOk = true;
					double dCentreAhead = 0;
					for (int d = 0; d < 3; d++)
					{
						if(n[d] < gridMin[d] || n[d] > gridMax[d])
						{
							bOk = false;
						}
						dCentreAhead += (n[d] - cur[d]) * vx[d] * refDirection[d];
					}
					//only the neighbors compatible with the direction of the line (ahead)
					if(!bOk || dCentreAhead <= 0)
						continue;
					final double dSal = mathBox.saliencyAtVoxel(n[0], n[1], n[2], null);
					if(!(dSal > 0))
						continue;
					if(!stegerLinePoint(n, smoothed, x, t))
						continue;
					if(Math.abs(LinAlgHelpers.dot(t, refDirection)) < dAngleThreshold)
						continue;
					double dForward = 0;
					double dDist = 0;
					for (int d = 0; d < 3; d++)
					{
						final double s = (x[d] - cur[d]) * vx[d];
						dForward += s * refDirection[d];
						dDist += s * s;
					}
					//ahead, in the cone of the momentum (Steger: only the neighbors compatible with the line direction)
					if(dForward < dLinkMinForward * dMinVox || dForward < dLinkMinStepCos * Math.sqrt(dDist))
						continue;
					final double dBeta = Math.acos(Math.min(1.0, Math.abs(LinAlgHelpers.dot(t, lastDirectionVector))));
					final double dCost = Math.sqrt(dDist) / dMinVox + dLinkAngleWeight * dBeta;
					if(dCost >= bestCost)
						continue;
					//not a point of the trace already (Steger: not added to a line yet),
					//e.g. the line point of a neighbor right next to the current one
					final double [] xFull = new double[3];
					for (int d = 0; d < 3; d++)
					{
						xFull[d] = x[d] * ds[d] + 0.5 * (ds[d] - 1);
					}
					if(checkIntersection(new RealPoint(xFull)))
						continue;
					if(bt.btData.bOCIntensityStop || bUseMask)
					{
						final long [] gr = new long[3];
						for (int d = 0; d < 3; d++)
						{
							gr[d] = Math.round(x[d]);
						}
						final long [] posFull = toFullLong(gr);
						if(bt.btData.bOCIntensityStop && raInput.setPositionAndGet( posFull ).getRealDouble() < bt.btData.dOCIntensityThreshold)
							continue;
						if(bUseMask && traceMask.isOccupied( posFull ))
							continue;
					}
					bestCost = dCost;
					bestSal = dSal;
					System.arraycopy(x, 0, bestX, 0, 3);
					System.arraycopy(t, 0, bestT, 0, 3);
				}
		if(bestCost == Double.MAX_VALUE)
			return null;

		//line direction at the new point, oriented along the momentum
		if(LinAlgHelpers.dot(bestT, refDirection) < 0)
		{
			LinAlgHelpers.scale(bestT, -1.0, bestT);
		}
		System.arraycopy(bestT, 0, lastDirectionVector, 0, 3);
		//momentum: the actual displacement and the line direction
		final double [] step = new double[3];
		final float [] finPos = new float[3];
		for (int d = 0; d < 3; d++)
		{
			step[d] = (bestX[d] - cur[d]) * vx[d];
			finPos[d] = (float) (bestX[d] * ds[d] + 0.5 * (ds[d] - 1));
		}
		LinAlgHelpers.normalize(step);
		for (int d = 0; d < 3; d++)
		{
			memDirectionVector[d] = dMomentumAlpha * memDirectionVector[d]
					+ (1.0 - dMomentumAlpha) * ((1.0 - dMomentumEigenShare) * step[d] + dMomentumEigenShare * bestT[d]);
		}
		LinAlgHelpers.normalize(memDirectionVector);
		dSalTrace = 0.9 * dSalTrace + 0.1 * bestSal;
		return new RealPoint(finPos);
	}

	/** Steger's line point test of the grid voxel n (dissertation 4.3.2, in physical units):
	 * the second order Taylor polynomial of the smoothed intensity at n (hessian of the math box,
	 * gradient by central differences of smoothedAt) restricted to the plane through n spanned by the two
	 * eigenvectors of the largest absolute eigenvalues (perpendicular to the line direction) has its maximum
	 * (bright line: both eigenvalues negative) within the voxel (dLinkVoxelTol per axis).
	 * The line point (continuous grid position) goes to xOut, the line direction (physical, unit) to tOut. **/
	boolean stegerLinePoint(final long [] n, final SmoothedCache smoothed, final double [] xOut, final double [] tOut)
	{
		final double [] vx = gridVoxelSize;
		final double [] h = new double[6];
		if(!mathBox.hessianAtVoxel(n, h))
			return false;
		//physical units (isotropic, as Steger's resampled data)
		h[0] /= vx[0] * vx[0];
		h[1] /= vx[0] * vx[1];
		h[2] /= vx[0] * vx[2];
		h[3] /= vx[1] * vx[1];
		h[4] /= vx[1] * vx[2];
		h[5] /= vx[2] * vx[2];
		
		if(!(SteerableLineFilter.saliency(h, tOut) > 0))
			return false;
		final double [] grad = new double[3];
		final long [] q = new long[3];
		for (int d = 0; d < 3; d++)
		{
			System.arraycopy(n, 0, q, 0, 3);
			q[d] = n[d] + 1;
			final double dPlus = smoothed.get(q);
			q[d] = n[d] - 1;
			grad[d] = 0.5 * (dPlus - smoothed.get(q)) / vx[d];
		}
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(tOut, u, v);
		final double [] hu = new double[3];
		final double [] hv = new double[3];
		hessianTimes(h, u, hu);
		hessianTimes(h, v, hv);
		final double a11 = LinAlgHelpers.dot(u, hu);
		final double a12 = LinAlgHelpers.dot(u, hv);
		final double a22 = LinAlgHelpers.dot(v, hv);
		final double b1 = LinAlgHelpers.dot(u, grad);
		final double b2 = LinAlgHelpers.dot(v, grad);
		final double det = a11 * a22 - a12 * a12;
		if(!(det > 0) || a11 + a22 >= 0)
			return false;
		final double ca = (a12 * b2 - a22 * b1) / det;
		final double cb = (a12 * b1 - a11 * b2) / det;
		for (int d = 0; d < 3; d++)
		{
			final double dOff = (ca * u[d] + cb * v[d]) / vx[d];
			if(Math.abs(dOff) > dLinkVoxelTol)
				return false;
			xOut[d] = n[d] + dOff;
		}
		return true;
	}

	/** smoothedAt values around the grid voxel c (offsets -2..2 per axis), computed once on demand **/
	final class SmoothedCache
	{
		final long [] c;
		final double [] vals = new double[125];
		SmoothedCache(final long [] c_)
		{
			c = c_.clone();
			java.util.Arrays.fill(vals, Double.NaN);
		}
		double get(final long [] g)
		{
			int index = 0;
			for (int d = 2; d >= 0; d--)
			{
				final long o = g[d] - c[d];
				if(o < -2 || o > 2)
				{
					return smoothedAt(g);
				}
				index = index * 5 + (int) (o + 2);
			}
			if(Double.isNaN(vals[index]))
			{
				vals[index] = smoothedAt(g);
			}
			return vals[index];
		}
	}

	/** checks of the Steger centre x (grid) in the cross-section through o (grid), line direction t (grid) at the centre, 
	 * see recenterSteger; sets subOut **/
	boolean acceptSteger(final RealPoint currpoint, final double [] o, final double [] x, final double [] t,
			final double [] subOut, final RandomAccess< T > raInput)
	{
		final double [] memGrid = physToGridDir(memDirectionVector);
		double dOffSD = 0;
		double dForward = 0;
		for (int d = 0; d < 3; d++)
		{
			subOut[d] = x[d];
			final double rr = (x[d] - o[d]) / sigmaGridV[d];
			dOffSD += rr * rr;
			dForward += (x[d] - toGridDouble(currpoint.getDoublePosition(d), d)) * memGrid[d];
		}
		if(Math.sqrt(dOffSD) > dStegerMaxSD || dForward < 0.5)
			return false;
		//same filament: orientation within the directionality constrain of the momentum
		if(Math.abs(LinAlgHelpers.dot(gridToPhysDir(t), memDirectionVector)) < dAngleThreshold)
			return false;
		if(bt.btData.bOCIntensityStop || bUseMask)
		{
			final long [] gr = new long[3];
			for (int d = 0; d < 3; d++)
			{
				gr[d] = Math.round(x[d]);
			}
			final long [] posFull = toFullLong(gr);
			if(bt.btData.bOCIntensityStop && raInput.setPositionAndGet( posFull ).getRealDouble() < bt.btData.dOCIntensityThreshold)
				return false;
			if(bUseMask && traceMask.isOccupied( posFull ))
				return false;
		}
		return true;
	}
	
	/** symmetric matrix (6 components: xx, xy, xz, yy, yz, zz) times x **/
	static void hessianTimes(final double [] h, final double [] x, final double [] out)
	{
		out[0] = h[0] * x[0] + h[1] * x[1] + h[2] * x[2];
		out[1] = h[1] * x[0] + h[3] * x[1] + h[4] * x[2];
		out[2] = h[2] * x[0] + h[4] * x[1] + h[5] * x[2];
	}
	
	/** Gaussian smoothed input (tracing sigma, smoothKernels) at the grid voxel g, 
	 * from the input of the math box (mathBoxInput) **/
	@SuppressWarnings( "unchecked" )
	double smoothedAt(final long [] g)
	{
		final RandomAccess<? extends RealType<?>> ra = ((RandomAccessible<? extends RealType<?>>) mathBoxInput).randomAccess();
		final double [] kx = smoothKernels[0];
		final double [] ky = smoothKernels[1];
		final double [] kz = smoothKernels[2];
		final int cx = kx.length / 2;
		final int cy = ky.length / 2;
		final int cz = kz.length / 2;
		final long [] pos = new long[3];
		double sum = 0;
		for (int k = 0; k < kz.length; k++)
		{
			pos[2] = g[2] + k - cz;
			for (int j = 0; j < ky.length; j++)
			{
				pos[1] = g[1] + j - cy;
				final double wyz = ky[j] * kz[k];
				double sumX = 0;
				for (int i = 0; i < kx.length; i++)
				{
					pos[0] = g[0] + i - cx;
					sumX += kx[i] * ra.setPositionAndGet(pos).getRealDouble();
				}
				sum += wyz * sumX;
			}
		}
		return sum;
	}

	/** Momentum stepping prefers going straight, so it does not re-center by itself.
	 * This hill-climbs saliency in the cross-section perpendicular to the momentum direction:
	 * at each iteration it checks the 3x3 neighbors (in the cross-section plane) 
	 * of the current best position and moves to the highest one 
	 * (same gating as candidates: orientation, intensity, mask).
	 * Stops at the nearest local maximum or after nRecenterMaxSteps, 
	 * so it does not jump across a valley onto a neighboring filament.
	 * The new position must still be in front of currpoint. 
	 * Returns true if gridPos was changed. **/
	boolean recenterOnRidge(final RealPoint currpoint, final long [] gridPos, final RandomAccess< T > raInput)
	{
		//orthonormal basis (u,v) perpendicular to momentum (grid space)
		final double [] memGrid = physToGridDir(memDirectionVector);
		final double [] u = new double[3];
		final double [] v = new double[3];
		Pipe3D.crossSectionBasis(memGrid, u, v);

		final long [] currGrid = new long[3];
		for (int d = 0; d < 3; d++)
		{
			currGrid[d] = toGrid(currpoint.getDoublePosition(d), d);
		}

		final long [] testPos = new long[3];
		final long [] bestPos = gridPos.clone();
		double bestSal = getGatedSaliency(gridPos, raInput);

		final long [] centerPos = new long[3];
		for (int nIter = 0; nIter < nRecenterHillSteps; nIter++)
		{
			System.arraycopy(bestPos, 0, centerPos, 0, 3);
			for (int a = -1; a <= 1; a++)
			{
				for (int b = -1; b <= 1; b++)
				{
					if(a == 0 && b == 0)
						continue;
					double dForward = 0;
					boolean bInside = true;
					for (int d = 0; d < 3; d++)
					{
						testPos[d] = centerPos[d] + Math.round(a * u[d] + b * v[d]);
						//stay inside the dataset and inside the current math (saliency/vector) box
						if(testPos[d] < gridMin[d] || testPos[d] > gridMax[d]
								|| testPos[d] < salWeights.min(d) || testPos[d] > salWeights.max(d))
						{
							bInside = false;
						}
						dForward += (testPos[d] - currGrid[d]) * memGrid[d];
					}
					//must stay inside and keep moving forward 
					//(otherwise self-intersection check would stop tracing)
					if(!bInside || dForward < 0.5)
						continue;
					final double testSal = getGatedSaliency(testPos, raInput);
					if(testSal > bestSal)
					{
						bestSal = testSal;
						System.arraycopy(testPos, 0, bestPos, 0, 3);
					}
					
				}
			}
			//local maximum reached
			if(bestPos[0] == centerPos[0] && bestPos[1] == centerPos[1] && bestPos[2] == centerPos[2])
				break;
		}
		boolean bChanged = false;
		for (int d = 0; d < 3; d++)
		{
			if(bestPos[d] != gridPos[d])
				bChanged = true;
			gridPos[d] = bestPos[d];
		}
		return bChanged;
	}
	
	/** sets the "path stiffness" value s (0-1), use of the path history.
	 * s = 0 switches history stepping off (original stepping, if junctions are off too) **/
	public void setStiffness(final double s_)
	{
		dStiffnessSet = Math.min(1.0, Math.max(0.0, s_));
		updateStepping();
	}
	
	/** switches junction (crossing) detection and passage on/off **/
	public void setJunctions(final boolean bJunctions_)
	{
		bJunctions = bJunctions_;
		updateStepping();
	}
	
	/** sets all momentum parameters from the effective stiffness:
	 * the set one scaled for the downsampled grid (dStiffnessGridScale), 
	 * at least dJunctionMinStiffness with junction handling on.
	 * s = 0.5 gives alpha 0.6, eigen share 0.25, powers 2 **/
	void updateStepping()
	{
		final double sGrid = dStiffnessSet * dStiffnessGridScale;
		final double s = bJunctions ? Math.max(sGrid, dJunctionMinStiffness) : sGrid;
		dStiffness = s;
		bUseHistory = s > 0;
		dMomentumAlpha = 1.0 - 1.0 / (1.0 + 3.0 * s);
		dMomentumEigenShare = (1.0 - s) * (1.0 - s);
		dScorePowEig = 4.0 * s;
		dScorePowStep = 4.0 * s;
	}

	/** saliency at grid position, set to 0 if orientation does not agree with momentum,
	 * intensity is below threshold or position is masked **/
	double getGatedSaliency(final long [] gridPos, final RandomAccess< T > raInput)
	{
		final float sal = raW.setPositionAndGet(gridPos).get();
		if(sal <= 0)
			return 0;
		if(Math.abs(LinAlgHelpers.dot(getVectorAtGrid(gridPos), memDirectionVector)) < Math.max(dAngleThreshold, dRecenterAngleCos))
			return 0;
		if(bt.btData.bOCIntensityStop || bUseMask)
		{
			final long [] posFull = toFullLong(gridPos);
			if(bt.btData.bOCIntensityStop && raInput.setPositionAndGet( posFull ).getRealDouble() < bt.btData.dOCIntensityThreshold)
				return 0;
			if(bUseMask && traceMask.isOccupied( posFull ))
				return 0;
		}
		return sal;
	}

	/*
     * Executed in event dispatching thread
     */
    @Override
    public void done() 
    {
    	//see if we have some errors
    	 try {
    		 
             get();
         } 
    	 catch (ExecutionException e) 
    	 {
             e.getCause().printStackTrace();
             String msg = String.format("Unexpected problem during one-click tracing: %s", 
                            e.getCause().toString());
             System.out.println(msg);
         } 
    	 catch (InterruptedException e) 
    	 {
             // Process e here
         }
     	catch (Exception e)
     	{

     		//System.out.println("Tracing interrupted by user.");
         	setProgressState("Tracing interrupted by user.");
         	setProgress(100);	
     	}
    	//es.shutdown();
    	releaseMultiThread();
    	
    	//debug visualization: junction map off, raw data back
    	if(bt.ocVisDebug != null)
    	{
    		bt.ocVisDebug.hideJunctionMap();
    	}

    	
    	bt.visualBoxes.bShowTraceBox = false;
    	bt.roiManager.rmPanel.setOneClickTracing( false );
    	
    	//deselect the trace if we just made it
    	if( bNewTrace )
    	{
    		bt.roiManager.unselect();
    	}
    	
    	if(bUnlockInTheEnd)
    	{
    		//unlock user interaction  	
    		bt.setLockMode(false);
        	bt.bInputLock = false;
        	// bvv_trace = BvvFunctions.show(btdata.trace_weights, "weights", Bvv.options().addTo(bvv_main));
    	}

    }
    
    /*
    public static void main(String[] args) 
    {
    	OneClickTrace test =new OneClickTrace();
    	test.fillNeighborsHashMap();
   
    }
    */
	/*
	//show gradient absolute value in salWeights
	Cursor<FloatType> cu = salWeights.cursor();
	while(cu.hasNext())
	{
		cu.next().set(0.0f);
	}

	/// let's make a gradient image
	RandomAccess<FloatType> rasW = salWeights.randomAccess();
	cu = gradient.localizingCursor();

	long [] pos = new long[3];
	float zz;
	while(cu.hasNext())
	{
		cu.fwd();
		for(i=0;i<3;i++)
		{bNewTrace
			pos[i]=cu.getLongPosition(i);
		}
		rasW.setPosition(pos);
		//if(cu.getLongPosition(3)==1)
		//{
			zz = cu.get().getRealFloat();
			zz=rasW.get().getRealFloat()+zz*zz;
			rasW.get().set(zz);
		//}
	}
	//square root
	cu = salWeights.cursor();
	cu.reset();
	while(cu.hasNext())
	{
		cu.fwd();
		zz=cu.get().getRealFloat();
		cu.get().set((float)Math.sqrt(zz));
	}
*/


}
