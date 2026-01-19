package bigtrace.volume;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.geometry.Pipe3D;
import bigtrace.measure.Circle2DMeasure;
import bigtrace.rois.Roi3D;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import bigtrace.rois.AbstractCurve3D;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class StraightenCurve < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker{

	private String progressState;
	final public BigTrace<T> bt;
	final float fRadiusIn;
	final int nStraightenAxis;
	final int nStraightenShape;
	/** 0 - single time point, 1 - all time points **/
	final int nTimeRange;
	final ArrayList<AbstractCurve3D> curveROIArr;	
	final int nOutput;
	final String sSaveFolderPath;
	final Calibration cal;
	
	public boolean bMacroMessage = false;
		
	public StraightenCurve(final ArrayList<AbstractCurve3D> curveROIArr_, final BigTrace<T> bt_, final float fRadius_, final int nStraightenAxis_, final int nStraightenShape_, final int nTimePoint_, final int nOutput_, final String sSaveFolderPath_)
	{
		super();
		curveROIArr = curveROIArr_;
		bt = bt_;
		nTimeRange = nTimePoint_;
		fRadiusIn = fRadius_;
		nStraightenAxis = nStraightenAxis_;
		nStraightenShape = nStraightenShape_;
		nOutput = nOutput_;
		sSaveFolderPath = sSaveFolderPath_;
		cal = new Calibration();
	}
	
	@Override
	public String getProgressState() {
		
		return progressState;
	}
	
	@Override
	public void setProgressState(String state_) {
		
		progressState=state_;
		
	}
	
	@Override
	protected Void doInBackground() throws Exception {

        bt.bInputLock = true;
        bt.setLockMode(true);
        

		try {
			  Thread.sleep(1);
		  } catch (InterruptedException ignore) {}
		setProgress(1);
		setProgressState("Starting straightening..");
		
		//get the all data RAI
		//XYZTC
		RandomAccessibleInterval<T> full_RAI = bt.btData.getAllDataRAI();
		
		//output calibration
		cal.setUnit(bt.btData.sVoxelUnit);
		cal.setTimeUnit(bt.btData.sTimeUnit);
		cal.pixelWidth = bt.btData.dMinVoxelSize;
		cal.pixelHeight = bt.btData.dMinVoxelSize;
		cal.pixelDepth = bt.btData.dMinVoxelSize;
		final int nTotROIs = curveROIArr.size();  
		if(nTotROIs == 0)
			return null;
		String sRoiName;
		if(nTotROIs == 1)
		{
			sRoiName = bt.roiManager.getFullDisplayedRoiName(curveROIArr.get(0));
			IntervalView<T> extractedRAI = extractCurveRAI(curveROIArr.get(0),  full_RAI, true);
			if(extractedRAI == null)
			{
				IJ.log( "error straightening ROI "+sRoiName+", too few vertices." );
			}
			else
			{
				outputImagePlus(VolumeMisc.wrapImgImagePlusCal(extractedRAI, sRoiName + "_straight",cal, bt.btData.nNumTimepoints));
			}
		}
		else
		{
			
			for(int nRoi = 0; nRoi < nTotROIs; nRoi++)
			{
				sRoiName = bt.roiManager.getFullDisplayedRoiName(curveROIArr.get(nRoi));
				try {
					  Thread.sleep(1);
				  } catch (InterruptedException ignore) {}
				setProgress(100*nRoi/(nTotROIs));
				setProgressState("extracting ROI ("+Integer.toString(nRoi+1)+"/"+Integer.toString(nTotROIs)+") "+ sRoiName);
				IntervalView<T> extractedRAI = extractCurveRAI(curveROIArr.get(nRoi),  full_RAI, false);
				if(extractedRAI == null)
				{
					IJ.log( "error straightening ROI "+sRoiName+", too few vertices." );
				}
				else
				{
					setProgressState("storing ROI ("+Integer.toString(nRoi+1)+"/"+Integer.toString(nTotROIs)+") "+ sRoiName);
					if(!outputImagePlus(VolumeMisc.wrapImgImagePlusCal(extractedRAI, sRoiName + "_straight",cal, bt.btData.nNumTimepoints)))
					{
						IJ.log("Error saving straighten ROIs to "+sSaveFolderPath);
						break;
					}
				}
			}
			setProgressState("ROI straightening finished.");
			setProgress(100);
		}
			

		return null;
	}
	
	boolean outputImagePlus(ImagePlus ip)
	{
		if(nOutput == 0)
		{
			ip.show();
			return true;
		}
		
		return IJ.saveAsTiff(ip, sSaveFolderPath+ip.getTitle());
	}

	
	IntervalView<T> extractCurveRAI(final AbstractCurve3D curveROI, final RandomAccessibleInterval<T> all_RAI, boolean bUpdateProgressBar)
	{
		//get the curve and tangent vectors
		//curve points in SPACE units
		//sampled with dMin step
		ArrayList<RealPoint> points_space = curveROI.getJointSegmentResampled();
		
		if(points_space == null)
			return null;
		//get tangent vectors		
		ArrayList<double []> tangents = curveROI.getJointSegmentTangentsResampled();
		
		int nRadius;
		
		//take radius from ROI
		if(fRadiusIn < 0)
		{
			nRadius = (int) Math.round(0.5*curveROI.getLineThickness());			
		}
		else
		{
			nRadius = Math.round(fRadiusIn);
		}
		int dimXY = nRadius*2+1;
		
		int nTotDim = all_RAI.numDimensions();
		long [] dimS =new long[nTotDim];
		all_RAI.dimensions(dimS);
		dimS[0]=points_space.size(); //length along the line becomes X
		dimS[1]=dimXY;
		dimS[2]=dimXY;
		long nChannelN = 1;
		
		int nMinTimePoint, nMaxTimePoint;
		double [] currXYmCh = new double[nTotDim];
		//single time point
		if(nTimeRange == 0)
		{
			dimS[3] = 1;
			nMinTimePoint = curveROI.getTimePoint();
			nMaxTimePoint = curveROI.getTimePoint();
		}
		else
		{
			nMinTimePoint = 0;
			nMaxTimePoint = bt.btData.nNumTimepoints - 1;
		}
		//channels number
		nChannelN = dimS[4];

		//this is where we store straightened volume
		Img<T> out1 = Util.getSuitableImgFactory(all_RAI, all_RAI.getType()).create(new FinalInterval(dimS));
		
		
		//get a frame around line
		double [][][] rsVect =  Pipe3D.rotationMinimizingFrame(points_space, tangents, bt.btData.rotationMinFrame);

		
		//plane perpendicular to the line
		//ArrayList< RealPoint > planeNorm;
		ArrayList< ValuePair< int[],RealPoint>  > planeNorm;

		double [] current_point = new double [3];
		RealRandomAccessible<T> interpolate = Views.interpolate(Views.extendZero(all_RAI), bt.btData.nInterpolatorFactory);
		RealRandomAccess<T> ra = interpolate.realRandomAccess();
		RandomAccess<T> ra_out = out1.randomAccess();
	
		RealPoint curr_XY;
		if(bUpdateProgressBar)
		{
			setProgressState("sampling pipe " + curveROI.getName() +"..");
		}
		//go over all points
		for (int nPoint = 0;nPoint<points_space.size();nPoint++)
		{
			
			points_space.get(nPoint).localize(current_point); 

			if(nStraightenShape == 0 )
			{
				planeNorm = getNormSquareGridXYPairs(nRadius, bt.btData.dMinVoxelSize, rsVect[0][nPoint], rsVect[1][nPoint], current_point);							
			}
			else
			{			
				planeNorm = getNormCircleGridXYPairs(nRadius, bt.btData.dMinVoxelSize, rsVect[0][nPoint], rsVect[1][nPoint], current_point);			
			}
			for(int i=0;i<planeNorm.size();i++)
			{
				//current XY point coordinates
				curr_XY = new RealPoint( planeNorm.get(i).getB());
				//back to voxel units
				curr_XY = Roi3D.scaleGlobInv(curr_XY, bt.btData.globCal);
				for(int nTimePoint = nMinTimePoint;nTimePoint<=nMaxTimePoint;nTimePoint++)
				{
					for (int nCh=0;nCh<nChannelN;nCh++)
					{

						curr_XY.localize(currXYmCh);
						//time
						currXYmCh[3] = nTimePoint; 
						//channel
						currXYmCh[4] = nCh; 
						ra.setPosition(currXYmCh);
						ra_out.setPosition(new int [] {nPoint,planeNorm.get(i).getA()[1],planeNorm.get(i).getA()[0],nTimePoint-nMinTimePoint,nCh});

						ra_out.get().setReal(ra.get().getRealDouble());
					}
				}
			}
			
			if(bUpdateProgressBar)
			{
				setProgress(100*nPoint/(points_space.size()-1));
			}
			
		}
		if(bUpdateProgressBar)
		{
			setProgressState("ROI straightening finished.");
			setProgress(100);
		}
		/** in case line is along Y or Z**/
		if(nStraightenAxis>0)
		{
			return Views.permute(out1, 0, nStraightenAxis);
		}
		
		return Views.interval(out1,out1);
		
	}
	
	/** generates initial square XY plane sampling with data from -nRadius till nRadius values (in dPixSize units) in XY 
	 * centered around (0,0)**/
	public static ArrayList< RealPoint > iniNormPlane(final int nRadius,final double dPixSize)
	{
		 ArrayList< RealPoint > planeXY = new  ArrayList< > ();
		 		 
		 for (int i=-nRadius;i<=nRadius;i++)
		 {
			 for (int j=-nRadius;j<=nRadius;j++)
			 {
				 planeXY.add(new RealPoint(i*dPixSize,j*dPixSize,0.0));
			 }
		 }
		 
		 return planeXY;
	}
	
	/** generates XY sampling grid coordinates in the shape of a square from -nRadius till nRadius values (in dPixSize units) in XY plane 
	 * defined by two normalized perpendicular vectors X and Y and with center at c **/
	public static ArrayList< ValuePair< int[],RealPoint> > getNormSquareGridXYPairs(final int nRadius,final double dPixSize,final double [] x,final double [] y, final double [] c)
	{
		 ArrayList< ValuePair< int[],RealPoint >> squareXY = new  ArrayList< > ();
		 double [] xp = new double[3];
		 double [] yp = new double[3];
		 
		 for (int i=-nRadius;i<=nRadius;i++)
		 {
			 for (int j=-nRadius;j<=nRadius;j++)
			 {
				 LinAlgHelpers.scale(x, i*dPixSize, xp);
				 LinAlgHelpers.scale(y, j*dPixSize, yp);
				 LinAlgHelpers.add(xp, yp,xp);
				 LinAlgHelpers.add(xp, c,xp);
				 squareXY.add(new ValuePair<>(new int[] {i+nRadius, j+nRadius},new RealPoint(xp)));
			 }
		 }
		 
		 return squareXY;
	}
	
	/** generates XY sampling grid coordinates in the shape of a circle from -nRadius till nRadius values (in dPixSize units) in XY plane 
	 * defined by two normalized perpendicular vectors X and Y and with center at c **/
	public static ArrayList< ValuePair< int[],RealPoint> > getNormCircleGridXYPairs(final int nRadius,final double dPixSize,final double [] x,final double [] y, final double [] c)
	{
		 ArrayList< ValuePair< int[],RealPoint> > circleXY = new  ArrayList< > ();
		 
		 Circle2DMeasure circle = new Circle2DMeasure();
		 circle.setRadius( nRadius );
		 double [] currentPoint = new double[3];
		 double [] xp = new double[3];
		 double [] yp = new double[3];
		 
		 while (circle.cursorCircle.hasNext())
		 {
			 circle.cursorCircle.fwd();
			 circle.cursorCircle.localize(currentPoint);
			 LinAlgHelpers.scale(x, currentPoint[0]*dPixSize, xp);
			 LinAlgHelpers.scale(y, currentPoint[1]*dPixSize, yp);
			 LinAlgHelpers.add(xp, yp,xp);
			 LinAlgHelpers.add(xp, c,xp);
			 circleXY.add(new ValuePair<>(new int[] {(int)Math.round(currentPoint[0])+nRadius, (int)Math.round( currentPoint[1])+nRadius},new RealPoint(xp)));
		 }		 

		 return circleXY;
	}

	
    /*
     * Executed in event dispatching thread
     */
    @Override
    public void done() 
    {
    	//see if we have some errors
    	try {
    		get();
    	} 
    	catch (ExecutionException e) {
    		e.getCause().printStackTrace();
    		String msg = String.format("Unexpected problem during straightening: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} catch (InterruptedException e) {
    		// Process e here
    	}
		//unlock user interaction
    	bt.bInputLock = false;
        bt.setLockMode(false);
        if(bMacroMessage)
        	IJ.log( "BigTrace: done saving straightened volumes to " + sSaveFolderPath);

    }
}
