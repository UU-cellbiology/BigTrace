package bigtrace.geometry;


import java.util.ArrayList;

import bigtrace.BigTraceData;
import bigtrace.rois.Roi3D;
import bigtrace.volume.VolumeMisc;
import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;

/** class performing shape interpolation for PolyLine3D and LineTrace3D,
 * it can do linear and cubic spline interpolation and tangents calculation
 * along the curve, resampling length, etc 
 * Depends on BigTraceData.globCal calibration **/
public class CurveShapeInterpolation {
	
	/** type of ROI **/
	private int nRoiType = -1;
	
	/** linear intepolator **/
	private LerpCurve3D linInter = null;
	
	/** spline intepolator **/
	private SplineCurve3D splineInter = null;
	
	/** current interpolation. The value of -1 means interpolators were not initialized or not up-to-date **/
	private int currInterpolation = -1;
	
	/** reference curve points in SPACE coordinates **/
	ArrayList<RealPoint> points_curve = null;
	
	boolean bInit = false;
		
	final BigTraceData<?> btdata;
	
	public CurveShapeInterpolation (final int nRoiType_, final BigTraceData<?> btdata_)
	{
		btdata = btdata_;
		nRoiType = nRoiType_;
		bInit = false;
	}
	
	public CurveShapeInterpolation (final ArrayList<RealPoint> points, final int nShapeInterpolation,final int nRoiType_, final BigTraceData<?> btdata_)
	{
		btdata = btdata_;
		nRoiType = nRoiType_;
		init(points, nShapeInterpolation);
	}
	
	/** depending on the Roi3D type and interpolation type, 
	 * builds different "reference" curves.
	 * Provided points in array are assumed to be in VOXEL coordinates.
	 * It inits either linInter or splineInter, only one of them could be not null **/
	public boolean init(final ArrayList<RealPoint> points, final int nShapeInterpolation)
	{
		
		if(points.size() > 1)
		{
				currInterpolation = nShapeInterpolation;
				points_curve = new ArrayList<>();
				switch (currInterpolation)
				{
					case BigTraceData.SHAPE_Voxel:
						if(nRoiType == Roi3D.POLYLINE)
						{
							//sample data between points using Bresenham
							points_curve = Roi3D.scaleGlob(getJointSegmentBresenhamPolyLine(points), btdata.globCal);
						}
						else
						{
							//keep original trace
							points_curve = Roi3D.scaleGlob(points, btdata.globCal);
						}
						//init linear interpolator
						linInter = new LerpCurve3D(points_curve);
						splineInter = null;
						break;
					case BigTraceData.SHAPE_Smooth:
						if(nRoiType == Roi3D.POLYLINE)
						{							
							//sample segments between points with pixel step of 1
							points_curve = Roi3D.scaleGlob(getJointSegmentSmoothPolyLine(points), btdata.globCal);
						}
						else
						{
							//smooth positions of points
							points_curve = Roi3D.scaleGlob(getSmoothVals(points, btdata.nSmoothWindow), btdata.globCal);							
						}
						//init linear interpolator
						linInter = new LerpCurve3D(points_curve);
						splineInter = null;
						break;
					case BigTraceData.SHAPE_Spline:
						if(nRoiType == Roi3D.POLYLINE)
						{
							if(points.size()==2)
							{
								points_curve = Roi3D.scaleGlob(points, btdata.globCal);
								linInter = new LerpCurve3D(points_curve);
								splineInter = null;
							}
							else
							{
								//perform spline interpolation
								//estimating end derivatives
								splineInter = new SplineCurve3D( Roi3D.scaleGlob(points, btdata.globCal), 2, btdata);
								linInter = null;
							}
						}
						else
						{
							if(points.size() == 2)
							{
								points_curve = Roi3D.scaleGlob(points, btdata.globCal);
								linInter = new LerpCurve3D(points_curve);
								splineInter = null;
							}
							else
							{
								//smooth positions of points
								points_curve = Roi3D.scaleGlob(getSmoothVals(points, btdata.nSmoothWindow), btdata.globCal);		
								//take some points along the curve as spline nodes
								final ArrayList< RealPoint > sparse_points = getSplineSparsePoints(points_curve, btdata.nSmoothWindow);
								if(sparse_points.size() == 2)
								{
									points_curve = sparse_points;
									linInter = new LerpCurve3D(points_curve);
									splineInter = null;
								}
								else
								{
									splineInter = new SplineCurve3D(sparse_points, 2, btdata);
									linInter = null;
								}
							}
						}
						
						break;
				}
				bInit = true;
				return true;
		}
		return false;
	}
	
	
	/** function returns joint segment for polyline ROI building bresenham line between points **/
	ArrayList<RealPoint> getJointSegmentBresenhamPolyLine(final ArrayList<RealPoint> points)
	{
		final ArrayList<RealPoint> out = new ArrayList<>();
		ArrayList<RealPoint> segment = new ArrayList<>();
		double [] pos1 = new double [3];
		double [] pos2 = new double [3];
		out.add(points.get(0));
		for(int i=1;i<points.size(); i++)
		{
			points.get(i-1).localize(pos1);
			points.get(i).localize(pos2);
			if(LinAlgHelpers.distance(pos1, pos2)>0.000000000001)
			{
				segment = VolumeMisc.BresenhamWrap(points.get(i-1), points.get(i));
				for(int j = 1; j<segment.size();j++)
				{
					out.add(segment.get(j));
				}
			}
		}
		return out;
	}
	/** samples segments between points with a step of approx 1 voxel,
	 * making curve pass through points**/
	private static ArrayList<RealPoint> getJointSegmentSmoothPolyLine(final ArrayList<RealPoint> points)
	{
		final ArrayList<RealPoint> out = new ArrayList<>();

		int nPoints;
		double [] pos1 = new double [3];
		double [] pos2 = new double [3];
		double [] posCurr = new double [3];
		double nStep;
		out.add(points.get(0));
		for(int i=1;i<points.size(); i++)
		{
			points.get(i-1).localize(pos1);
			points.get(i).localize(pos2);
			double length = LinAlgHelpers.distance(pos1, pos2);
			
			if(LinAlgHelpers.distance(pos1, pos2)>0.000000000001)
			{
				nPoints = (int) Math.ceil(length);
				if(nPoints<2)
				{
					out.add(points.get(i));
				}
				else
				{
					nStep = length/nPoints;
					LinAlgHelpers.subtract(pos2, pos1, pos2);
					LinAlgHelpers.normalize(pos2);
					for (int j=1;j<nPoints;j++)
					{
						LinAlgHelpers.scale(pos2, nStep*j, posCurr);
						LinAlgHelpers.add(pos1, posCurr, posCurr);
						out.add(new RealPoint(posCurr));
					}
				}
			}
		}
		return out;
	}
	
	/**
	 * returns smoothed coordinates along each axis
	 * using running average with window defined by 
	 * static variable of Smoothing class (one for everywhere for now, it is static BigTraceData.nSmoothWindow);
	 * 
	 * Boundaries (ends) are handled in a special way:
	 * 1) end points' positions are not averaged
	 * 2) moving average window's size when dealing with points that are close to the end points is reduced. 
	 *  If the distance (in numbers/points) between current point and end point
	 *  is less than half of average window, it becomes new half average window.
	 *  It works similar to Matlab's "smooth()" function;
	 * **/
	private static ArrayList< RealPoint > getSmoothVals (final ArrayList< RealPoint > points, final int nSmoothWindow)
	{
		ArrayList< RealPoint > out = new ArrayList< >();
		double [][] coords= new double[points.size()][3];
		double [] aver = new double[3];
		int i,j,k;
		//int nCount;
		int nHalfWindow = (int)Math.floor(nSmoothWindow * 0.5);
		int nCurrWindow = 0;

		if(points.size()<3)
		{
			return points;
		}


		for (i = 0; i < points.size(); i++)
		{
				points.get(i).localize(coords[i]);
		}
		
		out.add(new RealPoint(points.get(0)));
		
		for ( i = 1; i < points.size() - 1; i++)
		{
			//nCount = 0;
			
			for( j = 0; j < 3; j++)
			{
				aver[ j ] = 0.0;
			}
					
			nCurrWindow = Math.min(Math.min(nHalfWindow,i), Math.min(nHalfWindow, points.size()-i-1));
			
			for(j = (i - nCurrWindow); j < (i + nCurrWindow + 1); j++)
			{
				for(k = 0; k < 3; k++)
				{
					aver[ k ] += points.get(j).getDoublePosition(k);
				}
				//nCount++;
			}
			LinAlgHelpers.scale(aver, 1.0/(nCurrWindow*2+1), aver);
			out.add(new RealPoint(aver));
		}
		out.add(new RealPoint(points.get(points.size()-1)));
		return out;
	}
	
	/** given a set of points defining polyline the function calculates 
	 * tangent vector (normalized) at each point location as a average angle 
	 * between two adjacent segments **/
	public static ArrayList<double []> getTangentsAverage(final ArrayList< RealPoint > points)
	{
		int i,j;
		ArrayList<double []> tangents = new ArrayList<>();
		double [][] path = new double [3][3];
		double [] prev_segment = new double [3];
		double [] next_segment = new double [3];
		double [] tanVector = new double [3];
		int nPointsNum=points.size();
		if(nPointsNum>1)
		{
			//first two points
			for (i=0;i<2;i++)
			{
				points.get(i).localize(path[i]);
			}
			//vector between first two points 
			LinAlgHelpers.subtract(path[1], path[0], prev_segment );
			LinAlgHelpers.normalize(prev_segment);
			tangents.add(prev_segment.clone());
			//the middle
			for (i=1;i<(points.size()-1);i++)
			{
				//next segment
				points.get(i+1).localize(path[2]);
				LinAlgHelpers.subtract(path[2], path[1], next_segment);
				LinAlgHelpers.normalize(next_segment);

				//2) average angle/vector between segments
				LinAlgHelpers.add(prev_segment, next_segment, tanVector);
				LinAlgHelpers.scale(tanVector, 0.5, tanVector);
				//reversal, special case
				if(Math.abs(LinAlgHelpers.length(tanVector))<0.0000000001)
				{
					tanVector= prev_segment.clone();
				}
					
				LinAlgHelpers.normalize(tanVector);
				tangents.add(tanVector.clone());
				
				//prepare to move forward
				for(j=0;j<3;j++)
				{
					prev_segment[j]=next_segment[j];
					path[1][j]=path[2][j];
				}

			}
			points.get(nPointsNum-2).localize(path[0]);
			points.get(nPointsNum-1).localize(path[1]);
			LinAlgHelpers.subtract(path[1],path[0],tanVector);
			LinAlgHelpers.normalize(tanVector);
			tangents.add(tanVector.clone());
		}
		return tangents;
	}
	
	/** returns approximately every BigTraceData.nSmoothWindow's point from array
	 * (and in addition, mandatory boundary (end) points **/
	private static ArrayList<RealPoint> getSplineSparsePoints(final ArrayList< RealPoint > points, final int nSmoothWindow)
	{
		ArrayList< RealPoint > out = new ArrayList< >();
		int nPointsN = points.size();
		int nStep, i;
		out.add(new RealPoint(points.get(0)));
		if (nPointsN < nSmoothWindow)
		{	
			nStep = 1;
		}
		else
		{
			nStep = (int)Math.ceil((float)nPointsN/(float)nSmoothWindow);
			nStep = Math.round((float)nPointsN/(float)nStep);
		}
		for(i = nStep;i<nPointsN;i+=nStep)
		{
			out.add(new RealPoint(points.get(i)));
		}
		//just in case, last point
		if ((i-nStep)!=(nPointsN-1))
		{
			out.add(new RealPoint(points.get(nPointsN-1)));
		}
		
		return out;
		
	}
	/** returns points array, passing through reference curve points in SPACE coordinates**/
	public ArrayList<RealPoint> getVerticesVisual()
	{
		if(linInter != null)
		{
			return points_curve;
		}
		return getPointsSplineVisual();
		
	}
	
	/** returns points array, passing through reference curve points in SPACE coordinates **/
	public ArrayList<double []> getTangentsVisual()
	{
		if(linInter != null)
		{
			return getTangentsAverage(points_curve);
		}
		return getTangentsSplineVisual();
	}
	

	private ArrayList<RealPoint> getPointsSplineVisual()
	{
		
		double dLength;
		int nNewPoints;
		double [] xLSample;
		int i;
		dLength = splineInter.getMaxArcLength();
		nNewPoints = (int) Math.floor(dLength / btdata.dMinVoxelSize);
		xLSample = new double[nNewPoints+1];
		double dStep = dLength/nNewPoints;
		for(i = 0; i <= nNewPoints; i++)
		{
			xLSample[i] = i * dStep;
		}
		return splineInter.interpolate(xLSample);
	}
	
	private ArrayList<double []> getTangentsSplineVisual()
	{
	
		double dLength;
		int nNewPoints;
		double [] xLSample;
		int i;
		waitForInit();
		dLength = splineInter.getMaxArcLength();
		nNewPoints =(int) Math.floor(dLength / btdata.dMinVoxelSize);
		xLSample = new double[nNewPoints+1];
		double dStep = dLength/nNewPoints;
		for(i = 0; i <= nNewPoints;i++)
		{
			xLSample[ i ] = i * dStep;
		}
		return splineInter.interpolateSlopes(xLSample);
	}
	
	public ArrayList<RealPoint> getVerticesResample()
	{
	
		double dLength;
		int nNewPoints;
		double [] xLSample;
		int i;
		waitForInit();
		if(linInter != null)
		{
			dLength = linInter.getMaxLength();
		}
		else
		{
			if(splineInter == null)
				return null;
			dLength = splineInter.getMaxArcLength();
		}
		nNewPoints = (int) Math.ceil(dLength / btdata.dMinVoxelSize);
		xLSample = new double[nNewPoints];
		for(i = 0; i < nNewPoints; i++)
		{
			xLSample[ i ] = i * btdata.dMinVoxelSize;
		}
		if(linInter != null)
		{
			return linInter.interpolate(xLSample);
		}
		return splineInter.interpolate(xLSample);
	}
	
	void waitForInit()
	{
		//in case it was not initialized yet
		if(!bInit)
		{
			while(!bInit)
			{
				try
				{
					Thread.sleep( 1 );
				}
				catch ( InterruptedException exc )
				{
					exc.printStackTrace();
				}
			}
		}
	}
	
	public ArrayList<double[]> getTangentsResample()
	{
		
		double dLength;
		int nNewPoints;
		double [] xLSample;
		int i;
		
		waitForInit();
		if(linInter != null)
		{
			dLength = linInter.getMaxLength();
		}
		else
		{
			dLength = splineInter.getMaxArcLength();
		}
		nNewPoints = (int) Math.ceil(dLength/ btdata.dMinVoxelSize);
		xLSample = new double[nNewPoints];
		for(i = 0; i < nNewPoints;i++)
		{
			xLSample[i] = i * btdata.dMinVoxelSize;
		}
		if(linInter != null)
		{
			return linInter.interpolateSlopes(xLSample);
		}
		return splineInter.interpolateSlopes(xLSample);
	}
	
	/** returns the length of the reference "generating" curve **/
	public double getLength()
	{
		waitForInit();
		if(linInter != null)
		{
			return linInter.getMaxLength();
		}
		return splineInter.getMaxArcLength();
	}
}
