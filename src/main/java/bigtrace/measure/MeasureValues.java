package bigtrace.measure;


import net.imglib2.RealPoint;

public class MeasureValues implements Measurements {
	
	public String roiName;
	public String roiGroupName;
	public float pointSize;
	public float lineThickness;
	public int nTimePoint;
	public int roiType;
	
	public double volume;
	public double length;
	
	public double [] intensity_values = null;
	public double mean;
	public double stdDev;
	public double integrated;

	public double [] lin_intensity_values = null;	
	public double mean_linear;
	public double std_linear;
	
	public double straightness;
	public double endsDistance;
	
	/** coordinates of the ends **/
	public RealPoint [] ends;
	
	/** normalized vector pointing between ends **/
	public RealPoint direction = null;


	void setRoiName(String roiname_)
	{
		this.roiName = new String(roiname_);	
	}
	
	void setRoiGroupName(String groupname_)
	{
		this.roiGroupName = new String(groupname_);	
	}
	
	public void setRoiType(final int roiType_)
	{
		this.roiType = roiType_;
	}
	
	void setTimePoint(int nTimePoint_)
	{
		nTimePoint = nTimePoint_;
	}
	
	String getRoiName()
	{
		return new String(roiName);
	}
	
	String getRoiGroupName()
	{
		return new String(roiGroupName);
	}
	
	public int getRoiType() 
	{		
		return roiType;
	}
	
	public int getTimePoint() 
	{		
		return nTimePoint;
	}
	
	void setPointSize(float pointSize_)
	{
		pointSize = pointSize_;
	}
	
	public float getPointSize()
	{
		return pointSize;
	}
	
	void setLineThickness(float lineThickness_)
	{
		lineThickness = lineThickness_;
	}
	
	public float getLineThickness()
	{
		return lineThickness;
	}
	public double getFirstNineMeasurements (final int measure)
	{
		switch (measure)
		{
		case Measurements.VOLUME:
			return volume;
		case Measurements.LENGTH:
			return length;
		case Measurements.MEAN:
			return mean;
		case Measurements.STD_DEV:
			return stdDev;
		case Measurements.MEAN_LINEAR:
			return mean_linear;
		case Measurements.STD_LINEAR:
			return std_linear;
		case Measurements.INTEGRATED:
			return integrated;
		case Measurements.DIST_ENDS:
			return endsDistance;
		case Measurements.STRAIGHTNESS:
			return straightness;
		}
		System.out.print( "Warning, requested Measurements values outside the range." );
		return Double.NaN;
	}
}
