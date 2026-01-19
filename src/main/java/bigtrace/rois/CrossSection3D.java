package bigtrace.rois;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.joml.Matrix4fc;

import com.jogamp.opengl.GL3;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import bigtrace.BigTraceData;
import bigtrace.geometry.Intersections3D;
import bigtrace.geometry.Line3D;
import bigtrace.geometry.Plane3D;
import bigtrace.scene.VisPointsScaled;
import bigtrace.scene.VisPolygonFlat;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;

public class CrossSection3D extends AbstractRoi3D 
{
	
	/** vertices provided by user (plane is fitted to them) **/
	public ArrayList<RealPoint> vertices;
	
	/** visualization of user-provided vertices**/
	public VisPointsScaled verticesVis;
	
	/** cross-section visualization**/
	public VisPolygonFlat planeVis;
	
	/** coordinates of polygon **/
	public ArrayList<RealPoint> polygonVert;
	
	public float [][] nDimBox;
	
	public Plane3D fittedPlane = null;


	public CrossSection3D(final BigTraceData<?> btdata_, final Roi3DGroup preset_in, final long [][] nDimIni_, final int nTimePoint_)
	{
		super(btdata_);
		type = Roi3D.PLANE;
		
		pointSize = preset_in.pointSize;
		lineThickness = preset_in.lineThickness;
		
		pointColor = new Color(preset_in.pointColor.getRed(),preset_in.pointColor.getGreen(),preset_in.pointColor.getBlue(),preset_in.pointColor.getAlpha());
		lineColor = new Color(preset_in.lineColor.getRed(),preset_in.lineColor.getGreen(),preset_in.lineColor.getBlue(),preset_in.lineColor.getAlpha());
				
		renderType = preset_in.renderType;
		
		
		vertices = new ArrayList<>();
		verticesVis = new VisPointsScaled();
		verticesVis.setColor(pointColor);
		verticesVis.setSize(pointSize);
		planeVis = new VisPolygonFlat();
	
	
		planeVis.setColor(lineColor);
		planeVis.setThickness(lineThickness);
		planeVis.setRenderType(renderType);
		name = "plane"+Integer.toString(this.hashCode());
		nTimePoint = nTimePoint_;
		nDimBox = new float [2][3];
		for(int i=0;i<2;i++)
			for(int j=0;j<3;j++)
				nDimBox[i][j]=nDimIni_[i][j];

	}
	
	/** adds initial vertex **/
	public void addPoint(final RealPoint in_)
	{
		if (vertices.size()==1)
		{
			//check if the second point is at the same place that the first 
			double [] dist = new double [3];
			LinAlgHelpers.subtract(vertices.get(0).positionAsDoubleArray(), in_.positionAsDoubleArray(), dist);
			if(LinAlgHelpers.length(dist)>0.000001)
			{
				vertices.add(new RealPoint(in_));
			}
		}
		else
		{
			//check that first three points do not lay on the line, 
			// so we can build a plane
			if(vertices.size()==2)
			{
				double [] dist1 = new double [3];
				double [] dist2 = new double [3];
				double [] cross = new double [3];
				
				LinAlgHelpers.subtract(vertices.get(1).positionAsDoubleArray(), vertices.get(0).positionAsDoubleArray(), dist1);
				LinAlgHelpers.subtract(in_.positionAsDoubleArray(),vertices.get(1).positionAsDoubleArray(), dist2);
				LinAlgHelpers.cross(dist1, dist2, cross);
				if(LinAlgHelpers.length(cross)>0.000001)
				{
					vertices.add(new RealPoint(in_));
				}
			}
			else
			{
				vertices.add(new RealPoint(in_));
			}
		}
		updateRenderVertices();
	}
	//removes the point from the "end" and returns "true"
	//if it is the last point, returns "false"
	public boolean removePoint()
	 {
		 
		 int nP= vertices.size();
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
	public void draw(GL3 gl, Matrix4fc pvm, final Matrix4fc vm, int[] screen_size) {
		verticesVis.draw(gl, pvm, screen_size, btdata);
		planeVis.draw(gl, pvm, btdata);
		
	}
	
	public void setVertices(ArrayList<RealPoint> vertices_)
	{
		vertices = new ArrayList<>();
		for(int i=0;i<vertices_.size();i++)
			vertices.add(new RealPoint(vertices_.get(i)));		
		updateRenderVertices();
		
	}


	@Override
	public void setPointColor(Color pointColor_) 
	{
		
		pointColor = new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor_.getAlpha());
		verticesVis.setColor(pointColor);
	}
	
	@Override
	public void setLineColor(Color lineColor_) 
	{
		
		lineColor = new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor_.getAlpha());
		planeVis.setColor(lineColor);
	}


	@Override
	public void reversePoints() 
	{
		vertices = Roi3D.reverseArrayRP(vertices); 		
		updateRenderVertices();
		return;		
	}


	@Override
	public void setLineThickness(float line_thickness) 
	{

		lineThickness=line_thickness;
		planeVis.setThickness(lineThickness);
		updateRenderVertices();
	}

	@Override
	public void setPointSize(float point_size) 
	{

		pointSize=point_size;
		verticesVis.setSize(pointSize);
		
	}

	@Override
	public void setRenderType(int nRenderType) 
	{

		renderType=nRenderType;
		planeVis.setRenderType(renderType);
		updateRenderVertices();
	}

	@Override
	public void saveRoi(FileWriter writer) 
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
	public void setGroup(Roi3DGroup preset_in) {
		
		setPointColor(preset_in.pointColor);
		setLineColor(preset_in.lineColor);

		setRenderType(preset_in.renderType);
		setPointSize(preset_in.pointSize);
		setLineThickness(preset_in.lineThickness);
		updateRenderVertices();
	}

	@Override
	public void updateRenderVertices() {
		
		verticesVis.setVertices(vertices);
		polygonVert = null;
		fittedPlane = null;
		if(vertices.size()>2)
		{
			double [] intersectionPoint = new double[3];
			//for now;
			//fittedPlane = new Plane3D(vertices.get(0),vertices.get(1),vertices.get(2));
			fittedPlane = fitPlane(vertices);
			polygonVert = new ArrayList<  >();
			ArrayList<ArrayList< RealPoint >> boxEdges = Box3D.getEdgesPairPoints(nDimBox);
			
			for(int i = 0;i<boxEdges.size();i++)
			{
		
				if(Intersections3D.planeEdgeIntersect(fittedPlane,boxEdges.get(i).get(0),boxEdges.get(i).get(1),intersectionPoint))
				{
					polygonVert.add(new RealPoint(intersectionPoint));
				}
			}
			if(polygonVert.size()>1)
			{
				sortPolygonVertices(polygonVert,fittedPlane.n);
				//outline.add(new RealPoint(outline.get(0)));
				planeVis.setVertices(polygonVert);
			}
		}
		else
		{
			planeVis.setVertices(new ArrayList<>());
		}
	}
	/** given a set of points (more than 2) 
	 * 1) all lying in the same plane (with its normal vector in planeNormal); 
	 * 2) assuming they form convex polygon,
	 * sorts them to form outline. 
	 * Maybe better to use ConvexHull, but ok for now.
	 * Taken from https://www.asawicki.info/news_1428_finding_polygon_of_plane-aabb_intersection **/
	public static void sortPolygonVertices(final ArrayList< RealPoint > vertices, final double [] planeNormal )
	{
		double [] origin = vertices.get(0).positionAsDoubleArray();
		
		Comparator<RealPoint> compareRP = new Comparator<RealPoint> ()
		{
			  
		    @Override
		    public int compare(RealPoint rp1, RealPoint rp2)
		    {
		    	double [] v = new double [3];
		    	double [] lhs = new double [3];
		    	double [] rhs = new double [3];
		    	rp1.localize(lhs);
		    	rp2.localize(rhs);
		    	LinAlgHelpers.subtract(lhs, origin, lhs);
		    	LinAlgHelpers.subtract(rhs, origin, rhs);
		    	LinAlgHelpers.cross(lhs,rhs, v);
		    	if(LinAlgHelpers.dot(v, planeNormal)<0)
		    		return 1;
				return -1;
		    }
		};
		Collections.sort(vertices,compareRP);
		
	}
	private static Plane3D fitPlane(final ArrayList<RealPoint> vertices)
	{
		RealPoint centroidRP = centroid(vertices);
			
		double [][] matrixD = new double [vertices.size()][3];
		
		//double [][] matrixU;
		int d;
		//subtract centroid
		for(int i=0;i<vertices.size();i++)
			for(d=0;d<3;d++)
			{
				matrixD[i][d]=vertices.get(i).getDoublePosition(d)-centroidRP.getDoublePosition(d);
			}
		Matrix A = new Matrix(matrixD);
		SingularValueDecomposition svd = new SingularValueDecomposition(A);		
		final double [][] matrixV = svd.getV().getArray();
		//matrixU = svd.getU().getArray();
		RealPoint normalRP = new RealPoint(3);
		for(d=0;d<3;d++)
		{
			normalRP.setPosition(matrixV[d][2], d);
		}
		Plane3D out = new Plane3D();
		
		out.setVectors(centroidRP.positionAsDoubleArray(), normalRP.positionAsDoubleArray());
		
		return out;
		
		
	}
	public static RealPoint centroid(final ArrayList<RealPoint> vertices)
	{
		if(vertices.size()<1)
			return null;
		final int nDim=vertices.get(0).numDimensions();
		final double nPoints = vertices.size();
		int d;
		double [] meanV = new double [nDim];
		for(int i=0;i<vertices.size();i++)
		{
			for(d=0;d<nDim;d++)
			{
				meanV[d]+=vertices.get(i).getDoublePosition(d);
			}
		}
		for(d=0;d<nDim;d++)
		{
			meanV[d] /= nPoints;
		
		}
		return new RealPoint(meanV);
	}
	
	@Override
	public double getMinDist(Line3D line) {
		
		double [] intersectionPoint = new double[3];
		double dMinDist = (-1.0)*Double.MAX_VALUE;
		double currDist = 0.0;
		if(fittedPlane==null)
		{
			for (int i=0;i<vertices.size();i++)
			{
				currDist= Line3D.distancePointLine(vertices.get(i), line);
			
				if(currDist <dMinDist)
				{
					dMinDist = currDist;
				}
			}
			return dMinDist;
		}
		if(Intersections3D.planeLineIntersect(fittedPlane,line,  intersectionPoint))
		{
			//see if the point inside the polygon
			if(Intersections3D.pointInsideConvexPolygon(polygonVert, intersectionPoint,fittedPlane.n))
				return 0.0;
			
			return Double.MAX_VALUE;
		}
		
		return Double.MAX_VALUE;
	
	}
	/*
	public static void main(String[] args) 
	{
		ArrayList<RealPoint> vertices = new ArrayList<RealPoint>();
		for(int i=0;i<4;i++)
		{
			RealPoint rp = new RealPoint(3);
			for(int d=0;d<3;d++)
			{
				rp.setPosition(5*i*(i-d)+d, d);				
			}
			vertices.add(rp);
		}
		Plane3D fitplane = fitPlane(vertices);
		int i=10;
		i++;
	}
	*/
	@Override
	public Interval getBoundingBox() {
		ArrayList<RealPoint> allvertices;
		//in VOXEL coordinates
		if(this.polygonVert==null)
		{
			allvertices = this.vertices;
		}
		else
		{
			allvertices = this.polygonVert;
		}
		long [][] bBox = new long [2][3];
		for (int d = 0; d<3;d++)
		{
			bBox[0][d] = Long.MAX_VALUE; 
			bBox[1][d]= (-1)* Long.MAX_VALUE; 
		}
		double [] currPoint = new double [3];
		for (int i = 0; i<allvertices.size();i++)
		{
			allvertices.get(i).localize(currPoint);
			for (int d=0;d<3;d++)
			{
				if(currPoint[d]<bBox[0][d])
				{
					bBox[0][d] = Math.round(currPoint[d]);
				}
				if(currPoint[d]>bBox[1][d])
				{
					bBox[1][d] = Math.round(currPoint[d]);
				}

			}
		}
		return new FinalInterval(bBox[0],bBox[1]);
	}
	
	@Override
	public Interval getBoundingBoxVisual() {
		
		return getBoundingBox();

	}
	@Override
	public < T extends RealType< T > & NativeType< T >  > Cursor< T > getSingle3DVolumeCursor( RandomAccessibleInterval< T > input )
	{
		return null;
	}


}
