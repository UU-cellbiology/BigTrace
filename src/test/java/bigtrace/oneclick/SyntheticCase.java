package bigtrace.oneclick;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;

/** A test case for one-click tracing: filaments (curves in physical units) rendered with an anisotropic Gaussian PSF
 * into a volume with a given voxel size, optionally with background and Gaussian noise; the target filament
 * (one or more alternative targets, e.g. both arms of a Y), the start point and direction.
 * Rendering: a thin filament of uniform brightness convolved with the PSF, scaled so that a filament along x
 * peaks at PEAK above the background. **/
public class SyntheticCase
{
	public static final double PEAK = 100.0;
	public static final double BACKGROUND = 20.0;

	/** dense polyline (physical units, step ~0.1) with its tangents **/
	public static class Curve
	{
		public final ArrayList<double[]> p = new ArrayList<>();
		public final ArrayList<double[]> t = new ArrayList<>();
		/** arc length at each point **/
		public final ArrayList<Double> s = new ArrayList<>();

		public double length()
		{
			return s.isEmpty() ? 0 : s.get(s.size() - 1);
		}

		/** Catmull-Rom spline through the control points, resampled by arc length with step ds **/
		public static Curve spline(final double [][] cp, final double ds)
		{
			final ArrayList<double[]> dense = new ArrayList<>();
			final int n = cp.length;
			for (int i = 0; i < n - 1; i++)
			{
				final double [] p0 = cp[Math.max(0, i - 1)];
				final double [] p1 = cp[i];
				final double [] p2 = cp[i + 1];
				final double [] p3 = cp[Math.min(n - 1, i + 2)];
				for (int k = 0; k < 400; k++)
				{
					final double u = k / 400.0;
					final double [] q = new double[3];
					for (int d = 0; d < 3; d++)
					{
						q[d] = 0.5 * ((2 * p1[d]) + (-p0[d] + p2[d]) * u + (2 * p0[d] - 5 * p1[d] + 4 * p2[d] - p3[d]) * u * u
								+ (-p0[d] + 3 * p1[d] - 3 * p2[d] + p3[d]) * u * u * u);
					}
					dense.add(q);
				}
			}
			dense.add(cp[n - 1].clone());
			return resample(dense, ds);
		}

		/** straight segment from a to b **/
		public static Curve line(final double [] a, final double [] b, final double ds)
		{
			final ArrayList<double[]> dense = new ArrayList<>();
			dense.add(a.clone());
			dense.add(b.clone());
			return resample(dense, ds);
		}

		/** polyline through the given points, resampled **/
		public static Curve resample(final List<double[]> dense, final double ds)
		{
			final Curve c = new Curve();
			double sAcc = 0;
			double sNext = 0;
			for (int i = 0; i < dense.size() - 1; i++)
			{
				final double [] a = dense.get(i);
				final double [] b = dense.get(i + 1);
				final double len = dist(a, b);
				if(len <= 0)
					continue;
				final double [] dir = new double[3];
				for (int d = 0; d < 3; d++)
				{
					dir[d] = (b[d] - a[d]) / len;
				}
				while(sNext <= sAcc + len)
				{
					final double f = (sNext - sAcc) / len;
					final double [] q = new double[3];
					for (int d = 0; d < 3; d++)
					{
						q[d] = a[d] + f * (b[d] - a[d]);
					}
					c.p.add(q);
					c.t.add(dir.clone());
					c.s.add(sNext);
					sNext += ds;
				}
				sAcc += len;
			}
			return c;
		}

		/** concatenation of curves (the next one starts where the previous ends) **/
		public static Curve join(final Curve a, final Curve b)
		{
			final Curve c = new Curve();
			c.p.addAll(a.p);
			c.t.addAll(a.t);
			c.s.addAll(a.s);
			final double s0 = a.length();
			for (int i = 1; i < b.p.size(); i++)
			{
				c.p.add(b.p.get(i));
				c.t.add(b.t.get(i));
				c.s.add(s0 + b.s.get(i));
			}
			return c;
		}

		/** part of the curve between arc lengths s0 and s1 **/
		public Curve sub(final double s0, final double s1)
		{
			final Curve c = new Curve();
			for (int i = 0; i < p.size(); i++)
			{
				if(s.get(i) >= s0 && s.get(i) <= s1)
				{
					c.p.add(p.get(i));
					c.t.add(t.get(i));
					c.s.add(s.get(i) - s0);
				}
			}
			return c;
		}

		/** index of the point at arc length s **/
		public int indexAt(final double sv)
		{
			for (int i = 0; i < s.size(); i++)
			{
				if(s.get(i) >= sv)
					return i;
			}
			return s.size() - 1;
		}
	}

	public final String name;
	/** extent of the volume (physical) **/
	public final double [] extent;
	/** voxel size (physical) **/
	public final double [] voxel;
	/** PSF SD (physical) **/
	public final double [] psfPhys;
	/** all filaments in the image **/
	public final ArrayList<Curve> filaments = new ArrayList<>();
	/** filament pieces in the image that are dimmer (gap): rendered with this factor **/
	public final ArrayList<double[]> gaps = new ArrayList<>();
	/** alternative targets: the trace should follow one of them (starting at the start point) **/
	public final ArrayList<Curve> targets = new ArrayList<>();
	/** start (physical) and direction **/
	public double [] start;
	public double [] startDir;
	/** what is expected from plain stepping (for the report) **/
	public String expected = "";

	public SyntheticCase(final String name_, final double [] extent_, final double [] voxel_, final double [] psfPhys_)
	{
		name = name_;
		extent = extent_.clone();
		voxel = voxel_.clone();
		psfPhys = psfPhys_.clone();
	}

	/** volume size in voxels **/
	public long [] dims()
	{
		final long [] out = new long[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = (long) Math.floor(extent[d] / voxel[d]);
		}
		return out;
	}

	/** PSF SD in voxels **/
	public double [] psfVoxels()
	{
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = psfPhys[d] / voxel[d];
		}
		return out;
	}

	/** start on target 0 at arc length s0, along it **/
	public void startOnTarget(final double s0)
	{
		final Curve c = targets.get(0);
		final int i = c.indexAt(s0);
		start = c.p.get(i).clone();
		startDir = c.t.get(i).clone();
		//targets start at the start point
		for (int k = 0; k < targets.size(); k++)
		{
			final Curve tc = targets.get(k);
			targets.set(k, tc.sub(tc.s.get(tc.indexAt(s0)), tc.length()));
		}
	}

	/** noise free image (background included) **/
	public ArrayImg<FloatType, FloatArray> render()
	{
		final long [] n = dims();
		final ArrayImg<FloatType, FloatArray> img = ArrayImgs.floats(n);
		final float [] a = img.update(null).getCurrentStorageArray();
		final double dStep = 0.2;
		//brightness per unit length: a filament along x peaks at PEAK
		final double dLin = PEAK / (psfPhys[0] * Math.sqrt(2 * Math.PI));
		final int [] rad = new int[3];
		for (int d = 0; d < 3; d++)
		{
			rad[d] = (int) Math.ceil(4 * psfPhys[d] / voxel[d]);
		}
		for (final Curve c : filaments)
		{
			for (int i = 0; i < c.p.size(); i += Math.max(1, (int) Math.round(dStep / 0.1)))
			{
				final double [] q = c.p.get(i);
				double w = dLin * dStep;
				for (final double [] g : gaps)
				{
					if(q[0] >= g[0] && q[0] <= g[1])
						w *= g[2];
				}
				if(w <= 0)
					continue;
				final long [] lo = new long[3];
				final long [] hi = new long[3];
				for (int d = 0; d < 3; d++)
				{
					final long gc = Math.round(q[d] / voxel[d]);
					lo[d] = Math.max(0, gc - rad[d]);
					hi[d] = Math.min(n[d] - 1, gc + rad[d]);
				}
				for (long z = lo[2]; z <= hi[2]; z++)
				{
					final double rz = (z * voxel[2] - q[2]) / psfPhys[2];
					final double gz = Math.exp(-0.5 * rz * rz);
					for (long y = lo[1]; y <= hi[1]; y++)
					{
						final double ry = (y * voxel[1] - q[1]) / psfPhys[1];
						final double gyz = gz * Math.exp(-0.5 * ry * ry);
						for (long x = lo[0]; x <= hi[0]; x++)
						{
							final double rx = (x * voxel[0] - q[0]) / psfPhys[0];
							a[(int) ((z * n[1] + y) * n[0] + x)] += (float) (w * gyz * Math.exp(-0.5 * rx * rx));
						}
					}
				}
			}
		}
		for (int i = 0; i < a.length; i++)
		{
			a[i] += (float) BACKGROUND;
		}
		return img;
	}

	/** copy of img with Gaussian noise of SD PEAK / snr (seeded); snr <= 0: a copy without noise **/
	public static ArrayImg<FloatType, FloatArray> withNoise(final ArrayImg<FloatType, FloatArray> img, final double snr, final long seed)
	{
		final float [] a = img.update(null).getCurrentStorageArray().clone();
		final ArrayImg<FloatType, FloatArray> out = ArrayImgs.floats(a, img.dimensionsAsLongArray());
		if(snr <= 0)
			return out;
		final Random rnd = new Random(seed);
		final double sd = PEAK / snr;
		for (int i = 0; i < a.length; i++)
		{
			a[i] += (float) (sd * rnd.nextGaussian());
		}
		return out;
	}

	static double dist(final double [] a, final double [] b)
	{
		double dd = 0;
		for (int d = 0; d < 3; d++)
		{
			dd += (a[d] - b[d]) * (a[d] - b[d]);
		}
		return Math.sqrt(dd);
	}

	//------------------ the cases

	/** voxel size variants: isotropic voxels, z voxel 3x **/
	public static final double [][] VOXELS = new double [][] {{1, 1, 1}, {1, 1, 3}};
	public static final String [] VOXEL_NAMES = new String [] {"iso", "z3"};
	/** the PSF of all cases (physical): elongated 3x in z **/
	public static final double [] PSF = new double [] {2, 2, 6};

	/** all cases for the voxel size variant v **/
	public static ArrayList<SyntheticCase> all(final int v)
	{
		final ArrayList<SyntheticCase> out = new ArrayList<>();
		final double [] vox = VOXELS[v];
		final String sv = "_" + VOXEL_NAMES[v];
		final double ds = 0.1;
		//S-curve: S in xz, then S in xy
		{
			final SyntheticCase c = new SyntheticCase("scurve" + sv, new double [] {136, 128, 96}, vox, PSF);
			final Curve f = Curve.spline(new double [][] {{10, 64, 48}, {22, 64, 64}, {36, 64, 56}, {48, 64, 36}, {62, 64, 48},
				{74, 76, 48}, {86, 84, 48}, {98, 52, 48}, {110, 44, 48}, {122, 54, 48}}, ds);
			c.filaments.add(f);
			c.targets.add(f);
			c.startOnTarget(8);
			c.expected = "traced to the end";
			out.add(c);
		}
		//crossings in xy at 90, 60, 30 degrees, and one tilted in z
		for (final int nAngle : new int [] {90, 60, 30})
		{
			final SyntheticCase c = new SyntheticCase("cross" + nAngle + sv, new double [] {120, 120, 60}, vox, PSF);
			final Curve a = Curve.line(new double [] {10, 60, 30}, new double [] {110, 60, 30}, ds);
			final double th = Math.toRadians(nAngle);
			final Curve b = Curve.line(new double [] {60 - 45 * Math.cos(th), 60 - 45 * Math.sin(th), 30},
					new double [] {60 + 45 * Math.cos(th), 60 + 45 * Math.sin(th), 30}, ds);
			c.filaments.add(a);
			c.filaments.add(b);
			c.targets.add(a);
			c.startOnTarget(10);
			c.expected = nAngle >= 60 ? "straight through" : "may switch filament";
			out.add(c);
		}
		{
			final SyntheticCase c = new SyntheticCase("crossz60" + sv, new double [] {120, 120, 72}, vox, PSF);
			final Curve a = Curve.line(new double [] {10, 60, 36}, new double [] {110, 60, 36}, ds);
			final double th = Math.toRadians(60);
			final double h = 30 / Math.sin(th);
			final Curve b = Curve.line(new double [] {60 - h * Math.cos(th), 60, 36 - h * Math.sin(th)},
					new double [] {60 + h * Math.cos(th), 60, 36 + h * Math.sin(th)}, ds);
			c.filaments.add(a);
			c.filaments.add(b);
			c.targets.add(a);
			c.startOnTarget(10);
			c.expected = "straight through";
			out.add(c);
		}
		//Y branch: stem, then two arms at +-30 degrees
		{
			final SyntheticCase c = new SyntheticCase("ybranch" + sv, new double [] {120, 120, 60}, vox, PSF);
			final Curve stem = Curve.line(new double [] {10, 60, 30}, new double [] {60, 60, 30}, ds);
			final double th = Math.toRadians(30);
			final Curve arm1 = Curve.line(new double [] {60, 60, 30}, new double [] {60 + 50 * Math.cos(th), 60 + 50 * Math.sin(th), 30}, ds);
			final Curve arm2 = Curve.line(new double [] {60, 60, 30}, new double [] {60 + 50 * Math.cos(th), 60 - 50 * Math.sin(th), 30}, ds);
			c.filaments.add(stem);
			c.filaments.add(arm1);
			c.filaments.add(arm2);
			c.targets.add(Curve.join(stem, arm1));
			c.targets.add(Curve.join(stem, arm2));
			c.startOnTarget(10);
			c.expected = "stops at the branch or takes one arm";
			out.add(c);
		}
		//gap of 3 tracing sigmas (6 xy px) in a straight filament
		{
			final SyntheticCase c = new SyntheticCase("gap" + sv, new double [] {120, 120, 60}, vox, PSF);
			final Curve a = Curve.line(new double [] {10, 60, 30}, new double [] {110, 60, 30}, ds);
			c.filaments.add(a);
			c.gaps.add(new double [] {57, 63, 0.0});
			c.targets.add(a);
			c.startOnTarget(10);
			c.expected = "stops at the gap";
			out.add(c);
		}
		//two wavy filaments side by side, 2.5 profile widths apart: in xy (7 px), in z (21 px)
		{
			final SyntheticCase c = new SyntheticCase("parxy" + sv, new double [] {120, 120, 60}, vox, PSF);
			final ArrayList<double[]> p1 = new ArrayList<>();
			final ArrayList<double[]> p2 = new ArrayList<>();
			for (double x = 10; x <= 110; x += 0.05)
			{
				final double y = 56 + 6 * Math.sin(2 * Math.PI * x / 60);
				p1.add(new double [] {x, y, 30});
				p2.add(new double [] {x, y + 7, 30});
			}
			final Curve f1 = Curve.resample(p1, ds);
			c.filaments.add(f1);
			c.filaments.add(Curve.resample(p2, ds));
			c.targets.add(f1);
			c.startOnTarget(10);
			c.expected = "stays on its filament";
			out.add(c);
		}
		{
			final SyntheticCase c = new SyntheticCase("parz" + sv, new double [] {120, 120, 84}, vox, PSF);
			final ArrayList<double[]> p1 = new ArrayList<>();
			final ArrayList<double[]> p2 = new ArrayList<>();
			for (double x = 10; x <= 110; x += 0.05)
			{
				final double y = 60 + 10 * Math.sin(2 * Math.PI * x / 60);
				p1.add(new double [] {x, y, 31});
				p2.add(new double [] {x, y, 52});
			}
			final Curve f1 = Curve.resample(p1, ds);
			c.filaments.add(f1);
			c.filaments.add(Curve.resample(p2, ds));
			c.targets.add(f1);
			c.startOnTarget(10);
			c.expected = "stays on its filament";
			out.add(c);
		}
		//helix climbing at 45 degrees in z
		{
			final SyntheticCase c = new SyntheticCase("helix" + sv, new double [] {80, 80, 150}, vox, PSF);
			final ArrayList<double[]> p = new ArrayList<>();
			for (double tt = 0; tt <= 3 * Math.PI; tt += 0.002)
			{
				p.add(new double [] {40 + 14 * Math.cos(tt), 40 + 14 * Math.sin(tt), 20 + 14 * tt});
			}
			final Curve f = Curve.resample(p, ds);
			c.filaments.add(f);
			c.targets.add(f);
			c.startOnTarget(8);
			c.expected = "traced to the end";
			out.add(c);
		}
		return out;
	}
}
