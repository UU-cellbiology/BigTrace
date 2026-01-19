package bigtrace.geometry;

import java.util.ArrayList;

import bigtrace.BigTraceData;
import bigtrace.rois.Roi3D;
import net.imglib2.RealPoint;
/** cubic spline interpolation wrapper for each 3D coordinates of points set **/
public class SplineCurve3D {
		
	/** interpolation components for each coordinate **/
	CubicSpline [] interpXYZ = new CubicSpline[3];
	
	/** nodes (arbitrary parametrization, usually as a length of the input polyline)**/
	double [] xnodes = null;
	
	/** arclength of spline through the nodes **/
	double [] arclength = null;
	
	/** reparametrization of arclength spline**/
	CubicSpline arcToNodes = null;
	
	final BigTraceData<?> btdata;
		
	/** do natural cubic spline interpolation **/
	public SplineCurve3D(ArrayList<RealPoint> points, final BigTraceData<?> btdata_)
	{
		btdata = btdata_;
		init(points, 0);
	}
	
	/** do cubic spline interpolation with end derivatives estimated 
	 * from nDeriveEst points from each end **/
	public SplineCurve3D(ArrayList<RealPoint> points, final int nDeriveEst, final BigTraceData<?> btdata_)
	{
		btdata = btdata_;
		init(points, nDeriveEst);
	}
	
	public void init(ArrayList<RealPoint> points, final int nDeriveEst)
	{
		double [][] coords = new double[3][points.size()];
		double [] curr_point = new double[3]; 
		xnodes = Roi3D.getSegmentTabLength(points);
		int i,d;
		for (i =0;i<points.size();i++)
		{
			points.get(i).localize(curr_point);
			for (d=0;d<3;d++)
			{
				coords[d][i]=curr_point[d];
			}
		}
		for (d=0;d<3;d++)
		{
			if(nDeriveEst == 0)
			{
				interpXYZ[d] = new CubicSpline(xnodes, coords[d]);
			}
			else
			{
				interpXYZ[d] = new CubicSpline(xnodes, coords[d], nDeriveEst);
			}
		}
		
		//init arclenght reparametrization
		initArcLength();

		
	}
	

	/** interpolates vectors at xp positions along the arclength of the curve **/
	public ArrayList<RealPoint> interpolate(double [] xp)
	{
		
		int nP = xp.length;
		
		double [] curr_point = new double[3]; 
		double currNodePos;
		ArrayList<RealPoint> out = new ArrayList<>();
		//just in case
		if (xnodes == null)
			return null;
		for (int i=0;i<nP;i++)
		{
			for (int d=0;d<3;d++)
			{
				currNodePos = arcToNodes.evalSpline(xp[i]);
				curr_point[d] = interpXYZ[d].evalSpline(currNodePos);				
			}
			out.add(new RealPoint(curr_point));
			
		}
		return out;
	}
	
	/** interpolates slopes at xp positions along the arclength of the curve **/
	public ArrayList<double []> interpolateSlopes(double [] xp)
	{
		
		int nP = xp.length;
		double [] curr_point = new double[3];
		double currNodePos;
		ArrayList<double []> out = new ArrayList<>();
		for (int i=0;i<nP;i++)
		{
			for (int d=0;d<3;d++)
			{
				currNodePos = arcToNodes.evalSpline(xp[i]);
				curr_point[d] = interpXYZ[d].evalSlope(currNodePos);				
			}
			out.add(curr_point.clone());
			
		}
		return out;
	}
	
	/** arc lenght of the spline-fitted curve  **/
	public double getMaxArcLength()
	{
		if(arclength == null)
		{
			return Double.NaN;
		}
		//arclength integration verification
		//double len1 = verifyIntegration();
		//double len2 = arclength[arclength.length-1];
		//double diff = Math.abs(len1-len2);
		return arclength[arclength.length-1];
		
	}
	
	/** function calculates arclength of the fitted spline 
	 * and reparametrizes interpolation to this value
	 ***/
	public void initArcLength()
	{		

		final double approxL = xnodes[xnodes.length-1];

		final int nNewPoints = (int) Math.floor(approxL / btdata.dMinVoxelSize);
		final double [] xLSample = new double[nNewPoints + 2];
		
		for(int i = 1; i < nNewPoints; i++)
		{
			xLSample[i] = i * btdata.dMinVoxelSize;
		}
		xLSample[nNewPoints+1] = approxL;

		xLSample[nNewPoints] = 0.5*(approxL+xLSample[nNewPoints-1]);

		arclength = getTabulatedArcLength(xLSample);
		
		//reparametrize arclenth to initial arbitrary (polyline length) parametrization
		arcToNodes = new CubicSpline(arclength, xLSample, 2);
	
	}
	
	/** arc lenght of 3D spline-fitted curve calculated using Gaussian quadrature at two points
	 * at the each interval**/
	public double [] getTabulatedArcLength(final double [] nodes)
	{
		//just is case 
		if(nodes == null)
		{
			return null;
		}
		final int nNodesN = nodes.length;
		double [] out = new double [nNodesN];
		double diff,aver,curr;
		
		//integration using Simpson's  rule	
		/*
		out[0]=0.0;
		//integrate spline length
		for (int i=1;i<nNodesN; i++)
		{
			curr=0.0;
			diff= (nodes[i]-nodes[i-1])/6.0;
			aver = 0.5*(nodes[i]+nodes[i-1]);

			curr+=getIntegrFunction(nodes[i]);
			curr+=getIntegrFunction(nodes[i-1]);
			curr+=4*getIntegrFunction(aver);
			curr*=diff;
			out[i]=out[i-1]+curr;
		}
		*/
		//Gaussian 2nd order
		/**/
		final double evalGauss = 1.0/Math.sqrt(3.0);
		out[0] = 0.0;
		//integrate spline length
		for (int i=1;i<nNodesN; i++)
		{
			curr = 0.0;
			diff= 0.5*(nodes[i]-nodes[i-1]);
			aver = 0.5*(nodes[i]+nodes[i-1]);

			curr += getIntegrFunction(diff*evalGauss+aver);
			curr += getIntegrFunction(aver-diff*evalGauss);
			curr *= diff;
			out[i] = out[i-1]+curr;
		}
		
		
		/**/
		/*
		//Gaussian 3d order
		final double evalGauss = Math.sqrt(3.0/5.0);// 1/sqrt(3)
		out[0]=0.0;
		//integrate spline length
		for (int i=1;i<nNodesN; i++)
		{
			curr=0.0;
			diff= 0.5*(nodes[i]-nodes[i-1]);
			aver = 0.5*(nodes[i]+nodes[i-1]);

			curr+=(5.0/9.0)*getIntegrFunction(diff*evalGauss+aver);
			curr+=(5.0/9.0)*getIntegrFunction(aver-diff*evalGauss);
			curr+=(8.0/9.0)*getIntegrFunction(aver);

			curr*=diff;
			out[i]=out[i-1]+curr;
		}	
		*/	
		return out;
		
	}
	/** helper function for the calculation of arclength **/
	private double getIntegrFunction(double x)
	{
		double out = 0.0;
		double v;
		for (int d=0;d<3; d++)
		{
			v = interpXYZ[d].evalSlope(x);
			out += v*v;
		}
		out = Math.sqrt(out);
		return out;
		
	}
	/** compare arclength integration vs small step polyline measurements 
	 * it is a test function used for verification**/
	/*
	private double verifyIntegration()
	{
		int nPointsN = 100000;
		double maxL = xnodes[xnodes.length-1];
		double nStep = maxL/ (nPointsN-1);
		double [] sampleNodes = new double [nPointsN];
		double [] curr_point = new double[3];
		for (int i = 0; i<nPointsN;i++)
		{
			sampleNodes[i]=i*nStep;
		}
		ArrayList<RealPoint> points = new ArrayList<RealPoint>();
		for (int i=0;i<nPointsN;i++)
		{
			for (int d=0;d<3;d++)
			{
				
				curr_point[d]=interpXYZ[d].evalSpline(sampleNodes[i]);				
			}
			points.add(new RealPoint(curr_point));
			
		}
		return Roi3D.getSegmentLength(points);
	}
	*/
}
