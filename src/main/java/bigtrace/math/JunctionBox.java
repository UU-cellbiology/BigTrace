package bigtrace.math;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;

/** Junction box: a cubic MathBox of the tracing grid around a junction (or a gap), larger than the math box,
 * with point-wise (interpolated) hessian access and the elongated steerable line filter map
 * for one direction (the entry direction of a junction passage). **/
public class JunctionBox extends MathBox
{
	/** half size of the box (grid voxels) **/
	final int nHalf;

	/** size of the box (grid voxels) **/
	final int nDim;

	/** steered line filter R_e at the voxels of the box **/
	final ArrayImg<FloatType, FloatArray> lineImg;

	/** elongated line filter map: R_e smoothed along e **/
	final ArrayImg<FloatType, FloatArray> mapImg;

	public JunctionBox(final int nHalf_, final boolean bDownsample)
	{
		super(new long [] {2 * nHalf_ + 1, 2 * nHalf_ + 1, 2 * nHalf_ + 1}, true);
		nHalf = nHalf_;
		nDim = 2 * nHalf + 1;
		lineImg = ArrayImgs.floats( nDim, nDim, nDim );
		mapImg = ArrayImgs.floats( nDim, nDim, nDim );
		if(bDownsample)
		{
			setGridMargin(new long[3]);
		}
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
		return toDisplay(mapImg, ds);
	}
	
	/** shows a copy of the map in ImageJ **/
	public void showMap(final String sTitle)
	{
		ImageJFunctions.show( mapImg.copy(), sTitle );
	}
}
