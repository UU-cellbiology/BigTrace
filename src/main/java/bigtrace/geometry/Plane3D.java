package bigtrace.geometry;

import org.joml.Vector3f;

import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;

/**
*  vector notation for a plane in 3D:
*  (p-p0) dot n = 0
*  
*  p0 - some vector on the plane
*  n  - normal vector to the plane (normalized)
**/
public class Plane3D {

	/** some vector on a plane **/
	public double [] p0;
	/** normal to the plane **/
	public double [] n;
	
    /** empty constructor **/
	public Plane3D()
	{
		p0= new double [3]; 
		n= new double [3];
	}

    /** set normal vector and point in the plane
     * @param p0_ - vector on a plane
     * @param n_ - normal vector (does not have to be normalized) **/
	public void setVectors(double [] p0_, double [] n_)
	{
		p0 = new double [3]; 
		n = new double [3];
		for (int i =0;i<3;i++)
		{
			p0[i]=p0_[i];
			n[i]=n_[i];
		}
		//just in case 
		LinAlgHelpers.normalize(n);
	}
	
	/** from three vectors on the plane (for RealPoint)
	 *  make sure they are not collinear
	 * notice, that normal vector (n) will be looking as cross product of
	 * v1-v2(x) and v3-v2(y), i.e. as (z) **/
	public Plane3D(final RealPoint v1, final RealPoint v2, final RealPoint v3)
	{
		p0 = new double [3]; 
		n= new double [3];
		
		double [] v12 = new double [3];
		double [] v32 = new double [3];
		v2.localize(p0);
		v1.localize(v12);
		v3.localize(v32);
		//v1-v2
		LinAlgHelpers.subtract(v12, p0, v12);
		//v3-v2
		LinAlgHelpers.subtract(v32, p0, v32);
		//normal
		LinAlgHelpers.cross(v12, v32, n);
		LinAlgHelpers.normalize(n);
	}
	/** from three vectors on the plane (for RealPoint)
	 *  make sure they are not collinear
	 * notice, that normal vector (n) will be looking as cross product of
	 * v1-v2(x) and v3-v2(y), i.e. as (z) **/
	public Plane3D(final Vector3f v1, final Vector3f v2, final Vector3f v3)
	{
		p0= new double [3]; 
		n= new double [3];
		
		double [] v12 = new double [3];
		double [] v32 = new double [3];
		for(int i=0;i<3;i++)
		{
			v12[i] = v1.get(i)-v2.get(i);
			v32[i] = v3.get(i)-v2.get(i);
			p0[i] = v2.get(i);
		}
		//normal
		LinAlgHelpers.cross(v12, v32, n);
		LinAlgHelpers.normalize(n);
	}
	/** from three vectors on the plane (for double [])
	 *  make sure they are not collinear
	 * notice, that normal vector (n) will be looking as cross product of
	 * v1-v2(x) and v3-v2(y), i.e. as (z) **/
	public Plane3D(final double[] v1, final double[] v2, final double[] v3)
	{
		p0 = new double [3]; 
		n = new double [3];
		
		double [] v12 = new double [3];
		double [] v32 = new double [3];
		for(int i=0;i<3;i++)
		{
			v12[i] = v1[i]-v2[i];
			v32[i] = v3[i]-v2[i];
			p0[i] = v2[i];
		}

		//normal
		LinAlgHelpers.cross(v12, v32, n);
		LinAlgHelpers.normalize(n);
	}	
	/** returns absolute distance from a point p to the current plane **/
	public double distance(final RealPoint p)
	{
		double [] v = p.positionAsDoubleArray();
		LinAlgHelpers.subtract(v, p0, v);
		
		return Math.abs(LinAlgHelpers.dot(v, n));
	}
	
	/** returns signed distance from a point p to the current plane,
	 * it is negative if point is below plane's normal vector direction**/
	public double signedDistance(final RealPoint p)
	{
		double [] v = p.positionAsDoubleArray();
		LinAlgHelpers.subtract(v, p0, v);
		
		return LinAlgHelpers.dot(v, n);
	}
}
