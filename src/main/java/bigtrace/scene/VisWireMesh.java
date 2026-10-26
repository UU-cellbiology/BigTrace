package bigtrace.scene;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import bigtrace.BigTraceData;
import bigtrace.geometry.Pipe3D;
import bigtrace.rois.Roi3D;

import bvvpg.core.backend.jogl.JoglGpuContext;
import bvvpg.core.shadergen.DefaultShader;
import bvvpg.core.shadergen.Shader;
import bvvpg.core.shadergen.generate.Segment;
import bvvpg.core.shadergen.generate.SegmentTemplate;

import net.imglib2.RealPoint;
import net.imglib2.mesh.impl.nio.BufferMesh;

public class VisWireMesh {
	
	private final Shader progLine;
	
	private final Shader progMesh;

	/** VBOs for mesh surface render **/
	private int [] vbosWire;
	
	/** vertex arrays for mesh surface**/
	private int [] vaosWire;
	
	private boolean bBuffersWireGenerated = false;
	
	/** VBOs for mesh surface render **/
	private int [] vbosMesh;
	
	/** vertex arrays for mesh surface**/
	private int [] vaosMesh;
	
	private boolean bBuffersMeshGenerated = false;
		
	private float vertices[]; 
	
	public int nPointsN = 0;

	public int renderType = Roi3D.WIRE;
	
	private Vector4f l_color;	
	
	public float fLineThickness;
	
	public final float fWireLineThickness = 1.0f;

	private boolean initialized;
	
	private BufferMesh mesh = null;
	
	private long nMeshTrianglesSize = 0;
	
	volatile boolean bLocked = false;
	
	/** anti-aliased centerline **/
	final VisPolyLineAA centerLine = new VisPolyLineAA ();
	
	/** anti-aliased wire mesh **/
	final ArrayList<VisPolyLineAA> wireLine = new ArrayList<>();
	
	final BigTraceData<?> btdata;
	

	public VisWireMesh(final BigTraceData<?> btdata_)
	{
		
		btdata = btdata_;
		final Segment lineVp = new SegmentTemplate( VisWireMesh.class, "/bt_scene/simple_color_clip.vp" ).instantiate();
		final Segment lineFp = new SegmentTemplate( VisWireMesh.class, "/bt_scene/simple_color_clip.fp" ).instantiate();		
		progLine = new DefaultShader( lineVp.getCode(), lineFp.getCode() );
				
		final Segment meshVp = new SegmentTemplate( VisWireMesh.class, "/bt_scene/mesh.vp" ).instantiate();
		final Segment meshFp = new SegmentTemplate( VisWireMesh.class, "/bt_scene/mesh.fp" ).instantiate();
		progMesh = new DefaultShader( meshVp.getCode(), meshFp.getCode() );
	}
	
	public VisWireMesh(final BigTraceData<?> btdata_, final ArrayList< RealPoint > points, final ArrayList< double [] > tangents, final float fLineThickness_, final Color color_in, final int nRenderType)
	{
		this(btdata_);
		
		fLineThickness= fLineThickness_;	
		l_color = new Vector4f(color_in.getComponents(null));		
		renderType = nRenderType;
		setVertices(points, tangents);
		
	}
	
	public void setThickness(float fLineThickness_)
	{
		fLineThickness = fLineThickness_;
		centerLine.setThickness( fLineThickness );
	}
	
	public void setColor(Color color_in)
	{
		l_color = new Vector4f(color_in.getComponents(null));
		centerLine.setColor( l_color );
		for (VisPolyLineAA segment : wireLine)
			segment.setColor( l_color ); 
	}
	
	public void setRenderType(int nRenderType_)
	{
		renderType = nRenderType_;		
	}
	
	public int getRenderType()
	{
		return renderType;		
	}
	
	public synchronized void setVertices(final ArrayList< RealPoint > points_, final ArrayList<double[]> tangents_)
	{
	
		while (bLocked)
		{
			try
			{
				Thread.sleep( 10 );
			}
			catch ( InterruptedException exc )
			{
				exc.printStackTrace();
			}
		}
		bLocked  = true;
		if(renderType == Roi3D.OUTLINE)
		{
			setVerticesCenterLine(Roi3D.scaleGlobInv(points_, btdata.globCal));
			//wireLine.clear();
		}
		else
		{
			
			//build a pipe in scaled space
			ArrayList<ArrayList< RealPoint >> point_contours = Pipe3D.getCountours(points_, tangents_, btdata.sectorN, 0.5 * fLineThickness * btdata.dMinVoxelSize, btdata.rotationMinFrame);
			//return to voxel space	for the render		
			for(int i = 0; i < point_contours.size(); i++)
			{
				point_contours.set(i, Roi3D.scaleGlobInv(point_contours.get(i), btdata.globCal));
			}
				
			if(renderType == Roi3D.WIRE)
			{
				setVerticesWire(point_contours);
			}
			else
			//(renderType == Roi3D.SURFACE)
			{
					//wireLine.clear();
					//initMesh(point_contours);
					nMeshTrianglesSize = 0;
					initMeshOpenEnds(point_contours);
					//mesh = initClosedVolumeMesh(point_contours,  Roi3D.scaleGlobInv(points_, btdata.globCal));
					nMeshTrianglesSize = mesh.triangles().size();
					nPointsN = point_contours.size();
			}
		}
		
		initialized = false;
		bLocked  = false;
		
	}	
	
	/** simple central polyline, not cylindrical **/
	public void setVerticesCenterLine(final ArrayList< RealPoint > points)
	{		
		
		nPointsN = points.size();
		if(btdata.wireAntiAliasing)
		{
			centerLine.setThickness( fLineThickness );
			centerLine.setColor( l_color );
			centerLine.setVertices( points );
			centerLine.bIncludeClip = true;
		}
		else
		{
			vertices = new float [nPointsN*3];//assume 3D	

			for (int i = 0; i < nPointsN; i++)
			{
				for (int d = 0; d < 3; d++)
				{
					vertices[i * 3 + d] = points.get(i).getFloatPosition(d);
				}			
			}
		}
	}

	
	/** generates a wireframe mesh of a pipe around provided points **/
	public void setVerticesWire( final ArrayList<ArrayList< RealPoint >> allContours)
	{
		if(btdata.wireAntiAliasing)
		{
			setVerticesWireAA(allContours);
		}
		else
		{
			setVerticesWireSharp(allContours);
		}
	}
	
	/** generates a wireframe mesh of a pipe around provided points **/
	public void setVerticesWireAA( final ArrayList<ArrayList< RealPoint >> allContours)
	{

		wireLine.clear();
		final int nSectorN = btdata.sectorN;
		nPointsN = allContours.size();
		if(nPointsN > 1)
		{
			int iPoint;
			for (iPoint = 0; iPoint < nPointsN; iPoint += btdata.wireCountourStep)
			{
				
				ArrayList<RealPoint> contour_arr = new ArrayList<>();

				for (int i = 0; i < nSectorN; i++)
				{
					contour_arr.add( allContours.get(iPoint).get(i));			
				}
				contour_arr.add( allContours.get(iPoint).get(0));
				VisPolyLineAA contour = new VisPolyLineAA(contour_arr,fWireLineThickness, l_color) ;
				contour.bIncludeClip = true;
				wireLine.add( contour );
				
			}
			
			//the last contour
			if((iPoint - btdata.wireCountourStep) != (nPointsN - 1))
			{
				iPoint = nPointsN - 1;
				
				ArrayList<RealPoint> contour_arr = new  ArrayList<>();
				for (int i = 0; i < nSectorN; i++)
				{
					contour_arr.add( allContours.get(iPoint).get(i));
				}
				contour_arr.add( allContours.get(iPoint).get(0));
				VisPolyLineAA contour = new VisPolyLineAA(contour_arr, fWireLineThickness, l_color) ;
				contour.bIncludeClip = true;
				wireLine.add( contour );
			}
			
			for (int i = 0; i < nSectorN; i++)
			{
				ArrayList<RealPoint> line_arr = new  ArrayList<>();
				for(int j = 0; j < nPointsN; j++)
				{
					line_arr.add( allContours.get(j).get(i));
				}
				VisPolyLineAA line = new VisPolyLineAA(line_arr, fWireLineThickness, l_color) ;
				line.bIncludeClip = true;
				wireLine.add( line );
			}
			
		}
	}
	
	public void setVerticesWireSharp( final ArrayList<ArrayList< RealPoint >> allContours)
	{
		int vertShift;

		final int nSectorN = btdata.sectorN;
		nPointsN = allContours.size();
		if(nPointsN > 1)
		{
			//all vertices
			vertices = new float [2*nSectorN*3*nPointsN];
			for (int iPoint = 0; iPoint < nPointsN; iPoint++)
			{
				//drawing contours around each point
				vertShift = iPoint*nSectorN*3;
				for (int i = 0; i < nSectorN; i++)
				{
					for (int j = 0; j < 3; j++)
					{
						vertices[vertShift+i*3+j] = allContours.get(iPoint).get(i).getFloatPosition(j);
					}				
				}
			}
			final int nShift = nSectorN*3*nPointsN;
			for (int i = 0; i < nSectorN; i++)
				for(int j = 0; j < nPointsN; j++)
					for(int k = 0; k < 3; k++)
					{
						vertices[nShift+3*i*nPointsN+j*3+k] = vertices[i*3+3*j*nSectorN+k];
					}
		}
	}
	
	private boolean init( final GL3 gl )
	{
		bLocked  = true;
		if(renderType == Roi3D.SURFACE)
		{
			return initGPUBufferMesh(gl);	
		}
		
		if(!btdata.wireAntiAliasing)
		{
			initGPUBufferWire( gl );
		}
		else
		{
			initialized = true;
		}
		bLocked  = false;
		return true;
	}
	
	private void generateWireBuffers(final GL3 gl )
	{
		vbosWire = new int[ 1 ];
		vaosWire = new int[ 1 ];
		
		gl.glGenBuffers( 1, vbosWire, 0 );
		vaosWire[0] = vbosWire[0];
		gl.glGenVertexArrays( 1, vaosWire, 0 );
		
		//this can be done once
		
		gl.glBindVertexArray( vaosWire[ 0 ] );
	
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbosWire[0] );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glBindVertexArray( 0 );
		
		bBuffersWireGenerated = true;
	}
	
	private void initGPUBufferWire( final GL3 gl )
	{
		if(nPointsN > 1)
		{
			// ..:: VERTEX BUFFERS & ARRAY OBJECTS ::..
			if(!bBuffersWireGenerated)
			{
				generateWireBuffers( gl );
			}	
			//upload data to GPU
			gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbosWire[0] );
			gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
			gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );	
		}		
		initialized = true;
	}
	
	/** given contours coordinates, returns a mesh with open ends**/
	private void initMeshOpenEnds(final ArrayList<ArrayList< RealPoint >> allContours )
	{
	
		BufferMesh meshOut = null;
		
		final int nPointsCurveN = allContours.size();
		
		if(nPointsCurveN == 0)
		{
			mesh = null;
			return;
		}
		final int nSectorN = allContours.get( 0 ).size();
		
		final int nMeshTrianglesN = (nPointsCurveN - 1) * nSectorN * 2;
		final int nVerticesN = nPointsCurveN * nSectorN;
		meshOut = new BufferMesh( nVerticesN, nMeshTrianglesN , true );
		//add vertices
		for (int iPoint = 0; iPoint < nPointsCurveN; iPoint++)
		{
			for (int iS = 0; iS < nSectorN; iS++)
			{
				final RealPoint p = allContours.get(iPoint).get( iS );
				meshOut.vertices().add( p.getFloatPosition( 0 ), p.getFloatPosition( 1 ), p.getFloatPosition( 2 ));
			}
		}
		final long [] ind = new long [3];
		//add triangles
		for (int iPoint = 1; iPoint < nPointsCurveN; iPoint++)
		{
			for (int iS = 0; iS < nSectorN; iS++)
			{
				ind[0] = (iPoint-1) * nSectorN + iS;
				ind[1] = (iPoint-1) * nSectorN + (iS + 1) % nSectorN;
				ind[2] = (iPoint) * nSectorN + + iS;
				meshOut.triangles().add(ind[0], ind[1], ind[2]);

				ind[0] = (iPoint) * nSectorN + iS;
				ind[1] = (iPoint-1) * nSectorN + (iS + 1) % nSectorN;
				ind[2] = (iPoint) * nSectorN + (iS + 1) % nSectorN;
				meshOut.triangles().add(ind[0], ind[1], ind[2]);
			}
		}
		//calculate normals
		this.mesh = new BufferMesh( nVerticesN, nMeshTrianglesN, true );
		MeshProcessing.calculateNormals( meshOut, mesh );

	}
	
	/** given contours and centerline coordinates, returns a mesh with capped (closed) ends**/
	public static BufferMesh initClosedVolumeMesh(final ArrayList<ArrayList< RealPoint >> allContours, ArrayList< RealPoint > centerline )
	{
		BufferMesh meshOut = null;
		
		final int nPointsCurveN = allContours.size();
		
		if(nPointsCurveN == 0)
		{
			return meshOut;
		}
		final int nSectorN = allContours.get( 0 ).size();
		
		final int nMeshTrianglesN = (nPointsCurveN - 1) * nSectorN * 2 + nSectorN * 2;
		final int nVerticesN = nPointsCurveN * nSectorN + 2;
		meshOut = new BufferMesh( nVerticesN, nMeshTrianglesN , true );
		//add vertices
		for (int iPoint = 0; iPoint < nPointsCurveN; iPoint++)
		{
			for (int iS = 0; iS < nSectorN; iS++)
			{
				final RealPoint p = allContours.get(iPoint).get( iS );
				meshOut.vertices().add( p.getFloatPosition( 0 ), p.getFloatPosition( 1 ), p.getFloatPosition( 2 ));
			}
		}
		//add centerline vertex from the beginning
		meshOut.vertices().add(centerline.get(0).getFloatPosition(0), centerline.get(0).getFloatPosition(1), centerline.get(0).getFloatPosition(2));
		meshOut.vertices().add(centerline.get(nPointsCurveN-1).getFloatPosition(0), centerline.get(nPointsCurveN-1).getFloatPosition(1), centerline.get(nPointsCurveN-1).getFloatPosition(2));
		
		final long [] ind = new long [3];
		//add triangles
		for (int iPoint = 1; iPoint < nPointsCurveN; iPoint++)
		{
			for (int iS = 0; iS < nSectorN; iS++)
			{
				ind[0] = (iPoint-1) * nSectorN + iS;
				ind[1] = (iPoint-1) * nSectorN + (iS + 1) % nSectorN;
				ind[2] = (iPoint) * nSectorN + + iS;
				meshOut.triangles().add(ind[0], ind[1], ind[2]);

				ind[0] = (iPoint) * nSectorN + iS;
				ind[1] = (iPoint-1) * nSectorN + (iS + 1) % nSectorN;
				ind[2] = (iPoint) * nSectorN + (iS + 1) % nSectorN;
				meshOut.triangles().add(ind[0], ind[1], ind[2]);
			}
		}
		
		//"lids" of the mesh, beginning
		for (int iS = 0; iS < nSectorN; iS++)
		{
			//lid at the beginning
			ind[0] = (iS + 1) % nSectorN;
			ind[1] = iS;
			ind[2] = nPointsCurveN * nSectorN;
			meshOut.triangles().add(ind[0], ind[1], ind[2]);
			
			//lid at the end
			ind[0] = (nPointsCurveN - 1) * nSectorN + iS;
			ind[1] = (nPointsCurveN - 1) * nSectorN + (iS + 1) % nSectorN;
			ind[2] = nPointsCurveN * nSectorN + 1;
			meshOut.triangles().add(ind[0], ind[1], ind[2]);
		}
		return meshOut;
	}
	
	private void generateMeshBuffers(final GL3 gl )
	{
		vbosMesh = new int[ 3 ];
		vaosMesh = new int[ 3 ];
		
		gl.glGenBuffers( 3, vbosMesh, 0 );
		for(int i = 0; i < 3; i++)
			vaosMesh[i] = vbosMesh[i];
		gl.glGenVertexArrays( 1, vaosMesh, 0 );
		
		//this can be done once		
		gl.glBindVertexArray( vaosMesh[ 0 ] );
		
		for (int i = 0; i < 2; i++)
		{
			gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbosMesh[ i ] );
			gl.glVertexAttribPointer( i, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
			gl.glEnableVertexAttribArray( i );
		}
				
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, vbosMesh[ 2 ] );
		gl.glBindVertexArray( 0 );
		
		bBuffersMeshGenerated = true;
	}
	
	/** upload MeshData to GPU **/
	private boolean initGPUBufferMesh( GL3 gl )
	{
				
		if(mesh == null)
			return false;
		else
			if (mesh.vertices() == null)
				return false;
		
		if(!bBuffersMeshGenerated)
		{
			generateMeshBuffers(gl);
		}	
		
		//upload data to GPU
		final FloatBuffer vertBuff = mesh.vertices().verts();
		vertBuff.rewind();
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbosMesh[0] );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertBuff.limit() * Float.BYTES, vertBuff, GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		final FloatBuffer normals = mesh.vertices().normals();
		normals.rewind();
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbosMesh[1] );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, normals.limit() * Float.BYTES, normals, GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		final IntBuffer indices = mesh.triangles().indices();
		indices.rewind();
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, vbosMesh[2] );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.limit() * Integer.BYTES, indices, GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, 0 );
		
		initialized = true;
		
		return true; 
	}

	public void draw( final GL3 gl, final Matrix4fc pvm, final Matrix4fc vm, final BigTraceData<?> btdatain, final boolean bWeightedOIT )
	{
		
		while (bLocked)
		{
			try
			{
				Thread.sleep( 10 );
			}
			catch ( InterruptedException exc )
			{
				exc.printStackTrace();
			}
		}
		bLocked = true;
		if ( !initialized )
		{
			if(!init(gl))
			{
				bLocked = false;
				return;
			}
		}
		bLocked = false;

		if(nPointsN > 1)
		{			
			final JoglGpuContext context = JoglGpuContext.get( gl );
			
			if(renderType == Roi3D.SURFACE)
			{
				final Matrix4f itvm = vm.invert( new Matrix4f() ).transpose();

				progMesh.getUniformMatrix4f( "pvm" ).set( pvm );
				progMesh.getUniformMatrix4f( "vm" ).set( vm );
				progMesh.getUniformMatrix3f( "itvm" ).set( itvm.get3x3( new Matrix3f() ) );
				progMesh.getUniform4f("colorin").set(l_color);
				progMesh.getUniform1i("surfaceRender").set(btdatain.surfaceRender);
				progMesh.getUniform1i("clipactive").set(btdatain.nClipROI);
				progMesh.getUniform3f("clipmin").set(new Vector3f(btdatain.nDimCurr[0][0],btdatain.nDimCurr[0][1],btdatain.nDimCurr[0][2]));
				progMesh.getUniform3f("clipmax").set(new Vector3f(btdatain.nDimCurr[1][0],btdatain.nDimCurr[1][1],btdatain.nDimCurr[1][2]));
				progMesh.getUniform1i("silType").set(btdatain.silhouetteRender);
				progMesh.getUniform1f("silDecay").set((float)btdatain.silhouetteDecay);
				progMesh.getUniform1i("wOIT").set(bWeightedOIT?1:0);
				progMesh.setUniforms( context );
				progMesh.use( context );
				
				gl.glBindVertexArray( vaosMesh[0] );			
				gl.glDrawElements( GL_TRIANGLES, ( int ) nMeshTrianglesSize * 3, GL_UNSIGNED_INT, 0 );
				gl.glBindVertexArray( 0 );
			}
			else
			{
				if(btdatain.wireAntiAliasing)
				{				    
					if(renderType == Roi3D.OUTLINE)
					{
						centerLine.draw( gl, pvm, btdatain );
					}
					
					if(renderType == Roi3D.WIRE)
					{					
						for (int nS = 0; nS < wireLine.size(); nS++)
						{
							wireLine.get( nS ).draw( gl, pvm, btdatain );
						}
					}
				}
				/// no antialiasing
				else
				{
					progLine.getUniformMatrix4f( "pvm" ).set( pvm );
					progLine.getUniform4f("colorin").set(l_color);
					progLine.getUniform1i("clipactive").set(btdatain.nClipROI);
					progLine.getUniform3f("clipmin").set(new Vector3f(btdatain.nDimCurr[0][0], btdatain.nDimCurr[0][1], btdatain.nDimCurr[0][2]));
					progLine.getUniform3f("clipmax").set(new Vector3f(btdatain.nDimCurr[1][0], btdatain.nDimCurr[1][1], btdatain.nDimCurr[1][2]));
					progLine.getUniform1i("wOIT").set(bWeightedOIT?1:0);
					progLine.setUniforms( context );
					progLine.use( context );		
					gl.glBindVertexArray( vaosWire[ 0 ] );	
					if(renderType == Roi3D.OUTLINE)
					{
						gl.glLineWidth( fLineThickness );
						gl.glDrawArrays( GL.GL_LINE_STRIP, 0, nPointsN);
					}
					
					if(renderType == Roi3D.WIRE)
					{
						int nPointIt;
						final int nSectorN = btdatain.sectorN;
	
						gl.glLineWidth( 1.0f );
						
						//contours
						for(nPointIt = 0; nPointIt < nPointsN; nPointIt += btdatain.wireCountourStep)
						{
							gl.glDrawArrays( GL.GL_LINE_LOOP, nPointIt * nSectorN, nSectorN);
						}
						//the last contour
						if((nPointIt - btdatain.wireCountourStep) != (nPointsN-1))
						{
							gl.glDrawArrays( GL.GL_LINE_LOOP, (nPointsN - 1) * nSectorN, nSectorN);
						}
						//lines along the pipe
						int nShift = nSectorN * nPointsN;
						
						for(nPointIt = 0; nPointIt < nSectorN; nPointIt += 1)
						{
							gl.glDrawArrays( GL.GL_LINE_STRIP, nShift + nPointIt * nPointsN, nPointsN);
						}
					}
					gl.glBindVertexArray( 0 );	
				}				
			}
		}
	}	

}
