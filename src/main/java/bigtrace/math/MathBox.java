package bigtrace.math;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.algorithm.convolution.kernel.Kernel1D;
import net.imglib2.algorithm.convolution.kernel.SeparableKernelConvolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/** A box of voxels (fixed size, placed anywhere in the image) with the scale-normalised hessian
 * (and optionally the gradient) of the Gaussian smoothed input at its voxels, and the line saliency
 * and direction computed from the hessian (EigenValVecSymmDecomposition).
 * <p>
 * Scale normalisation (setScale): the derivatives are taken with respect to x_i / scale_i,
 * H'_ij = scale_i scale_j d2/dx_i dx_j and g'_i = scale_i d/dx_i (scale per axis, in voxels).
 * With the scale equal to the width of the smoothed filament profile per axis (lineScale: the PSF and the
 * smoothing combined), a filament has a round cross-section in that space, so the eigen decomposition
 * (which direction has the smallest curvature) does not depend on the anisotropy of the PSF or of the voxels.
 * The line directions (vecImg and all direction outputs) are converted back to voxel units (normToGridDir);
 * only the hessian and the gradient themselves (hessianAt, hessianAtVoxel, gradientAtVoxel) stay normalised.
 * <p>
 * Used by one-click tracing (the box around the current point, OneClickCore) and by semi-auto tracing
 * (the trace box, TraceBoxMath). **/
public class MathBox
{
	/** size of the box (voxels) **/
	final long [] dims = new long[3];

	/** number of voxels of the box **/
	final int nVol;

	/** position of the first voxel of the box **/
	final long [] boxMin = new long[3];

	/** scale-normalised hessian at the voxels of the box (6 components: xx, xy, xz, yy, yz, zz) **/
	final ArrayImg<FloatType, FloatArray> hessImg;

	/** line saliency at the voxels of the box (computeSaliency) **/
	final ArrayImg<FloatType, FloatArray> salImg;

	/** line direction (eigenvector of the smallest absolute eigenvalue, voxel units, unit length)
	 * at the voxels of the box (3 components, computeSaliency) **/
	final ArrayImg<FloatType, FloatArray> vecImg;

	/** scale-normalised gradient at the voxels of the box (3 components), only if enabled (enableGradient) **/
	ArrayImg<FloatType, FloatArray> gradImg = null;

	/** line corners (EigenValVecSymmDecomposition.computeVWCRAI) at the voxels of the box,
	 * only if enabled (enableCorners) **/
	ArrayImg<FloatType, FloatArray> cornImg = null;

	/** scale (voxels) per axis of the normalised derivatives (setScale) **/
	final double [] scale = new double [] {1.0, 1.0, 1.0};

	/** interpolators per hessian component (6 of them), made on demand for the current position of the box **/
	final List <RealRandomAccess< FloatType >>  rraHess = new ArrayList<>();

	/** interpolated saliency, made on demand for the current position of the box **/
	RealRandomAccess< FloatType > rraSal = null;

	/** box of size dims_ (voxels) **/
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
	}

	/** width (SD, voxels) per axis of the profile of a thin filament imaged with the PSF (SD psf)
	 * and smoothed with the Gaussian of SD sigma: sqrt(psf^2 + sigma^2). The scale of the normalised derivatives. **/
	public static double [] lineScale(final double [] sigma, final double [] psf)
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = Math.sqrt(psf[d] * psf[d] + sigma[d] * sigma[d]);
		}
		return out;
	}

	/** scale (voxels, per axis) of the normalised derivatives (see derivativeConvolutions, lineScale);
	 * the line directions are converted with it to voxel units **/
	public void setScale(final double [] scale_)
	{
		for (int d = 0; d < 3; d++)
		{
			scale[d] = scale_[d];
		}
	}

	public double [] getScale()
	{
		return scale.clone();
	}

	/** also the gradient (gradientAtVoxel) at the voxels of the box, allocates it **/
	public void enableGradient()
	{
		if(gradImg == null)
		{
			gradImg = ArrayImgs.floats( dims[0], dims[1], dims[2], 3 );
		}
	}

	/** also the line corners (cornView) at the voxels of the box, allocates them **/
	public void enableCorners()
	{
		if(cornImg == null)
		{
			cornImg = ArrayImgs.floats( dims[0], dims[1], dims[2] );
		}
	}

	/** The 9 separable scale-normalised derivative convolutions of the Gaussian with SD sigma (voxels, per axis):
	 * 0-5 the second derivatives xx, xy, xz, yy, yz, zz, 6-8 the first derivatives x, y, z.
	 * The mask of a derivative of order k along axis i is multiplied by scale_i^k.
	 * They are run by es if it is not null. **/
	@SuppressWarnings("rawtypes")
	public static Convolution [] derivativeConvolutions(final double [] sigma, final double [] scale, final ExecutorService es)
	{
		final Convolution [] conv = new Convolution[9];
		int count = 0;
		for (int d1 = 0; d1 < 3; d1++)
		{
			for (int d2 = d1; d2 < 3; d2++ )
			{
				final int [] nDerivOrder = new int [3];
				nDerivOrder[d1]++;
				nDerivOrder[d2]++;
				conv[count++] = convolution(sigma, scale, nDerivOrder, es);
			}
		}
		for (int d = 0; d < 3; d++)
		{
			final int [] nDerivOrder = new int [3];
			nDerivOrder[d]++;
			conv[count++] = convolution(sigma, scale, nDerivOrder, es);
		}
		return conv;
	}

	@SuppressWarnings({ "rawtypes", "deprecation" })
	static Convolution convolution(final double [] sigma, final double [] scale, final int [] nDerivOrder, final ExecutorService es)
	{
		final double [][] kernels = DerivConvolutionKernels.convolve_derive_kernel(sigma, nDerivOrder);
		for (int d = 0; d < 3; d++)
		{
			//the masks are convolution masks (Steger), imglib2 correlates: the odd ones are mirrored (sign)
			final double dScale = Math.pow(scale[d], nDerivOrder[d]) * (nDerivOrder[d] % 2 == 1 ? -1.0 : 1.0);
			for (int k = 0; k < kernels[d].length; k++)
			{
				kernels[d][k] *= dScale;
			}
		}
		final Convolution conv = SeparableKernelConvolution.convolution( Kernel1D.centralAsymmetric(kernels) );
		if(es != null)
		{
			conv.setExecutor(es);
		}
		return conv;
	}

	/** places the box with its first voxel at min **/
	public void setMin(final long [] min)
	{
		for (int d = 0; d < 3; d++)
		{
			boxMin[d] = min[d];
		}
		resetInterpolators();
	}

	/** the interpolators are made for the box position (the views copy it),
	 * after the box is moved they are made again on demand **/
	void resetInterpolators()
	{
		rraHess.clear();
		rraSal = null;
	}

	/** hessian (and gradient, if enabled) of the box by the convolutions conv (derivativeConvolutions) of input
	 * (already extended beyond its borders), then the line saliency and direction at its voxels (computeSaliency).
	 * es may be null (single thread) **/
	@SuppressWarnings("rawtypes")
	public void compute(final RandomAccessible<?> input, final Convolution [] conv, final int nThreads, final ExecutorService es)
	{
		for (int c = 0; c < 6; c++)
		{
			computeHessianComponent(input, conv[c], c);
		}
		if(gradImg != null)
		{
			final IntervalView<FloatType> grad = Views.translate(gradImg, boxMin[0], boxMin[1], boxMin[2], 0);
			for (int d = 0; d < 3; d++)
			{
				process(conv[6 + d], input, Views.hyperSlice( grad, 3, d ));
			}
		}
		computeSaliency(nThreads, es);
	}

	/** one component (0-5: xx, xy, xz, yy, yz, zz) of the hessian of the box by its convolution conv of input
	 * (see compute; for a progress report between them, then computeSaliency) **/
	@SuppressWarnings("rawtypes")
	public void computeHessianComponent(final RandomAccessible<?> input, final Convolution conv, final int nComponent)
	{
		process(conv, input, Views.hyperSlice( hessView(), 3, nComponent ));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static void process(final Convolution conv, final RandomAccessible<?> input, final IntervalView<FloatType> out)
	{
		conv.process(input, out);
	}

	/** line saliency (salImg) and direction (vecImg, voxel units) at the voxels of the box from its
	 * normalised hessian (hessImg), EigenValVecSymmDecomposition (and the line corners, if enabled).
	 * es may be null (single thread) **/
	public void computeSaliency(final int nThreads, final ExecutorService es)
	{
		final EigenValVecSymmDecomposition<FloatType> mEV = new EigenValVecSymmDecomposition<>(3);
		final boolean bThreads = es != null && nThreads > 1;
		if(cornImg != null)
		{
			if(bThreads)
				mEV.computeVWCRAI(hessImg, vecImg, salImg, cornImg, nThreads, es);
			else
				mEV.computeVWCRAI(hessImg, vecImg, salImg, cornImg);
		}
		else
		{
			if(bThreads)
				mEV.computeVWRAI(hessImg, vecImg, salImg, nThreads, es);
			else
				mEV.computeVWRAI(hessImg, vecImg, salImg);
		}
		//eigenvectors: normalised space -> voxel units
		final float [] vec = vecImg.update( null ).getCurrentStorageArray();
		final double [] t = new double[3];
		for (int i = 0; i < nVol; i++)
		{
			for (int d = 0; d < 3; d++)
			{
				t[d] = vec[i + d * nVol];
			}
			normToGridDir(t, t);
			for (int d = 0; d < 3; d++)
			{
				vec[i + d * nVol] = (float) t[d];
			}
		}
		resetInterpolators();
	}

	/** direction t' of the normalised space -> unit direction in voxel units (scale * t', normalized) to out
	 * (may be the same array) **/
	public void normToGridDir(final double [] tn, final double [] out)
	{
		double dNorm = 0;
		for (int d = 0; d < 3; d++)
		{
			out[d] = tn[d] * scale[d];
			dNorm += out[d] * out[d];
		}
		scaleToUnit(out, dNorm);
	}

	/** direction in voxel units -> unit direction of the normalised space (e / scale, normalized) to out
	 * (may be the same array) **/
	public void gridToNormDir(final double [] e, final double [] out)
	{
		double dNorm = 0;
		for (int d = 0; d < 3; d++)
		{
			out[d] = e[d] / scale[d];
			dNorm += out[d] * out[d];
		}
		scaleToUnit(out, dNorm);
	}

	static void scaleToUnit(final double [] v, final double dNorm2)
	{
		if(dNorm2 > 0)
		{
			final double dInv = 1.0 / Math.sqrt(dNorm2);
			for (int d = 0; d < 3; d++)
			{
				v[d] *= dInv;
			}
		}
	}

	/** normalised hessian in image coordinates (x, y, z, component) **/
	public IntervalView<FloatType> hessView()
	{
		return Views.translate(hessImg, boxMin[0], boxMin[1], boxMin[2], 0);
	}

	/** line saliency in image coordinates **/
	public IntervalView< FloatType > salView()
	{
		return Views.translate(salImg, boxMin[0], boxMin[1], boxMin[2]);
	}

	/** line direction (voxel units, unit length) in image coordinates (x, y, z, component) **/
	public IntervalView<FloatType> vecView()
	{
		return Views.translate(vecImg, boxMin[0], boxMin[1], boxMin[2], 0);
	}

	/** line corners in image coordinates (null if not enabled, enableCorners) **/
	public IntervalView<FloatType> cornView()
	{
		return cornImg == null ? null : Views.translate(cornImg, boxMin[0], boxMin[1], boxMin[2]);
	}

	/** the voxel (x, y, z) is inside of the box **/
	public boolean contains(final long x, final long y, final long z)
	{
		return x >= boxMin[0] && y >= boxMin[1] && z >= boxMin[2]
				&& x < boxMin[0] + dims[0] && y < boxMin[1] + dims[1] && z < boxMin[2] + dims[2];
	}

	/** the continuous position x can be interpolated from the voxels of the box
	 * (it is between the first and the last voxel along each axis) **/
	public boolean containsReal(final double [] x)
	{
		for (int d = 0; d < 3; d++)
		{
			if(!(x[d] >= boxMin[d] && x[d] <= boxMin[d] + dims[d] - 1))
				return false;
		}
		return true;
	}

	/** index of the voxel v in the box arrays (first component), -1 outside of the box **/
	int voxelIndex(final long [] v)
	{
		if(!contains(v[0], v[1], v[2]))
			return -1;
		return (int) (((v[2] - boxMin[2]) * dims[1] + (v[1] - boxMin[1])) * dims[0] + (v[0] - boxMin[0]));
	}

	/** line saliency at the continuous position x, trilinearly interpolated from the voxels of the box;
	 * NaN outside of the box **/
	public double saliencyAt(final double [] x)
	{
		if(!containsReal(x))
			return Double.NaN;
		if(rraSal == null)
		{
			final InterpolatorFactory<FloatType, RandomAccessible< FloatType >> factory = new NLinearInterpolatorFactory<>();
			//(the border extension only fills the weight 0 corners on the last voxel plane, see containsReal)
			rraSal = Views.interpolate (Views.extendBorder(salView()), factory).realRandomAccess();
		}
		rraSal.setPosition( x );
		return rraSal.get().get();
	}

	/** line saliency at the voxel v, NaN outside of the box **/
	public double saliencyAtVoxel(final long [] v)
	{
		return saliencyAtVoxel(v[0], v[1], v[2], null);
	}

	/** line saliency at the voxel v, its direction (voxel units, unit length) to vecOut; NaN outside of the box **/
	public double saliencyAndVectorAtVoxel(final long [] v, final double [] vecOut)
	{
		return saliencyAtVoxel(v[0], v[1], v[2], vecOut);
	}

	/** line saliency at the voxel (x, y, z), its direction (voxel units, unit length) to vecOut (may be null);
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

	/** normalised hessian (6 components: xx, xy, xz, yy, yz, zz) at the continuous position x,
	 * trilinearly interpolated from the voxels of the box, to h; false outside of the box **/
	public boolean hessianAt(final double [] x, final double [] h)
	{
		if(!containsReal(x))
			return false;
		if(rraHess.size() < 1)
		{
			final InterpolatorFactory<FloatType, RandomAccessible< FloatType >> factory = new NLinearInterpolatorFactory<>();
			for(int c = 0; c < 6; c++)
			{
				rraHess.add( Views.interpolate( Views.extendBorder(
						Views.translate(Views.hyperSlice( hessImg, 3, c ), boxMin)), factory).realRandomAccess() );
			}
		}
		for (int c = 0; c < 6; c++)
		{
			h[c] = rraHess.get( c ).setPositionAndGet( x ).get();
		}
		return true;
	}

	/** line saliency and direction (voxel units, unit length) to vecOut at the continuous position x
	 * from the trilinearly interpolated hessian (its eigen decomposition, SteerableLineFilter.saliency:
	 * the eigenvectors themselves can not be interpolated, their sign is arbitrary at each voxel);
	 * NaN (vecOut not changed) outside of the box **/
	public double saliencyAndVectorAt(final double [] x, final double [] vecOut)
	{
		final double [] h = new double[6];
		if(!hessianAt(x, h))
			return Double.NaN;
		final double [] t = new double[3];
		final double dSal = SteerableLineFilter.saliency(h, t);
		normToGridDir(t, vecOut);
		return dSal;
	}

	/** normalised hessian (6 components: xx, xy, xz, yy, yz, zz) at the voxel v to h, false outside of the box **/
	public boolean hessianAtVoxel(final long [] v, final double [] h)
	{
		final int i = voxelIndex(v);
		if(i < 0)
			return false;
		final float [] hess = hessImg.update( null ).getCurrentStorageArray();
		for (int c = 0; c < 6; c++)
		{
			h[c] = hess[i + c * nVol];
		}
		return true;
	}

	/** normalised gradient at the voxel v to g, false outside of the box or if the gradient is not enabled **/
	public boolean gradientAtVoxel(final long [] v, final double [] g)
	{
		final int i = voxelIndex(v);
		if(i < 0 || gradImg == null)
			return false;
		final float [] grad = gradImg.update( null ).getCurrentStorageArray();
		for (int d = 0; d < 3; d++)
		{
			g[d] = grad[i + d * nVol];
		}
		return true;
	}
}
