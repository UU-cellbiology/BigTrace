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
import net.imglib2.algorithm.convolution.kernel.Kernel1D;
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.parallel.Parallelization;
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
			final ArrayImg<FloatType, FloatArray> [] res = computeVectorsWeightsCorners(Views.extendBorder(input), minV, dim, bt.btData.sigmaTrace, null);
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
			
			final ArrayImg<FloatType, FloatArray> [] res = computeVectorsWeightsCorners(
					Views.extendBorder(Views.translate(gridImg, gridMin)), gridMin, gridDim, sigmaGrid, ds);
			
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
	
	/** hessian (second derivative convolutions of source with sigma) in the box (minV, dim),
	 * its eigen decomposition, returns {direction vectors (dim x 3), saliency weights, line corners}
	 * as ArrayImgs starting at zero (box coordinates).
	 * ds: if not null, source is a downsampled grid and the hessian is converted 
	 * to full resolution units (d/dx_full = d/dx_grid / ds), so the vectors are 
	 * full resolution directions also for anisotropic ds **/
	@SuppressWarnings({ "rawtypes", "unchecked" })
	ArrayImg<FloatType, FloatArray> [] computeVectorsWeightsCorners(final RandomAccessible source, final long [] minV, final long [] dim, 
			final double [] sigma, final long [] ds)
	{
		final long[] nShift = new long [4];
		for (int i = 0; i < 3; i++)
		{
			nShift[i] = minV[i];
		}
		final int nStepShift = nProgressSteps - 7;
		
		ArrayImg<FloatType, FloatArray> hessFloat = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 6 );
		IntervalView<FloatType> hessian = Views.translate(hessFloat, nShift);
		
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

				IntervalView< FloatType > hs2 = Views.hyperSlice( hessian, 3, count );
				final int [] nDerivOrder = new int [3];
				nDerivOrder[d1]++;
				nDerivOrder[d2]++;
				final double [][] kernels = DerivConvolutionKernels.convolve_derive_kernel(sigma, nDerivOrder );
				if(ds != null)
				{
					final double dScale = 1.0 / (ds[d1] * ds[d2]);
					for(int k = 0; k < kernels[0].length; k++)
					{
						kernels[0][k] *= dScale;
					}
				}
				final Kernel1D[] derivKernel = Kernel1D.centralAsymmetric(kernels);
				
				final Convolution convObjx = SeparableKernelConvolution.convolution( derivKernel );

				Parallelization.runMultiThreaded( () -> {
					convObjx.process(source, hs2 );
				} );
				count++;
			}
		}

		EigenValVecSymmDecomposition<FloatType> mEV = new EigenValVecSymmDecomposition<>(3);
		ArrayImg<FloatType, FloatArray> dV = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 3 );
		ArrayImg<FloatType, FloatArray> sW = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ]);
		ArrayImg<FloatType, FloatArray> nC = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ]);
		IntervalView<FloatType> directionVectors =  Views.translate(dV, nShift);
		IntervalView<FloatType> salWeights =  Views.translate(sW, minV);
		IntervalView<FloatType> lineCorners =  Views.translate(nC, minV);

		setProgressState("trace box eigenvalues/corners...");
		  //Sleep for up to one second.
		try {
			Thread.sleep(1);
		} catch (InterruptedException ignore) {}
		setProgress((6 + nStepShift)*100/nProgressSteps);
		
		final int nThreads =  Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool( nThreads );
		mEV.computeVWCRAI(hessian, directionVectors, salWeights, lineCorners, nThreads, es);
		es.shutdown();
		return new ArrayImg [] {dV, sW, nC};
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
