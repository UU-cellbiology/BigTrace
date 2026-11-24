package bigtrace.math;

import java.util.concurrent.ExecutorService;

import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
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

	/** point-wise (interpolated) access to the hessian is made after each computeHessian;
	 * otherwise only on demand (getHessianBox) **/
	final boolean bInterpolated;

	/** point-wise access to the hessian of the box (after computeHessian if bInterpolated, see getHessianBox) **/
	HessianBox hessBox = null;

	/** box of size dims_ (grid voxels); bInterpolated_: point-wise (interpolated) hessian access (hessBox) **/
	public MathBox(final long [] dims_, final boolean bInterpolated_)
	{
		for (int d = 0; d < 3; d++)
		{
			dims[d] = dims_[d];
		}
		nVol = (int) (dims[0] * dims[1] * dims[2]);
		bInterpolated = bInterpolated_;
		hessImg = ArrayImgs.floats( dims[0], dims[1], dims[2], 6 );
		salImg = ArrayImgs.floats( dims[0], dims[1], dims[2] );
		vecImg = ArrayImgs.floats( dims[0], dims[1], dims[2], 3 );
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
		hessBox = null;
	}

	/** places the box centered at the grid position center **/
	public void setCenter(final long [] center)
	{
		for (int d = 0; d < 3; d++)
		{
			boxMin[d] = center[d] - dims[d] / 2;
		}
		hessBox = null;
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
		hessBox = bInterpolated ? new HessianBox(hessImg, boxMin) : null;
	}

	/** point-wise (linearly interpolated) access to the current hessian of the box,
	 * made on demand (once per computeHessian) if the box is not bInterpolated **/
	public HessianBox getHessianBox()
	{
		if(hessBox == null)
		{
			hessBox = new HessianBox(hessImg, boxMin);
		}
		return hessBox;
	}

	/** hessian in grid coordinates (x, y, z, component) **/
	public IntervalView<FloatType> hessView()
	{
		return Views.translate(hessImg, boxMin[0], boxMin[1], boxMin[2], 0);
	}

	/** line saliency in grid coordinates **/
	public IntervalView<FloatType> salView()
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
}
