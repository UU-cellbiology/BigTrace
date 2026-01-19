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
import bigtrace.scene.VisPolyLineAA;

import net.imglib2.AbstractInterval;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class Box3D extends AbstractRoi3D 
{

	public ArrayList<RealPoint> vertices;

	public ArrayList<ArrayList<RealPoint>> edges;
	
	public final ArrayList<VisPolyLineAA> edgesVis;

	public Box3D(final BigTraceData<?> btdata_, final Roi3DGroup preset_in, final int nTimePoint_)
	{
		super(btdata_);
		type = Roi3D.BOX;

		pointSize = preset_in.pointSize;
		lineThickness = preset_in.lineThickness;
		
		pointColor = new Color(preset_in.pointColor.getRed(),preset_in.pointColor.getGreen(),preset_in.pointColor.getBlue(),preset_in.pointColor.getAlpha());
		lineColor = new Color(preset_in.lineColor.getRed(),preset_in.lineColor.getGreen(),preset_in.lineColor.getBlue(),preset_in.lineColor.getAlpha());
		nTimePoint = nTimePoint_;
		edgesVis = new ArrayList<>();
	}
	
	public Box3D(final BigTraceData<?> btdata_, final float lineThickness_)
	{
		super(btdata_);
		
		type = Roi3D.BOX;

		lineThickness = lineThickness_;		

		edgesVis = new ArrayList<>();

	}
	
	public Box3D(final BigTraceData<?> btdata_, float [][] nDimBox, final float lineThickness_, final float pointSize_, final Color lineColor_, final Color pointColor_, final int nTimePoint_)
	{
		super(btdata_);
		type = Roi3D.BOX;
		pointSize = pointSize_;
		lineThickness = lineThickness_;
		
		pointColor = new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor_.getAlpha());
		lineColor = new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor_.getAlpha());

		nTimePoint = nTimePoint_;
		edgesVis = new ArrayList<>();
		
		setIntervalFloatArray(nDimBox);
	}

	public Box3D(final BigTraceData<?> btdata_, final AbstractInterval nIntervalBox, final float lineThickness_, final float pointSize_, final Color lineColor_, final Color pointColor_, final int nTimePoint_)
	{
		super(btdata_);
		type = Roi3D.BOX;
		pointSize = pointSize_;
		lineThickness = lineThickness_;
		
		pointColor = new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor_.getAlpha());
		lineColor = new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor_.getAlpha());

		nTimePoint = nTimePoint_;
		edgesVis = new ArrayList<>();

		setInterval(nIntervalBox);
	}
	
	public void setInterval(AbstractInterval nIntervalBox)
	{
		float [][] nDimBox = new float [2][3];
		
		double [] minI = nIntervalBox.minAsDoubleArray();
		double [] maxI = nIntervalBox.maxAsDoubleArray();

		for(int i = 0; i < 3; i++)
		{
			nDimBox[0][i] = (float)minI[i];
			nDimBox[1][i] = (float)maxI[i];

		}
		setIntervalFloatArray(nDimBox);
	}
	
	public void setIntervalLongArray(long [][] nDimBoxL)
	{
		final float [][] nDimBox = new float[2][3];
		
		for(int d = 0; d < 3; d++)
		{
			nDimBox[0][d] = nDimBoxL[0][d];
			nDimBox[1][d] = nDimBoxL[1][d];

		}
		
		setIntervalFloatArray(nDimBox);
	}
	
	public void setIntervalFloatArray(float[][] nDimBox)
	{
		final ArrayList<ArrayList< RealPoint >> edgesPairPoints = getEdgesPairPoints(nDimBox);
		
		if(edgesVis.size() == 0)
		{
			for(int i=0; i<edgesPairPoints.size(); i++)
			{
				final VisPolyLineAA pL = new VisPolyLineAA(edgesPairPoints.get(i), lineThickness, lineColor);
				edgesVis.add(pL);
			}
		}
		else
		{
			for(int i=0; i<edgesPairPoints.size(); i++)
			{
				edgesVis.get( i ).setVertices( edgesPairPoints.get(i) );
			}
		}
	}
	
	@Override
	public void draw(final GL3 gl, final Matrix4fc pvm, final Matrix4fc vm, final int[] screen_size ) 
	{
	
		for (int i = 0; i < edgesVis.size(); i++)
		{
			edgesVis.get(i).draw(gl, pvm, btdata);
		}
	}
	
	
	@Override
	public void setPointColor(Color pointColor_) 
	{
		pointColor = new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor_.getAlpha());	
	}

	@Override
	public void setLineColor(Color lineColor_) 
	{
		
		lineColor = new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor_.getAlpha());
		for(int i = 0; i < edgesVis.size(); i++)
		{
			edgesVis.get(i).setColor(lineColor);
		}
	}
	

	@Override
	public void setLineThickness(float line_thickness) 
	{

		lineThickness = line_thickness;
		for(int i = 0; i < edgesVis.size(); i++)
		{
			edgesVis.get(i).setThickness(lineThickness);
		}
	}
	
	@Override
	public void setPointSize(float point_size) {
	
		pointSize=point_size;
	}
	
	@Override
	public void setRenderType(int nRenderType){
		return;
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
			writer.write("RenderType,"+ Integer.toString(this.getRenderType()));
			
			writer.write("Vertices,"+Integer.toString(vertices.size())+"\n");
			vert = new float[3];
			for (iPoint = 0;iPoint<vertices.size();i++)
			{ 
				vertices.get(iPoint).localize(vert);
				for(i=0;i<3;i++)
				{
					writer.write(df3.format(vert[i])+",");
				}
				writer.write("\n");
			}
		}
		catch (IOException e) {	
			System.err.print(e.getMessage());
			
		}
	}
	@Override
	public void reversePoints() {
		return;
		
	}

	@Override

	public void setGroup(final Roi3DGroup preset_in) 
	{
		
		setPointColor(preset_in.pointColor);
		setLineColor(preset_in.lineColor);

		setRenderType(preset_in.renderType);
		setPointSize(preset_in.pointSize);
		setLineThickness(preset_in.lineThickness);
	}

	@Override
	public void updateRenderVertices() 
	{
		// TODO Auto-generated method stub
		
	}
	
	/** returns array of paired coordinates for each edge of the box,
	 * specified by nDimBox[0] - one corner, nDimBox[1] - opposite corner.
	 * no checks on provided coordinates performed  **/
	public static ArrayList<ArrayList< RealPoint >> getEdgesPairPoints(final float [][] nDimBox)
	{
		int i,j,z;
		ArrayList<ArrayList< RealPoint >> out = new ArrayList<>();
		int [][] edgesxy = new int [5][2];
		edgesxy[0] = new int[]{0,0};
		edgesxy[1] = new int[]{1,0};
		edgesxy[2] = new int[]{1,1};
		edgesxy[3] = new int[]{0,1};
		edgesxy[4] = new int[]{0,0};
		//draw front and back
		RealPoint vertex1 = new RealPoint(0,0,0);
		RealPoint vertex2 = new RealPoint(0,0,0);
		for (z=0;z<2;z++)
		{
			for (i=0;i<4;i++)
			{
				for (j=0;j<2;j++)
				{
					vertex1.setPosition(nDimBox[edgesxy[i][j]][j], j);
					vertex2.setPosition(nDimBox[edgesxy[i+1][j]][j], j);
				}
				//z coord
				vertex1.setPosition(nDimBox[z][2], 2);
				vertex2.setPosition(nDimBox[z][2], 2);
				
				ArrayList< RealPoint > point_coords = new ArrayList<  >();
				point_coords.add(new RealPoint(vertex1));
				point_coords.add(new RealPoint(vertex2));

				out.add(point_coords);

			}
		}
		//draw the rest 4 edges

		for (i=0;i<4;i++)
		{
			for (j=0;j<2;j++)
			{
				vertex1.setPosition(nDimBox[edgesxy[i][j]][j], j);
				vertex2.setPosition(nDimBox[edgesxy[i][j]][j], j);
			}
			//z coord
			vertex1.setPosition(nDimBox[0][2], 2);
			vertex2.setPosition(nDimBox[1][2], 2);
			ArrayList< RealPoint > point_coords = new ArrayList<  >();

			point_coords.add(new RealPoint(vertex1));
			point_coords.add(new RealPoint(vertex2));
			out.add(point_coords);
	
		}	
		return out;
	}
	
	/** returns vertices of box specified by provided interval in no particular order **/
	public static ArrayList<RealPoint > getBoxVertices(final Interval interval)
	{
		int i,d;
		ArrayList<RealPoint> out = new ArrayList<>();
		RealPoint [] rpBounds = new RealPoint [2];
		rpBounds[0]= interval.minAsRealPoint();
		rpBounds[1]= interval.maxAsRealPoint();
		for (i =0;i<8; i++)
		{
			
		  String indexes = String.format("%3s", Integer.toBinaryString(i)).replaceAll(" ", "0");
		  //System.out.println(indexes);
		  RealPoint vert = new RealPoint(3);
		  for(d=0;d<3;d++)
		  {
			  vert.setPosition(rpBounds[Character.getNumericValue(indexes.charAt(d))].getDoublePosition(d), d);
		  }
		  out.add(vert);
		}
		
		return out;
	}
	@Override
	public double getMinDist(Line3D line) {
		// TODO change to proper calculation
		return Double.MAX_VALUE;
	}
	
	@Override
	public Interval getBoundingBox() 
	{
		if(vertices.size() == 0)
			return null;
	
		long [][] bBox = new long [2][3];
		for (int d = 0; d < 3; d++)
		{
			bBox[0][d] = Long.MAX_VALUE; 
			bBox[1][d] = (-1)* Long.MAX_VALUE; 
		}
		double [] currPoint = new double [3];
		for (int i = 0; i < vertices.size(); i++)
		{
			vertices.get(i).localize(currPoint);
			currPoint = Roi3D.scaleGlobInv(currPoint, btdata.globCal);
			for (int d = 0; d < 3; d++)
			{
				if(currPoint[d] < bBox[0][d])
				{
					bBox[0][d] = Math.round(currPoint[d]);
				}
				if(currPoint[d] > bBox[1][d])
				{
					bBox[1][d] = Math.round(currPoint[d]);
				}

			}
		}
		return new FinalInterval(bBox[0], bBox[1]);
	}
	
	@Override
	public Interval getBoundingBoxVisual() {
		
		return getBoundingBox();	
	}
	@Override
	public < T extends RealType< T > & NativeType< T >  > Cursor< T > getSingle3DVolumeCursor( RandomAccessibleInterval< T > input )
	{		
		return Views.interval( input, getBoundingBox() ).cursor();
	}
}
