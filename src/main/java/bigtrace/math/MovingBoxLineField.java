package bigtrace.math;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

/** LineField computed in a box (MathBox) of +- BOX_SD sigmas that moves with the trace: it is recomputed around
 * a position when the position leaves the inner +- INNER_SD sigmas of the box (one-click tracing).
 * <p>
 * Downsampled: each box is block-averaged from the input (with a margin for the kernels), the kernels are reduced
 * by the blur of the block averaging and the hessian is normalised by sigma / ds, as in semi-auto (DownsampleGrid).
 * <p>
 * Beyond the input borders the image is continued by oob; for MODE with a constant background, the mode of
 * the first box after setInput (one trace), so that it does not change while the box moves (FINDINGS, finding 8). **/
public class MovingBoxLineField < T extends RealType< T > > implements LineField
{
	/** box of the hessian around the current point: +- BOX_SD tracing sigmas **/
	public static final double BOX_SD = 3.0;

	/** the box is recomputed when the point is further than INNER_SD sigmas from its centre **/
	public static final double INNER_SD = 1.0;

	final MathBox.OutOfBounds oob;
	final int nThreads;

	/** downsampling factors **/
	final long [] ds = new long [] {1, 1, 1};
	final boolean bGrid;
	/** tracing sigma (the normalisation scale) and kernel sigma, grid voxels **/
	final double [] sigW = new double[3];
	final double [] sigK = new double[3];

	final MathBox box;
	@SuppressWarnings( "rawtypes" )
	final Convolution [] conv;
	ExecutorService es;

	/** half size of the box and of its inner part, centre of the box (grid voxels) **/
	final long [] boxHalf = new long[3];
	final long [] innerHalf = new long[3];
	final long [] boxCentre = new long[3];
	boolean bBoxValid = false;

	RandomAccessibleInterval< T > input;
	/** the input continued beyond its borders, made with the first box (background estimate from it) **/
	RandomAccessible< T > inputExtended;
	/** the input in grid voxels **/
	Interval grid;
	/** constant continuation beyond the image (MODE), downsampled case **/
	double dBackground = 0.0;

	/** sigma: tracing sigma (curve thickness SD) in input voxels; bDownsample, dTargetSigma: see DownsampleGrid.computeFactors **/
	public MovingBoxLineField(final double [] sigma, final boolean bDownsample, final double dTargetSigma, final MathBox.OutOfBounds oob_, final int nThreads_)
	{
		oob = oob_;
		nThreads = Math.max(1, nThreads_);
		//on the grid the kernels are smaller by the blur of the block averaging; the normalisation scale is sigma / ds
		bGrid = DownsampleGrid.computeFactors(sigma, bDownsample, dTargetSigma, ds, sigK);
		final long [] dims = new long[3];
		for (int d = 0; d < 3; d++)
		{
			sigW[d] = sigma[d] / ds[d];
			boxHalf[d] = (long) Math.ceil(BOX_SD * sigW[d]);
			innerHalf[d] = Math.max(1, (long) Math.ceil(INNER_SD * sigW[d]));
			dims[d] = 2 * boxHalf[d] + 1;
		}
		box = new MathBox(dims);
		box.setScale(sigW);
		box.enableGradient();
		es = Executors.newFixedThreadPool( nThreads );
		conv = MathBox.derivativeConvolutions(sigK, sigW, es);
	}

	/** the image (input voxels); also starts a new estimate of the background **/
	public void setInput(final RandomAccessibleInterval< T > input_)
	{
		input = input_;
		inputExtended = null;
		bBoxValid = false;
		if(bGrid)
		{
			final long [] gMin = new long[3];
			final long [] gMax = new long[3];
			for (int d = 0; d < 3; d++)
			{
				gMin[d] = DownsampleGrid.toGrid(input.min(d), ds[d]);
				gMax[d] = DownsampleGrid.toGrid(input.max(d), ds[d]);
			}
			grid = new FinalInterval(gMin, gMax);
		}
		else
			grid = input;
	}

	@Override
	public long [] downsampling()
	{
		return ds;
	}

	@Override
	public double [] gridSigma()
	{
		return sigW;
	}

	@Override
	public Interval gridInterval()
	{
		return grid;
	}

	/** the box is computed around x if x is not in its inner part **/
	@Override
	public boolean ensure(final double [] x)
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
			return false;
		final long [] min = new long[3];
		for (int d = 0; d < 3; d++)
		{
			boxCentre[d] = c[d];
			//inside of the image if it fits
			min[d] = Math.max(grid.min(d), Math.min(grid.max(d) - box.dims[d] + 1, c[d] - boxHalf[d]));
		}
		box.setMin(min);
		if(!bGrid)
		{
			if(inputExtended == null)
			{
				//the background (mode) is estimated once per trace, from its first box,
				//so the continuation beyond the image does not change while the box moves
				inputExtended = MathBox.extend(input, oob, box.boxInterval());
			}
			box.compute(inputExtended, conv, nThreads, es);
		}
		else
		{
			if(inputExtended == null)
			{
				//as above, from the first box (input voxels, within the input)
				dBackground = oob == MathBox.OutOfBounds.MODE ? MathBox.estimateMode(Views.interval(input, Intervals.intersect(input, availableInputInterval()))) : 0.0;
				inputExtended = MathBox.extend(input, oob, dBackground);
			}
			//block averages of the box and a margin for the kernels
			final long [] gMin = new long[3];
			final long [] gDim = new long[3];
			for (int d = 0; d < 3; d++)
			{
				final long m = (long) Math.ceil(4.0 * sigK[d]) + 2;
				gMin[d] = min[d] - m;
				gDim[d] = box.dims[d] + 2 * m;
			}
			final ArrayImg<FloatType, FloatArray> gridImg = ArrayImgs.floats(gDim);
			DownsampleGrid.blockAverage(inputExtended, gridImg, gMin, ds);
			box.compute(MathBox.extend(Views.translate(gridImg, gMin), oob, dBackground), conv, nThreads, es);
		}
		bBoxValid = true;
		return true;
	}

	/** the box in input voxels **/
	@Override
	public Interval availableInputInterval()
	{
		final long [] min = new long[3];
		final long [] max = new long[3];
		for (int d = 0; d < 3; d++)
		{
			min[d] = box.boxMin[d] * ds[d];
			max[d] = (box.boxMin[d] + box.dims[d]) * ds[d] - 1;
		}
		return new FinalInterval(min, max);
	}

	@Override
	public double saliencyAtVoxel(final long [] v)
	{
		return box.saliencyAtVoxel(v);
	}

	@Override
	public double saliencyAndVectorAtVoxel(final long [] v, final double [] vecOut)
	{
		return box.saliencyAndVectorAtVoxel(v, vecOut);
	}

	@Override
	public boolean hessianAtVoxel(final long [] v, final double [] h)
	{
		return box.hessianAtVoxel(v, h);
	}

	@Override
	public boolean gradientAtVoxel(final long [] v, final double [] g)
	{
		return box.gradientAtVoxel(v, g);
	}

	@Override
	public void normToGridDir(final double [] tn, final double [] out)
	{
		box.normToGridDir(tn, out);
	}

	@Override
	public void release()
	{
		if(es != null)
		{
			es.shutdown();
			es = null;
		}
	}
}
