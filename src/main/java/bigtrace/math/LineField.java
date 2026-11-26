package bigtrace.math;

import net.imglib2.Interval;

/** The line field of an image, what tracing is computed from: the scale-normalised hessian (normalised by the tracing
 * sigma, see MathBox), its gradient, the line saliency and the line direction, on a working grid.
 * The grid is either the input voxels or a block-averaged grid with ds input voxels per grid voxel along each axis
 * (DownsampleGrid). The field is defined by the input, the tracing sigma per axis, the downsampling and how the image
 * is continued beyond its borders (MathBox.OutOfBounds); implementations differ in where and when it is computed
 * (a box that moves with the trace: MovingBoxLineField; later possibly the whole volume, lazily computed and cached).
 * <p>
 * Positions and voxels are grid voxels, unless the name says input. Values are NaN (or false) where the field
 * is not available (call ensure first). **/
public interface LineField
{
	/** downsampling factors per axis (all 1 without downsampling) **/
	long [] downsampling();

	/** tracing sigma in grid voxels (the normalisation scale of the hessian) **/
	double [] gridSigma();

	/** the input in grid voxels **/
	Interval gridInterval();

	/** makes the field available around x (grid voxels); true if something was (re)computed **/
	boolean ensure(double [] x);

	/** the part of the input (input voxels) where the field is available now (e.g. to show it) **/
	Interval availableInputInterval();

	/** line saliency at the grid voxel v **/
	double saliencyAtVoxel(long [] v);

	/** line saliency at the grid voxel v, its line direction (grid voxel units, unit length) to vecOut **/
	double saliencyAndVectorAtVoxel(long [] v, double [] vecOut);

	/** normalised hessian (xx, xy, xz, yy, yz, zz) at the grid voxel v **/
	boolean hessianAtVoxel(long [] v, double [] h);

	/** normalised gradient at the grid voxel v **/
	boolean gradientAtVoxel(long [] v, double [] g);

	/** a direction in the normalised space of the hessian -> grid voxel units (unit length) **/
	void normToGridDir(double [] tn, double [] out);

	/** frees threads etc. **/
	void release();

	/** input voxel coordinates -> grid voxels (the centre of grid voxel g is at g * ds + (ds - 1) / 2) **/
	default double [] toGrid(final double [] x)
	{
		final long [] ds = downsampling();
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = (x[d] - 0.5 * (ds[d] - 1)) / ds[d];
		}
		return out;
	}

	/** grid voxels -> input voxel coordinates (null stays null) **/
	default double [] toInput(final double [] x)
	{
		if(x == null)
			return null;
		final long [] ds = downsampling();
		final double [] out = new double[3];
		for (int d = 0; d < 3; d++)
		{
			out[d] = x[d] * ds[d] + 0.5 * (ds[d] - 1);
		}
		return out;
	}

	/** the grid voxel v is inside of the input **/
	default boolean insideGrid(final long [] v)
	{
		final Interval g = gridInterval();
		for (int d = 0; d < 3; d++)
		{
			if(v[d] < g.min(d) || v[d] > g.max(d))
				return false;
		}
		return true;
	}
}
