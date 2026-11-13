package bigtrace.math;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.convolution.Convolution;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/** Junction box: a cubic box of the tracing grid around a junction, larger than the math box,
 * with its own hessian (full convolution) and the elongated steerable line filter map
 * for one direction (the entry direction of a junction passage). **/
public class JunctionBox
{
	/** half size of the box (grid voxels) **/
	final int nHalf;

	/** size of the box (grid voxels) **/
	final int nDim;

	/** grid position of the first voxel of the box **/
	final long [] boxMin = new long[3];

	/** hessian of the box (6 components) **/
	final ArrayImg<FloatType, FloatArray> hessImg;

	/** steered line filter R_e at the voxels of the box **/
	final ArrayImg<FloatType, FloatArray> lineImg;

	/** elongated line filter map: R_e smoothed along e **/
	final ArrayImg<FloatType, FloatArray> mapImg;

	/** block averaged input of the box with the convolution margin (only used with downsampling) **/
	ArrayImg<FloatType, FloatArray> gridImg;
	
	/** margin of gridImg around the box (convolution input) **/
	final long [] gridMargin = new long[3];

	/** point-wise access to the hessian of the box (after computeHessian) **/
	HessianBox hessBox = null;

	public JunctionBox(final int nHalf_, final boolean bDownsample)
	{
		nHalf = nHalf_;
		nDim = 2 * nHalf + 1;
		hessImg = ArrayImgs.floats( nDim, nDim, nDim, 6 );
		lineImg = ArrayImgs.floats( nDim, nDim, nDim );
		mapImg = ArrayImgs.floats( nDim, nDim, nDim );
		gridImg = bDownsample ? ArrayImgs.floats( nDim, nDim, nDim ) : null;
	}

	/** sets the margin of the block averaged input around the box (needed by the convolutions) **/
	public void setGridMargin(final long [] margin)
	{
		for (int d = 0; d < 3; d++)
		{
			gridMargin[d] = margin[d];
		}
		gridImg = ArrayImgs.floats( nDim + 2 * gridMargin[0], nDim + 2 * gridMargin[1], nDim + 2 * gridMargin[2] );
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
	
	/** places the box centered at the grid position center **/
	public void setCenter(final long [] center)
	{
		for (int d = 0; d < 3; d++)
		{
			boxMin[d] = center[d] - nHalf;
		}
	}

	/** hessian of the box by full (separable Gaussian derivative) convolution of input
	 * (border extended), conv are the 6 second derivative convolutions (xx,xy,xz,yy,yz,zz) **/
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public void computeHessian(final RandomAccessibleInterval<?> input, final Convolution [] conv)
	{
		final IntervalView<FloatType> jHess = Views.translate(hessImg, boxMin[0], boxMin[1], boxMin[2], 0);
		for (int count = 0; count < 6; count++)
		{
			conv[count].process(Views.extendBorder(input), Views.hyperSlice( jHess, 3, count ));
		}
		hessBox = new HessianBox(hessImg, boxMin);
	}

	/** elongated line filter map for the unit direction e:
	 * steered line filter R_e at each voxel (from the hessian of the box),
	 * smoothed along e with a Gaussian of SD alongSigma (grid voxels).
	 * The result is a line filter long along e and narrow across it: it integrates along
	 * the filament and averages out the crossing one (and the bright junction blob). **/
	public void computeMap(final double [] e, final double alongSigma)
	{
		final float [] hess = hessImg.update( null ).getCurrentStorageArray();
		final float [] line = lineImg.update( null ).getCurrentStorageArray();
		final float [] map = mapImg.update( null ).getCurrentStorageArray();
		final int nVol = nDim * nDim * nDim;
		final double [] h = new double[6];
		for (int i = 0; i < nVol; i++)
		{
			for (int c = 0; c < 6; c++)
			{
				h[c] = hess[i + c * nVol];
			}
			line[i] = (float) SteerableLineFilter.lineResponse(h, e);
		}

		final int [] dims = new int [] {nDim, nDim, nDim};
		final int nK = Math.max(1, (int) Math.ceil(2.0 * alongSigma));
		final double [] gauss = new double[2 * nK + 1];
		for (int k = -nK; k <= nK; k++)
		{
			gauss[k + nK] = Math.exp(-0.5 * k * k / (alongSigma * alongSigma));
		}
		final double [] xs = new double[3];
		int nIndex = 0;
		for (int z = 0; z < nDim; z++)
		{
			for (int y = 0; y < nDim; y++)
			{
				for (int x = 0; x < nDim; x++)
				{
					double sum = 0;
					double wsum = 0;
					for (int k = -nK; k <= nK; k++)
					{
						xs[0] = x + k * e[0];
						xs[1] = y + k * e[1];
						xs[2] = z + k * e[2];
						final double val = SteerableLineFilter.interpolate(line, 0, dims, xs);
						if(!Double.isNaN(val))
						{
							sum += gauss[k + nK] * val;
							wsum += gauss[k + nK];
						}
					}
					map[nIndex++] = (float) (wsum > 0 ? sum / wsum : 0.0);
				}
			}
		}
	}

	/** value of the map at the continuous grid position x, NaN outside of the box **/
	public double mapValue(final double [] x)
	{
		final int [] dims = new int [] {nDim, nDim, nDim};
		final double [] xl = new double[3];
		for (int d = 0; d < 3; d++)
		{
			xl[d] = x[d] - boxMin[d];
		}
		return SteerableLineFilter.interpolate(mapImg.update( null ).getCurrentStorageArray(), 0, dims, xl);
	}

	/** Copy of the map for display (debug visualization): full resolution voxel coordinates
	 * (upsampled from the grid if ds is more than 1), scaled to 0-255 by its maximum, 
	 * negative responses are 0 **/
	public IntervalView<UnsignedByteType> getMapForDisplay(final long [] ds)
	{
		ArrayImg<FloatType, FloatArray> src = mapImg;
		final long [] min = boxMin.clone();
		if(ds[0] > 1 || ds[1] > 1 || ds[2] > 1)
		{
			final long [] dims = new long[3];
			for (int d = 0; d < 3; d++)
			{
				min[d] = boxMin[d] * ds[d];
				dims[d] = nDim * ds[d];
			}
			src = ArrayImgs.floats( dims );
			DownsampleGrid.upsample(mapImg, boxMin, src, min, ds, 1);
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

	/** shows a copy of the map in ImageJ **/
	public void showMap(final String sTitle)
	{
		ImageJFunctions.show( mapImg.copy(), sTitle );
	}
}
