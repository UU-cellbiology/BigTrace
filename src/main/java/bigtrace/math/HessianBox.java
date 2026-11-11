package bigtrace.math;

import java.util.ArrayList;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;

/** Hessian (6 components) of a box of the tracing grid, stored in a 4D float ArrayImg
 * (x, y, z, component), with point-wise (trilinear) evaluation of the steerable line filter,
 * of the elongated (ray) line filter and counting of the line "arms" around a point.
 * All positions are continuous grid coordinates. **/
public class HessianBox
{
	final float [] hess;

	final int [] dims = new int[3];

	final int nVol;

	/** grid position of the first voxel of the box **/
	final long [] boxMin = new long[3];

	/** wraps a hessian image (dims x 6); boxMin_ is the grid position of its first voxel
	 * (only the first 3 values are used) **/
	public HessianBox(final ArrayImg<FloatType, FloatArray> hessImg, final long [] boxMin_)
	{
		hess = hessImg.update( null ).getCurrentStorageArray();
		for (int d = 0; d < 3; d++)
		{
			dims[d] = (int) hessImg.dimension(d);
			boxMin[d] = boxMin_[d];
		}
		nVol = dims[0] * dims[1] * dims[2];
	}

	/** steered line filter (SteerableLineFilter.lineResponse) at the grid position x
	 * from the trilinearly interpolated hessian. Returns NaN outside of the box **/
	public double lineResponse(final double [] x, final double [] e)
	{
		final double [] xl = new double[3];
		for (int d = 0; d < 3; d++)
		{
			xl[d] = x[d] - boxMin[d];
		}
		final double [] h = new double[6];
		for (int c = 0; c < 6; c++)
		{
			h[c] = SteerableLineFilter.interpolate(hess, c * nVol, dims, xl);
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
				if(SteerableLineFilter.dot(dirs[i], dirs[j]) >= dSepCos)
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
				if(SteerableLineFilter.dot(dirs[i], dirs[k]) > dNbCos && resp[k] > resp[i])
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
