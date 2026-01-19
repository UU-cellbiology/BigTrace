package bigtrace.rois;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

import org.joml.Matrix4fc;

import com.jogamp.opengl.GL3;

import bigtrace.BigTraceData;
import bigtrace.geometry.CurveShapeInterpolation;
import bigtrace.geometry.Line3D;
import bigtrace.scene.VisPointsScaled;
import bigtrace.scene.VisWireMesh;

import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;


public class PolyLine3D extends AbstractCurve3D 
{	

	public VisPointsScaled verticesVis;
	public VisWireMesh edgesVis;
	

	public PolyLine3D(final BigTraceData<?> btdata_, final Roi3DGroup preset_in, final int nTimePoint_)
	{
		super(btdata_);
		type = Roi3D.POLYLINE;
		
		pointSize = preset_in.pointSize;
		lineThickness=preset_in.lineThickness;
		
		pointColor = new Color(preset_in.pointColor.getRed(),preset_in.pointColor.getGreen(),preset_in.pointColor.getBlue(),preset_in.pointColor.getAlpha());
		lineColor = new Color(preset_in.lineColor.getRed(),preset_in.lineColor.getGreen(),preset_in.lineColor.getBlue(),preset_in.lineColor.getAlpha());
				
		renderType = preset_in.renderType;

		vertices = new ArrayList<>();
		verticesVis = new VisPointsScaled();
		verticesVis.setColor(pointColor);
		verticesVis.setSize(pointSize);
		verticesVis.setRenderType(renderType);

		edgesVis = new VisWireMesh(btdata);	
		edgesVis.setColor(lineColor);
		edgesVis.setThickness(lineThickness);
		edgesVis.setRenderType(renderType);
		
		nTimePoint = nTimePoint_;
		interpolator = new CurveShapeInterpolation(type, btdata);
		name = "polyl" + Integer.toString(this.hashCode());

	}
	
	//adds a point to the "end" of polyline
	public void addPointToEnd(final RealPoint in_)
	{

		if (vertices.size()>0)
		{
			//check if the new point is at the same place that previous or not
			double [] dist = new double [3];
			LinAlgHelpers.subtract(vertices.get(vertices.size()-1).positionAsDoubleArray(), in_.positionAsDoubleArray(), dist);
			if(LinAlgHelpers.length(dist)>0.000001)
			{
				vertices.add(new RealPoint(in_));
			}
		}
		else
		{
			vertices.add(new RealPoint(in_));			
		}
		
		updateRenderVertices();
	}
	//removes the point from the "end" and returns "true"
	//if it is the last point, returns "false"
	public boolean removeEndPoint()
	{

		final int nP = vertices.size();
		
		if(nP>0)
		{
			vertices.remove(nP-1);
			updateRenderVertices();
			if(nP==1)
				return false;
			return true;
		}
		
		return false;
	}
	
	
	@Override
	public void updateRenderVertices()
	{

		verticesVis.setVertices(vertices);
		bMeshInit = false;
		if(vertices.size()>1)
		{
			interpolator.init(vertices, BigTraceData.shapeInterpolation);
			edgesVis.setVertices(interpolator.getVerticesVisual(),interpolator.getTangentsVisual());
		}
		else
		{
			if(vertices.size() == 1)
			{
				edgesVis.nPointsN = 0;	
			}
		}

		
	}
	public void setVertices(ArrayList<RealPoint> vertices_)
	{
		vertices = new ArrayList<>();
		for(int i=0;i<vertices_.size();i++)
			vertices.add(new RealPoint(vertices_.get(i)));		
		updateRenderVertices();
		
	}

	@Override
	public void draw(final GL3 gl, final Matrix4fc pvm, final Matrix4fc vm, final int[] screen_size) 
	{
		verticesVis.draw(gl, pvm, screen_size, btdata);
		edgesVis.draw(gl, pvm, vm, btdata);
		
	}


	@Override
	public void setPointColor(Color pointColor_) {
		
		pointColor = new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor_.getAlpha());
		verticesVis.setColor(pointColor);
	}
	
	@Override
	public void setLineColor(Color lineColor_) {
		
		lineColor = new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor_.getAlpha());
		edgesVis.setColor(lineColor);
	}


	@Override
	public void setLineThickness(float line_thickness) {

		lineThickness=line_thickness;
		edgesVis.setThickness(lineThickness);
		updateRenderVertices();
	}

	@Override
	public void setPointSize(float point_size) {

		pointSize=point_size;
		verticesVis.setSize(pointSize);
		
	}
	

	
	@Override
	public void setRenderType(int nRenderType){
	
		renderType = nRenderType;
		verticesVis.setRenderType(nRenderType);
		edgesVis.setRenderType(renderType);
		updateRenderVertices();
	}	
	
	
	@Override
	public void saveRoi(final FileWriter writer)
	{
		int i, iPoint;
		float [] vert;
		
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		DecimalFormat df3 = new DecimalFormat ("#.###", symbols);
		try {
			writer.write("Type," + Roi3D.intTypeToString(this.getType())+"\n");
			writer.write("Name," + this.getName()+"\n");
			writer.write("GroupInd," + Integer.toString(this.getGroupInd())+"\n");
			writer.write("TimePoint," + Integer.toString(this.getTimePoint())+"\n");
			writer.write("PointSize," + df3.format(this.getPointSize())+"\n");
			writer.write("PointColor,"+ Integer.toString(pointColor.getRed()) +","
									  +	Integer.toString(pointColor.getGreen()) +","
									  +	Integer.toString(pointColor.getBlue()) +","
									  +	Integer.toString(pointColor.getAlpha()) +"\n");
			writer.write("LineThickness," + df3.format(this.getLineThickness())+"\n");
			writer.write("LineColor,"+ Integer.toString(lineColor.getRed()) +","
									  +	Integer.toString(lineColor.getGreen()) +","
									  +	Integer.toString(lineColor.getBlue()) +","
									  +	Integer.toString(lineColor.getAlpha()) +"\n");
			writer.write("RenderType,"+ Integer.toString(this.getRenderType())+"\n");
			
			writer.write("Vertices,"+Integer.toString(vertices.size())+"\n");
			vert = new float[3];
			for (iPoint = 0;iPoint<vertices.size();iPoint++)
			{ 
				vertices.get(iPoint).localize(vert);
				for(i=0;i<3;i++)
				{
					writer.write(df3.format(vert[i])+",");
				}
				//time point
				writer.write("\n");
			}
		}
		catch (IOException e) {	
			System.err.print(e.getMessage());
			
		}
	}
	@Override
	public void reversePoints() {
		
		vertices = Roi3D.reverseArrayRP(vertices); 		
		updateRenderVertices();
		return;
		
	}


	@Override
	public double getMinDist(Line3D line) 
	{
		
		ArrayList<RealPoint> allvertices;
		//in VOXEL coordinates
		if(this.vertices.size() == 1)
		{
			allvertices = this.vertices;
		}
		else
		{
			allvertices = Roi3D.scaleGlobInv(interpolator.getVerticesVisual(), btdata.globCal);
		}
		double dMinDist = Double.MAX_VALUE;
		double currDist = 0.0;
		for(int i = 0; i < allvertices.size(); i++)
		{
			currDist = Line3D.distancePointLine( allvertices.get(i), line);
			
			if(currDist < dMinDist)
			{
				dMinDist = currDist;
			}
				
		}
		return dMinDist;
	}


	

}

