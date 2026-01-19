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
import bigtrace.geometry.Line3D;
import bigtrace.geometry.CurveShapeInterpolation;
import bigtrace.scene.VisPointsScaled;
import bigtrace.scene.VisWireMesh;

import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;


public class LineTrace3D extends AbstractCurve3D
{	
	public ArrayList<ArrayList<RealPoint>> segments;
	
	public final VisPointsScaled verticesVis;
	
	public final VisWireMesh segmentsVis;

	public LineTrace3D(final BigTraceData<?> btdata_, final Roi3DGroup preset_in, final int nTimePoint_)
	{
		super (btdata_);
		
		type = Roi3D.LINE_TRACE;
		
		pointSize = preset_in.pointSize;
		
		lineThickness = preset_in.lineThickness;
		
		pointColor = new Color(preset_in.pointColor.getRed(),preset_in.pointColor.getGreen(),preset_in.pointColor.getBlue(),preset_in.pointColor.getAlpha());
		
		lineColor = new Color(preset_in.lineColor.getRed(),preset_in.lineColor.getGreen(),preset_in.lineColor.getBlue(),preset_in.lineColor.getAlpha());

		nTimePoint = nTimePoint_;
		
		renderType= preset_in.renderType;
		
		vertices = new ArrayList<>();
		segments = new ArrayList<>();
		verticesVis = new VisPointsScaled();
		verticesVis.setColor(pointColor);
		verticesVis.setSize(pointSize);
		verticesVis.setRenderType(renderType);
		
		interpolator = new CurveShapeInterpolation(type, btdata);
		segmentsVis = new VisWireMesh(btdata);
		segmentsVis.setColor(lineColor);
		segmentsVis.setThickness(lineThickness);
		segmentsVis.setRenderType(renderType);
		name = "trace" + Integer.toString(this.hashCode());

	}
	
	/** adds initial vertex **/
	public void addFirstPoint(final RealPoint in_)
	{
		vertices.add(new RealPoint(in_));
		verticesVis.setVertices(vertices);
	}
	
	public void addPointAndSegment(final RealPoint in_, final ArrayList<RealPoint> segments_)
	{
		//check if the new point is at the same place that previous or not
		double [] dist = new double [3];
		LinAlgHelpers.subtract(vertices.get(vertices.size()-1).positionAsDoubleArray(), in_.positionAsDoubleArray(), dist);
		if(LinAlgHelpers.length(dist) > 0.000001)
		{		
			vertices.add(new RealPoint(in_));
			segments.add(segments_);
			updateRenderVertices();

		}
	}
	public void addPointAndSegmentNoUpdate(final RealPoint in_, final ArrayList<RealPoint> segments_)
	{
		//check if the new point is at the same place that previous or not
		double [] dist = new double [3];
		LinAlgHelpers.subtract(vertices.get(vertices.size()-1).positionAsDoubleArray(), in_.positionAsDoubleArray(), dist);
		if(LinAlgHelpers.length(dist)>0.000001)
		{
			vertices.add(new RealPoint(in_));
			//verticesVis.setVertices(vertices);
			segments.add(segments_);
			//segmentsVis = new VisPolyLineScaled(makeJointSegment( BigTraceData.shapeInterpolation),lineThickness, lineColor, renderType);
			//updateRenderVertices();
		}
	}
	
	/** removes last segment of the tracing.
	 * if there was just one spot, returns false**/
	public boolean removeLastSegment() 
	{
		
		vertices.remove(vertices.size()-1);
		//verticesVis.setVertices(vertices);
		if(vertices.size()>0)
		{
			segments.remove(segments.size()-1);
			updateRenderVertices();
			return true;
		}
		
		return false;
		
	}
	
	/** returns the last segment of the tracing.**/
	public ArrayList<RealPoint> getLastSegment() 
	{		
		return segments.get(segments.size()-1);		
	}


	@Override
	public void draw(final GL3 gl, final Matrix4fc pvm, final Matrix4fc vm, final int[] screen_size) 
	{
		verticesVis.draw(gl, pvm, screen_size, btdata);
		segmentsVis.draw(gl, pvm, vm, btdata);	
	}
	
	@Override
	public void setPointColor(Color pointColor_) 
	{	
		pointColor = new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor_.getAlpha());	
		verticesVis.setColor(pointColor);
	}
	
	@Override
	public void setLineColor(Color lineColor_) {
		
		lineColor = new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor_.getAlpha());
		segmentsVis.setColor(lineColor);
		
	}
	
	@Override
	public void setPointSize(float point_size) {

		pointSize = point_size;
		verticesVis.setSize(pointSize);
	}
	

	@Override
	public void setLineThickness(float line_thickness) 
	{
		lineThickness = line_thickness;
		segmentsVis.setThickness(lineThickness);
		updateRenderVertices();
	}
	
	
	@Override
	public void setRenderType(int nRenderType)
	{	
		renderType = nRenderType;
		verticesVis.setRenderType(renderType);
		segmentsVis.setRenderType(renderType);
		updateRenderVertices();

	}	

	
	/** Methods from imglib2 Polyline, 
	 * I do not really understand them yet, 
	 * but added for the future and implemented them
	 * to the best of my knowledge (and so they do not produce errors)
	 */
	
	public int numVertices() {
		return vertices.size();
	}
	
	
	public int numSegments() {
		return segments.size();
	}


	@Override
	public void saveRoi(final FileWriter writer)
	{
		int i, iPoint, iSegment;
		float [] vert;
		ArrayList<RealPoint> segment;
		
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
			writer.write("SegmentsNumber,"+Integer.toString(segments.size())+"\n");
			for(iSegment=0;iSegment<segments.size();iSegment++)
			{
				segment = segments.get(iSegment);
				writer.write("Segment,"+Integer.toString(iSegment+1)+",Points,"+Integer.toString(segment.size())+"\n");
				for (iPoint = 0; iPoint < segment.size(); iPoint++)
				{ 
					segment.get(iPoint).localize(vert);
					for(i = 0; i < 3; i++)
					{
						writer.write(df3.format(vert[i])+",");
					}
					//time point
					writer.write("\n");
				}
			}
		}
		catch (IOException e) {	
			System.err.print(e.getMessage());
			
		}
	}

	@Override
	public void reversePoints() {
		
		int i;
		
		vertices = Roi3D.reverseArrayRP(vertices); 
		//update drawing component
		verticesVis.setVertices(vertices);
		
		ArrayList<ArrayList<RealPoint>> segments_r = new ArrayList<>();
		for(i = segments.size() - 1; i >= 0; i--)
		{
			segments_r.add(Roi3D.reverseArrayRP(segments.get(i)));
		}
		segments = segments_r;
		
		//update drawing component
		updateRenderVertices();
		
		return;
		
	}
	
	public int getNumberOfPointsInJointSegment()
	{
		int out = 0;
		if(vertices.size() > 1)
		{
			out++;
			for(int i = 0; i < segments.size(); i++)
			{
				out+= segments.get(i).size() - 1;
			}
		}
		return out;
	}

	/** returns joint segment of ROI in VOXEL coordinates as RealPoint **/
	public ArrayList<RealPoint> makeJointSegment()
	{
		ArrayList<RealPoint> out = new ArrayList<>();
		if(vertices.size() > 1)
		{
			//first vertex
			out.add(vertices.get(0));
			//the rest
			for(int i = 0; i < segments.size(); i++)
			{
				for(int j = 1; j < segments.get(i).size(); j++)
				{
					out.add(segments.get(i).get(j));
				}
			}
		}
		else 
		{
			return null;
		}
		return out;
	}	
	
	/** returns joint segment of ROI in VOXEL coordinates **/
	public ArrayList<double[]> makeJointSegmentDouble()
	{
		final ArrayList<double[]> out = new ArrayList<>();
		if(vertices.size()>1)
		{
			//first vertex
			out.add(vertices.get(0).positionAsDoubleArray());
			//the rest
			for(int i = 0; i < segments.size(); i++)
			{
				for(int j = 1; j < segments.get(i).size(); j++)
				{
					out.add(segments.get(i).get(j).positionAsDoubleArray());
				}
			}
		}
		else 
		{
			return null;
		}
		return out;
	}	
	
	@Override
	public void updateRenderVertices() 
	{
		
		verticesVis.setVertices(vertices);
		bMeshInit = false;
		if(vertices.size() > 1)
		{
			interpolator.init(makeJointSegment(), btdata.shapeInterpolation);
			segmentsVis.setVertices(interpolator.getVerticesVisual(),interpolator.getTangentsVisual());
			//segmentsVis.setVertices(interpolator.getVerticesResample(),interpolator.getTangentsResample());
		}
		else
		{
			if(vertices.size() == 1)
			{
				segmentsVis.nPointsN = 0;	
			}
		}
	
	}
	
	public ArrayList<RealPoint> getVerticesVisual()
	{
		return interpolator.getVerticesVisual();
	}

	public ArrayList<double[]> getTangentsVisual()
	{
		return interpolator.getTangentsVisual();
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
			currDist = Line3D.distancePointLine(allvertices.get(i), line);
			
			if(currDist < dMinDist)
			{
				dMinDist = currDist;
			}
				
		}
		return dMinDist;
	}

}

