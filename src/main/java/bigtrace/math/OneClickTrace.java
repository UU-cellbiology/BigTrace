package bigtrace.math;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Roi3D;
import net.imglib2.FinalInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.IntervalView;

/** One-click tracing task: traces from a click in both directions (or continues the active trace)
 * with OneClickCore and builds the line trace ROI from its points. Also used by full auto tracing
 * and curve tracking (runTracing, without the background task). **/
public class OneClickTrace < T extends RealType< T > & NativeType< T > > extends BigTraceBGTask
{
	public BigTrace<T> bt;

	/** full dataset to trace **/
	public IntervalView<T> fullInput;

	public int nTimePoint = -1;

	public RealPoint startPoint;

	public boolean bUpdateProgressBar = true;

	/** if true (default, interactive one-click), the ROI render geometry is rebuilt
	 * and the viewer repainted after every segment, with a short pause to let UI update.
	 * Set to false for batch tracing (full auto): curve geometry is built
	 * once at the end of each tracing direction, no pauses. **/
	public boolean bLiveUpdate = true;

	public boolean bUnlockInTheEnd = true;

	/** if running in auto-trace**/
	public boolean bUseMask = false;

	/** "trace mask" containing areas marked by existing ROIs **/
	public RoiTraceMask<T> traceMask = null;

	/** whether to initialize internally **/
	public boolean bInit = true;

	/** if the optimized location is already occupied in the tracemask **/
	public boolean bStartLocationOccupied = false;

	/** whether we are starting a new trace or continue with existing **/
	public boolean bNewTrace;

	/** if true, inserts ROI to the list with nInsertROIInd  index,
	 * otherwise just adds ROI to the end **/
	public boolean bInsertROI = false;

	public int nInsertROIInd = 0;

	/** the tracing algorithm **/
	final OneClickCore<T> core = new OneClickCore<>();

	/** after this amount of points new segment will be added to LineTrace ROI **/
	int nPointPerSegment;

	float fEstimatedThickness = 5.0f;

	private LineTrace3D currentTracing;

	@Override
	protected Void doInBackground() throws Exception
	{
		runTracing();
		return null;
	}

	/** settings of the tracing from BigTraceData **/
	public void init()
	{
		for (int d = 0; d < 3; d++)
		{
			core.sigma[d] = bt.btData.sigmaTrace[d];
			final double cal = bt.btData.globCal[d];
			core.voxelSize[d] = cal > 0 ? cal : 1.0;
		}
		core.psf = bt.btData.psfTrace.clone();
		core.dDirectionality = bt.btData.dDirectionalityOneClick;
		core.dIntensityThreshold = bt.btData.bOCIntensityStop ? bt.btData.dOCIntensityThreshold : Double.NaN;
		core.init();
		nPointPerSegment = bt.btData.nVertexPlacementPointN;
		if(bt.btData.bEstimateROIThicknessFromParams)
		{
			fEstimatedThickness = bt.btData.estimateROIThicknessFromTracing();
		}
	}

	public void releaseMultiThread()
	{
		core.release();
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
		core.setInput(fullInput);
		core.occupied = bUseMask ? (v -> traceMask.isOccupied(v)) : null;
		bt.visualBoxes.bShowTraceBox = true;
		core.clearTrace();

		final double [] start;
		final double [] startDir = new double[3];
		int nTotPoints = 0;
		if(bNewTrace)
		{
			start = core.findStart(startPoint.positionAsDoubleArray(), startDir);
			showBox();
			if(start == null)
			{
				if(bUseMask)
				{
					markStartArea();
					bStartLocationOccupied = true;
				}
				setProgressState("no curve found at the start point.");
				return;
			}
			//masked tracing: already traced?
			if(bUseMask)
			{
				bStartLocationOccupied = false;
				if(traceMask.isOccupied( new RealPoint(start) ))
				{
					markStartArea();
					bStartLocationOccupied = true;
					return;
				}
			}
			//one direction
			core.begin(start, startDir);
			nTotPoints = traceOneDirection(true, 0, start);
			if(nTotPoints < 0)
			{
				setProgressState("Tracing interrupted by user.");
				return;
			}
			if(bUpdateProgressBar)
			{
				setProgress(50);
				setProgressState(Integer.toString(nTotPoints) + " points found.");
			}
			//the other direction
			currentTracing.reversePoints();
			LinAlgHelpers.scale(startDir, -1.0, startDir);
		}
		else
		{
			//continue the active trace from its last point (from the line point next to it)
			currentTracing = (LineTrace3D)bt.roiManager.getActiveRoi();
			final double [] last = currentTracing.vertices.get(currentTracing.vertices.size() - 1).positionAsDoubleArray();
			final double [] onLine = core.nearestLinePoint(last, startDir);
			showBox();
			if(onLine == null)
			{
				setProgressState("no curve found at the end of the trace.");
				return;
			}
			start = onLine;
			final ArrayList<double[]> pts = currentTracing.makeJointSegmentDouble();
			for(final double [] pt : pts)
			{
				core.addTracePoint( pt );
			}
			//away from the existing trace
			if(pts.size() > 1)
			{
				final double [] back = pts.get(Math.max(0, pts.size() - 1 - (int) Math.ceil(core.sigma[0])));
				final double [] away = new double[3];
				for (int d = 0; d < 3; d++)
				{
					away[d] = (start[d] - back[d]) * core.voxelSize[d];
				}
				if(LinAlgHelpers.dot(away, startDir) < 0)
				{
					LinAlgHelpers.scale(startDir, -1.0, startDir);
				}
			}
			nTotPoints = pts.size();
		}
		core.begin(start, startDir);
		nTotPoints = traceOneDirection(false, nTotPoints, start);
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
		if(bUpdateProgressBar)
		{
			setProgress(100);
			setProgressState("trace with " + Integer.toString(nTotPoints) + " points, stopped: " + stopMessage(core.stopReason) + ".");
		}
	}

	/** the ROI created by the last runTracing() (may have been removed again) **/
	public LineTrace3D getCurrentTracing()
	{
		return currentTracing;
	}

	/** traces line in one direction from start (core.begin was called). Returns number of current found points
	 * or -1 if it got interrupted.
	 * @param bFirstTrace
	 *        if it is a first trace or continuation of existing
	 * @param nCountIn
	 *        if it is a continuation, how many vertices in ROI already **/
	int traceOneDirection(final boolean bFirstTrace, final int nCountIn, final double [] start)
	{
		ArrayList<RealPoint> points = new ArrayList<>();
		points.add(new RealPoint(start));
		int nCountPoints = 0;
		if(bFirstTrace)
		{
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

		double [] next = core.step();
		while (next != null)
		{
			showBox();
			if(points.size() == nPointPerSegment)
			{
				final RealPoint lastSegmentPoint = points.get(points.size() - 1).positionAsRealPoint();
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
					setProgressState(Integer.toString(nCountPoints) + " points found.");
					setProgress(bFirstTrace ? 20 : 70);
				}
				if(bLiveUpdate)
				{
					try {
						Thread.sleep(1);
					} catch (InterruptedException ignore) {}
				}
			}
			points.add(new RealPoint(next));
			nCountPoints++;
			if(isCancelled())
			{
				return -1;
			}
			next = core.step();
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

	/** shows the box of the hessian if it moved **/
	void showBox()
	{
		if(core.bBoxMoved)
		{
			core.bBoxMoved = false;
			bt.visualBoxes.traceBox.setInterval( core.getBoxInterval() );
		}
	}

	/** marks the start search area (+- 2 SD) as traced in the mask (auto tracing) **/
	void markStartArea()
	{
		final long [][] range = new long[2][3];
		for(int d = 0; d < 3; d++)
		{
			range[0][d] = Math.round(startPoint.getDoublePosition(d) - OneClickCore.START_SD * core.sigma[d]);
			range[1][d] = Math.round(startPoint.getDoublePosition(d) + OneClickCore.START_SD * core.sigma[d]);
		}
		traceMask.markInterval(new FinalInterval(range[0], range[1]));
	}

	static String stopMessage(final OneClickCore.Stop stop)
	{
		switch(stop)
		{
			case DIRECTION: return "directionality constrain";
			case INTENSITY: return "intensity threshold";
			case MASK: return "existing ROI";
			case SELF_INTERSECTION: return "self intersection";
			case BORDER: return "image border";
			case NO_LINE_POINT: return "end of the curve";
			default: return "-";
		}
	}

	/*
	 * Executed in event dispatching thread
	 */
	@Override
	public void done()
	{
		//see if we have some errors
		try
		{
			get();
		}
		catch (ExecutionException e)
		{
			e.getCause().printStackTrace();
			final String msg = String.format("Unexpected problem during one-click tracing: %s",
					e.getCause().toString());
			System.out.println(msg);
		}
		catch (InterruptedException e)
		{
			// Process e here
		}
		catch (Exception e)
		{
			setProgressState("Tracing interrupted by user.");
			setProgress(100);
		}
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
		}
	}
}
