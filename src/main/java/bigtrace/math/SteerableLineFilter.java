package bigtrace.math;

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
		cross(dir, axis, u);
		normalize(u);
		cross(dir, u, v);
		normalize(v);
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

	public static double dot(final double [] a, final double [] b)
	{
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	public static void cross(final double [] a, final double [] b, final double [] out)
	{
		final double x = a[1] * b[2] - a[2] * b[1];
		final double y = a[2] * b[0] - a[0] * b[2];
		final double z = a[0] * b[1] - a[1] * b[0];
		out[0] = x;
		out[1] = y;
		out[2] = z;
	}

	public static void normalize(final double [] v)
	{
		final double len = Math.sqrt(dot(v, v));
		if(len > 0)
		{
			for (int d = 0; d < 3; d++)
			{
				v[d] /= len;
			}
		}
	}
}
