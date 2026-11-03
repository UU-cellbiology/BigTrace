package bigtrace.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.volume.VolumeMisc;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Roi3D;

import net.imglib2.AbstractInterval;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.algorithm.convolution.kernel.Kernel1D;
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
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
	
	//private HashMap<String, ArrayList<int[]>> neighborsMap = new HashMap<>();
	
	/** same as neighborsMap, but indexed by (d1+1)*9+(d2+1)*3+(d3+1), avoids string building **/
	@SuppressWarnings( "unchecked" )
	private final ArrayList<int[]>[] neighborsArr = new ArrayList[27];
	
	/** vector of curve direction for the last point **/
	final double [] lastDirectionVector = new double[3];
	
	ArrayList<double[]> allPointsIntersection;
	
	/** spatial hash of allPointsIntersection: key is packed rounded voxel position,
	 * used for fast self-intersection check **/
	final HashMap<Long, ArrayList<double[]>> intersectionGrid = new HashMap<>();
	
	@SuppressWarnings( "rawtypes" )
	Convolution [] convObjects = new Convolution[6];
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
	
	/** eigenvectors container **/
	ArrayImg<FloatType, FloatArray> dV;
	/** weights (saliency) container **/
	ArrayImg<FloatType, FloatArray> sW;
	
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
	ArrayImg<FloatType, FloatArray> hessFloat;
	
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
	
	/** block-averaged local box (used only when downsampling) **/
	ArrayImg<FloatType, FloatArray> gridBox;	
	
	private LineTrace3D currentTracing;
	
	
	@Override
	protected Void doInBackground() throws Exception 
	{		
		runTracing();
		return null;
	}
	
	public void runTracing()
	{
		currentTracing = null;
		
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
		if(bNewTrace)
		{
			final RealPoint startRefinedPoint = refinePointUsingSaliency(startPoint);
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
				//direction vectors live in grid space
				for(int d = 0; d < 3; d++)
				{
						prevDirection[d] /= ds[d];
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
		points.add(startPoint);
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
			addIntersectionPoint(nextPoint.positionAsDoubleArray());
			nCountPoints++;
			//see if we are still inside reasonable 
			//values for math box
			if(!Intervals.contains(innerTraceBox, points.get(points.size()-1)))
			{
				getMathForCurrentPoint(points.get(points.size()-1));
			}

			nextPoint = getNextPoint(points.get(points.size()-1));
			
			if(nextPoint != null)
			{
				if(checkIntersection(nextPoint))
				{
					nextPoint = null;
					if(!bUseMask)
						System.out.println("one-click tracing stopped, self intersection found.");
				}
//				double [] nextPointD = nextPoint.positifillArrayOfTracedPointsonAsDoubleArray();
//				double [] prevPointD = points.get(points.size()-1).positionAsDoubleArray();
//				LinAlgHelpers.subtract(nextPointD, prevPointD, nextPointD);
//				LinAlgHelpers.scale(nextPointD, 1.0/LinAlgHelpers.length(nextPointD), nextPointD);				
//				System.out.println(LinAlgHelpers.length(saveVector)+" "+LinAlgHelpers.length(nextPointD)+" "+LinAlgHelpers.dot(saveVector, nextPointD));
//				System.out.println(LinAlgHelpers.dot(saveVector, nextPointD));
//				System.out.println(nCountPoints+" "+LinAlgHelpers.dot(saveVector, nextPointD));
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
	
	/** initialization of parameters **/
	@SuppressWarnings("deprecation")
	public void init()
	{	
		
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
			if(bt.btData.bOCDownsample)
			{
				ds[d] = Math.max(1, Math.round(s / bt.btData.dOCDownsampleSigma));
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
		//make downsampled box
		if(bDownsample)
		{
			gridBox = ArrayImgs.floats( boxFullRange[ 0 ], boxFullRange[ 1 ], boxFullRange[ 2 ]);
		}
		//IntervalView<T> input = Views.interval(fullInput, getLocalTraceBox(fullInput,boxFullHalfRange,startPoint));
		//dim = Intervals.dimensionsAsLongArray( input );
		
		//gradient, first derivatives, 3 values per voxel
		//gradFloat = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 3 );

		//hessian, second derivatives, 6 values per voxel
		//hessFloat = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 6 );
		//dV = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 3 );
		//sW = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ]);

		hessFloat = ArrayImgs.floats( boxFullRange[ 0 ], boxFullRange[ 1 ], boxFullRange[ 2 ], 6 );
		dV = ArrayImgs.floats( boxFullRange[ 0 ], boxFullRange[ 1 ], boxFullRange[ 2 ], 3 );
		sW = ArrayImgs.floats( boxFullRange[ 0 ], boxFullRange[ 1 ], boxFullRange[ 2 ]);

		
		initNeighborsHashMap();
		//initNeighbors();
		
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

		final double [] newDirection = new double[3];
		double [] candDirection;
	
		//find a pixel in the neighborhood 
		//according to direction vector
		int nNeighborIndex = 0;
		
		for (int d = 0; d < 3; d++)
		{
			//currNeighbor[d] = (int)Math.round(lastDirectionVector[d]);
			//currNeighborHash = currNeighborHash + Integer.toString(currNeighbor[d]);
			nNeighborIndex = nNeighborIndex * 3 + ((int)Math.round(lastDirectionVector[d]) + 1);
		}
		
		//get all relevant neighbors
		float maxSal = (-1) * Float.MAX_VALUE;
		
		float currSal = (-1) * Float.MAX_VALUE;
		
		ArrayList<int[]> scannedPos;
		
		if(nNeighborsMethods == 0)
		{
			//scannedPos = neighborsMap.get(currNeighborHash);
			scannedPos = neighborsArr[nNeighborIndex];
		}
		else
		{
			scannedPos = getNeighborPixels(lastDirectionVector);
		}
		int [] candidateNeighbor; 

		final long[] candPos = new long[3];
		
		final float[] finPos = new float[3];
		
		for(int nScan = 0; nScan < scannedPos.size(); nScan++)
		{
			candidateNeighbor = scannedPos.get(nScan);
			
			for (int d = 0; d < 3; d++)
			{
				//candPos[d] = Math.round(currpoint.getFloatPosition(d))+candidateNeighbor[d];
				candPos[d] = toGrid(currpoint.getDoublePosition(d), d) + candidateNeighbor[d];
			}		
		
			//if(Intervals.contains(fullInput, new RealPoint(new float []{candPos[0],candPos[1],candPos[2]})))
			boolean bInside = true;
			for (int d = 0; d < 3; d++)
			{
				//if(candPos[d] < fullInput.min( d ) || candPos[d] > fullInput.max( d ))
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
					//candDirection = getVectorAtLocation(candPos);
					candDirection = getVectorAtGrid(candPos);
					
					//full resolution position for intensity and mask checks
					final long [] candPosFull = toFullLong(candPos);
					
					//check directionality
					if(Math.abs(LinAlgHelpers.dot(candDirection, lastDirectionVector)) < dAngleThreshold)
					{
						currSal = 0.0f;
					}
					
					// intensity stop rule
					if(bt.btData.bOCIntensityStop)
					{
						//if(raInput.setPositionAndGet( candPos ).getRealDouble() < bt.btData.dOCIntensityThreshold)
						if(raInput.setPositionAndGet( candPosFull ).getRealDouble() < bt.btData.dOCIntensityThreshold)
						{
							currSal = 0.0f;
						}
					}
					
					//check if we hit the mask
					if(bUseMask)
					{
						if(traceMask.isOccupied( candPosFull ))
						//if(traceMask.isOccupied( candPos ))
						{
							currSal = 0.0f;
						}
					}
					//currSal *= Math.abs(LinAlgHelpers.dot(candDirection, lastDirectionVector));
					//see if we pass
					if(currSal > maxSal)
					{
						maxSal = currSal;
						for (int d = 0; d < 3; d++)
						{
							//finPos[d] = candPos[d];
							finPos[d] = (float) toFull(candPos[d], d);
							newDirection[d] = candDirection[d];
						}
					}
				}
			}
		}
		//System.out.println(maxSal);
		if(maxSal > 0.000001)
		{			
			final double dCos = LinAlgHelpers.dot(newDirection, lastDirectionVector);			
			if(dCos > 0)
			{
				for (int d = 0; d < 3; d++)
				{
					lastDirectionVector[d] = newDirection[d];
				}
			}
			else
			{
				LinAlgHelpers.scale(newDirection,-1.0,lastDirectionVector);
			}
			out = new RealPoint(finPos);
		}
		
		return out;
		
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
		
		//input for convolutions (full resolution or block averaged grid)
		final RandomAccessibleInterval<?> mathInput;
		if(!bDownsample)
		{
			currentBox.min(minV);
			mathInput = currentBox;
		}
		else
		{
			//same logic as getLocalTraceBox, in grid coordinates
			for(int d = 0; d < 3; d++)
			{
				minV[d] = Math.max(gridMin[d], toGrid(currPoint.getDoublePosition(d), d) - boxGridHalfRange[d]);
			}
			fillGridBox(minV);
			mathInput = Views.translate(gridBox, minV[0], minV[1], minV[2]);
		}
		
		//IntervalView<FloatType> gradient = Views.translate(gradFloat, nShift);
		IntervalView<FloatType> hessian = Views.translate(hessFloat, minV);
		
		bt.visualBoxes.traceBox.setInterval( currentBox );

	/*
		double [][] kernels;
		Kernel1D[] derivKernel;
		int [] nDerivOrder;
		Convolution convObj;
		//start1 = System.currentTimeMillis();
		final int nThreads = Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool( nThreads );
		//setProgress(0);
		/*
		//first derivatives
		for (int d1=0;d1<3;d1++)
		{
			//current coordinate
			IntervalView< FloatType > gd2 = Views.hyperSlice( gradient, 3, d1 );
			nDerivOrder = new int [3];
			nDerivOrder[d1]++;
			kernels = DerivConvolutionKernels.convolve_derive_kernel(bt.btdata.sigmaTrace, nDerivOrder);
			derivKernel = Kernel1D.centralAsymmetric(kernels);
			convObj = SeparableKernelConvolution.convolution( derivKernel );
			convObj.setExecutor(es);
			convObj.process(Views.extendBorder(currentBox), gd2 );
		}
		*/
		
		//second derivatives
		int count = 0;
		for (int d1 = 0; d1 < 3; d1++)
		{
			for (int d2 = d1; d2 < 3; d2++ )
			{
				IntervalView< FloatType > hs2 = Views.hyperSlice( hessian, 3, count );
				//FinalInterval test = (FinalInterval) convObjects[count].requiredSourceInterval(hs2);
				//convObjects[count].process(Views.extendBorder(currentBox), hs2 );
				convObjects[count].process(Views.extendBorder(mathInput), hs2 );
				count++;
			}
		}


		EigenValVecSymmDecomposition<FloatType> mEV = new EigenValVecSymmDecomposition<>(3);

		directionVectors =  Views.translate(dV, minV);
		salWeights =  Views.translate(sW, minV[0], minV[1], minV[2]);
		//salWeightsUB = VolumeMisc.convertFloatToUnsignedByte(salWeights,false);
		mEV.computeVWRAI(hessian, directionVectors, salWeights, nThreads, es);
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
		//get an box around the target
		final FinalInterval searchArea  = Intervals.intersect( fullInput, getLocalSearchArea(target_in, 2.0f) );
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
	
	/** returns orientation vector (in grid space) at the provided full resolution location **/
	public double[] getVectorAtLocation(final RealPoint point)
	{
		final long [] gridPos = new long [3];
		for (int d = 0; d < 3; d++)
		{
			gridPos[d] = toGrid(point.getDoublePosition(d), d);
		}
		return getVectorAtGrid(gridPos);
	}
	
	/** returns orientation vector (in grid space) at the provided grid location **/
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
			currDirVector[d] = raV.get().getRealDouble();
		}
		return currDirVector;
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

	/** packs 3 coordinates (each within +-2^20) into one long **/
	static long packKey(final long x, final long y, final long z)
	{
		final long nOff = 1L << 20;
		final long nMask = (1L << 21) - 1;
		return (((x + nOff) & nMask) << 42) | (((y + nOff) & nMask) << 21) | ((z + nOff) & nMask);
	}
	
	/** for each voxel in the 26 neighborhood
	 * it maps adjacent voxels
	 */
	void initNeighborsHashMap()
	{
		//int nCount = 0;
		for (int d1=-1;d1<2;d1++)
		{
			for ( int d2 = -1; d2 < 2; d2++ )
			{
				for ( int d3 = -1; d3 < 2; d3++ )
				{
					//let's determine "neighbor type"
					int nType = Math.abs(d1)+Math.abs(d2)+Math.abs(d3);
					//remove central pixel
					if(nType != 0)
					{
						//let's make a hash string
						//String sKey = Integer.toString(d1)+Integer.toString(d2)+Integer.toString(d3);
						ArrayList<int[]> around =new ArrayList<>();
						//System.out.print(sKey);
						
						//nCount++;	

						//iterating around the neighborhood of each pixel
						for (int m1=-1;m1<2;m1++)
						{
							for ( int m2 = -1; m2 < 2; m2++ )
							{
								for ( int m3 = -1; m3 < 2; m3++ )
								{
									int [] nb = new int [3];
									//"face cross" neighbor
									if(nType == 1)
									{
										nb[0] = m1+d1*2;
										nb[1] = m2+d2*2;
										nb[2] = m3+d3*2;
									}
									else
									{
										nb[0] = m1+d1;
										nb[1] = m2+d2;
										nb[2] = m3+d3;								
									}
									//check if it belongs to the original neighborhood
									
									if(Math.abs(nb[0])<2 && Math.abs(nb[1])<2 && Math.abs(nb[2])<2)
									{
										switch(nType)
										{
											//face, central neighbor
											case 1:
												around.add(nb);
												break;
											//neighbor and the middle of the edge
											case 2:
												//(let's remove central pixel)
												if(Math.abs(nb[0])+Math.abs(nb[1])+Math.abs(nb[2])!=0)
												{
													//remove sides
													if(Math.abs(m1)+Math.abs(m2)+Math.abs(m3)<3)
													{
														around.add(nb);
													}
												}
												break;
											//corner neighbor (let's remove central pixel)
											case 3:
												if(Math.abs(nb[0])+Math.abs(nb[1])+Math.abs(nb[2])!=0)
												{
													around.add(nb);
												}
												break;
											
										}
											
									}
								}
							}
						}
						//neighborsMap.put(sKey, around);
						neighborsArr[(d1 + 1) * 9 + (d2 + 1) * 3 + (d3 + 1)] = around;
						//System.out.println(" "+around.size()+" type"+Integer.toString(nType));
					}
				}
			}
		}
		//System.out.println(nCount);
		/*
		ArrayList<int[]> test = neighborsMap.get("0-11");
		for(int d=0;d<test.size();d++)
		{
			int[] vals=test.get(d);
			//System.out.println(Integer.toString(vals[0])+Integer.toString(vals[1])+Integer.toString(vals[2]));
			double [] origvector = new double[3];
			origvector[0] = 0.0;
			origvector[1] = -1.0;
			origvector[2] = 1.0;
			LinAlgHelpers.normalize(origvector);
			double [] neighbor = new double [3];
			for(int i=0;i<3;i++)
			{
				neighbor[i] = vals[i];
			}
			LinAlgHelpers.normalize(neighbor);
			//LinAlgHelpers.scale(neighbor, LinAlgHelpers.length(neighbor), neighbor);
			System.out.println(LinAlgHelpers.dot(origvector, neighbor));
			
		}
		*/
		
	}
	
	/** full resolution coordinate -> grid voxel. For ds = 1 it is Math.round(x), as before **/
	long toGrid(final double x, final int d)
	{
			return Math.floorDiv(Math.round(x), ds[d]);
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
	/** fills gridBox (positioned at gridMinPos in grid coordinates)
	 * with block averages of fullInput (border-extended) **/
	void fillGridBox(final long [] gridMinPos)
	{
		final RandomAccess<T> raIn = Views.extendBorder(fullInput).randomAccess();
		final RandomAccess<FloatType> raOut = gridBox.randomAccess();
		final long [] dims = Intervals.dimensionsAsLongArray( gridBox );
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
