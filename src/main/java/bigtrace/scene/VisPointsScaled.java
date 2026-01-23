package bigtrace.scene;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import bigtrace.BigTraceData;
import bigtrace.rois.Roi3D;
import net.imglib2.RealPoint;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import bvvpg.core.backend.jogl.JoglGpuContext;
import bvvpg.core.shadergen.DefaultShader;
import bvvpg.core.shadergen.Shader;
import bvvpg.core.shadergen.generate.Segment;
import bvvpg.core.shadergen.generate.SegmentTemplate;


import static com.jogamp.opengl.GL.GL_FLOAT;


public class VisPointsScaled
{

	private final Shader progRound;
	
	private final Shader progSquare;
	
	/** VBOs **/
	private int [] vbos;
	
	/** vertex arrays **/
	private int [] vaos;
	
	private boolean bBuffersGenerated = false;
	
	private Vector4f l_color;
	
	private float fPointSize;
	
	public int renderType = Roi3D.WIRE;
	
	float vertices[]; 
	
	private int nPointsN;
	
	private boolean initialized;

	public VisPointsScaled()
	{
		final Segment pointVp = new SegmentTemplate( VisPointsScaled.class, "/bt_scene/scaled_point.vp" ).instantiate();
		final Segment pointVpS = new SegmentTemplate( VisPointsScaled.class, "/bt_scene/scaled_point.vp" ).instantiate();
		final Segment pointFpRound = new SegmentTemplate( VisPointsScaled.class, "/bt_scene/scaled_round_point.fp" ).instantiate();		
		final Segment pointFpSquare = new SegmentTemplate( VisPointsScaled.class, "/bt_scene/scaled_square_point.fp" ).instantiate();
		progRound = new DefaultShader( pointVp.getCode(), pointFpRound.getCode() );
		progSquare = new DefaultShader( pointVpS.getCode(), pointFpSquare.getCode() );
	
	}
	/** constructor with one point **/
	public VisPointsScaled(final RealPoint point, final float fPointSize_,final Color color_in, final int nRenderType)
	{		
		this();
		
		fPointSize = fPointSize_;
		
		l_color = new Vector4f(color_in.getComponents(null));
		
		nPointsN = 1;
		
		renderType = nRenderType;
		
		vertices = new float [nPointsN * 3];//assume 3D
		
		point.localize(vertices);	

	}
	
	/** constructor with multiple vertices **/
	public VisPointsScaled(final ArrayList< RealPoint > points, final float fPointSize_,final Color color_in ,final int nRenderType)
	{
		this();
		
		fPointSize = fPointSize_;
		
		l_color = new Vector4f(color_in.getComponents(null));
		
		nPointsN = points.size();
		
		renderType = nRenderType;
		
		vertices = new float [nPointsN*3];//assume 3D

		for (int i = 0; i < nPointsN; i++)
		{
			for (int j = 0; j < 3; j++)
			{				
				vertices[i*3 + j] = points.get(i).getFloatPosition(j);
			}			
		}

	}
	
	public void setVertices( ArrayList< RealPoint > points)
	{
		int i,j;	
		
		nPointsN = points.size();
		if(nPointsN == 1)
			vertices = new float [nPointsN * 3]; //assume 3D
		else
			vertices = new float [(nPointsN + 1) * 3]; //assume 3D

		
		for (i = 0; i < nPointsN; i++)
		{
			for (j = 0; j < 3; j++)
			{
				vertices[ i*3 + j ]=points.get(i).getFloatPosition(j);
			}			
		}
		if(nPointsN > 1)
		{
			i = nPointsN - 1;
			for (j = 0; j < 3; j++)
			{
				vertices[ (i+1)*3 + j ]=points.get(i).getFloatPosition(j);
			}			
		}
		
		initialized = false;
	}
	
	public void setColor(Color pointColor) 
	{		
		l_color = new Vector4f(pointColor.getComponents(null));		
	}
	
	public void setSize(float fPointSize_)
	{
		fPointSize= fPointSize_;
	}
	
	public void setRenderType(int nRenderType_)
	{
		renderType = nRenderType_;		
	}
	
	private void generateBuffers(final GL3 gl )
	{
		vbos = new int[ 1 ];
		vaos = new int[ 1 ];
		
		gl.glGenBuffers( 1, vbos, 0 );
		vaos[0] = vbos[0];
		gl.glGenVertexArrays( 1, vaos, 0 );
		
		//this can be done once
		
		gl.glBindVertexArray( vaos[ 0 ] );
	
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbos[0] );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glBindVertexArray( 0 );
		
		bBuffersGenerated = true;
	}

	private void init( GL3 gl )
	{		
		// ..:: VERTEX BUFFERS & ARRAY OBJECTS ::..

		if(!bBuffersGenerated)
		{
			generateBuffers( gl );
		}	

		//upload data to GPU
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbos[0] );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		initialized = true;

	}

	public void draw(final GL3 gl, final Matrix4fc pvm, final int [] screen_size, final BigTraceData<?> btdata )
	{
		
		if (fPointSize < 0.0001)
			return;
		if ( !initialized )
			init( gl );
		
		
		Vector2f window_sizef;
		Vector2f ellipse_axes;

		JoglGpuContext context = JoglGpuContext.get( gl );
		
		//scale disk with viewport transform
		window_sizef =  new Vector2f (screen_size[0], screen_size[1]);
		
		//The whole story behind the code below is that
		//the size of the OpenGL sprite corresponding to a point is
		//changing depending on the actual window size and the render window size parameters.
		//Basically it scales with coefficient screen_size[0]/renderParams.nRenderW (in each dimension).
		//To compensate for that, we have to enlarge (shrink) effective point size
		//(it is done in the vertex shader, we enabled gl.glEnable(GL3.GL_PROGRAM_POINT_SIZE))
		//and then render the point as nice circle by painting it as an ellipse (in the fragment shader)
		//that will scale into the circle %)
		//
		
		ellipse_axes = new Vector2f((float)screen_size[0]/(float)btdata.renderParams.renderWidth, (float)screen_size[1]/(float)btdata.renderParams.renderHeight);
		//scale of viewport vs render
		//we enlarge/shrink to minimum dimension scale
		//and in the ellipse the other dimension will be cropped
		//(maybe this part can be moved to GPU? seems not critical right now)
		float fPointScale = Math.min(ellipse_axes.x,ellipse_axes.y);
		ellipse_axes.mul(1.0f/fPointScale);
		//actually it is not true ellipse axes,
		//but rather inverse squared values
		ellipse_axes.x = ellipse_axes.x*ellipse_axes.x;
		ellipse_axes.y = ellipse_axes.y*ellipse_axes.y;
				
		//voxel size
		Vector3f globCalForw = new Vector3f((float)btdata.globCal[0], (float)btdata.globCal[1], (float)btdata.globCal[2]);

		progRound.getUniform1f( "pointSizeReal" ).set( (float) (fPointSize * btdata.dMinVoxelSize) );
		progRound.getUniform1f( "pointScale" ).set( fPointScale );
		progRound.getUniformMatrix4f( "pvm" ).set( pvm );
		progRound.getUniform3f( "voxelScale" ).set( globCalForw );
		progRound.getUniform4f("colorin").set(l_color);
		progRound.getUniform2f("windowSize").set(window_sizef);
		progRound.getUniform2f("ellipseAxes").set(ellipse_axes);
		progRound.getUniform1i("renderType").set(renderType);
		progRound.getUniform1i("clipactive").set(btdata.nClipROI);
		progRound.getUniform3f("clipmin").set(new Vector3f(btdata.nDimCurr[0][0], btdata.nDimCurr[0][1], btdata.nDimCurr[0][2]));
		progRound.getUniform3f("clipmax").set(new Vector3f(btdata.nDimCurr[1][0], btdata.nDimCurr[1][1], btdata.nDimCurr[1][2]));
		progRound.setUniforms( context );
		
		progSquare.getUniform1f( "pointSizeReal" ).set( (float) (1.25 * fPointSize * btdata.dMinVoxelSize) );
		progSquare.getUniform1f( "pointScale" ).set( fPointScale );
		progSquare.getUniformMatrix4f( "pvm" ).set( pvm );
		progSquare.getUniform3f( "voxelScale" ).set( globCalForw );
		progSquare.getUniform4f("colorin").set(l_color);
		progSquare.getUniform2f("windowSize").set(window_sizef);
		progSquare.getUniform2f("ellipseAxes").set(ellipse_axes);
		progSquare.getUniform1i("renderType").set(Roi3D.WIRE);
		progSquare.getUniform1i("clipactive").set(btdata.nClipROI);
		progSquare.getUniform3f("clipmin").set(new Vector3f(btdata.nDimCurr[0][0],btdata.nDimCurr[0][1],btdata.nDimCurr[0][2]));
		progSquare.getUniform3f("clipmax").set(new Vector3f(btdata.nDimCurr[1][0],btdata.nDimCurr[1][1],btdata.nDimCurr[1][2]));
		progSquare.setUniforms( context );

		progRound.use( context );

		gl.glBindVertexArray( vaos[ 0 ] );
		
		if(nPointsN == 1)
		{
			gl.glDrawArrays( GL.GL_POINTS, 0, nPointsN);
		}
		else
		{
			gl.glDrawArrays( GL.GL_POINTS, 0, nPointsN);
			progSquare.use( context );
			gl.glDrawArrays( GL.GL_POINTS, nPointsN-1, 1);
		}
		gl.glBindVertexArray( 0 );
	}

}
