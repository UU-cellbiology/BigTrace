package bigtrace.geometry;

import java.util.ArrayList;

import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

public class Pipe3D {

	/** double precision for geometry calculations **/
	public static double doublePrecision = 0.0000000000001;
	/** given a set of points, generates Pipe circular contours around each point (using rotation minimizing frame) 
	 * with radius dRadius and nSectorN sectors**/
	public static ArrayList<ArrayList< RealPoint >> getCountours(final ArrayList< RealPoint > points, final int nSectorN, final double dRadius, final int rotationMinFrameAlgorithm)
	{

		ArrayList<double []> tangents;
		ArrayList<ArrayList< RealPoint >> allCountours = new ArrayList<>();
		double [] center = new double[3];
	
		int nPointsNum=points.size();
		if(nPointsNum>1)
		{
			
			//calculate tangents at each point
			tangents = CurveShapeInterpolation.getTangentsAverage(points);
			double [][][] rsVect =  rotationMinimizingFrame(points, tangents, rotationMinFrameAlgorithm);
			for (int i=0;i<points.size();i++)			
			{
				points.get(i).localize(center);
				allCountours.add(getContourInXY( dRadius,  nSectorN, rsVect[0][i],rsVect[1][i], center));
			}
	
		}
		return allCountours;
		
	}
	
	/** given a set of points and tangents at their location, 
	 * generates Pipe circular contours around each point (using rotation minimizing frame) 
	 * with radius dRadius and nSectorN sectors**/
	public static ArrayList<ArrayList< RealPoint >> getCountours(final ArrayList< RealPoint > points, final ArrayList<double []> tangents, final int nSectorN, final double dRadius, final int rotationMinFrameAlgorithm)
	{
		ArrayList<ArrayList< RealPoint >> allCountours = new ArrayList<>();
		
		double [] center = new double[3];
	
		int nPointsNum = points.size();
		
		if(nPointsNum > 1)
		{
			
			double [][][] rsVect =  rotationMinimizingFrame(points, tangents, rotationMinFrameAlgorithm);
			for (int i = 0; i < points.size(); i++)			
			{
				points.get(i).localize(center);
				allCountours.add(getContourInXY( dRadius,  nSectorN, rsVect[0][i],rsVect[1][i], center));
			}
	
		}
		return allCountours;
		
	}

	public static double [][][] rotationMinimizingFrame(final ArrayList< RealPoint > points, final ArrayList< double [] > tangents, final int rotationMinFrameAlgorithm)
	{
		if(rotationMinFrameAlgorithm == 0)
		{
			return rotationMinimizingFrameWang(points,tangents);
		}
		return rotationMinimizingFrameExp(points,tangents);
	}

	
		
	/** given a set of points and tangents vectors at their locations (in 3D),
	 * calculates array of 2 vectors making rotation minimizing frame at each point.
	 * See "Computation of Rotation Minimizing Frames" (Wenping Wang, Bert Jüttler, Dayue Zheng, and Yang Liu, 2008),
	 * this code uses double reflection method**/
	public static double [][][] rotationMinimizingFrameWang(final ArrayList< RealPoint > points, final ArrayList< double [] > tangents)
	{
		int i, nPointsN;
		nPointsN = points.size();
		double [][] path = new double [2][3];
		double [][][] out = new double [2][nPointsN][3];
		double [][] v = new double[2][3];
		double [] rLi = new double[3];
		double [] tLi = new double[3];
		double [] ti;// = new double[3];
		double [] ti_plus_one;// = new double[3];
		double [] ri;
		double c1,c2;

		//calculate initial frame
		final double [][] initFrame = getInitialFrame(tangents.get(0));
		out[0][0] = initFrame[0].clone();
		out[1][0] = initFrame[1].clone();
		
		for(i = 0;i<(nPointsN-1);i++)
		{
			points.get(i).localize(path[0]);
			points.get(i+1).localize(path[1]);
			LinAlgHelpers.subtract(path[1],path[0], v[0]);
			c1 = LinAlgHelpers.dot(v[0], v[0]);
			//special case, zero length
			//did not verify validity yet
			if(Math.abs(c1)<doublePrecision)
			{
				out[0][i+1] = out[0][i];
				out[1][i+1] = out[1][i];
				
			}
			else
			{

				ri = out[0][i];			
				ti = tangents.get(i);
				ti_plus_one = tangents.get(i+1);
				
				LinAlgHelpers.scale(v[0], (2.0/c1)*LinAlgHelpers.dot(v[0],ri), rLi);
				LinAlgHelpers.subtract(ri, rLi, rLi);
				LinAlgHelpers.scale(v[0], (2.0/c1)*LinAlgHelpers.dot(v[0],ti), tLi);
				LinAlgHelpers.subtract(ti, tLi, tLi);
	
				
				LinAlgHelpers.subtract(ti_plus_one, tLi, v[1]);
				c2 = LinAlgHelpers.dot(v[1],v[1]);
				
				LinAlgHelpers.scale(v[1],(2.0/c2)*LinAlgHelpers.dot(v[1],rLi),out[0][i+1]);
				LinAlgHelpers.subtract(rLi,out[0][i+1],out[0][i+1]);
	
				LinAlgHelpers.cross(ti_plus_one, out[0][i+1], out[1][i+1]);
				LinAlgHelpers.normalize(out[0][i+1]);
				LinAlgHelpers.normalize(out[1][i+1]);
			}

		}
		
		return out;
	}
	
	/** given a set of points and tangents vectors at their locations (in 3D),
	 * calculates array of 2 vectors making rotation minimizing frame at each point.
	 * using simple cross-product, as suggested in this post
	 * Herman Tulleken (https://math.stackexchange.com/users/67933/herman-tulleken), 
	 * Getting consistent normals along a 3D (Bezier) curve, URL (version: 2020-12-01): https://math.stackexchange.com/q/3929639**/
	public static double [][][] rotationMinimizingFrameExp(final ArrayList< RealPoint > points, final ArrayList< double [] > tangents)
	{
		int i, nPointsN;
		nPointsN = points.size();

		double [][][] out = new double [2][nPointsN][3];

		//calculate initial frame
		final double [][] initFrame = getInitialFrame(tangents.get(0));
		out[0][0] = initFrame[0].clone();
		out[1][0] = initFrame[1].clone();

		
		for(i = 0;i<(nPointsN-1);i++)
		{
			
			LinAlgHelpers.cross(tangents.get(i+1),out[0][i],out[1][i+1]);
			if(Math.abs(LinAlgHelpers.length(out[1][i+1]))<doublePrecision)
			{
				LinAlgHelpers.cross(out[1][i],tangents.get(i+1),out[0][i+1]);
				LinAlgHelpers.normalize(out[0][i+1]);
				LinAlgHelpers.cross(tangents.get(i+1),out[0][i+1],out[1][i+1]);
				LinAlgHelpers.normalize(out[1][i+1]);
			}	
			else
			{
				LinAlgHelpers.normalize(out[1][i+1]);
				LinAlgHelpers.cross(out[1][i+1],tangents.get(i+1),out[0][i+1]);
				LinAlgHelpers.normalize(out[0][i+1]);
			}

		}
		
		return out;
	}
	
	/** returns a contour in a plane defined by two perpendicular vectors X and Y and with center at c**/
	public static ArrayList< RealPoint > getContourInXY(final double dRadius, final int nSectorN, final double [] x,final double [] y, final double [] c)
	{
		 ArrayList< RealPoint > contourXY = new  ArrayList< > ();
		 
		 final double dAngleInc = 2.0*Math.PI/(nSectorN);

		 double [] xp = new double[3];
		 double [] yp = new double[3];
		 
		 for (int i=0;i<nSectorN;i++)
		 {
			 LinAlgHelpers.scale(x, dRadius*Math.cos(Math.PI*0.25+i*dAngleInc), xp);
			 LinAlgHelpers.scale(y, dRadius*Math.sin(Math.PI*0.25+i*dAngleInc), yp);
			 LinAlgHelpers.add(xp, yp,xp);
			 LinAlgHelpers.add(xp, c,xp);
			 contourXY.add(new RealPoint(xp));			 
		 }
		 
		 return contourXY;
		
	}
	
	/** given initial tangent vector, builds another two of the frame,
	 * trying to align them in the XYZ direction **/
	public static double [][] getInitialFrame(double [] ini_tangent)
	{
		double [][] out  = new double [2][3];

		//try to orient initial frame to XYZ
		LinAlgHelpers.cross(ini_tangent, new double[] {0.0,0.0,1.0}, out[1]);
		if(Math.abs(LinAlgHelpers.length(out[1]))<doublePrecision)
		{
			LinAlgHelpers.cross(new double[] {0.0,1.0,0.0}, ini_tangent, out[0]);
			LinAlgHelpers.normalize(out[0]);
			LinAlgHelpers.cross(out[0],ini_tangent, out[1]);
			LinAlgHelpers.normalize(out[1]);
		}
		else
		{
			LinAlgHelpers.normalize(out[1]);
			LinAlgHelpers.cross(out[1], ini_tangent, out[0]);
			LinAlgHelpers.normalize(out[0]);
		}

		return out;
	}
	
	
	
	/** given a set of points (polyline) generates "cylindrical contours/circles" 
	 *  around each point with a thickness of fLineThickness (diameter).
	 *  Each contour has BigTraceData.sectorN sectors**/
	public static ArrayList<ArrayList< RealPoint >> getCountoursOld(final ArrayList< RealPoint > points, final int nSectorN, final double dRadius)
	{
		int i, iPoint;
		double [][] path = new double [3][3];
		double [] prev_segment = new double [3];
		//double [] next_segment = new double [3];
		double [] plane_norm = new double[3];
		double [] cont_point = new double [3];
		Plane3D planeBetween =new Plane3D();
		ArrayList<Line3D> extrudeLines;
		Line3D contLine;
		int contour_curr;
		int contour_next;
		ArrayList<double []> tangents;
		ArrayList<ArrayList< RealPoint >> allCountours = new ArrayList<>();
	
		int nPointsNum=points.size();
		if(nPointsNum>1)
		{
			
			//calculate tangents at each point
			tangents = CurveShapeInterpolation.getTangentsAverage(points);
	
			//first contour around first line
			
			//first two points
			for (i=0;i<2;i++)
			{
				points.get(i).localize(path[i]);
			}
			
			//vector between first two points 
			//equal to initial tangent vector
			prev_segment = tangents.get(0);
			RealPoint iniVec = new RealPoint(prev_segment );			
			RealPoint zVec = new RealPoint(0.0,0.0,1.0);
			//transform that rotates vector (0,0,1) to be aligned with the vector between first two points
			AffineTransform3D ini_rot = Intersections3D.alignVectors( iniVec,zVec);
			//also we want to move it to the beginning of the curve
			ini_rot.translate(path[0]);
			//generate a "circle/contour" with given sectors N in XY plane
			contour_curr = 0;
			allCountours.add(iniSectorContour( dRadius, nSectorN));
			
			//not translate it to the position of the beginning
			//and align it with the vector between first two point
			for(i=0;i<nSectorN;i++)
			{
				ini_rot.apply(allCountours.get(contour_curr).get(i), allCountours.get(contour_curr).get(i));
			}
			
			//just a verification, probably not needed
			//ini_rot.apply(zVec, zVec);
			
			
			//other contours, inbetween
			for (iPoint=1;iPoint<(points.size()-1);iPoint++)
			{
				//tangent vector at this point between segments
				plane_norm = tangents.get(iPoint);
				//calculate intersection
				// also there is a special case: reverse, basically do nothing
				// use the same contour
				if(LinAlgHelpers.length(plane_norm)>0.0000001)
				{					
					// plane of segments cross-section
					planeBetween.setVectors(path[1], plane_norm);
					//make a set of lines emanating from the current contour
					extrudeLines = new ArrayList<>();
					for(i=0;i<nSectorN;i++)
					{
						contLine=new Line3D();
						allCountours.get(contour_curr).get(i).localize(cont_point);
						contLine.setVectors(cont_point, prev_segment);
						extrudeLines.add(contLine);
					}
					
					//intersections
					allCountours.add(Intersections3D.planeLinesIntersect(planeBetween, extrudeLines));
					
					//test
					//if (bNormalize)
					//{
					//	allCountours.add(normalizeSector(Intersections3D.planeLinesIntersect(planeBetween, extrudeLines),points.get(iPoint),dRadius));
					//}
									
					contour_next =contour_curr+1;					
				}
				else
				{
					allCountours.add(allCountours.get(contour_curr));
					//reversed direction
					contour_next=contour_curr+1;
				}
				
				points.get(iPoint+1).localize(path[2]);
				LinAlgHelpers.subtract(path[2], path[1], prev_segment);
				LinAlgHelpers.normalize(prev_segment);

				//prepare to move forward, switch indexes
				for(i=0;i<3;i++)
				{					
					path[1][i]=path[2][i];
				}
				contour_curr=contour_next;
			}
			
			//THE last point
			points.get(nPointsNum-2).localize(path[0]);
			points.get(nPointsNum-1).localize(path[1]);
			//last segment is also just a last tangent (as with first)
			plane_norm = tangents.get(nPointsNum-1);
			
			planeBetween.setVectors(path[1], plane_norm);
			
			extrudeLines = new ArrayList<>();
			for(i=0;i<nSectorN;i++)
			{
				contLine=new Line3D();
				allCountours.get(contour_curr).get(i).localize(cont_point);
				contLine.setVectors(cont_point, plane_norm);
				extrudeLines.add(contLine);
			}
			//intersections
			allCountours.add(Intersections3D.planeLinesIntersect(planeBetween, extrudeLines));
			
			//test
			//if (bNormalize)
			//{
			//	allCountours.add(normalizeSector(Intersections3D.planeLinesIntersect(planeBetween, extrudeLines),points.get(nPointsNum-1),dRadius));
			//}
			
		}
		return allCountours;
		
	}	
	/** generates initial (first point) contour around path in XY plane **/
	public static ArrayList< RealPoint > iniSectorContour(double dRadius, final int nSectorN)
	{
		 ArrayList< RealPoint > contourXY = new  ArrayList< > ();
		 
		 double dAngleInc = 2.0*Math.PI/(nSectorN);
		 double dAngle = 0.0;
		 
		 for (int i=0;i<nSectorN;i++)
		 {
			 contourXY.add(new RealPoint(dRadius*Math.cos(dAngle),dRadius*Math.sin(dAngle),0.0));
			 dAngle+=dAngleInc;
		 }
		 
		 return contourXY;
	}

}
