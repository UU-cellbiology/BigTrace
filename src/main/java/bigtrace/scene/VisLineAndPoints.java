package bigtrace.scene;

import com.jogamp.opengl.GL3;

import java.awt.Color;
import java.util.ArrayList;

import net.imglib2.RealPoint;

import org.joml.Matrix4fc;

import bigtrace.BigTraceData;
import bigtrace.rois.Roi3D;

public class VisLineAndPoints
{
	public final VisPointsScaled verticesVis;
	
	public final VisPolyLineAA lineVis;
	
	
	public VisLineAndPoints()
	{
		verticesVis = new VisPointsScaled();
		lineVis = new VisPolyLineAA();
		lineVis.bIncludeClip = false;
		verticesVis.setSize( 1.0f );
		verticesVis.setRenderType( Roi3D.SURFACE );
	}

	
	public void setVertices(final ArrayList< RealPoint > points)
	{
		lineVis.setVertices( points );
		verticesVis.setVertices( points );
	}
	public void draw(final GL3 gl, final Matrix4fc pvm, final int [] screen_size, final BigTraceData<?> btdata, final boolean bWeightedOIT )
	{
		lineVis.draw( gl, pvm, btdata );
		verticesVis.draw( gl, pvm, screen_size, btdata, bWeightedOIT );
	}
	
	public void setThickness(float fLineThickness_)
	{
		lineVis.setThickness( fLineThickness_ ); 
	}
	
	public void setLineColor(Color color_in)
	{
		lineVis.setColor( color_in );
	}
	
	public void setPointColor(Color color_in)
	{
		verticesVis.setColor( color_in );
	}

}
