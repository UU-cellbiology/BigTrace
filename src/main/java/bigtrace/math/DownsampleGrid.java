package bigtrace.math;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

/** Helpers for tracing math on a block-averaged ("downsampled") grid.
 * Grid voxel g along axis d covers full resolution voxels [g*ds[d], g*ds[d]+ds[d]-1],
 * its center is at full resolution coordinate g*ds[d] + 0.5*(ds[d]-1).
 * Same convention as in OneClickTrace. **/
public class DownsampleGrid
{
	/** downsampling factors ds and sigma of the kernels on the grid (grid voxels).
	 * ds[d] = max(1, round(sigma[d]/dTargetSigma)) if bEnabled, otherwise 1.
	 * Block averaging by ds already blurs with variance (ds^2-1)/12 (full res px^2),
	 * it is subtracted from the kernel. Returns true if any ds > 1. **/
	public static boolean computeFactors(final double [] sigma, final boolean bEnabled, final double dTargetSigma,
			final long [] ds, final double [] sigmaGrid)
	{
		boolean bDownsample = false;
		for (int d = 0; d < 3; d++)
		{
			final double s = sigma[d];
			ds[d] = 1;
			sigmaGrid[d] = s;
			if(bEnabled && dTargetSigma > 0)
			{
				ds[d] = Math.max(1, Math.round(s / dTargetSigma));
				if(ds[d] > 1)
				{
					bDownsample = true;
					sigmaGrid[d] = Math.sqrt(Math.max(0.25, s * s - (ds[d] * ds[d] - 1) / 12.0)) / ds[d];
				}
			}
		}
		return bDownsample;
	}

	/** full resolution voxel -> grid voxel **/
	public static long toGrid(final long x, final long ds)
	{
		return Math.floorDiv(x, ds);
	}

	/** grid voxel -> full resolution coordinate of its center **/
	public static double toFull(final long g, final long ds)
	{
		return g * ds + 0.5 * (ds - 1);
	}

	/** fills target (3D, placed at gridMin in grid coordinates) with block averages of in
	 * (in should be extended, blocks at the border can be partially outside) **/
	public static < T extends RealType< T > > void blockAverage(final RandomAccessible< T > in,
			final ArrayImg<FloatType, FloatArray> target, final long [] gridMin, final long [] ds)
	{
		final RandomAccess< T > raIn = in.randomAccess();
		final float [] out = target.update( null ).getCurrentStorageArray();
		final long [] dims = Intervals.dimensionsAsLongArray( target );
		final long [] posIn = new long[3];
		final double norm = 1.0 / (ds[0] * ds[1] * ds[2]);
		int nIndex = 0;
		for(long z = 0; z < dims[2]; z++)
			for(long y = 0; y < dims[1]; y++)
				for(long x = 0; x < dims[0]; x++)
				{
					double sum = 0;
					for(long k = 0; k < ds[2]; k++)
						for(long j = 0; j < ds[1]; j++)
							for(long i = 0; i < ds[0]; i++)
							{
								posIn[0] = (gridMin[0] + x) * ds[0] + i;
								posIn[1] = (gridMin[1] + y) * ds[1] + j;
								posIn[2] = (gridMin[2] + z) * ds[2] + k;
								sum += raIn.setPositionAndGet(posIn).getRealDouble();
							}
					out[nIndex++] = (float)(sum * norm);
				}
	}

	/** linear interpolation table along one axis: for each full resolution voxel
	 * fullMin..fullMin+nFull-1, lower grid index (relative to gridMin), upper one and weight of the upper **/
	static void axisTable(final long fullMin, final int nFull, final long gridMin, final int nGrid, final long ds,
			final int [] i0, final int [] i1, final float [] f)
	{
		for(int n = 0; n < nFull; n++)
		{
			double g = ((fullMin + n) - 0.5 * (ds - 1)) / ds - gridMin;
			g = Math.max(0, Math.min(nGrid - 1, g));
			final int lo = (int) Math.floor(g);
			i0[n] = lo;
			i1[n] = Math.min(lo + 1, nGrid - 1);
			f[n] = (float) (g - lo);
		}
	}

	/** Trilinear upsampling of a grid scalar map (placed at gridMin)
	 * to the full resolution box (placed at fullMin).
	 * nComp = 1: scalar image (3D);
	 * nComp = 3: unit (undirected) vectors (4D, 3 components):
	 * corner vectors are flipped to agree with the nearest one before averaging,
	 * result is normalized. **/
	public static void upsample(final ArrayImg<FloatType, FloatArray> grid, final long [] gridMin,
			final ArrayImg<FloatType, FloatArray> full, final long [] fullMin, final long [] ds, final int nComp)
	{
		final float [] gArr = grid.update( null ).getCurrentStorageArray();
		final float [] fArr = full.update( null ).getCurrentStorageArray();
		final int [] gd = new int[3];
		final int [] fd = new int[3];
		for(int d = 0; d < 3; d++)
		{
			gd[d] = (int) grid.dimension(d);
			fd[d] = (int) full.dimension(d);
		}
		final int gVol = gd[0] * gd[1] * gd[2];
		final int fVol = fd[0] * fd[1] * fd[2];
		final int [][] i0 = new int[3][];
		final int [][] i1 = new int[3][];
		final float [][] fw = new float[3][];
		for(int d = 0; d < 3; d++)
		{
			i0[d] = new int[fd[d]];
			i1[d] = new int[fd[d]];
			fw[d] = new float[fd[d]];
			axisTable(fullMin[d], fd[d], gridMin[d], gd[d], ds[d], i0[d], i1[d], fw[d]);
		}
		final int [] cIdx = new int[8];
		final float [] cW = new float[8];
		final double [] v = new double[3];
		int nOut = 0;
		for(int z = 0; z < fd[2]; z++)
		{
			for(int y = 0; y < fd[1]; y++)
			{
				for(int x = 0; x < fd[0]; x++)
				{
					//8 corners and their weights
					int c = 0;
					int nMax = 0;
					for(int kz = 0; kz < 2; kz++)
					{
						final int iz = (kz == 0) ? i0[2][z] : i1[2][z];
						final float wz = (kz == 0) ? 1.0f - fw[2][z] : fw[2][z];
						for(int ky = 0; ky < 2; ky++)
						{
							final int iy = (ky == 0) ? i0[1][y] : i1[1][y];
							final float wy = (ky == 0) ? 1.0f - fw[1][y] : fw[1][y];
							for(int kx = 0; kx < 2; kx++)
							{
								final int ix = (kx == 0) ? i0[0][x] : i1[0][x];
								final float wx = (kx == 0) ? 1.0f - fw[0][x] : fw[0][x];
								cIdx[c] = (iz * gd[1] + iy) * gd[0] + ix;
								cW[c] = wx * wy * wz;
								if(cW[c] > cW[nMax])
									nMax = c;
								c++;
							}
						}
					}
					if(nComp == 1)
					{
						float sum = 0;
						for(c = 0; c < 8; c++)
						{
							sum += cW[c] * gArr[cIdx[c]];
						}
						fArr[nOut] = sum;
					}
					else
					{
						//reference: nearest corner
						final int nRef = cIdx[nMax];
						v[0] = 0; v[1] = 0; v[2] = 0;
						for(c = 0; c < 8; c++)
						{
							final int n = cIdx[c];
							double dot = 0;
							for(int d = 0; d < 3; d++)
							{
								dot += gArr[n + d * gVol] * gArr[nRef + d * gVol];
							}
							final double w = (dot < 0) ? -cW[c] : cW[c];
							for(int d = 0; d < 3; d++)
							{
								v[d] += w * gArr[n + d * gVol];
							}
						}
						double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
						if(len < 1e-12)
						{
							//degenerate, take the nearest corner
							for(int d = 0; d < 3; d++)
							{
								v[d] = gArr[nRef + d * gVol];
							}
							len = 1.0;
						}
						for(int d = 0; d < 3; d++)
						{
							fArr[nOut + d * fVol] = (float) (v[d] / len);
						}
					}
					nOut++;
				}
			}
		}
	}
}
