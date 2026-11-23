package bigtrace.volume;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RealInterval;
import net.imglib2.util.LinAlgHelpers;

/**
 * Cursor over the voxels of a cone, inspired by imglib2-mesh's MeshCursor.
 * <p>
 * All geometry (apex, axis, bounding box, shell thickness) is in physical
 * (calibrated) units; voxel {@code i} sits at physical position {@code i * cal}.
 * Iteration order is X fastest, then Y, then Z.
 * <p>
 * Two modes:
 * <ul>
 * <li>{@code shellOnly = true}: voxels whose distance to the slanted surface is
 * below {@code shellHalfThickness} (no base cap);</li>
 * <li>{@code shellOnly = false}: all voxels inside the solid cone.</li>
 * </ul>
 */
public class ConeCursor< T > implements Cursor< T >
{
	private final RandomAccess< T > ra;

	private final double[] cal;

	/** apex (tip) of the cone, physical units **/
	private final double[] apex;

	/** unit axis direction, from apex to base **/
	private final double[] u;

	/** cone height (length of the axis vector) **/
	private final double h;

	/** tan of the half opening angle **/
	private final double tanHalf;

	/** base radius **/
	private final double r;

	/** squared length of the side segment (r, h) in the (radial, along) plane **/
	private final double sideLen2;

	private final boolean shellOnly;

	private final double shellHalfThickness;

	private final double shellHalfThickness2;

	private final int minX, maxX, minY, maxY, minZ, maxZ;

	/** position of the next voxel to visit **/
	private int nx, ny, nz;

	private boolean hasNext;

	private static final double EPS = 1e-9;

	/** scratch **/
	private final double[] p = new double[ 3 ];

	/**
	 * Cursor over the shell (slanted surface, no base) of a cone, restricted to
	 * the cone's own bounding box. The shell half-thickness is half the smallest voxel size.
	 */
	public ConeCursor( final RandomAccess< T > ra, final double[] apex, final double[] axis, final double fullAngle, final double[] cal )
	{
		this( ra, apex, axis, fullAngle, cal, null, true, 0.5 * Math.min( cal[ 0 ], Math.min( cal[ 1 ], cal[ 2 ] ) ) );
	}

	/**
	 * @param ra
	 *            random access to the image
	 * @param apex
	 *            position of the cone tip, physical units
	 * @param axis
	 *            vector from the tip to the base center, physical units (its
	 *            length is the cone height)
	 * @param fullAngle
	 *            full opening angle at the tip, radians
	 * @param cal
	 *            voxel size
	 * @param boundingBox
	 *            optional extra bounds in physical units (e.g. the image
	 *            extent, so the RandomAccess is never out of bounds); may be
	 *            {@code null}. It is intersected with the cone's own bounding
	 *            box.
	 * @param shellOnly
	 *            iterate only over the slanted surface (true) or over the
	 *            whole solid cone (false)
	 * @param shellHalfThickness
	 *            max distance to the surface in shell mode, physical units
	 */
	public ConeCursor( final RandomAccess< T > ra, final double[] apex, final double[] axis, final double fullAngle,
			final double[] cal, final RealInterval boundingBox, final boolean shellOnly, final double shellHalfThickness )
	{
		this.ra = ra;
		this.cal = cal.clone();
		this.apex = apex.clone();
		this.h = LinAlgHelpers.length( axis );
		this.u = axis.clone();
		LinAlgHelpers.normalize( u );
		this.tanHalf = Math.tan( 0.5 * fullAngle );
		this.r = h * tanHalf;
		this.sideLen2 = r * r + h * h;
		this.shellOnly = shellOnly;
		this.shellHalfThickness = shellHalfThickness;
		this.shellHalfThickness2 = shellHalfThickness * shellHalfThickness;

		// Analytic bounding box of the cone: hull of the apex and the base disc.
		// The disc (center c, radius r, normal u) extends r * sqrt(1 - u_d^2) along axis d.
		final double pad = shellOnly ? shellHalfThickness : 0.0;
		final int[] mn = new int[ 3 ];
		final int[] mx = new int[ 3 ];
		for ( int d = 0; d < 3; d++ )
		{
			final double baseC = apex[ d ] + axis[ d ];
			final double ext = r * Math.sqrt( Math.max( 0.0, 1.0 - u[ d ] * u[ d ] ) );
			double lo = Math.min( apex[ d ], baseC - ext ) - pad;
			double hi = Math.max( apex[ d ], baseC + ext ) + pad;
			if ( boundingBox != null )
			{
				lo = Math.max( lo, boundingBox.realMin( d ) );
				hi = Math.min( hi, boundingBox.realMax( d ) );
			}
			mn[ d ] = ( int ) Math.ceil( lo / cal[ d ] );
			mx[ d ] = ( int ) Math.floor( hi / cal[ d ] );
		}
		minX = mn[ 0 ]; maxX = mx[ 0 ];
		minY = mn[ 1 ]; maxY = mx[ 1 ];
		minZ = mn[ 2 ]; maxZ = mx[ 2 ];
		reset();
	}

	/** copy constructor **/
	private ConeCursor( final ConeCursor< T > c )
	{
		ra = c.ra.copy();
		cal = c.cal;
		apex = c.apex;
		u = c.u;
		h = c.h;
		tanHalf = c.tanHalf;
		r = c.r;
		sideLen2 = c.sideLen2;
		shellOnly = c.shellOnly;
		shellHalfThickness = c.shellHalfThickness;
		shellHalfThickness2 = c.shellHalfThickness2;
		minX = c.minX; maxX = c.maxX;
		minY = c.minY; maxY = c.maxY;
		minZ = c.minZ; maxZ = c.maxZ;
		nx = c.nx; ny = c.ny; nz = c.nz;
		hasNext = c.hasNext;
	}

	/** true if voxel (x,y,z) belongs to the cone (shell or solid, depending on mode) **/
	public boolean contains( final int x, final int y, final int z )
	{
		p[ 0 ] = x * cal[ 0 ] - apex[ 0 ];
		p[ 1 ] = y * cal[ 1 ] - apex[ 1 ];
		p[ 2 ] = z * cal[ 2 ] - apex[ 2 ];

		final double along = LinAlgHelpers.dot( p, u );
		if ( shellOnly )
		{
			if ( along < -shellHalfThickness || along > h + shellHalfThickness )
				return false;
		}
		else if ( along < -EPS || along > h + EPS ) // EPS: voxels exactly on the apex/base plane are kept
			return false;

		final double radial = Math.sqrt( Math.max( 0.0, LinAlgHelpers.squareLength( p ) - along * along ) );

		if ( !shellOnly )
			return radial <= along * tanHalf + EPS;

		// distance in the (radial, along) plane to the segment (0,0) -> (r, h)
		final double t = Math.max( 0.0, Math.min( 1.0, ( radial * r + along * h ) / sideLen2 ) );
		final double dx = radial - r * t;
		final double dy = along - h * t;
		return dx * dx + dy * dy < shellHalfThickness2;
	}

	@Override
	public void reset()
	{
		nx = minX - 1;
		ny = minY;
		nz = minZ;
		preFetch();
	}

	/** advances (nx,ny,nz) to the next voxel inside the cone **/
	private void preFetch()
	{
		hasNext = false;
		if ( minX > maxX || minY > maxY || minZ > maxZ )
			return;
		while ( true )
		{
			nx++;
			if ( nx > maxX )
			{
				nx = minX;
				ny++;
				if ( ny > maxY )
				{
					ny = minY;
					nz++;
					if ( nz > maxZ )
						return; // finished
				}
			}
			if ( contains( nx, ny, nz ) )
			{
				hasNext = true;
				return;
			}
		}
	}

	@Override
	public void fwd()
	{
		ra.setPosition( nx, 0 );
		ra.setPosition( ny, 1 );
		ra.setPosition( nz, 2 );
		preFetch();
	}

	@Override
	public boolean hasNext()
	{
		return hasNext;
	}

	@Override
	public void jumpFwd( final long steps )
	{
		for ( long i = 0; i < steps; i++ )
			fwd();
	}

	@Override
	public T next()
	{
		fwd();
		return get();
	}

	@Override
	public T get()
	{
		return ra.get();
	}

	@Override
	public long getLongPosition( final int d )
	{
		return ra.getLongPosition( d );
	}

	@Override
	public int numDimensions()
	{
		return 3;
	}

	@Override
	public ConeCursor< T > copy()
	{
		return new ConeCursor<>( this );
	}
}
