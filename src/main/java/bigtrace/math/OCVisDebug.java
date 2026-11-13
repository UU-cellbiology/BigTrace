package bigtrace.math;

import com.jogamp.opengl.GL3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.RealPoint;

import org.joml.Matrix4fc;

import bigtrace.BigTrace;
import bigtrace.BigTraceData;
import bigtrace.scene.VisCone;
import bigtrace.scene.VisLineAndPoints;

public class OCVisDebug
{
	final BigTrace<?> bt;
	//cones for visualization
	final public List<VisCone> cones = new ArrayList<>();
	//lines for visualization
	final public List<VisLineAndPoints> polylines = new ArrayList<>();
	
	//int nConeSurfaceRender = BigTraceData.SURFACE_SHADE;

	int nConeSurfaceRender = BigTraceData.SURFACE_SILHOUETTE;
	boolean initialized = false;
	
	public OCVisDebug(final BigTrace<?> bt)
	{
		this.bt = bt;
		
	}
	
	void init()
	{
		
		//example init of a cone
		double [] position = new double[3];
		
		double [] direction = new double[3];
		for(int d = 0; d < 3; d++)
		{
			position[d] = 0.0;
		}		
		direction[1] = 5.0;
		final VisCone testCone = new VisCone(bt.btData);
		testCone.surfaceRender = nConeSurfaceRender;
		testCone.setVertices( direction, position, 45. * Math.PI/180. );
		testCone.setColor( Color.YELLOW );
		cones.add( testCone );
		
		//example of a line
		VisLineAndPoints lineexample = new VisLineAndPoints ();
		ArrayList< RealPoint > points = new ArrayList<>();
		for(int i = 0; i< 50; i++)
		{
			points.add( new RealPoint(i, i, i) );
		}

		lineexample.setVertices( points );
		lineexample.setThickness( 5.0f );
		lineexample.setLineColor( Color.GREEN );
		lineexample.setPointColor( Color.YELLOW);

		polylines.add( lineexample );
	}
	
	 public void draw(final GL3 gl, final Matrix4fc pvm,  Matrix4fc vm, final int [] screen_size, final boolean bWeightedOIT)
	 {
		 if(!initialized)
		 {
			 init();
			 initialized = true;
		 }
		 for(final VisCone cone : cones)
		 {
			 if(cone.isTransparent() == bWeightedOIT)
			 {
				 cone.draw( gl, pvm, vm, bt.btData, bWeightedOIT );
			 }
		 }
		 if(!bWeightedOIT)
		 {
			 for(final VisLineAndPoints line:polylines)
			 {
				 line.draw( gl, pvm, screen_size, bt.btData, bWeightedOIT );   
			 }
		 }
	 }
}
