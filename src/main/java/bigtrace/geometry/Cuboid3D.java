
package bigtrace.geometry;

import java.util.ArrayList;

import net.imglib2.AbstractInterval;
import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;

/**
 * 3D shape that has 8 vertices and 6 faces, not necessarily box, but it is not
 * verified and supposed to be checked by the user
 **/
public class Cuboid3D {

	public double[][] vertices;
	public double[][] dMinMax;
	public ArrayList<Plane3D> faces;
	public boolean faces_init = false;

	public Cuboid3D() {
		vertices = new double[8][3];
		faces = new ArrayList<>();
	}

	/**
	 * Creates a cube with vertices_in and calculates bounding box. Sequence of
	 * vertices is important for proper initialization of faces/volume check. Does
	 * not initializes "faces", use iniFaces()
	 **/
	public Cuboid3D(final ArrayList<RealPoint> vertices_in) {
		vertices = new double[8][3];
		dMinMax = new double[2][3];

		vertices_in.get(0).localize(vertices[1]); // minx.maxy.minz
		vertices_in.get(1).localize(vertices[5]); // minx.maxy.maxz
		vertices_in.get(2).localize(vertices[0]); // minx.miny.minz
		vertices_in.get(3).localize(vertices[4]); // minx.miny.maxz
		vertices_in.get(4).localize(vertices[2]); // maxx.maxy.minz
		vertices_in.get(5).localize(vertices[6]); // maxx.maxy.maxz
		vertices_in.get(6).localize(vertices[3]); // maxx.miny.minz
		vertices_in.get(7).localize(vertices[7]); // maxx.miny.maxz

		// range
		Intersections3D.makeBoundBox(vertices_in, dMinMax);

	}

	/**
	 * Creates a cube with min coordinates at nMinMax[0] and max coordinates at
	 * nMinMax[1]. Does not initializes "faces", use iniFaces()
	 **/
	public Cuboid3D(final long[][] nMinMax) {
		dMinMax = new double[2][3];
		int i;
		for (i = 0; i < 3; i++) {
			dMinMax[0][i] = Math.round(nMinMax[0][i]);
			dMinMax[1][i] = Math.round(nMinMax[1][i]);
		}
		vertices = new double[8][3];
		final double[] temp = new double[3];
		final double[] diff = new double[3];

		// range
		for (i = 0; i < 3; i++)
			diff[i] = dMinMax[1][i] - dMinMax[0][i];

		LinAlgHelpers.scale(dMinMax[0], 1.0, vertices[0]);
		LinAlgHelpers.scale(dMinMax[1], 1.0, vertices[6]);
		temp[1] = diff[1];
		LinAlgHelpers.add(vertices[0], temp, vertices[1]);
		temp[0] = diff[0];
		LinAlgHelpers.add(vertices[0], temp, vertices[2]);
		temp[1] = 0.0;
		LinAlgHelpers.add(vertices[0], temp, vertices[3]);
		temp[0] = 0.0;
		temp[2] = diff[2];
		LinAlgHelpers.add(vertices[0], temp, vertices[4]);
		LinAlgHelpers.add(vertices[1], temp, vertices[5]);
		LinAlgHelpers.add(vertices[3], temp, vertices[7]);
	}

	/**
	 * Creates a cube with min/max coordinates defined by interval_in. Does not
	 * initializes "faces", use iniFaces()
	 **/
	public Cuboid3D(final AbstractInterval interval_in) {
		dMinMax = new double[2][3];
		int i;

		for (i = 0; i < 3; i++) {
			dMinMax[0][i] = interval_in.min(i);
			dMinMax[1][i] = interval_in.max(i);
		}
		vertices = new double[8][3];
		final double[] temp = new double[3];
		final double[] diff = new double[3];

		// range
		for (i = 0; i < 3; i++)
			diff[i] = dMinMax[1][i] - dMinMax[0][i];

		LinAlgHelpers.scale(dMinMax[0], 1.0, vertices[0]);
		LinAlgHelpers.scale(dMinMax[1], 1.0, vertices[6]);
		temp[1] = diff[1];
		LinAlgHelpers.add(vertices[0], temp, vertices[1]);
		temp[0] = diff[0];
		LinAlgHelpers.add(vertices[0], temp, vertices[2]);
		temp[1] = 0.0;
		LinAlgHelpers.add(vertices[0], temp, vertices[3]);
		temp[0] = 0.0;
		temp[2] = diff[2];
		LinAlgHelpers.add(vertices[0], temp, vertices[4]);
		LinAlgHelpers.add(vertices[1], temp, vertices[5]);
		LinAlgHelpers.add(vertices[3], temp, vertices[7]);
	}

	/**
	 * Creates a cube with min coordinates at nMinMax[0] and max coordinates at
	 * nMinMax[1]. Does not initializes "faces", use iniFaces()
	 **/
	public Cuboid3D(final double[][] dMinMaxin) {
		dMinMax = new double[2][3];
		int i;
		for (i = 0; i < 3; i++) {
			dMinMax[0][i] = dMinMaxin[0][i];
			dMinMax[1][i] = dMinMaxin[1][i];
		}
		vertices = new double[8][3];
		final double[] temp = new double[3];
		final double[] diff = new double[3];

		// range
		for (i = 0; i < 3; i++)
			diff[i] = dMinMax[1][i] - dMinMax[0][i];

		LinAlgHelpers.scale(dMinMax[0], 1.0, vertices[0]);
		LinAlgHelpers.scale(dMinMax[1], 1.0, vertices[6]);
		temp[1] = diff[1];
		LinAlgHelpers.add(vertices[0], temp, vertices[1]);
		temp[0] = diff[0];
		LinAlgHelpers.add(vertices[0], temp, vertices[2]);
		temp[1] = 0.0;
		LinAlgHelpers.add(vertices[0], temp, vertices[3]);
		temp[0] = 0.0;
		temp[2] = diff[2];
		LinAlgHelpers.add(vertices[0], temp, vertices[4]);
		LinAlgHelpers.add(vertices[1], temp, vertices[5]);
		LinAlgHelpers.add(vertices[3], temp, vertices[7]);
	}

	/** initializes equation for faces **/
	public void iniFaces() {
		faces = new ArrayList<>();
		// int i,j;
		// front
		faces.add(new Plane3D(vertices[3], vertices[0], vertices[1]));
		// bottom
		faces.add(new Plane3D(vertices[7], vertices[4], vertices[0]));
		// left
		faces.add(new Plane3D(vertices[0], vertices[4], vertices[5]));
		// back
		faces.add(new Plane3D(vertices[6], vertices[5], vertices[4]));
		// top
		faces.add(new Plane3D(vertices[2], vertices[1], vertices[5]));
		// right
		faces.add(new Plane3D(vertices[7], vertices[3], vertices[2]));

		checkFacesNormalsOrientation();
	}
	
	/** verifies that faces normales are looking inside the cuboid.
	 * if not, inverts them **/
	void checkFacesNormalsOrientation()
	{
		if(faces_init)
		{
			final double [] frontToBack = new double [3];
			LinAlgHelpers.subtract(  vertices[6],  vertices[0], frontToBack );
			LinAlgHelpers.normalize( frontToBack );
			double direction = LinAlgHelpers.dot( frontToBack, faces.get( 0 ).n );
			//invert faces normales so they look "inside" the shape
			if(direction < 0)
			{
				for(int i = 0; i< 6; i++)
				{
					for(int d = 0; d < 3; d++)
					{
						faces.get( i ).n[d] *= -1.0;
					}
				}
			}
		}
	}

	/**
	 * Verifies if point is inside the bounding box of cuboid. Faster version. For
	 * proper non-rectangular shape boundaries' check use slower
	 * isPointInsideShape
	 **/
	public boolean isPointInsideMinMax(final double[] pPoint) {
		for (int i = 0; i < 3; i++) {
			if ((pPoint[i] < dMinMax[0][i]) || (pPoint[i] > dMinMax[1][i])) return false;
		}
		return true;
	}

	/**
	 * Verifies if point is inside the bounding box of cuboid. Faster version. For
	 * proper non-rectangular shape boundaries' check use slower
	 * isPointInsideShape
	 **/
	public boolean isPointInsideMinMax(final RealPoint pPoint) {
		for (int i = 0; i < 3; i++) {
			if ((pPoint.getDoublePosition(i) < dMinMax[0][i]) || (pPoint.getDoublePosition(i) > dMinMax[1][i])) return false;
		}
		return true;
	}

	/**
	 * Verifies if point is inside the bounding box of cuboid. Slower version, for
	 * quick check of minmax bounding box boundaries use faster
	 * isPointInsideMinMax
	 **/
	public boolean isPointInsideShape(final double[] pPoint) {
		final double[] diff = new double[3];
		int i;
		if (!faces_init) iniFaces();

		for (i = 0; i < 6; i++) 
		{
			LinAlgHelpers.subtract(pPoint, faces.get(i).p0, diff);
			// because how the faces normals are constructed
			// (facing "inside" of the cuboid)
			if (LinAlgHelpers.dot(diff, faces.get(i).n) < -0.00000001)																														// calculation
			{
				return false;
			}
		}
		return true;
	}
}
