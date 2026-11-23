package bigtrace.math;

import net.imglib2.util.LinAlgHelpers;

/** Steerable line filter computed from the 6 hessian components and small geometry helpers,
 * used for junction (crossing) handling in one-click tracing.
 * Hessian component order everywhere: xx, xy, xz, yy, yz, zz. **/
public class SteerableLineFilter
{
	/** line filter steered to the unit direction e (second derivatives are steerable):
	 * curvature across e minus a penalty for curvature along e,
	 *   R_e = -0.5*(tr(H) - e'He) - |e'He|.
	 * For a bright line along e R_e = lambda, for a line crossing at 45 deg 0.25 lambda,
	 * at 90 deg -0.5 lambda, at the center of an X along e 0.5 lambda. **/
	public static double lineResponse(final double [] h, final double [] e)
	{
		final double eHe = h[0] * e[0] * e[0] + h[3] * e[1] * e[1] + h[5] * e[2] * e[2]
				+ 2.0 * (h[1] * e[0] * e[1] + h[2] * e[0] * e[2] + h[4] * e[1] * e[2]);
		return -0.5 * (h[0] + h[3] + h[5] - eHe) - Math.abs(eHe);
	}

	/** orthonormal basis (u,v) of the plane perpendicular to the unit direction dir **/
	public static void crossSectionBasis(final double [] dir, final double [] u, final double [] v)
	{
		//pick the axis least aligned with dir
		int nMinAxis = 0;
		for (int d = 1; d < 3; d++)
		{
			if(Math.abs(dir[d]) < Math.abs(dir[nMinAxis]))
			{
				nMinAxis = d;
			}
		}
		final double [] axis = new double[3];
		axis[nMinAxis] = 1.0;
		LinAlgHelpers.cross(dir, axis, u);
		LinAlgHelpers.normalize(u);
		LinAlgHelpers.cross(dir, u, v);
		LinAlgHelpers.normalize(v);
	}

	/** trilinear interpolation in a float array holding 3D volumes (x fastest),
	 * of the volume starting at nOffset, at the local continuous position xl.
	 * Returns NaN outside of [0, dims-1] **/
	public static double interpolate(final float [] data, final int nOffset, final int [] dims, final double [] xl)
	{
		final int [] x0 = new int[3];
		final double [] f = new double[3];
		for (int d = 0; d < 3; d++)
		{
			if(xl[d] < 0 || xl[d] > dims[d] - 1)
				return Double.NaN;
			x0[d] = Math.min((int) Math.floor(xl[d]), Math.max(0, dims[d] - 2));
			f[d] = xl[d] - x0[d];
		}
		final int sy = dims[0];
		final int sz = dims[0] * dims[1];
		double val = 0;
		for (int corner = 0; corner < 8; corner++)
		{
			double w = 1.0;
			int nIndex = nOffset;
			for (int d = 0; d < 3; d++)
			{
				final int bit = (corner >> d) & 1;
				w *= (bit == 1) ? f[d] : (1.0 - f[d]);
				final int c = Math.min(dims[d] - 1, x0[d] + bit);
				nIndex += c * (d == 0 ? 1 : (d == 1 ? sy : sz));
			}
			if(w != 0)
			{
				val += w * data[nIndex];
			}
		}
		return val;
	}

	/** n unit directions spread evenly over the whole sphere (Fibonacci lattice) **/
	public static double [][] sphereDirections(final int n)
	{
		final double [][] out = new double[n][3];
		final double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		for (int i = 0; i < n; i++)
		{
			final double z = 1.0 - 2.0 * (i + 0.5) / n;
			final double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
			out[i][0] = r * Math.cos(golden * i);
			out[i][1] = r * Math.sin(golden * i);
			out[i][2] = z;
		}
		return out;
	}


	/** eigen decomposition of the symmetric 3x3 matrix h (xx,xy,xz,yy,yz,zz) by Jacobi rotations:
	 * eigenvalues in w, eigenvectors in the columns of V (V[component][index]) **/
	public static void eigenSym3(final double [] h, final double [] w, final double [][] V)
	{
		final double [][] a = new double [][] {{h[0], h[1], h[2]}, {h[1], h[3], h[4]}, {h[2], h[4], h[5]}};
		for (int i = 0; i < 3; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				V[i][j] = (i == j) ? 1.0 : 0.0;
			}
		}
		final double dScale = Math.abs(h[0]) + Math.abs(h[3]) + Math.abs(h[5]) + Math.abs(h[1]) + Math.abs(h[2]) + Math.abs(h[4]);
		final int [][] pairs = new int [][] {{0, 1}, {0, 2}, {1, 2}};
		for (int nSweep = 0; nSweep < 50; nSweep++)
		{
			final double off = a[0][1] * a[0][1] + a[0][2] * a[0][2] + a[1][2] * a[1][2];
			if(off <= 1e-24 * dScale * dScale || dScale == 0)
				break;
			for(final int [] pq : pairs)
			{
				final int p = pq[0];
				final int q = pq[1];
				if(a[p][q] == 0)
					continue;
				final double theta = (a[q][q] - a[p][p]) / (2.0 * a[p][q]);
				final double t = Math.signum(theta == 0 ? 1.0 : theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
				final double c = 1.0 / Math.sqrt(t * t + 1.0);
				final double s = t * c;
				for (int k = 0; k < 3; k++)
				{
					final double akp = a[k][p];
					final double akq = a[k][q];
					a[k][p] = c * akp - s * akq;
					a[k][q] = s * akp + c * akq;
				}
				for (int k = 0; k < 3; k++)
				{
					final double apk = a[p][k];
					final double aqk = a[q][k];
					a[p][k] = c * apk - s * aqk;
					a[q][k] = s * apk + c * aqk;
				}
				for (int k = 0; k < 3; k++)
				{
					final double vkp = V[k][p];
					final double vkq = V[k][q];
					V[k][p] = c * vkp - s * vkq;
					V[k][q] = s * vkp + c * vkq;
				}
			}
		}
		for (int i = 0; i < 3; i++)
		{
			w[i] = a[i][i];
		}
	}

	/** line saliency and direction from the hessian h (xx,xy,xz,yy,yz,zz),
	 * the same as EigenValVecSymmDecomposition.computeVectorWeight:
	 * direction = eigenvector of the smallest |eigenvalue| (written to vecOut, unit, may be null),
	 * saliency = minus the sum of the two other eigenvalues if both are not positive, otherwise 0 **/
	public static double saliency(final double [] h, final double [] vecOut)
	{
		final double [] w = new double[3];
		final double [][] V = new double[3][3];
		eigenSym3(h, w, V);
		int index = 0;
		for (int i = 1; i < 3; i++)
		{
			if(Math.abs(w[i]) < Math.abs(w[index]))
				index = i;
		}
		if(vecOut != null)
		{
			for (int d = 0; d < 3; d++)
			{
				vecOut[d] = V[d][index];
			}
			LinAlgHelpers.normalize(vecOut);
		}
		double dWeight = 0.0;
		for (int i = 0; i < 3; i++)
		{
			if(i == index)
				continue;
			if(w[i] > 0)
				return 0.0;
			dWeight -= w[i];
		}
		return dWeight;
	}
}
