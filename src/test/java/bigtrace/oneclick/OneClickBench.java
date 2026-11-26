package bigtrace.oneclick;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import bigtrace.math.OneClickCore;
import bigtrace.oneclick.SyntheticCase.Curve;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;

/** Regression harness for one-click tracing (OneClickCore), without the viewer.
 * <p>
 * Each run traces forward from the start of a case along its start direction and scores the trace against
 * the target filament(s):
 * <ul>
 * <li>reached: fraction of the target (arc length, from the start) with a trace point within REACH_TOL;</li>
 * <li>wrong: number of wrong-branch events, runs of trace points more than OFF_TOL from the target
 * that are longer than 2 SD (xy) of path;</li>
 * <li>mean, max: distance of the trace points to the target;</li>
 * <li>stop: why tracing ended and where (arc length along the target).</li>
 * </ul>
 * Distances in xy voxels. With several alternative targets (a Y branch) the best scoring one counts.
 * <p>
 * Usage: OneClickBench [-cases name,name] [-noise] [-replays dir] [-dump dir] [-score caseName points.txt] [-out file.csv]
 * <ul>
 * <li>synthetic cases (SyntheticCase.all, both voxel variants): noise free, and with -noise also SNR 10 and 3 (seeds 1-3);</li>
 * <li>-replays dir: every subfolder with case.txt (see Replay) is a real data case;</li>
 * <li>-dump dir: writes every synthetic run as a replay folder (for running other tracers on the same data);</li>
 * <li>-score case points.txt [stop]: scores a trace (one point per line, voxel coordinates) made by another tracer;
 * case: the name of a synthetic run or a replay folder.</li>
 * </ul> **/
public class OneClickBench
{
	/** a target point is reached if a trace point is within this distance (xy voxels) **/
	static final double REACH_TOL = 2.0;
	/** a trace point is off the target beyond this distance (xy voxels) **/
	static final double OFF_TOL = 3.0;
	/** max number of steps of a run **/
	static final int MAX_STEPS = 2000;

	/** one run: image, voxel size, tracing settings, start, targets (physical) **/
	public static class Run
	{
		String name;
		ArrayImg<FloatType, FloatArray> img;
		double [] voxel;
		double [] sigma;
		double [] psf;
		double dDirectionality = 0.6;
		double [] start;
		double [] startDir;
		ArrayList<Curve> targets = new ArrayList<>();
		String expected = "";
		/** for dumping: the image without noise is not needed, the history behind the start (physical) **/
		ArrayList<double[]> history = new ArrayList<>();
	}

	/** result of a run **/
	public static class Result
	{
		String name;
		int nPoints;
		double reached;
		int nWrong;
		double dMean;
		double dMax;
		String stop;
		double stopAt;
		double dMillis;

		String row()
		{
			return String.format(Locale.US, "%-24s %5d  %6.1f%%  %3d  %5.2f  %6.2f  %-18s %6.1f  %6.0f",
					name, nPoints, 100 * reached, nWrong, dMean, dMax, stop, stopAt, dMillis);
		}
		static String header()
		{
			return String.format(Locale.US, "%-24s %5s  %7s  %3s  %5s  %6s  %-18s %6s  %6s",
					"case", "pts", "reached", "wrg", "mean", "max", "stop", "at", "ms");
		}
	}

	public static void main(final String [] args) throws Exception
	{
		String sCases = null;
		boolean bNoise = false;
		String sReplays = null;
		String sDump = null;
		String sOut = null;
		for (int i = 0; i < args.length; i++)
		{
			switch(args[i])
			{
				case "-cases": sCases = args[++i]; break;
				case "-noise": bNoise = true; break;
				case "-replays": sReplays = args[++i]; break;
				case "-dump": sDump = args[++i]; break;
				case "-out": sOut = args[++i]; break;
				case "-score":
					score(args[i + 1], args[i + 2], args.length > i + 3 ? args[i + 3] : null);
					return;
				default: System.out.println("unknown option " + args[i]); return;
			}
		}
		final ArrayList<Result> results = new ArrayList<>();
		System.out.println(Result.header());
		for (final Run run : synthetic(sCases, bNoise))
		{
			if(sDump != null)
			{
				dump(run, sDump);
				continue;
			}
			final Result r = trace(run);
			results.add(r);
			System.out.println(r.row() + "   " + run.expected);
		}
		if(sReplays != null)
		{
			final File [] dirs = new File(sReplays).listFiles();
			java.util.Arrays.sort(dirs);
			for (final File dir : dirs)
			{
				if(!new File(dir, "case.txt").exists())
					continue;
				if(sCases != null && !matches(dir.getName(), sCases))
					continue;
				final Result r = trace(Replay.load(dir));
				results.add(r);
				System.out.println(r.row());
			}
		}
		if(sOut != null)
		{
			try (PrintWriter w = new PrintWriter(sOut))
			{
				w.println("case,points,reached,wrong,mean,max,stop,stopAt,ms");
				for (final Result r : results)
				{
					w.println(String.format(Locale.US, "%s,%d,%.4f,%d,%.3f,%.3f,%s,%.2f,%.0f", r.name, r.nPoints, r.reached, r.nWrong, r.dMean, r.dMax, r.stop, r.stopAt, r.dMillis));
				}
			}
		}
	}

	static boolean matches(final String name, final String sCases)
	{
		for (final String s : sCases.split(","))
		{
			if(name.startsWith(s))
				return true;
		}
		return false;
	}

	/** the synthetic runs: every case, both voxel variants; noise free, with bNoise also SNR 10 and 3 (seeds 1-3) **/
	static ArrayList<Run> synthetic(final String sCases, final boolean bNoise)
	{
		final ArrayList<Run> runs = new ArrayList<>();
		for (int v = 0; v < SyntheticCase.VOXELS.length; v++)
		{
			for (final SyntheticCase c : SyntheticCase.all(v))
			{
				if(sCases != null && !matches(c.name, sCases))
					continue;
				final ArrayImg<FloatType, FloatArray> clean = c.render();
				final ArrayList<double[]> noise = new ArrayList<>();
				noise.add(new double [] {0, 0});
				if(bNoise)
				{
					for (int s = 1; s <= 3; s++)
						noise.add(new double [] {10, s});
					for (int s = 1; s <= 3; s++)
						noise.add(new double [] {3, 100 + s});
				}
				//tracing sigma: the PSF (default rule); with isotropic voxels also the PSF with half of it in z
				//(the user's rule for an elongated PSF: smooth less along it), which tests the scaling by sqrt(psf^2 + sigma^2)
				for (int nSig = 0; nSig < (v == 0 ? 2 : 1); nSig++)
				for (final double [] nz : noise)
				{
					final Run run = new Run();
					run.name = c.name + (nSig == 1 ? "_sz" : "") + (nz[0] > 0 ? String.format(Locale.US, "_snr%d_s%d", (int) nz[0], (int) nz[1]) : "");
					run.img = SyntheticCase.withNoise(clean, nz[0], (long) nz[1]);
					run.voxel = c.voxel.clone();
					//tracing sigma = PSF (the default rule)
					run.psf = c.psfVoxels();
					run.sigma = c.psfVoxels();
					if(nSig == 1)
					{
						run.sigma[2] *= 0.5;
					}
					run.start = c.start.clone();
					run.startDir = c.startDir.clone();
					run.targets = c.targets;
					run.expected = c.expected;
					//history behind the start along the first target (physical)
					final Curve f = c.filaments.get(0);
					int i0 = 0;
					double dBest = Double.MAX_VALUE;
					for (int i = 0; i < f.p.size(); i++)
					{
						final double dd = SyntheticCase.dist(f.p.get(i), c.start);
						if(dd < dBest)
						{
							dBest = dd;
							i0 = i;
						}
					}
					for (int i = Math.max(0, i0 - 200); i <= i0; i += 10)
					{
						run.history.add(f.p.get(i));
					}
					runs.add(run);
				}
			}
		}
		return runs;
	}

	/** traces the run with OneClickCore and scores it **/
	static Result trace(final Run run)
	{
		final long t0 = System.nanoTime();
		final OneClickCore<FloatType> core = new OneClickCore<>();
		for (int d = 0; d < 3; d++)
		{
			core.sigma[d] = run.sigma[d];
			core.voxelSize[d] = run.voxel[d];
		}
		core.psf = run.psf.clone();
		core.dDirectionality = run.dDirectionality;
		core.nThreads = 1;
		core.init();
		core.setInput(run.img);
		final double [] startVox = new double[3];
		for (int d = 0; d < 3; d++)
		{
			startVox[d] = run.start[d] / run.voxel[d];
		}
		if(System.getenv("BENCH_LOG") != null && run.name.equals(System.getenv("BENCH_LOG")))
		{
			core.log = s -> System.out.println(s);
		}
		//on the line (as the start of a continued trace in OneClickTrace)
		final double [] dirStart = run.startDir.clone();
		final double [] snapped = core.nearestLinePoint(startVox, dirStart);
		if(snapped != null)
		{
			if(dirStart[0] * run.startDir[0] + dirStart[1] * run.startDir[1] + dirStart[2] * run.startDir[2] < 0)
			{
				for (int d = 0; d < 3; d++)
					dirStart[d] = -dirStart[d];
			}
			System.arraycopy(snapped, 0, startVox, 0, 3);
		}
		core.begin(startVox, dirStart);
		final ArrayList<double[]> pts = new ArrayList<>();
		pts.add(startVox.clone());
		for (int n = 0; n < MAX_STEPS; n++)
		{
			if(core.log != null)
				System.out.println(String.format(Locale.US, "step %d at (%.2f,%.2f,%.2f) dir (%.2f,%.2f,%.2f)", n,
						core.getPosition()[0], core.getPosition()[1], core.getPosition()[2], core.getDirection()[0], core.getDirection()[1], core.getDirection()[2]));
			final double [] q = core.step();
			if(q == null)
				break;
			pts.add(q);
		}
		core.release();
		final Result r = score(run, pts, core.stopReason.toString());
		r.dMillis = (System.nanoTime() - t0) * 1e-6;
		return r;
	}

	/** scores trace points (voxel coordinates) of a run, see the class description **/
	static Result score(final Run run, final List<double[]> ptsVox, final String sStop)
	{
		Result best = null;
		for (final Curve target : run.targets)
		{
			final Result r = scoreTarget(run, target, ptsVox, sStop);
			if(best == null || r.reached - 0.1 * r.nWrong > best.reached - 0.1 * best.nWrong)
				best = r;
		}
		return best;
	}

	static Result scoreTarget(final Run run, final Curve target, final List<double[]> ptsVox, final String sStop)
	{
		final double dUnit = run.voxel[0];
		final Result r = new Result();
		r.name = run.name;
		r.nPoints = ptsVox.size();
		r.stop = sStop;
		//distance of each trace point to the target
		final int nT = target.p.size();
		final boolean [] reached = new boolean[nT];
		final double [] dist = new double[ptsVox.size()];
		final double dReach2 = (REACH_TOL * dUnit) * (REACH_TOL * dUnit);
		int iLast = 0;
		for (int k = 0; k < ptsVox.size(); k++)
		{
			final double [] q = new double[3];
			for (int d = 0; d < 3; d++)
			{
				q[d] = ptsVox.get(k)[d] * run.voxel[d];
			}
			double dMin = Double.MAX_VALUE;
			int iMin = 0;
			for (int i = 0; i < nT; i++)
			{
				final double [] p = target.p.get(i);
				final double dd = (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]);
				if(dd < dMin)
				{
					dMin = dd;
					iMin = i;
				}
				if(dd <= dReach2)
				{
					reached[i] = true;
				}
			}
			dist[k] = Math.sqrt(dMin) / dUnit;
			if(dist[k] <= OFF_TOL)
				iLast = Math.max(iLast, iMin);
		}
		//reached fraction (by arc length)
		int nReached = 0;
		for (final boolean b : reached)
		{
			if(b)
				nReached++;
		}
		r.reached = nT > 0 ? nReached / (double) nT : 0;
		r.stopAt = target.s.isEmpty() ? 0 : target.s.get(iLast) / dUnit;
		//wrong-branch events: runs off the target longer than 2 SD (xy) of path
		final double dMinRun = 2.0 * run.sigma[0] * run.voxel[0];
		double dRun = 0;
		boolean bCounted = false;
		double dSum = 0;
		r.dMax = 0;
		for (int k = 0; k < ptsVox.size(); k++)
		{
			dSum += dist[k];
			r.dMax = Math.max(r.dMax, dist[k]);
			if(dist[k] > OFF_TOL)
			{
				if(k > 0)
				{
					double dd = 0;
					for (int d = 0; d < 3; d++)
					{
						final double s = (ptsVox.get(k)[d] - ptsVox.get(k - 1)[d]) * run.voxel[d];
						dd += s * s;
					}
					dRun += Math.sqrt(dd);
				}
				if(dRun > dMinRun && !bCounted)
				{
					r.nWrong++;
					bCounted = true;
				}
			}
			else
			{
				dRun = 0;
				bCounted = false;
			}
		}
		r.dMean = ptsVox.isEmpty() ? 0 : dSum / ptsVox.size();
		return r;
	}

	/** -score case points.txt [stop]: scores the points (voxel coordinates, one per line) against the synthetic run or replay folder **/
	static void score(final String sCase, final String sFile, final String sStop) throws IOException
	{
		if(new File(sCase, "case.txt").exists())
		{
			final Run run = Replay.load(new File(sCase));
			System.out.println(score(run, Replay.readPoints(new File(sFile)), sStop == null ? "-" : sStop).row());
			return;
		}
		for (final Run run : synthetic(sCase.replaceAll("_snr.*", ""), sCase.contains("_snr")))
		{
			if(!run.name.equals(sCase))
				continue;
			final ArrayList<double[]> pts = new ArrayList<>();
			for (final String line : Files.readAllLines(Paths.get(sFile)))
			{
				final String [] t = line.trim().split("[\\s,]+");
				if(t.length >= 3)
					pts.add(new double [] {Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2])});
			}
			System.out.println(score(run, pts, sStop == null ? "-" : sStop).row());
			return;
		}
		System.out.println("no case " + sCase);
	}

	/** writes the run as a replay folder: img.f32 (x fastest), case.txt, trace.txt (history up to the start, voxels),
	 * gt.txt (first target, voxels) **/
	static void dump(final Run run, final String sDir) throws IOException
	{
		final File dir = new File(sDir, run.name);
		dir.mkdirs();
		final float [] a = run.img.update(null).getCurrentStorageArray();
		final ByteBuffer bb = ByteBuffer.allocate(4 * a.length).order(ByteOrder.LITTLE_ENDIAN);
		bb.asFloatBuffer().put(a);
		Files.write(new File(dir, "img.f32").toPath(), bb.array());
		try (PrintWriter w = new PrintWriter(new File(dir, "trace.txt")))
		{
			for (final double [] p : run.history)
				w.println(String.format(Locale.US, "%.3f %.3f %.3f", p[0] / run.voxel[0], p[1] / run.voxel[1], p[2] / run.voxel[2]));
			w.println(String.format(Locale.US, "%.3f %.3f %.3f", run.start[0] / run.voxel[0], run.start[1] / run.voxel[1], run.start[2] / run.voxel[2]));
		}
		try (PrintWriter w = new PrintWriter(new File(dir, "gt.txt")))
		{
			final Curve t = run.targets.get(0);
			for (int i = 0; i < t.p.size(); i += 5)
			{
				final double [] p = t.p.get(i);
				w.println(String.format(Locale.US, "%.3f %.3f %.3f", p[0] / run.voxel[0], p[1] / run.voxel[1], p[2] / run.voxel[2]));
			}
		}
		final long [] dims = new long[3];
		run.img.dimensions(dims);
		try (PrintWriter w = new PrintWriter(new File(dir, "case.txt")))
		{
			w.println(String.format(Locale.US, "dims %d %d %d", dims[0], dims[1], dims[2]));
			w.println(String.format(Locale.US, "voxel %s %s %s", run.voxel[0], run.voxel[1], run.voxel[2]));
			w.println(String.format(Locale.US, "sigma %s %s %s", run.sigma[0], run.sigma[1], run.sigma[2]));
			w.println(String.format(Locale.US, "psf %s %s %s", run.psf[0], run.psf[1], run.psf[2]));
			w.println(String.format(Locale.US, "start %d", run.history.size()));
		}
	}

	/** real data case from a folder: img.f32 (float32 little endian, x fastest), case.txt with lines
	 * "dims nx ny nz", "voxel vx vy vz", "sigma sx sy sz" (voxels), optional "psf px py pz" (voxels, default sigma),
	 * optional "directionality c", "start i" (index into trace.txt); trace.txt: the trace up to the start (voxels, one point per line);
	 * gt.txt: the target (voxels), starting anywhere before or at the start **/
	public static class Replay
	{
		static Run load(final File dir) throws IOException
		{
			final Run run = new Run();
			run.name = dir.getName();
			long [] dims = null;
			int nStart = -1;
			for (final String line : Files.readAllLines(new File(dir, "case.txt").toPath()))
			{
				final String [] t = line.trim().split("\\s+");
				switch(t[0])
				{
					case "dims": dims = new long [] {Long.parseLong(t[1]), Long.parseLong(t[2]), Long.parseLong(t[3])}; break;
					case "voxel": run.voxel = vec(t); break;
					case "sigma": run.sigma = vec(t); break;
					case "psf": run.psf = vec(t); break;
					case "directionality": run.dDirectionality = Double.parseDouble(t[1]); break;
					case "start": nStart = Integer.parseInt(t[1]); break;
					default: break;
				}
			}
			if(run.psf == null)
				run.psf = run.sigma.clone();
			final byte [] raw = Files.readAllBytes(new File(dir, "img.f32").toPath());
			final float [] a = new float[raw.length / 4];
			ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(a);
			run.img = ArrayImgs.floats(a, dims);
			final ArrayList<double[]> trace = readPoints(new File(dir, "trace.txt"));
			if(nStart < 0)
				nStart = trace.size() - 1;
			final double [] s = trace.get(nStart);
			final double [] back = trace.get(Math.max(0, nStart - 3));
			run.start = new double[3];
			run.startDir = new double[3];
			for (int d = 0; d < 3; d++)
			{
				run.start[d] = s[d] * run.voxel[d];
				run.startDir[d] = (s[d] - back[d]) * run.voxel[d];
			}
			normalize(run.startDir);
			//target: the ground truth from its point nearest to the start on, in the start direction
			final ArrayList<double[]> gt = readPoints(new File(dir, "gt.txt"));
			final ArrayList<double[]> gtPhys = new ArrayList<>();
			for (final double [] g : gt)
				gtPhys.add(new double [] {g[0] * run.voxel[0], g[1] * run.voxel[1], g[2] * run.voxel[2]});
			int i0 = 0;
			double dBest = Double.MAX_VALUE;
			for (int i = 0; i < gtPhys.size(); i++)
			{
				final double dd = SyntheticCase.dist(gtPhys.get(i), run.start);
				if(dd < dBest)
				{
					dBest = dd;
					i0 = i;
				}
			}
			//which way along gt
			final int iNext = Math.min(gtPhys.size() - 1, i0 + 3);
			double dot = 0;
			for (int d = 0; d < 3; d++)
				dot += (gtPhys.get(iNext)[d] - gtPhys.get(i0)[d]) * run.startDir[d];
			final ArrayList<double[]> ahead = new ArrayList<>();
			if(dot >= 0)
				for (int i = i0; i < gtPhys.size(); i++) ahead.add(gtPhys.get(i));
			else
				for (int i = i0; i >= 0; i--) ahead.add(gtPhys.get(i));
			run.targets.add(Curve.resample(ahead, 0.1 * run.voxel[0]));
			return run;
		}

		static double [] vec(final String [] t)
		{
			return new double [] {Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3])};
		}

		static ArrayList<double[]> readPoints(final File f) throws IOException
		{
			final ArrayList<double[]> out = new ArrayList<>();
			for (final String line : Files.readAllLines(f.toPath()))
			{
				final String [] t = line.trim().split("[\\s,]+");
				if(t.length >= 3)
					out.add(new double [] {Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2])});
			}
			return out;
		}
	}

	static void normalize(final double [] v)
	{
		final double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		if(n > 0)
			for (int d = 0; d < 3; d++)
				v[d] /= n;
	}
}
