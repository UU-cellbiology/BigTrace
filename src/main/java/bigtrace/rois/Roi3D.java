package bigtrace.rois;

import java.awt.Color;
import java.io.FileWriter;
import java.util.ArrayList;

import org.joml.Matrix4fc;

import com.jogamp.opengl.GL3;

import bigtrace.geometry.Line3D;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;

public interface Roi3D 
{

	/** ROI types**/
	public static final int POINT = 0, POLYLINE = 1, LINE_TRACE = 2, PLANE = 3, BOX = 4;
	
	/** render types **/
	public static final int OUTLINE = 0, WIRE = 1, SURFACE = 2;
	
	/** returns ROI type**/
	public int getType();
	
	/** Returns the name of this ROI, or null. */
	public String getName();

	/** Sets the name of this ROI. */
	public void setName(String name);
	
	/** Draws ROI into the volume **/
	public void draw( final GL3 gl, final Matrix4fc pvm,  final Matrix4fc vm, final int [] screen_size);
	
	public void setLineColorRGB(Color lineColor_);
	public void setPointColorRGB(Color pointColor_);
	public void setLineColor(Color lineColor_);
	public void setPointColor(Color pointColor_);
	public void reversePoints();
	
	public Color getLineColor();
	public Color getPointColor();	
	
	public void setOpacity(float fOpacity);
	public float getOpacity();

	public void setLineThickness(final float line_thickness);
	public float getLineThickness();
	
	public void setPointSize(final float point_size);
	public float getPointSize();

	public void setRenderType(int nRenderType);
	public int getRenderType();
	
	public void setTimePoint(int nTimePoint_);
	public int getTimePoint();
	
	public void saveRoi(final FileWriter writer);
	
	public void setGroup(final Roi3DGroup preset_in);
	public void setGroupInd(final int nGIndex);
	public int getGroupInd();
	public void updateRenderVertices();
	
	public double getMinDist(final Line3D line);
	
	public Interval getBoundingBoxVisual();
	public Interval getBoundingBox();
	
	/** given 3D RAI (without channel or time),
	 * returns a cursor iterating over the volume of the ROI **/
	public <T extends RealType< T > & NativeType< T > > Cursor<T> getSingle3DVolumeCursor(RandomAccessibleInterval< T > input);
	
	public static String intTypeToString(int nType)
	{
		String sType = "Point";
		switch (nType)
		{
		 case POINT:
			 sType = "Point";
			 break;
		 case POLYLINE:
			 sType = "Polyline";
			 break;
		 case LINE_TRACE:
			 sType = "LineTrace";
			 break;
		 case PLANE:
			 sType = "Plane";
			 break;
		 case BOX:
			 sType = "Box";
			 break;
		
		}
		return sType;
	}
	
	public static int stringTypeToInt(String sType)
	{
		if(sType.equals("Point"))
		{ return POINT;}
		if(sType.equals("Polyline"))
		{ return POLYLINE;}
		if(sType.equals("LineTrace"))
		{ return LINE_TRACE;}
		if(sType.equals("Plane"))
		{ return PLANE;}
		if(sType.equals("Box"))
		{ return BOX;}
		
		return -1;
	}
	
	public static ArrayList<RealPoint> reverseArrayRP(final ArrayList<RealPoint> vert_in) 
	{
		ArrayList<RealPoint> reversed = new ArrayList<>();
		
		for(int i = vert_in.size() - 1; i >= 0; i--)
		{
			reversed.add(new RealPoint(vert_in.get(i)));
		}
		return reversed; 
	}
	/**
	 * transforms realpoint in pixel coordinates to space units
	 * **/
	public static RealPoint scaleGlob(final RealPoint in, final double [] globCal)
	{
		RealPoint out = new RealPoint(in);
		for (int i = 0; i < in.numDimensions(); i++)
		{
			out.setPosition(out.getDoublePosition(i) * globCal[i], i);
		}
		return out;
	}
	
	/**
	 * transforms realpoint in pixel coordinates to space units (ArrayList version)
	 * **/
	public static ArrayList<RealPoint> scaleGlob(final ArrayList<RealPoint> in, final double [] globCal)
	{
		ArrayList<RealPoint> out = new ArrayList<>(in.size());
			
		for (int i = 0; i < in.size(); i++)
		{
			out.add(scaleGlob(in.get(i), globCal));
		}
		return out;
	}
	/**
	 * transforms realpoint in space units to pixels(voxels) coordinates 
	 * **/
	public static RealPoint scaleGlobInv(final RealPoint in, final double [] globCal)
	{
		RealPoint out = new RealPoint(in);
		for (int i = 0; i < in.numDimensions(); i++)
		{
			out.setPosition(out.getDoublePosition(i) / globCal[ i ], i);
		}
		return out;
	}
	
	/**
	 * transforms realpoint in space units to pixels(voxels) coordinates (ArrayList version)
	 * **/
	public static ArrayList<RealPoint> scaleGlobInv(final ArrayList<RealPoint> in, final double [] globCal)
	{
		ArrayList<RealPoint> out = new ArrayList<>(in.size());
		
		for (int i = 0; i < in.size(); i++)
		{
			out.add(scaleGlobInv(in.get(i), globCal));
		}
		return out;
	}
	/**
	 * transforms double array of pixel coordinates to space units
	 * **/
	public static double [] scaleGlob(final double [] in, final double [] globCal)
	{
		double [] out = new double [in.length];
		for (int i = 0; i < in.length; i++)
		{
			out[ i ] = in[ i ] * globCal[ i ];
		}
		return out;
	}
	/**
	 * transforms double array of coordinates in space units to pixels
	 * **/
	public static double [] scaleGlobInv(final double [] in, final double [] globCal)
	{
		double [] out = new double [in.length];
		for (int i = 0; i < in.length; i++)
		{
			out[ i ] = in[ i ] / globCal[ i ];
		}
		return out;
	}
	/** calculates cumulative length between vert_in 3D points  **/
	public static double getSegmentLength(final ArrayList<RealPoint> vert_in)
	{
		double length = 0.0;
		double [] pos1 = new double [3];
		double [] pos2 = new double [3];
		
		if (vert_in == null)
			{return 0.0;}
		for (int i = 0; i < vert_in.size() - 1; i++)
		{
			vert_in.get(i).localize(pos1);
			vert_in.get(i+1).localize(pos2);
			length += LinAlgHelpers.distance(pos1, pos2);
		}
		//System.out.print(length);
		return length;
	}
	/** assumes vert_in are in VOXEL coordinates and calculates cumulative length between vert_in 3D points 
	 * using globCal calibration. The returned value is in SPACE coordinates **/
	public static double getSegmentLength(final ArrayList<RealPoint> vert_in, double [] globCal)
	{
		double length = 0.0;
		double [] pos1 = new double [3];
		double [] pos2 = new double [3];
		
		if (vert_in == null)
			{return 0.0;}
		for (int i = 0; i < vert_in.size() - 1; i++)
		{
			vert_in.get(i).localize(pos1);
			vert_in.get(i+1).localize(pos2);
			for (int j = 0; j<3; j++)
			{
				pos1[j] *= globCal[j];
				pos2[j] *= globCal[j];
			}
			length += LinAlgHelpers.distance(pos1, pos2);
		}
		//System.out.print(length);
		return length;
	}
	
	/** calculates cumulative length between vert_in 3D points using globCal calibration **/
	public static double[] getSegmentTabLength(final ArrayList<RealPoint> vert_in, double [] globCal)
	{
		
		if (vert_in == null)
		{return null;}
		
		double[] length=new double [vert_in.size()];
		double [] pos1 = new double [3];
		double [] pos2 = new double [3];
		
		length[0] = 0.0f;
		for (int i = 0; i < vert_in.size() - 1; i++)
		{
			vert_in.get(i).localize(pos1);
			vert_in.get(i+1).localize(pos2);
			for (int j = 0; j < 3; j++)
			{
				pos1[ j ] *= globCal[ j ];
				pos2[ j ] *= globCal[ j ];
			}
			length[ i + 1 ] = length[ i ] + LinAlgHelpers.distance(pos1, pos2);
		}
		//System.out.print(length);
		return length;
	}
	
	/** calculates cumulative length between vert_in 3D points **/
	public static double[] getSegmentTabLength(final ArrayList<RealPoint> vert_in)
	{
		if (vert_in == null)
		{return null;}
		
		double [] length = new double [ vert_in.size() ];
		double [] pos1 = new double [ 3 ];
		double [] pos2 = new double [ 3 ];

		length[0] = 0.0f;
		for (int i = 0; i < vert_in.size() - 1; i++)
		{
			vert_in.get(i).localize(pos1);
			vert_in.get(i+1).localize(pos2);
			length[ i + 1 ] = length[ i ] + LinAlgHelpers.distance(pos1, pos2);
		}
		//System.out.print(length);
		return length;
	}

	/**
	 * returns a point with NaN coordinates
	 * **/
	public static RealPoint getNaNPoint()
	{
		RealPoint out = new RealPoint(3);
		for (int i = 0; i < 3; i++)
			out.setPosition(Double.NaN, i);
		return out;
	}
	

}
