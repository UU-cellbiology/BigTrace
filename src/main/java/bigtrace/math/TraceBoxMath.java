package bigtrace.math;


import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.volume.VolumeMisc;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class TraceBoxMath < T extends RealType< T > & NativeType< T > > extends BigTraceBGTask
{
	public BigTrace<T> bt;
	public IntervalView<T> input; 
	public RealPoint refinePosition = null;
	
	/** total number of progress steps (6 derivatives, eigen, (upsampling)) **/
	int nProgressSteps = 7;
	
	@Override
	protected Void doInBackground() throws Exception {
	
		final long[] dim = Intervals.dimensionsAsLongArray( input );
		final long[] minV = input.minAsLongArray();
		final long[] maxV = input.maxAsLongArray();
		
		//downsampling (block averaging) for large sigma
		final long [] ds = new long[3];
		final double [] sigmaGrid = new double[3];
		final boolean bDownsample = DownsampleGrid.computeFactors(bt.btData.sigmaTrace, bt.btData.bTraceDownsample, 
				bt.btData.dDownsampleSigma, ds, sigmaGrid);
		
		setProgress(0);
		
		IntervalView<FloatType> directionVectors;
		IntervalView<FloatType> salWeights;
		ArrayList<long []> jumpPoints;
		
		if(!bDownsample)
		{
			nProgressSteps = 7;
			//original full resolution calculation
			final ArrayImg<FloatType, FloatArray> [] res = computeVectorsWeightsCorners(Views.extendBorder(input), minV, dim, bt.btData.sigmaTrace,
					MathBox.lineScale(bt.btData.sigmaTrace, bt.btData.psfTrace), null);
			directionVectors =  Views.translate(res[0], minV[0], minV[1], minV[2], 0);
			salWeights =  Views.translate(res[1], minV);
			final IntervalView<FloatType> lineCorners =  Views.translate(res[2], minV);
			jumpPoints = VolumeMisc.localMaxPointList(VolumeMisc.convertFloatToUnsignedByte(lineCorners,false), 10);
		}
		else
		{
			nProgressSteps = 9;
			//grid box
			final long [] gridMin = new long[3];
			final long [] gridDim = new long[3];
			for(int d = 0; d < 3; d++)
			{
				gridMin[d] = DownsampleGrid.toGrid(minV[d], ds[d]);
				gridDim[d] = DownsampleGrid.toGrid(maxV[d], ds[d]) - gridMin[d] + 1;
			}
			setProgressState("trace box downsampling...");
			final ArrayImg<FloatType, FloatArray> gridImg = ArrayImgs.floats( gridDim[ 0 ], gridDim[ 1 ], gridDim[ 2 ]);
			DownsampleGrid.blockAverage(Views.extendBorder(input), gridImg, gridMin, ds);
			setProgress(100/nProgressSteps);
			
			//width of the filament profile on the grid
			final double [] scaleGrid = MathBox.lineScale(bt.btData.sigmaTrace, bt.btData.psfTrace);
			for(int d = 0; d < 3; d++)
			{
				scaleGrid[d] /= ds[d];
			}
			final ArrayImg<FloatType, FloatArray> [] res = computeVectorsWeightsCorners(
					Views.extendBorder(Views.translate(gridImg, gridMin)), gridMin, gridDim, sigmaGrid, scaleGrid, ds);
			
			//back to full resolution
			setProgressState("trace box upsampling...");
			setProgress(8*100/nProgressSteps);
			final ArrayImg<FloatType, FloatArray> dVFull = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 3 );
			final ArrayImg<FloatType, FloatArray> sWFull = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ]);
			DownsampleGrid.upsample(res[0], gridMin, dVFull, minV, ds, 3);
			DownsampleGrid.upsample(res[1], gridMin, sWFull, minV, ds, 1);
			directionVectors =  Views.translate(dVFull, minV[0], minV[1], minV[2], 0);
			salWeights =  Views.translate(sWFull, minV);
			
			//corners (jump points): local maxima on the grid, placed at the centers of grid voxels
			final ArrayList<long []> gridPoints = VolumeMisc.localMaxPointList(
					VolumeMisc.convertFloatToUnsignedByte(Views.translate(res[2], gridMin),false), 10);
			jumpPoints = new ArrayList<>();
			for(final long [] gp : gridPoints)
			{
				final long [] fp = new long[3];
				for(int d = 0; d < 3; d++)
				{
					fp[d] = Math.min(maxV[d], Math.max(minV[d], Math.round(DownsampleGrid.toFull(gp[d], ds[d]))));
				}
				jumpPoints.add(fp);
			}
		}
		
		setProgress(100);
		setProgressState("trace box done.");
		//ImageJFunctions.show( ( RandomAccessibleInterval< T > ) salWeights, "int" );
		//ImageJFunctions.show( ( RandomAccessibleInterval< T > ) input, "raw" );
		bt.btData.trace_weights = VolumeMisc.convertFloatToUnsignedByte(salWeights,false);

		
		//bt.btdata.jump_points =VolumeMisc.localMaxPointList(VolumeMisc.convertFloatToUnsignedByte(lineCorners,false), 50);
		bt.btData.jump_points = jumpPoints;
		bt.btData.trace_vectors = directionVectors;
		//bt.showCorners(bt.btdata.jump_points);
		
		//refine positions of the point
		if(refinePosition != null)
		{
			RealPoint refined = refinePointUsingSaliency(refinePosition);
			//System.out.println("Max int pos:"+Float.toString(refined.getFloatPosition(0))+" " +Float.toString(refined.getFloatPosition(1))+" "+Float.toString(refined.getFloatPosition(2))+" ");
			bt.roiManager.addSegment(refined,null);
		}
		return null;
	}
	
	/** scale-normalised hessian (second derivative convolutions of source with sigma, normalised by scale, MathBox) in the box (minV, dim),
	 * its eigen decomposition, returns {direction vectors (dim x 3), saliency weights, line corners}
	 * as ArrayImgs starting at zero (box coordinates).
	 * The direction vectors are unit directions in full resolution voxel units (as the steps of DijkstraFHRestrictVector).
	 * ds: if not null, source is a downsampled grid (sigma in grid voxels) **/
	@SuppressWarnings({ "rawtypes", "unchecked" })
	ArrayImg<FloatType, FloatArray> [] computeVectorsWeightsCorners(final RandomAccessible<?> source, final long [] minV, final long [] dim,
			final double [] sigma, final double [] scale, final long [] ds)
	{
		final int nStepShift = nProgressSteps - 7;
		
		final MathBox box = new MathBox(dim);
		box.setMin(minV);
		box.setScale(scale);
		box.enableCorners();

		final int nThreads =  Runtime.getRuntime().availableProcessors();
		final ExecutorService es = Executors.newFixedThreadPool( nThreads );
		final Convolution [] conv = MathBox.derivativeConvolutions(sigma, scale, es);
		int count = 0;
		//second derivatives
		for (int d1=0; d1<3; d1++)
		{
			for ( int d2 = d1; d2 < 3; d2++ )
			{
				//update progress bar
				setProgressState("trace box deriv_" +Integer.toString(d1+1)+"_"+Integer.toString(d2+1)+"...");
				  //Sleep for up to one second.
				try {
					Thread.sleep(1);
				} catch (InterruptedException ignore) {}
				setProgress((count + nStepShift)*100/nProgressSteps);
				box.computeHessianComponent(source, conv[count], count);
				count++;
			}
		}

		setProgressState("trace box eigenvalues/corners...");
		  //Sleep for up to one second.
		try {
			Thread.sleep(1);
		} catch (InterruptedException ignore) {}
		setProgress((6 + nStepShift)*100/nProgressSteps);
		
		box.computeSaliency(nThreads, es);
		es.shutdown();

		//directions: grid voxel space -> full resolution voxel space (DijkstraFHRestrictVector compares them
		//with the steps to the neighboring voxels: the angle cost is in the voxel lattice space)
		if(ds != null)
		{
			final float [] vec = box.vecImg.update( null ).getCurrentStorageArray();
			final int nVol = box.nVol;
			final double [] t = new double[3];
			for (int i = 0; i < nVol; i++)
			{
				double dNorm = 0;
				for (int d = 0; d < 3; d++)
				{
					t[d] = vec[i + d * nVol] * ds[d];
					dNorm += t[d] * t[d];
				}
				if(dNorm > 0)
				{
					dNorm = 1.0 / Math.sqrt(dNorm);
					for (int d = 0; d < 3; d++)
					{
						vec[i + d * nVol] = (float) (t[d] * dNorm);
					}
				}
			}
		}
		return new ArrayImg [] {box.vecImg, box.salImg, box.cornImg};
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
    	 catch (ExecutionException e) {
             e.getCause().printStackTrace();
             String msg = String.format("Unexpected problem during Hessian calculations: %s", 
                            e.getCause().toString());
             System.out.println(msg);
         } catch (InterruptedException e) {
             // Process e here
         }
    	bt.showTraceBox();

		//unlock user interaction
    	bt.bInputLock = false;
    }
    
	
	/** returns local maximum of saliency (using trace_weights) 
	 * around target_in point. The search box is equal to SD of tracing x2 **/
	RealPoint refinePointUsingSaliency(RealPoint target_in)
	{
		long[][] rangeMax = new long[2][3];
		float dSDN = 2.0f;
		for(int d=0;d<3;d++)
		{
			rangeMax[0][d] = Math.round(target_in.getFloatPosition(d)-dSDN*bt.btData.sigmaTrace[d]);
			rangeMax[1][d] = Math.round(target_in.getFloatPosition(d)+dSDN*bt.btData.sigmaTrace[d]);
		}
		//get an box around the target
		FinalInterval searchArea = new FinalInterval(rangeMax[0],rangeMax[1]);
		
		RealPoint out = new RealPoint(3);
		searchArea  = Intervals.intersect( bt.btData.trace_weights, searchArea );
		VolumeMisc.findMaxLocation(Views.interval(bt.btData.trace_weights, searchArea),out);
		return out;
		
	}

}
