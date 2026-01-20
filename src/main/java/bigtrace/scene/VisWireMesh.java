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
		final Segment lineVp = new SegmentTemplate( VisWireMesh.class, "/scene/simple_color_clip.vp" ).instantiate();
		final Segment lineFp = new SegmentTemplate( VisWireMesh.class, "/scene/simple_color_clip.fp" ).instantiate();		
		progLine = new DefaultShader( lineVp.getCode(), lineFp.getCode() );
				
		final Segment meshVp = new SegmentTemplate( VisWireMesh.class, "/scene/mesh.vp" ).instantiate();
		final Segment meshFp = new SegmentTemplate( VisWireMesh.class, "/scene/mesh.fp" ).instantiate();
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
					mesh = initMeshOpenEnds(point_contours);
					//mesh = initMeshCappedEnds(point_contours,  Roi3D.scaleGlobInv(points_, BigTraceData.globCal));
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

			for (int i=0; i<nPointsN; i++)
			{
				for (int d=0; d<3; d++)
				{
					vertices[i*3+d]=points.get(i).getFloatPosition(d);
				}			
			}
		}
	}
	
	/** generates triangulated surface mesh of a pipe around provided points **/
	public void setVerticesSurface(final ArrayList<ArrayList< RealPoint >> allContours)
	{
		int vertShift;
		int i;

		final int nSectorN = btdata.sectorN;
		nPointsN = allContours.size();
		if(nPointsN>1)
		{
			//all vertices
			vertices = new float [2*(nSectorN + 1)*3*nPointsN];
			
			for (int iPoint = 1; iPoint < nPointsN; iPoint++)
			{
				//add to drawing vertices triangles
				vertShift = (iPoint-1)*(nSectorN+1)*2*3;
				for (i = 0; i < nSectorN; i++)
				{
					for (int j = 0; j < 3; j++)
					{
						vertices[vertShift + i*6 + j]     = allContours.get(iPoint-1).get(i).getFloatPosition(j);
						vertices[vertShift + i*6 + j + 3] = allContours.get(iPoint).get(i).getFloatPosition(j);

					}				
				}
				
				//last one closing circles
				for (int j = 0; j < 3; j++)
				{
					vertices[vertShift + i*6 + j]     = allContours.get(iPoint-1).get(0).getFloatPosition(j);
					vertices[vertShift + i*6 + j + 3] = allContours.get(iPoint).get(0).getFloatPosition(j);

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
		int i,j, iPoint;
		wireLine.clear();
		final int nSectorN = btdata.sectorN;
		nPointsN = allContours.size();
		if(nPointsN > 1)
		{
			for (iPoint = 0; iPoint < nPointsN; iPoint += btdata.wireCountourStep)
			{
				
				ArrayList<RealPoint> contour_arr = new  ArrayList<>();

				for (i = 0; i < nSectorN; i++)
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
				iPoint = nPointsN-1;
				
				ArrayList<RealPoint> contour_arr = new  ArrayList<>();
				for (i = 0; i < nSectorN; i++)
				{
					contour_arr.add( allContours.get(iPoint).get(i));
				}
				contour_arr.add( allContours.get(iPoint).get(0));
				VisPolyLineAA contour = new VisPolyLineAA(contour_arr, fWireLineThickness, l_color) ;
				contour.bIncludeClip = true;
				wireLine.add( contour );
			}
			

			for (i = 0; i < nSectorN; i++)
			{
				ArrayList<RealPoint> line_arr = new  ArrayList<>();
				for(j = 0; j < nPointsN; j++)
				{
					line_arr.add( allContours.get(j).get(i));
				}
				VisPolyLineAA line = new VisPolyLineAA(line_arr, fWireLineThickness, l_color) ;
				line.bIncludeClip = true;
				wireLine.add( line );
			}
			
		}
	}
	
//	void addLineWireLine(final ArrayList<RealPoint> line_arr, final float fWireLineThickness, final Color l_color)
//	{
//		
//	}
	public void setVerticesWireSharp( final ArrayList<ArrayList< RealPoint >> allContours)
	{
		int i,j, iPoint;
		int vertShift;

		final int nSectorN = btdata.sectorN;
		nPointsN = allContours.size();
		if(nPointsN > 1)
		{
			//all vertices
			vertices = new float [2*nSectorN*3*nPointsN];
			for (iPoint = 0; iPoint < nPointsN; iPoint++)
			{
				//drawing contours around each point
				vertShift = iPoint*nSectorN*3;
				for (i = 0; i < nSectorN; i++)
				{
					for (j = 0; j < 3; j++)
					{
						vertices[vertShift+i*3+j]=allContours.get(iPoint).get(i).getFloatPosition(j);
					}				
				}
			}
			final int nShift = nSectorN*3*nPointsN;
			for (i = 0; i < nSectorN; i++)
				for(j = 0; j < nPointsN; j++)
					for(int k = 0; k < 3; k++)
					{
						vertices[nShift+3*i*nPointsN+j*3+k]=vertices[i*3+3*j*nSectorN+k];
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
	private BufferMesh initMeshOpenEnds(final ArrayList<ArrayList< RealPoint >> allContours )
	{
	
		BufferMesh meshOut = null;
		int i,j, iPoint;
		
		final int nPointsCurveN = allContours.size();
		if(nPointsCurveN == 0)
			return meshOut;
		
		final int nSectorN = allContours.get( 0 ).size();
		
		float [][] triangle = new float[3][3];
		int nMeshTrianglesN = (nPointsCurveN - 1) * nSectorN * 2;
		if(nPointsCurveN > 1)
		{
			//calculate total number of triangles
			meshOut = new BufferMesh( nMeshTrianglesN * 3, nMeshTrianglesN, true );
	
			//all vertices
			//vertices = new float [2*(nSectorN+1)*3*nPointsN];
			for (iPoint = 1; iPoint < nPointsCurveN; iPoint++)
			{
				//add to drawing vertices triangles
				//nmesh.triangles().addf(v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z)
				for (i = 0; i < nSectorN; i++)
				{
					for (j = 0; j < 3; j++)
					{						
						triangle[0][j] = allContours.get(iPoint-1).get(i).getFloatPosition(j);
						triangle[1][j] = allContours.get(iPoint-1).get((i+1) % nSectorN).getFloatPosition(j);
						triangle[2][j] = allContours.get(iPoint).get(i).getFloatPosition(j);						
					}						
					addTriangle(meshOut, triangle);
					
					for (j = 0; j < 3; j++)
					{						
						triangle[0][j] = allContours.get(iPoint).get(i).getFloatPosition(j);
						triangle[1][j] = allContours.get(iPoint-1).get((i+1)%nSectorN).getFloatPosition(j);
						triangle[2][j] = allContours.get(iPoint).get((i+1)%nSectorN).getFloatPosition(j);						
					}	
					addTriangle(meshOut, triangle);
				}
			}
		}
		return meshOut;
	}
	
	/** given contours and centerline coordinates, returns a mesh with capped (closed) ends**/
	public static BufferMesh initClosedVolumeMesh(final ArrayList<ArrayList< RealPoint >> allContours, ArrayList< RealPoint > centerline )
	{
	
		BufferMesh meshOut = null;
		int i, j, iPoint;
		
		final int nPointsCurveN = allContours.size();
		if(nPointsCurveN == 0)
			return meshOut;
		
		final int nSectorN = allContours.get( 0 ).size();
		
		float [][] triangle = new float[3][3];
		int nMeshTrianglesN = (nPointsCurveN-1) * nSectorN*2 + nSectorN*2;
		//int nMeshTrianglesN = (nPointsN-1)*nSectorN*2;
		if(nPointsCurveN > 1)
		{
			//calculate total number of triangles
			meshOut = new BufferMesh( nMeshTrianglesN*3, nMeshTrianglesN, true );
	
			//all vertices
			//vertices = new float [2*(nSectorN+1)*3*nPointsN];
			for (iPoint = 1; iPoint < nPointsCurveN; iPoint++)
			{
				//add to drawing vertices triangles
				//nmesh.triangles().addf(v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z)
				for (i = 0; i < nSectorN; i++)
				{
					for (j = 0; j < 3; j++)
					{						
						triangle[0][j] = allContours.get(iPoint-1).get(i).getFloatPosition(j);
						triangle[1][j] = allContours.get(iPoint-1).get((i+1)%nSectorN).getFloatPosition(j);
						triangle[2][j] = allContours.get(iPoint).get(i).getFloatPosition(j);						
					}						
					addTriangleWithoutNormale(meshOut, triangle);
					
					for (j = 0; j < 3; j++)
					{						
						triangle[0][j] = allContours.get(iPoint).get(i).getFloatPosition(j);
						triangle[1][j] = allContours.get(iPoint-1).get((i+1)%nSectorN).getFloatPosition(j);
						triangle[2][j] = allContours.get(iPoint).get((i+1)%nSectorN).getFloatPosition(j);						
					}	
					addTriangleWithoutNormale(meshOut, triangle);
				}

			}
			
			//"lids" of the mesh, beginning
			for (i = 0; i < nSectorN; i++)
			{
				for (j = 0; j < 3; j++)
				{					
					triangle[0][j] = allContours.get(0).get((i+1)%nSectorN).getFloatPosition(j);
					triangle[1][j] = allContours.get(0).get(i).getFloatPosition(j);
					triangle[2][j] = centerline.get(0).getFloatPosition(j);					
				}					
				addTriangleWithoutNormale(meshOut, triangle);
			}
			
			//"lids" of the mesh, end 
			for (i = 0; i < nSectorN; i++)
			{
				for (j = 0; j < 3; j++)
				{					
					triangle[0][j] = allContours.get(nPointsCurveN-1).get(i).getFloatPosition(j);
					triangle[1][j] = allContours.get(nPointsCurveN-1).get((i+1)%nSectorN).getFloatPosition(j);
					triangle[2][j] = centerline.get(nPointsCurveN-1).getFloatPosition(j);					
				}					
				addTriangleWithoutNormale(meshOut, triangle);
			}
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

	public void draw( final GL3 gl, final Matrix4fc pvm, Matrix4fc vm, final BigTraceData<?> btdatain )
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

		if(nPointsN>1)
		{
			
			JoglGpuContext context = JoglGpuContext.get( gl );
			
			gl.glDepthFunc( GL.GL_LESS);
			
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
				progMesh.setUniforms( context );
				progMesh.use( context );
				if(btdata.surfaceRender == BigTraceData.SURFACE_SILHOUETTE && btdatain.silhouetteRender == BigTraceData.silhouette_TRANSPARENT)
				{
					gl.glDepthFunc( GL.GL_ALWAYS);
				}

				//gl.glEnable(GL.GL_BLEND);
				//gl.glBlendFunc(GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA); 
				
				gl.glBindVertexArray( vaosMesh[0] );			
				gl.glDrawElements( GL_TRIANGLES, ( int ) nMeshTrianglesSize * 3, GL_UNSIGNED_INT, 0 );
				gl.glBindVertexArray( 0 );
			}
			else
			{
				if(btdatain.wireAntiAliasing)
				{
				    gl.glDepthFunc( GL.GL_ALWAYS);
				    
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
					progLine.getUniform3f("clipmin").set(new Vector3f(btdatain.nDimCurr[0][0],btdatain.nDimCurr[0][1],btdatain.nDimCurr[0][2]));
					progLine.getUniform3f("clipmax").set(new Vector3f(btdatain.nDimCurr[1][0],btdatain.nDimCurr[1][1],btdatain.nDimCurr[1][2]));
					progLine.setUniforms( context );
					progLine.use( context );		
					gl.glBindVertexArray( vaosWire[ 0 ] );	
					
					//			gl.glDepthFunc(GL3.GL_ALWAYS);
					if(renderType == Roi3D.OUTLINE)
					{
						gl.glLineWidth(fLineThickness);
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
							//gl.glDrawArrays( GL.GL_LINE_LOOP, nSectorN, nSectorN);
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
							//gl.glDrawArrays( GL.GL_LINE_LOOP, nSectorN, nSectorN);
						}
					}
					gl.glBindVertexArray( 0 );	
				}
				
			}
			gl.glDepthFunc( GL.GL_LESS);
		}
	}
	
	public static float[] getNormal(float [][] triangle)
	{
        final float v10x = triangle[1][0] - triangle[0][0];
        final float v10y = triangle[1][1] - triangle[0][1];
        final float v10z = triangle[1][2] - triangle[0][2];

        final float v20x = triangle[2][0] - triangle[0][0];
        final float v20y = triangle[2][1] - triangle[0][1];
        final float v20z = triangle[2][2] - triangle[0][2];

        final float nx = v10y * v20z - v10z * v20y;
        final float ny = v10z * v20x - v10x * v20z;
        final float nz = v10x * v20y - v10y * v20x;
        final float nmag = (float) Math.sqrt(Math.pow(nx, 2) + Math.pow(ny, 2) + Math.pow(nz, 2));

        return new float[]{nx / nmag, ny / nmag, nz / nmag};
	}
	
	public static float[][] getCumNormal(float [] normale)
	{
		float [][] out = new float [3][3];
		for (int i = 0; i < 3; i++)
		{
			for(int d = 0; d < 3; d++)
			{
				out[i][d] = ( i + 1 ) * normale[d]; 
			}
		}
		return out;
	}
	
	public static void addTriangle(final BufferMesh mesh_in, final float[][] triangle)
	{
		final float [] normale = getNormal(triangle);
		final float [][] cumNormal = getCumNormal(normale);
		long [] index = new long[3];
		double vNormalMag ;
		for (int i = 0; i < 3; i++)
		{
			vNormalMag =  Math.sqrt(Math.pow(cumNormal[i][0], 2) + Math.pow(cumNormal[i][1], 2) + Math.pow(cumNormal[i][2], 2));
			index[i] = mesh_in.vertices().add(triangle[i][0],triangle[i][1],triangle[i][2],
					cumNormal[i][0] / vNormalMag, cumNormal[i][1] / vNormalMag, cumNormal[i][2] / vNormalMag,0.0,0.0);
		}
		mesh_in.triangles().add(index[0], index[1], index[2], normale[0], normale[1], normale[2]);
	}
	
	public static void addTriangleWithoutNormale(final BufferMesh mesh_in, final float[][] triangle)
	{
		long [] index = new long[3];
		for (int i=0;i<3;i++)
		{			
			index[i] = mesh_in.vertices().add(triangle[i][0],triangle[i][1],triangle[i][2]);
		}
		mesh_in.triangles().add(index[0], index[1], index[2]);

	}
}
