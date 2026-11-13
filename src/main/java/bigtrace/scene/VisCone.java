package bigtrace.scene;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.imglib2.mesh.impl.nio.BufferMesh;
import net.imglib2.util.LinAlgHelpers;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import bigtrace.BigTraceData;
import bigtrace.geometry.Pipe3D;
import bigtrace.rois.Roi3D;
import bvvpg.core.backend.jogl.JoglGpuContext;
import bvvpg.core.shadergen.DefaultShader;
import bvvpg.core.shadergen.Shader;
import bvvpg.core.shadergen.generate.Segment;
import bvvpg.core.shadergen.generate.SegmentTemplate;

public class VisCone
{
	private final Shader progMesh;
	
	/** VBOs for mesh surface render **/
	private int [] vbosMesh;
	
	/** vertex arrays for mesh surface**/
	private int [] vaosMesh;
	
	private boolean bBuffersMeshGenerated = false;
	
	private Vector4f l_color;	
	
	private boolean initialized;
	
	private BufferMesh mesh = null;
	
	final BigTraceData<?> btdata;
	
	public int renderType = Roi3D.SURFACE;
	
	private long nMeshTrianglesSize = 0;
	
	volatile boolean bLocked = false;
	
	double []  vDirection;
	
	double [] vPosition;
	
	double fConeAngle;
	
	public boolean bVisible = true;
	
	public int nCircleRigdes = 20;
	
	public int surfaceRender = BigTraceData.SURFACE_SHADE;
	
	public VisCone(final BigTraceData<?> btdata_)
	{
		
		btdata = btdata_;
				
		final Segment meshVp = new SegmentTemplate( VisWireMesh.class, "/bt_scene/mesh.vp" ).instantiate();
		final Segment meshFp = new SegmentTemplate( VisWireMesh.class, "/bt_scene/cone.fp" ).instantiate();
		progMesh = new DefaultShader( meshVp.getCode(), meshFp.getCode() );
	}
	
	public VisCone(final BigTraceData<?> btdata_, final double [] vDirection_,  final double [] vPosition_, final double fConeAngle_, final Color color_in)
	{
		this(btdata_);
		
		l_color = new Vector4f(color_in.getComponents(null));
		setVertices(vDirection_, vPosition_, fConeAngle_);		
	}
	
	public void setColor(Color color_in)
	{
		l_color = new Vector4f(color_in.getComponents(null));
	}
	
	public void setRenderType(int nRenderType_)
	{
		renderType = nRenderType_;		
	}
	
	public int getRenderType()
	{
		return renderType;		
	}
	
	public boolean isTransparent()
	{
		if(surfaceRender == BigTraceData.SURFACE_SILHOUETTE)
			return true;
		return false;
	}
	
	/** vectors are provided in space, scaled coordinates 
	 * (and transformed to raw voxel coordinates for rendering) **/
	public void setVertices(final double [] vDirection_, final double [] vPosition_, final double fConeAngle_)
	{

		fConeAngle = fConeAngle_;
		vDirection = new double [3];
		vPosition = new double [3];
		for(int d = 0; d < 3; d++)
		{
			vDirection[d] = vDirection_[d];
			vPosition[d] = vPosition_[d];			
		}

		
		int nVerticesN = nCircleRigdes + 1;
		nMeshTrianglesSize = nCircleRigdes;
		final BufferMesh meshOut = new BufferMesh( nVerticesN, (int)nMeshTrianglesSize , true );
		
		//cone math
		final double[][] frame = Pipe3D.getInitialFrame( vDirection ); 
		final double dirLen = LinAlgHelpers.length( vDirection );
		final double r = dirLen * Math.tan( fConeAngle / 2 );
		final double dAngleStep = 2.0 * Math.PI / nCircleRigdes;

		///// VERTICES 
		// apex
		final double[] vert = Roi3D.scaleGlobInv( vPosition, btdata.globCal );
		meshOut.vertices().add( vert[0], vert[1], vert[2] );

		//base ring
		for ( int i = 0; i < nCircleRigdes; i++ )
		{
		    final double c = Math.cos( dAngleStep * i ) * r;
		    final double s = Math.sin( dAngleStep * i ) * r;
		    for ( int d = 0; d < 3; d++ )
		        vert[d] = vPosition[d] + vDirection[d] + c * frame[0][d] + s * frame[1][d];
		    Roi3D.scaleGlobInvInPlace( vert, btdata.globCal );
		    meshOut.vertices().add( vert[0], vert[1], vert[2] );
		}
		
		///// TRIANGLES
		for(int i = 0; i < nCircleRigdes; i++)
		{
			meshOut.triangles().add(0, ( i + 1 ) % nCircleRigdes + 1, i + 1  );
		}
		
		this.mesh = new BufferMesh( nVerticesN, (int) nMeshTrianglesSize, true );
		MeshProcessing.calculateNormals( meshOut, mesh );
		//upload the new vertices on the next draw (buffers are reused)
		initialized = false;
	}
	
	
	
	private boolean init( final GL3 gl )
	{
		bLocked  = true;
		return initGPUBufferMesh(gl);	
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

		if(bVisible)
		{			
			final JoglGpuContext context = JoglGpuContext.get( gl );
			
			if(renderType == Roi3D.SURFACE)
			{
				final Matrix4f itvm = vm.invert( new Matrix4f() ).transpose();

				progMesh.getUniformMatrix4f( "pvm" ).set( pvm );
				progMesh.getUniformMatrix4f( "vm" ).set( vm );
				progMesh.getUniformMatrix3f( "itvm" ).set( itvm.get3x3( new Matrix3f() ) );
				progMesh.getUniform4f("colorin").set(l_color);
				progMesh.getUniform1i("surfaceRender").set(surfaceRender);
				progMesh.getUniform1i("clipactive").set(0);
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
		}
	}	

}
