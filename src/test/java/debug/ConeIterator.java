package debug;

import net.imglib2.util.LinAlgHelpers;

public class ConeIterator
{
	final double []  vDirection;
	final double [] vPosition;
	final double fConeAngle;
	final double[] c;
	final double h; 
	final double[] qConeSDF;
	final double [] qRot;
	
	boolean bOnlySlanted = true;
	
	public ConeIterator(final double [] vDirection_, final double [] vPosition_, final double fConeAngle_)
	{
		fConeAngle = fConeAngle_ * 0.5;
		vDirection = new double [3];
		vPosition = new double [3];
		for(int d = 0; d < 3; d++)
		{
			vDirection[d] = vDirection_[d];
			vPosition[d] = vPosition_[d];			
		}
		c = new double[] {Math.sin( fConeAngle ), Math.cos( fConeAngle )};
		h = LinAlgHelpers.length( vDirection );
		qConeSDF = new double []  { h * c[ 0 ] / c[ 1 ], -h };
		double [] orientDef = new double [] {0,-1.0, 0};
		qRot = rotationBetween(vDirection, orientDef);
	}
	
	public boolean isOnTheCone(final double p [])
	{
		double [] pIn = new double [3];
		for(int d = 0; d < 3; d++)
		{
			pIn[d] = p[d];
		}
		LinAlgHelpers.subtract( pIn, vPosition,  pIn );

		LinAlgHelpers.quaternionApply( qRot, pIn, pIn);
		if(Math.abs(sdCone(pIn)) < 0.5)
			return true;
		return false;
	}
	
	double sdCone( final double[] p)
	{

		if(bOnlySlanted)
		{
			return udConeSide(p);
		}
		final double[] w = { Math.sqrt( p[ 0 ] * p[ 0 ] + p[ 2 ] * p[ 2 ] ), p[ 1 ] };

		final double[] a = new double[ 2 ];
		LinAlgHelpers.scale( qConeSDF, clamp( LinAlgHelpers.dot( w, qConeSDF ) / LinAlgHelpers.squareLength( qConeSDF ), 0.0, 1.0 ), a );
		LinAlgHelpers.subtract( w, a, a );

		final double[] b = {
				w[ 0 ] - qConeSDF[ 0 ] * clamp( w[ 0 ] / qConeSDF[ 0 ], 0.0, 1.0 ),
				w[ 1 ] - qConeSDF[ 1 ] };

		final double k = Math.signum( qConeSDF[ 1 ] );
		final double d = Math.min( LinAlgHelpers.squareLength( a ), LinAlgHelpers.squareLength( b ) );
		final double s = Math.max(
				k * ( w[ 0 ] * qConeSDF[ 1 ] - w[ 1 ] * qConeSDF[ 0 ] ),
				k * ( w[ 1 ] - qConeSDF[ 1 ] ) );

		return  Math.sqrt( d ) * Math.signum( s ) ;
	}
	
	/** unsigned distance to the lateral surface of the cone (no base cap) **/
	double udConeSide( final double[] p )
	{
		final double[] w = { Math.sqrt( p[ 0 ] * p[ 0 ] + p[ 2 ] * p[ 2 ] ), p[ 1 ] };
		final double t = clamp( LinAlgHelpers.dot( w, qConeSDF ) / LinAlgHelpers.squareLength( qConeSDF ), 0.0, 1.0 );
		final double dx = w[ 0 ] - qConeSDF[ 0 ] * t;
		final double dy = w[ 1 ] - qConeSDF[ 1 ] * t;
		return Math.sqrt( dx * dx + dy * dy );
	}
	
	/** from https://iquilezles.org/articles/distfunctions/ **/
	public static double sdCone( final double[] p, final double[] c, final double h )
	{

		final double[] qIn = { h * c[ 0 ] / c[ 1 ], -h };

		final double[] w = { Math.sqrt( p[ 0 ] * p[ 0 ] + p[ 2 ] * p[ 2 ] ), p[ 1 ] };

		final double[] a = new double[ 2 ];
		LinAlgHelpers.scale( qIn, clamp( LinAlgHelpers.dot( w, qIn ) / LinAlgHelpers.squareLength( qIn ), 0.0, 1.0 ), a );
		LinAlgHelpers.subtract( w, a, a );

		final double[] b = {
				w[ 0 ] - qIn[ 0 ] * clamp( w[ 0 ] / qIn[ 0 ], 0.0, 1.0 ),
				w[ 1 ] - qIn[ 1 ] };

		final double k = Math.signum( qIn[ 1 ] );
		final double d = Math.min( LinAlgHelpers.squareLength( a ), LinAlgHelpers.squareLength( b ) );
		final double s = Math.max(
				k * ( w[ 0 ] * qIn[ 1 ] - w[ 1 ] * qIn[ 0 ] ),
				k * ( w[ 1 ] - qIn[ 1 ] ) );

		return  Math.sqrt( d ) * Math.signum( s ) ;
	}

	private static double clamp( final double x, final double min, final double max )
	{
		return Math.max( min, Math.min( max, x ) );
	}
	
	/**
	 * Quaternion q = {w, x, y, z} rotating vector "from" onto vector "to"
	 * (shortest arc). Inputs need not be normalized.
	 */
	public static double[] rotationBetween( final double[] from, final double[] to )
	{
		final double[] a = from.clone();
		final double[] b = to.clone();
		LinAlgHelpers.normalize( a );
		LinAlgHelpers.normalize( b );

		final double d = LinAlgHelpers.dot( a, b );
		final double[] q = new double[ 4 ];

		if ( d < -1.0 + 1e-9 )
		{
			// Vectors are opposite: rotate 180° around any axis perpendicular to a
			final double[] axis = new double[ 3 ];
			LinAlgHelpers.cross( new double[] { 1, 0, 0 }, a, axis );
			if ( LinAlgHelpers.length( axis ) < 1e-6 ) // a was parallel to X
				LinAlgHelpers.cross( new double[] { 0, 1, 0 }, a, axis );
			LinAlgHelpers.normalize( axis );
			q[ 0 ] = 0;
			q[ 1 ] = axis[ 0 ];
			q[ 2 ] = axis[ 1 ];
			q[ 3 ] = axis[ 2 ];
			return q;
		}

		final double[] c = new double[ 3 ];
		LinAlgHelpers.cross( a, b, c );
		q[ 0 ] = 1.0 + d;
		q[ 1 ] = c[ 0 ];
		q[ 2 ] = c[ 1 ];
		q[ 3 ] = c[ 2 ];
		LinAlgHelpers.normalize( q ); // works on any length array
		return q;
	}

	
}
