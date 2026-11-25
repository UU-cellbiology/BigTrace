package bigtrace.math;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/** A box of the tracing grid (fixed size, anisotropic, placed anywhere) with the hessian at its voxels
 * (full separable Gaussian second derivative convolution) and the line saliency and direction
 * computed from it (EigenValVecSymmDecomposition), optionally with point-wise (linearly interpolated)
 * access to the hessian (HessianBox).
 * Used as the math box of the one-click tracing (around the current point, OneClickTrace) and,
 * extended by JunctionBox, as the box around a junction or a gap (with interpolation). **/
public class MathBox
{
	/** size of the box (grid voxels) **/
	final long [] dims = new long[3];

	/** number of voxels of the box **/
	final int nVol;

	/** grid position of the first voxel of the box **/
	final long [] boxMin = new long[3];

	/** hessian at the voxels of the box (6 components: xx, xy, xz, yy, yz, zz) **/
	final ArrayImg<FloatType, FloatArray> hessImg;

	/** line saliency at the voxels of the box (computeSaliency) **/
	final ArrayImg<FloatType, FloatArray> salImg;

	/** line direction (hessian eigenvector of the smallest absolute eigenvalue, grid space, unit)
	 * at the voxels of the box (3 components, computeSaliency) **/
	final ArrayImg<FloatType, FloatArray> vecImg;

	/** block averaged input of the box with the convolution margin (downsampling only, setGridMargin) **/
	ArrayImg<FloatType, FloatArray> gridImg = null;

	/** margin of gridImg around the box (convolution input) **/
	final long [] gridMargin = new long[3];


	/** interpolators per hessian component (6 of them) **/
	final List <RealRandomAccess< FloatType >>  rraHess = new ArrayList<>();

	/** interpolators per vector component (3 of them) **/
	final List <RealRandomAccess< FloatType >>  rraVec = new ArrayList<>();
	
	/** interpolated saliency volume**/
	RealRandomAccess< FloatType > rraSal = null;
	
	final OutOfBounds< FloatType > raSal;
	
	final OutOfBounds< FloatType > raHess;

	final OutOfBounds< FloatType > raVec;

	/** box of size dims_ (grid voxels); bInterpolated_: point-wise (interpolated) hessian access (hessBox) **/
	public MathBox(final long [] dims_)
	{
		for (int d = 0; d < 3; d++)
		{
			dims[d] = dims_[d];
		}
		nVol = (int) (dims[0] * dims[1] * dims[2]);
		hessImg = ArrayImgs.floats( dims[0], dims[1], dims[2], 6 );
		salImg = ArrayImgs.floats( dims[0], dims[1], dims[2] );
		vecImg = ArrayImgs.floats( dims[0], dims[1], dims[2], 3 );
		
		raSal = Views.extendValue( salView(), Float.NaN ).randomAccess();
		raHess = Views.extendValue( hessView(), Float.NaN ).randomAccess();
		raVec = Views.extendValue( vecView(), Float.NaN ).randomAccess();
	}

	/** sets the margin of the block averaged input around the box (needed by the convolutions),
	 * allocates it (used with downsampling) **/
	public void setGridMargin(final long [] margin)
	{
		for (int d = 0; d < 3; d++)
		{
			gridMargin[d] = margin[d];
		}
		gridImg = ArrayImgs.floats( dims[0] + 2 * gridMargin[0], dims[1] + 2 * gridMargin[1], dims[2] + 2 * gridMargin[2] );
	}

	/** grid position of the first voxel of the block averaged input (box minus margin) **/
	public long [] gridInputMin()
	{
		final long [] out = new long[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = boxMin[d] - gridMargin[d];
		}
		return out;
	}

	/** places the box with its first voxel at the grid position min (first 3 values) **/
	public void setMin(final long [] min)
	{
		for (int d = 0; d < 3; d++)
		{
			boxMin[d] = min[d];
		}
		resetInterpolators();
	}

	/** places the box centered at the grid position center **/
	public void setCenter(final long [] center)
	{
		for (int d = 0; d < 3; d++)
		{
			boxMin[d] = center[d] - dims[d] / 2;
		}
		resetInterpolators();
	}
	
	void resetInterpolators()
	{
		rraHess.clear();
		rraVec.clear();
		rraSal = null;
	}

	/** hessian of the box by the 6 second derivative convolutions conv (xx, xy, xz, yy, yz, zz) of input
	 * (grid coordinates, already extended beyond its borders), then the line saliency and direction
	 * at its voxels (computeSaliency). es may be null (single thread) **/
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public void computeHessian(final RandomAccessible<?> input, final Convolution [] conv, final int nThreads, final ExecutorService es)
	{
		final IntervalView<FloatType> hess = hessView();
		for (int count = 0; count < 6; count++)
		{
			conv[count].process(input, Views.hyperSlice( hess, 3, count ));
		}
		computeSaliency(nThreads, es);
	}

	/** line saliency (salImg) and direction (vecImg) at the voxels of the box from its hessian (hessImg),
	 * EigenValVecSymmDecomposition; the point-wise access (hessBox) if bInterpolated.
	 * es may be null (single thread) **/
	public void computeSaliency(final int nThreads, final ExecutorService es)
	{
		final EigenValVecSymmDecomposition<FloatType> mEV = new EigenValVecSymmDecomposition<>(3);
		if(es != null && nThreads > 1)
		{
			mEV.computeVWRAI(hessImg, vecImg, salImg, nThreads, es);
		}
		else
		{
			mEV.computeVWRAI(hessImg, vecImg, salImg);
		}
	}


	/** hessian in grid coordinates (x, y, z, component) **/
	public IntervalView<FloatType> hessView()
	{
		return Views.translate(hessImg, boxMin[0], boxMin[1], boxMin[2], 0);
	}

	/** line saliency in grid coordinates **/
	public IntervalView< FloatType > salView()
	{
		return Views.translate(salImg, boxMin[0], boxMin[1], boxMin[2]);
	}

	/** line direction (grid space, unit) in grid coordinates (x, y, z, component) **/
	public IntervalView<FloatType> vecView()
	{
		return Views.translate(vecImg, boxMin[0], boxMin[1], boxMin[2], 0);
	}

	/** the grid voxel (x, y, z) is inside of the box **/
	public boolean contains(final long x, final long y, final long z)
	{
		return x >= boxMin[0] && y >= boxMin[1] && z >= boxMin[2]
				&& x < boxMin[0] + dims[0] && y < boxMin[1] + dims[1] && z < boxMin[2] + dims[2];
	}

	/** line saliency at the continuous grid position x, 
	 * trilinearly interpolated from the voxels of the box;
	 * NaN outside of the box **/
	public double saliencyAt(final double [] x)
	{		
		if(rraSal == null)
		{
			initSaliencyInterpolation();
		}
		rraSal.setPosition( x );
		return rraSal.get().get();
	}
	
	/** line saliency at the continuous grid position x, 
	 * trilinearly interpolated from the voxels of the box;
	 * NaN outside of the box **/
	public double saliencyAtVoxel(final long [] v)
	{				
		return raSal.setPositionAndGet( v[0], v[1], v[2] ).get();
	}
	
	
	/** interpolated version **/
	public double saliencyAndVectorAt(final double [] x, final double [] vecOut)
	{
		final double sal = saliencyAt(x);
		if(Double.isNaN(sal))
			return sal;
		if(rraVec.size() < 1)
		{
			initVectorInterpolation();
		}
		for(int d = 0; d < 3; d++)
		{
			vecOut[d] = rraVec.get( d ).setPositionAndGet( x ).get();
		}
		return sal;
	}

	public double saliencyAndVectorAtVoxel(final long [] v, final double [] vecOut)
	{
		final double sal = saliencyAtVoxel(v);
		if(Double.isNaN(sal))
			return sal;
		for(int d = 0; d < 3; d++)
		{
			vecOut[d] = raVec.setPositionAndGet( v[0], v[1], v[2], d ).get();
		}
		return sal;
	}

	
	public boolean hessianAtVoxel(final long [] v, final double [] h)
	{
		for (int c = 0; c < 6; c++)
		{
			h[c] = raHess.setPositionAndGet( v[0], v[1], v[2], c ).get();
			if(Double.isNaN( h[c]))
				return false;
		}
		
		
		return true;
	}

	/** line saliency at the grid voxel (x, y, z), its direction (grid space, unit) to vecOut (may be null);
	 * NaN outside of the box **/
	public double saliencyAtVoxel(final long x, final long y, final long z, final double [] vecOut)
	{
		if(!contains(x, y, z))
			return Double.NaN;
		final int i = (int) (((z - boxMin[2]) * dims[1] + (y - boxMin[1])) * dims[0] + (x - boxMin[0]));
		if(vecOut != null)
		{
			final float [] vec = vecImg.update( null ).getCurrentStorageArray();
			for (int d = 0; d < 3; d++)
			{
				vecOut[d] = vec[i + d * nVol];
			}
		}
		return salImg.update( null ).getCurrentStorageArray()[i];
	}

	/** Line saliency of the box (salImg) for display (debug visualization), see toDisplay **/
	public IntervalView<UnsignedByteType> getSaliencyForDisplay(final long [] ds)
	{
		return toDisplay(salImg, ds);
	}

	/** box image (grid) -> full resolution voxel coordinates (upsampled from the grid if ds is more than 1),
	 * scaled to 0-255 by its maximum, negative values are 0 **/
	IntervalView<UnsignedByteType> toDisplay(final ArrayImg<FloatType, FloatArray> img, final long [] ds)
	{
		ArrayImg<FloatType, FloatArray> src = img;
		final long [] min = boxMin.clone();
		if(ds[0] > 1 || ds[1] > 1 || ds[2] > 1)
		{
			final long [] dimsFull = new long[3];
			for (int d = 0; d < 3; d++)
			{
				min[d] = boxMin[d] * ds[d];
				dimsFull[d] = dims[d] * ds[d];
			}
			src = ArrayImgs.floats( dimsFull );
			DownsampleGrid.upsample(img, boxMin, src, min, ds, 1);
		}
		final float [] in = src.update( null ).getCurrentStorageArray();
		float max = 0;
		for(final float v : in)
		{
			max = Math.max(max, v);
		}
		final double scale = max > 0 ? 255.0 / max : 0.0;
		final ArrayImg<UnsignedByteType, ByteArray> out = ArrayImgs.unsignedBytes( src.dimension(0), src.dimension(1), src.dimension(2) );
		final byte [] outArr = out.update( null ).getCurrentStorageArray();
		for (int i = 0; i < in.length; i++)
		{
			outArr[i] = (byte) (int) Math.round(Math.max(0.0, Math.min(255.0, in[i] * scale)));
		}
		return Views.translate(out, min);
	}
	
	void initSaliencyInterpolation()
	{
		final InterpolatorFactory<FloatType, RandomAccessible< FloatType >> nInterpolatorFactory = new NLinearInterpolatorFactory<>();
		rraSal = Views.interpolate (Views.extendValue(salView(), Float.NaN), nInterpolatorFactory).realRandomAccess();			
	}
	
	void initHessianInterpolation()
	{
		final InterpolatorFactory<FloatType, RandomAccessible< FloatType >> nInterpolatorFactory = new NLinearInterpolatorFactory<>();

		for(int c = 0; c < 6; c++)
		{
			rraHess.add( Views.interpolate( 
				Views.extendValue(
						Views.translate(
								Views.hyperSlice( hessImg, 3, c ), boxMin), Float.NaN), nInterpolatorFactory).realRandomAccess() );
		}
	}
	
	void initVectorInterpolation()
	{
		final InterpolatorFactory<FloatType, RandomAccessible< FloatType >> nInterpolatorFactory = new NLinearInterpolatorFactory<>();

		for(int d = 0; d < 3; d++)
		{
			rraVec.add( Views.interpolate( 
				Views.extendValue(
						Views.translate(
								Views.hyperSlice( vecImg, 3, d ), boxMin), Float.NaN), nInterpolatorFactory).realRandomAccess() );
		}
	}
	
	
	/** steered line filter (SteerableLineFilter.lineResponse) at the grid position x
	 * from the trilinearly interpolated hessian. Returns NaN outside of the box **/
	public double lineResponse(final double [] x, final double [] e)
	{
		final double [] h = new double[6];
		if(rraHess.size() < 1)
		{
			initHessianInterpolation();
		}
		for (int c = 0; c < 6; c++)
		{
			h[c] = rraHess.get( c ).setPositionAndGet( x ).get();
			if(Double.isNaN(h[c]))
				return Double.NaN;
		}
		return SteerableLineFilter.lineResponse(h, e);
	}
	
	/** elongated line filter on the ray ahead of p along the unit direction e:
	 * Gaussian (SD alongSigma) weighted mean of the steered line filter at 1..nMax grid voxels.
	 * Samples outside of the box are skipped, NaN if there are none inside **/
	public double rayResponse(final double [] p, final double [] e, final double alongSigma, final int nMax)
	{
		final double [] x = new double[3];
		double sum = 0;
		double wsum = 0;
		for (int s = 1; s <= nMax; s++)
		{
			for (int d = 0; d < 3; d++)
			{
				x[d] = p[d] + s * e[d];
			}
			final double r = lineResponse(x, e);
			if(!Double.isNaN(r))
			{
				final double w = Math.exp(-0.5 * s * s / (alongSigma * alongSigma));
				sum += w * r;
				wsum += w;
			}
		}
		return wsum > 0 ? sum / wsum : Double.NaN;
	}

	/** "Arms" of lines around p: directions (from dirs, covering the whole sphere) where the
	 * elongated ray filter has a local maximum (within 20 deg) of at least dFrac of the
	 * strongest response, at least dSepDeg apart from each other.
	 * A plain filament has 2 arms (opposite), a merge/Y junction 3, a crossing 4.
	 * Returns copies of the arm directions sorted by decreasing response. **/
	public ArrayList<double[]> findArms(final double [] p, final double [][] dirs, final double alongSigma, final int nMax, final double dFrac, final double dSepDeg)
	{
		final ArrayList<double[]> arms = new ArrayList<>();
		final int nDirs = dirs.length;
		final double [] resp = new double[nDirs];
		double dMax = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < nDirs; i++)
		{
			final double r = rayResponse(p, dirs[i], alongSigma, nMax);
			resp[i] = Double.isNaN(r) ? Double.NEGATIVE_INFINITY : r;
			dMax = Math.max(dMax, resp[i]);
		}
		if(!(dMax > 0))
			return arms;
		//indices sorted by decreasing response
		final Integer [] order = new Integer[nDirs];
		for (int i = 0; i < nDirs; i++)
		{
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Double.compare(resp[b], resp[a]));
		final double dNbCos = Math.cos(Math.toRadians(20.0));
		final double dSepCos = Math.cos(Math.toRadians(dSepDeg));
		final ArrayList<Integer> picked = new ArrayList<>();
		for (final int i : order)
		{
			if(resp[i] < dFrac * dMax)
				break;
			boolean bSeparated = true;
			for (final int j : picked)
			{
				if(LinAlgHelpers.dot(dirs[i], dirs[j]) >= dSepCos)
				{
					bSeparated = false;
					break;
				}
			}
			if(!bSeparated)
				continue;
			//local maximum among the neighboring directions
			boolean bLocalMax = true;
			for (int k = 0; k < nDirs; k++)
			{
				if(LinAlgHelpers.dot(dirs[i], dirs[k]) > dNbCos && resp[k] > resp[i])
				{
					bLocalMax = false;
					break;
				}
			}
			if(bLocalMax)
			{
				picked.add(i);
				arms.add(dirs[i].clone());
			}
		}
		return arms;
	}
	
	
	
}
