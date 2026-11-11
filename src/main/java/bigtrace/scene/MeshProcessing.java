package bigtrace.scene;

import java.util.HashMap;
import java.util.Map;

import net.imglib2.mesh.Triangle;
import net.imglib2.mesh.Vertex;

public class MeshProcessing
{
	/**
	 * Calculates the normals for a mesh. Creates a new mesh with the calculated
	 * normals. Assumes CCW winding order.
	 *
	 * @param src
	 *            Source mesh, used for vertex and triangle info
	 * @param dest
	 *            Destination mesh, will be populated with src's info plus the
	 *            calculated normals
	 */
	public static void calculateNormals( final net.imglib2.mesh.Mesh src, final net.imglib2.mesh.Mesh dest )
	{

		// Store the triangle normals
		final HashMap< Long, float[] > triNormals = new HashMap<>(src.triangles().size());
		// Store per vertex normals
		final HashMap< Long, float[] > vNormals = new HashMap<>(src.vertices().size());
		//vertex cumulative normal
		float[] cumNormal;
		//vertex index
		final long [] idxTr = new long [ 3 ];
		
		//create empty cumulative normals array
		final float [][] vNormalsArrays = new float [ src.vertices().size() ] [ 3 ];
		int nVertexCount = 0;
		
		for ( final Vertex v : src.vertices() )
		{
			vNormals.put( v.index(), vNormalsArrays[ nVertexCount ] );
			nVertexCount++;
		}
		
		for ( final Triangle tri : src.triangles() )
		{
			idxTr[ 0 ] = tri.vertex0();
			idxTr[ 1 ] = tri.vertex1();
			idxTr[ 2 ] = tri.vertex2();

			final float v0x = src.vertices().xf( idxTr[ 0 ] );
			final float v0y = src.vertices().yf( idxTr[ 0 ] );
			final float v0z = src.vertices().zf( idxTr[ 0 ] );
			final float v1x = src.vertices().xf( idxTr[ 1 ] );
			final float v1y = src.vertices().yf( idxTr[ 1 ] );
			final float v1z = src.vertices().zf( idxTr[ 1 ] );
			final float v2x = src.vertices().xf( idxTr[ 2 ] );
			final float v2y = src.vertices().yf( idxTr[ 2 ] );
			final float v2z = src.vertices().zf( idxTr[ 2 ] );

			final float v10x = v1x - v0x;
			final float v10y = v1y - v0y;
			final float v10z = v1z - v0z;

			final float v20x = v2x - v0x;
			final float v20y = v2y - v0y;
			final float v20z = v2z - v0z;

			final float nx = v10y * v20z - v10z * v20y;
			final float ny = v10z * v20x - v10x * v20z;
			final float nz = v10x * v20y - v10y * v20x;
			
			//cumulative, triangle area weighted normals per vertex.
			for ( final long idx : idxTr )
			{
				cumNormal = vNormals.get( idx );
				cumNormal[ 0 ] += nx;
				cumNormal[ 1 ] += ny;
				cumNormal[ 2 ] += nz;
			}
			
			final float nmag = ( float ) Math.sqrt( nx * nx + ny * ny + nz * nz );
			triNormals.put( tri.index(), new float[] { nx / nmag, ny / nmag, nz / nmag } );
		}

		// Now populate dest
		final Map< Long, Long > vIndexMap = new HashMap<>();
		float[] vNormal;
		double vNormalMag;
		// Copy the vertices, keeping track when indices change.
		for ( final Vertex v : src.vertices() )
		{
			final long srcIndex = v.index();
			vNormal = vNormals.get( v.index() );
			vNormalMag = Math.sqrt( vNormal[ 0 ] * vNormal[ 0 ] + vNormal[ 1 ] * vNormal[ 1 ] + vNormal[ 2 ] * vNormal[ 2 ] );
			final long destIndex = dest.vertices().add( //
					v.x(), v.y(), v.z(), //
					vNormal[ 0 ] / vNormalMag, vNormal[ 1 ] / vNormalMag, vNormal[ 2 ] / vNormalMag, //
					v.u(), v.v() );
			if ( srcIndex != destIndex )
			{
				/*
				 * NB: If the destination vertex index matches the source, we
				 * skip recording the entry, to save space in the map. Later, we
				 * leave indexes unchanged which are absent from the map.
				 * 
				 * This scenario is actually quite common, because vertices are
				 * often numbered in natural order, with the first vertex having
				 * index 0, the second having index 1, etc., although it is not
				 * guaranteed.
				 */
				vIndexMap.put( srcIndex, destIndex );
			}
		}
		// Copy the triangles, taking care to use destination indices.
		//triangle normalized normal
		float[] triNormal;
		for ( final Triangle tri : src.triangles() )
		{
			final long v0src = tri.vertex0();
			final long v1src = tri.vertex1();
			final long v2src = tri.vertex2();
			final long v0 = vIndexMap.getOrDefault( v0src, v0src );
			final long v1 = vIndexMap.getOrDefault( v1src, v1src );
			final long v2 = vIndexMap.getOrDefault( v2src, v2src );
			triNormal = triNormals.get( tri.index() );
			dest.triangles().add( v0, v1, v2, triNormal[ 0 ], triNormal[ 1 ], triNormal[ 2 ] );
		}
	}
}
