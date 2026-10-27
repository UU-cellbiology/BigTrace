package bigtrace.math;


import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.volume.VolumeMisc;

import net.imglib2.FinalInterval;
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
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected Void doInBackground() throws Exception {
	
		int i;
		double [][] kernels;
		Kernel1D[] derivKernel;
		final long[] dim = Intervals.dimensionsAsLongArray( input );
		long[] minV = input.minAsLongArray();
		long[] nShift = new long [input.numDimensions()+1];
		
		for (i=0;i<input.numDimensions();i++)
		{
			nShift[i]=minV[i];
		}
		
		ArrayImg<FloatType, FloatArray> hessFloat = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 6 );
		IntervalView<FloatType> hessian = Views.translate(hessFloat, nShift);
		//ArrayImg<FloatType, FloatArray> gradient = ArrayImgs.floats( dim[ 0 ], dim[ 1 ], dim[ 2 ], 3 );		
		//long start1, end1;
		
		
		int count = 0;
		int [] nDerivOrder;
		//start1 = System.currentTimeMillis();
		
		//Convolution convObj;
//		final int nThreads = Runtime.getRuntime().availableProcessors();
//		ExecutorService es = Executors.newFixedThreadPool( nThreads );
		
		
		setProgress(0);
		//start1 = System.currentTimeMillis();

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
				setProgress(count*100/7);

				IntervalView< FloatType > hs2 = Views.hyperSlice( hessian, 3, count );
				nDerivOrder = new int [3];
				nDerivOrder[d1]++;
				nDerivOrder[d2]++;
				kernels = DerivConvolutionKernels.convolve_derive_kernel(bt.btData.sigmaTrace, nDerivOrder );
				derivKernel = Kernel1D.centralAsymmetric(kernels);
//				convObj = SeparableKernelConvolution.convolution( derivKernel );
//				convObj.setExecutor(es);
//				convObj.process(Views.extendBorder(input), hs2 );
				
				
				final Convolution convObjx = SeparableKernelConvolution.convolution( derivKernel );

				Parallelization.runMultiThreaded( () -> {
					convObjx.process(Views.extendBorder(input), hs2 );
				} );
				//SeparableKernelConvolution.convolution( derivKernel ).process( input, hs2 );
				count++;
				//System.out.println(count);
			}
		}
		//end1 = System.currentTimeMillis();
		//System.out.println("impl new Elapsed Time in milli seconds: "+ (end1-start1));
		

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
		setProgress(6*100/7);
		
		final int nThreads =  Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool( nThreads );
		mEV.computeVWCRAI(hessian, directionVectors, salWeights, lineCorners, nThreads, es);
		es.shutdown();
		
		setProgress(100);
		setProgressState("trace box done.");
		//ImageJFunctions.show( ( RandomAccessibleInterval< T > ) salWeights, "int" );
		//ImageJFunctions.show( lineCorners, "lineCorners" );
		//ImageJFunctions.show( ( RandomAccessibleInterval< T > ) input, "raw" );
		bt.btData.trace_weights = VolumeMisc.convertFloatToUnsignedByte(salWeights,false);

		
		//bt.btdata.jump_points =VolumeMisc.localMaxPointList(VolumeMisc.convertFloatToUnsignedByte(lineCorners,false), 50);
		bt.btData.jump_points = VolumeMisc.localMaxPointList(VolumeMisc.convertFloatToUnsignedByte(lineCorners,false), 10);
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
