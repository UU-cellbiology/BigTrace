package bigtrace.gui;

import bdv.util.Affine3DHelpers;
import bdv.viewer.animate.AbstractTransformAnimator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

public class AnisotropicTransformAnimator3D extends AbstractTransformAnimator
{	

	private final double[] qStart;
	
	private final double[] qEnd;
	
	private final double [] scaleStart;
	
	private final double [] scaleEnd;

	private final double [] translateStart;
	
	private final double [] translateEnd;

	public AnisotropicTransformAnimator3D( final AffineTransform3D transformStart, final AffineTransform3D transformEnd, final long duration )
	{
		super( duration );
		
		qStart = new double[ 4 ];
		qEnd = new double[ 4 ];
		
		scaleStart = new double[3];
		scaleEnd = new double[3];
		
		translateStart = new double[3];
		translateEnd = new double[3];

		Affine3DHelpers.extractRotationAnisotropic( transformStart, qStart );
		
		Affine3DHelpers.extractRotationAnisotropic( transformEnd, qEnd );
				
		for (int d = 0; d < 3; d++)
		{
			scaleStart[d] = Affine3DHelpers.extractScale( transformStart, d );
			scaleEnd[d] = Affine3DHelpers.extractScale( transformEnd, d );
			translateStart[d] = transformStart.get( d, 3 );
			translateEnd[d] = transformEnd.get( d, 3 );
		}
	}

	@Override
	public AffineTransform3D get( final double fraction )
	{
		//just in case
		final double t = Math.min( Math.max( fraction, 0.0 ), 1.0);
		
		final double [] translate = new double[3];
		
		final double [] scale = new double[3];
		
		//lerp for translation and scale
		for(int d = 0; d < 3; d++)
		{
			translate[d] = (1.0 - t) * translateStart[d] + t * translateEnd[d];
			scale[d] = (1.0 - t) * scaleStart[d] + t * scaleEnd[d];
		}
		//quaternion slerp
		final double [] qfin = slerp(t);
		
		//assemble!
		final AffineTransform3D transform = new AffineTransform3D();
		
		//scale
		transform.scale( scale[0], scale[1], scale[2]);
		//rotation
		final double [][] rotMatrix = new double [3][4];  
		LinAlgHelpers.quaternionToR( qfin, rotMatrix );	
		final AffineTransform3D trRot = new AffineTransform3D();
		trRot.set( rotMatrix );	
		transform.preConcatenate( trRot );
		
		//translation
		transform.translate( translate );
		
		return transform;
	}
	
	/** Spherical Linear Interpolation
       interpolates rotations at constant angular velocity and avoids distortion.**/
	double [] slerp (double t)
	{
		final double [] qout = new double[4];
		final double [] q1 = new double[4];

		double dot = 0.0;
		// Compute cosine of angle between 
		for(int d = 0; d < 4; d++)
		{
			q1[d] = qEnd[d];
			dot += qStart[d] * qEnd[d];
		}
		// Use shortest path
		if (dot < 0.0) 
		{
			for(int d = 0; d < 4; d++)
			{
				q1[d] = - qEnd[d];
			}
			dot = -dot;
		}
		// If angle is very small, use LERP
		if (dot > 0.9995) 
		{
			for(int d = 0; d < 4; d++)
			{
				qout[d] =  qStart[d] + t*(q1[d] - qStart[d]);
			}
			LinAlgHelpers.normalize( qout );
			return qout;
		}
		final double theta0 = Math.acos(dot);
		final double theta  = theta0 * t;

		final double sinTheta  = Math.sin(theta);
		final double sinTheta0 = Math.sin(theta0);

		final double s0 = Math.cos(theta) - dot * sinTheta / sinTheta0;
		final double s1 = sinTheta / sinTheta0;

		for(int d = 0; d < 4; d++)
		{
			qout[d] = s0 * qStart[d] + s1 * q1[d];
		}        

		return qout;
	}
}
