package bigtrace.animation;


import io.scif.codec.CompressionType;
import io.scif.config.SCIFIOConfig;
import io.scif.img.ImgSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.StopWatch;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.geometry.Pipe3D;
import bigtrace.geometry.Plane3D;
import bigtrace.io.UnCoilHDF5Saver;
import bigtrace.rois.AbstractCurve3D;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Roi3D;
import bigtrace.volume.StraightenCurve;
import bigtrace.volume.VolumeMisc;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

public class UnCoilAnimation < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker
{
	
	private String progressState;
	
	public AbstractCurve3D inputROI;
	
	/** total number of intermediate frames **/
	public int nFrames = 100;
	
	/** 0 - generate ROIs only, 1 - ROIs and volumes **/
	public int nUnCoilTask = 0;
	
	/** interval containing all ROIs **/
	public FinalInterval unionInterval;
	
	/** plugin instance **/
	BigTrace<T> bt;
	
	/** frames of unbending ROIs **/
	ArrayList <double [][][]> allFrames;
	
	/** centerlines of unbending ROIs **/
	ArrayList <ArrayList<RealPoint>> allSegments;
	
	public boolean bUseTemplate = false;

	IntervalView <T> template = null;
	
	boolean bAddROIs = false;
		
	ArrayList <AbstractCurve3D> allRois;
	
	/** all intervals for all ROIs **/
	public ArrayList <FinalInterval> allIntervals;
	
	double [] finalOrientation = null;
	
	public String sSaveFolderPath;

	/** 0 - BDV HDF5, 1 - standard TIF, 2 - compressed TIF **/
	public int nUnCoilExport = 0;
	
	public UnCoilAnimation(final BigTrace<T> bt_)
	{
		bt = bt_;	
	}
	@Override
	public String getProgressState() 
	{		
		return progressState;
	}
	
	@Override
	public void setProgressState(String state_) 
	{		
		progressState = state_;		
	}
	
	@Override
	protected Void doInBackground() throws Exception 
	{
        bt.bInputLock = true;
        bt.setLockMode(true);
        

        try 
        {
        	Thread.sleep(1);
        } 
        catch (InterruptedException ignore) {}
        setProgress(1);
        setProgressState("creating straighten animation...");

		//generate only rois for show
		if(nUnCoilTask==0)
		{
			bAddROIs = true;
			generateROIs(inputROI);
			
		}
		//generate ROIs and volumes
		if(nUnCoilTask == 1)
		{
			bAddROIs = true;
			
			//need to edit ROIs to fit the new volumes
			if(!generateROIs(inputROI))
				return null;
			if(nUnCoilExport == 0)
			{
				setProgress(0);
			    setProgressState("saving HDF5 (see log)...");
				UnCoilHDF5Saver<T> saveH5 = new UnCoilHDF5Saver<>(this, bt);
				saveH5.exportUnCoil();
			}
			else
			{
				generateAllVolumesTIFF();
			}
		}

		return null;
		
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
    		String msg = String.format("Unexpected problem during straightening animation generation: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} catch (InterruptedException e) {
    		// Process e here
    	}
    	setProgress(100);
        setProgressState("straighten animation done.");
		//unlock user interaction
    	bt.bInputLock = false;
        bt.setLockMode(false);

    }
    
	/** Given input ROI (of AbstractCurve type), 
	generates output ROIs that gradually are straightened.
	They are added to ROI manager at subsequent time frames **/
	public boolean generateROIs(final AbstractCurve3D firstLine)
	{
		
        setProgress(0);
        setProgressState("Generating ROIs...");
		allRois = new ArrayList<>();
		allRois.add( firstLine );
		
		ArrayList<double []> tangents = firstLine.getJointSegmentTangentsResampled();
		
		//get all the points along the line with pixel sampling in scaled coords
		ArrayList<RealPoint> segment = firstLine.getJointSegmentResampled();
		allSegments = new ArrayList<>();
		allSegments.add( segment );
		
		//get a frame around line in SCALED
		double [][][] rsVect =  Pipe3D.rotationMinimizingFrame(segment, tangents);

		allFrames = new ArrayList<>();
		allFrames.add( rsVect );
		
		//get all the points along the line with pixel sampling in UNSCALED coords
		//segment = Roi3D.scaleGlobInv(segment,BigTraceData.globCal);
		
		//DEBUG NORMALES
		//addNormalROI(segment,rsVect,firstLine.getLineThickness());
		
		int nTotPoints = segment.size();
		
		//if using template, check its dimensions.
		if(bUseTemplate)
		{
			long [] dimsTemplate = template.dimensionsAsLongArray();
			boolean bTemplateFits = true;
			int dimYZ =(int)( Math.round(0.5*inputROI.getLineThickness()))*2+1;

			if(dimsTemplate[0] != segment.size())
				bTemplateFits  = false;
			if(dimsTemplate[1]!=dimYZ || dimsTemplate[2]!=dimYZ)
				bTemplateFits  = false;

			
			if(!bTemplateFits)
			{
				String dTempDims = Long.toString( dimsTemplate[0] );
				for(int i=1;i<template.numDimensions();i++)
				{
					dTempDims = dTempDims +"x"+ Long.toString( dimsTemplate[i] );
				}
				String sExpectedDims = Integer.toString( segment.size() );
				sExpectedDims = sExpectedDims +"x" +Integer.toString( dimYZ );
				sExpectedDims = sExpectedDims +"x" +Integer.toString( dimYZ );
				sExpectedDims = sExpectedDims +"x" +Integer.toString( bt.btData.nTotalChannels );
				IJ.log( "Provided template size for the straighten/uncoil animation is wrong! Aborting." );
				IJ.log( "Provided template dimensions (XYZC):" + dTempDims);
				IJ.log( "Expected dimensions (XYZC):" + sExpectedDims);
				IJ.log( "Make sure it is the same ROI and that the template straightening ");
				IJ.log( "happened with the same ROI Shape Interpolation/Smoothing window settings.");
				return false;
			}
		}
				
		//specify the final starting end orientation.
		// it is a vector added in the beginning,
		// which specifies, where the final straight line ROI is gonna point.
		double [] iniOrientP = segment.get( 0 ).positionAsDoubleArray();
		if(finalOrientation == null)
		{
			finalOrientation = new double[3];
			double [] nextP = segment.get( 1 ).positionAsDoubleArray();
			LinAlgHelpers.subtract( nextP,iniOrientP, finalOrientation);
			LinAlgHelpers.normalize( finalOrientation );
		}
		else
		{
			//just in case
			LinAlgHelpers.normalize( finalOrientation );			
		}		
		
		LinAlgHelpers.subtract( iniOrientP, finalOrientation, iniOrientP );		
		
		RealPoint iniOrientRP = new RealPoint(iniOrientP);	
		
		
		//angles at each triplets of points
		final double [] lineAngles = new double[nTotPoints-1];
		//planes containing each triplets of points
		final Plane3D [] planes = new Plane3D[nTotPoints-1];
		
		
		lineAngles[0] = calculateAngle(iniOrientRP,segment.get(0),segment.get(1));
		planes[0] = new Plane3D(iniOrientRP,segment.get(0),segment.get(1));
		
		for(int i = 0; i<nTotPoints-2;i++)
		{
			lineAngles[i+1] = calculateAngle(segment.get( i ),segment.get(i+1),segment.get(i+2));
			planes[i+1] = new Plane3D(segment.get(i),segment.get(i+1),segment.get(i+2));
		}	
		
		final double [] q = new double[4];
		final double [] joint = new double [3];
		double [] notrotated = new double [3];
		double [] moved = new double [3];
		double [] rotated = new double [3];
		
		for (int i=1; i<nFrames ; i++)
		{
			setProgress(100*i/(nFrames-1));
			setProgressState("generating ROI ("+Integer.toString(i+1)+"/"+Integer.toString(nFrames)+")");
			ArrayList<RealPoint> segment_new = new ArrayList<>();
			for (int nPoint = 0; nPoint<nTotPoints; nPoint++)
			{
				segment_new.add( segment.get( nPoint ) );
			}
			//new frame around ROI
			double [][][] newFrame = new double [2][nTotPoints][3];
			for(int ii=0;ii<2;ii++)
				for(int jj=0;jj<nTotPoints;jj++)
					for(int kk=0;kk<3;kk++)
						newFrame[ii][jj][kk]=rsVect[ii][jj][kk];
			
			for(int nTrio = nTotPoints-2; nTrio>=0; nTrio--)
			{
				//if already rotated, no need to rotate
				if(Math.abs( lineAngles[nTrio])>2.*Double.MIN_VALUE)
				{
					LinAlgHelpers.quaternionFromAngleAxis(planes[nTrio].n, lineAngles[nTrio]*i/(nFrames-1), q);
					segment_new.get(nTrio).localize( joint );
					for(int nPoint = nTrio+1; nPoint<nTotPoints; nPoint++)
					{
						segment_new.get( nPoint ).localize( notrotated );
						LinAlgHelpers.subtract( notrotated, joint, moved );
						LinAlgHelpers.quaternionApply( q, moved, rotated );
						LinAlgHelpers.add( rotated, joint, rotated );

						segment_new.set(nPoint, new RealPoint (rotated) );
						//taking care of the frame
						for(int nFr = 0; nFr<2;nFr++)
						{
							// add POINT
							LinAlgHelpers.add(newFrame[nFr][nPoint],notrotated,newFrame[nFr][nPoint]);

							//subtract rotation center
							LinAlgHelpers.subtract( newFrame[nFr][nPoint],joint, newFrame[nFr][nPoint] );

							//rotate
							LinAlgHelpers.quaternionApply( q, newFrame[nFr][nPoint], newFrame[nFr][nPoint] );

							//add back rotation center
							LinAlgHelpers.add( newFrame[nFr][nPoint], joint, newFrame[nFr][nPoint] );
							
							//subtract new position of the point
							LinAlgHelpers.subtract(newFrame[nFr][nPoint],rotated,newFrame[nFr][nPoint]);
						}
					}
				}
				//addNormalROI(segment_new,newFrame,firstLine.getLineThickness());
			}
			allFrames.add( newFrame );
			allSegments.add( segment_new);
			//LineTrace3D newROI = addROIsegment(bt, segment_new, i, ( int ) firstLine.getLineThickness());
			LineTrace3D newROI = addROIsegment(bt, Roi3D.scaleGlobInv( segment_new, bt.btData.globCal ),firstLine.getTimePoint(), ( int ) firstLine.getLineThickness(), bAddROIs);
			allRois.add( newROI );
			

		}
		if(nUnCoilTask > 0)
		{
			calculateIntervals();
	
			//add channel dimension to the final interval
			unionInterval = Intervals.addDimension( unionInterval, 0, bt.btData.nTotalChannels-1 );
	
			//output final size
			String sTotDims = "Final volume dimensions, voxels: " + Long.toString( unionInterval.dimension( 0 ) );
			for (int d=1;d<unionInterval.numDimensions(); d++)
			{
				sTotDims = sTotDims + "x"+ Long.toString( unionInterval.dimension( d ) );
			}
			System.out.println( sTotDims  );
			
			long[] alld = unionInterval.dimensionsAsLongArray();
			long nTotBytes = alld[0];
			for(int d=1; d<unionInterval.numDimensions();d++)
			{
				 nTotBytes*=alld[d];
			}
			nTotBytes*=2; //in bytes
			IJ.log( "required max memory is at least " +Double.toString( nTotBytes/1000000000.0 )+" Gb" );
			IJ.log( "available memory is " + Double.toString(IJ.maxMemory()/1000000000.0) +" Gb" );
		}
		return true;
	}
	//debug
	void addNormalROI (final ArrayList<RealPoint> segment_, final double [][][]rsVect, float nThickness)
	{
		ArrayList<RealPoint> segment = Roi3D.scaleGlobInv( segment_, bt.btData.globCal );
		int nTotPoints = segment.size();
		ArrayList<RealPoint> norm1 = new ArrayList<> ();
		double [] bvect = new double [3];
		double [] bnorm = new double [3];
		for (int nPoint = 0; nPoint<nTotPoints; nPoint++)
		{
			segment.get( nPoint ).localize( bvect );
			bvect = Roi3D.scaleGlob(bvect, bt.btData.globCal);
			for (int d =0; d<3; d++)
			{
				bnorm[d] =rsVect[0][nPoint][d];
			}
			LinAlgHelpers.scale( bnorm, nThickness * 0.5 * bt.btData.dMinVoxelSize, bnorm );
			LinAlgHelpers.add( bvect, bnorm, bvect );
			//norm1.add( new RealPoint(Roi3D.scaleGlobInv(bvect,BigTraceData.globCal)));
			norm1.add( new RealPoint(Roi3D.scaleGlobInv(bvect, bt.btData.globCal)));
		}
		addROIsegment(bt, norm1,0, 1, true);

	}
	public void calculateIntervals()
	{
		allIntervals = new ArrayList<>();
		double [] current_point_new = new double [3];
		final int nRadius = (int) Math.round(0.5*allRois.get(0).getLineThickness());
		ArrayList< ValuePair< int[],RealPoint>  > planeNormNew;
		long [] posInt = new long[3];
		double[] curr_XY_new = new double [3];
		//make an "empty" interval
		//union function will take the "proper one"
		unionInterval =  new FinalInterval(new long [] {10,10,10},new long [] {0,0,0});
		//unionInterval =  new FinalInterval(new long [3],new long [3] );
		for(int nTP = 0; nTP<nFrames;nTP++)
		{
			setProgress(100*nTP/(nFrames-1));
			setProgressState("estimating intervals ("+Integer.toString(nTP+1)+"/"+Integer.toString(nFrames)+")");
			//make an "empty" interval
			//union function will take the "proper one"
			FinalInterval currInt = new FinalInterval(new long [] {10,10,10},new long [] {0,0,0});

			for (int nPoint = 0;nPoint<allSegments.get( 0 ).size();nPoint++)
			{
				allSegments.get( nTP ).get(nPoint).localize(current_point_new); 
				planeNormNew = StraightenCurve.getNormCircleGridXYPairs(nRadius, bt.btData.dMinVoxelSize,allFrames.get( nTP )[0][nPoint],allFrames.get( nTP )[1][nPoint], current_point_new);
				for(int i=0;i<planeNormNew.size();i++)
				{
					planeNormNew.get(i).getB().localize( curr_XY_new );
					curr_XY_new = Roi3D.scaleGlobInv(curr_XY_new, bt.btData.globCal);
					for(int d=0;d<3;d++)
					{
						posInt[d] = Math.round( curr_XY_new[d]);
					}
					
					currInt = Intervals.union(currInt,new FinalInterval(posInt,posInt));
					
				}
			}
			allIntervals.add( currInt );

			unionInterval = Intervals.union( unionInterval, currInt );				
			
		}
	}
	
	public void testRMFrames()
	{
		for (int nFrame = 0; nFrame<allFrames.size();nFrame++)
		{
			ArrayList<RealPoint> frameX1 = new ArrayList<>();
			ArrayList<RealPoint> frameX2 = new ArrayList<>();
			double [] pos = new double[3];
			double [] comp =  new double[3];
			double [] addition = new double[3];
			for(int nPoint = 0; nPoint < allSegments.get( nFrame ).size(); nPoint++)
			{
				allSegments.get( nFrame ).get( nPoint ).localize( pos );
				LinAlgHelpers.scale( allFrames.get( nFrame )[0][nPoint], 20 * bt.btData.dMinVoxelSize, addition );
				LinAlgHelpers.add( addition, pos, comp );
				frameX1.add( new RealPoint(Roi3D.scaleGlobInv(comp, bt.btData.globCal)));
				LinAlgHelpers.scale( allFrames.get( nFrame )[1][nPoint], 20 * bt.btData.dMinVoxelSize, addition );
				LinAlgHelpers.add( addition, pos, comp );
				frameX2.add( new RealPoint(Roi3D.scaleGlobInv(comp, bt.btData.globCal)));

				
//				frameX1.add( new RealPoint(Roi3D.scaleGlobInv(pos,BigTraceData.globCal)));
//				frameX2.add( new RealPoint(Roi3D.scaleGlobInv(pos,BigTraceData.globCal)));

			}
			addROIsegment(bt, frameX1, 0, 2, true);
			addROIsegment(bt, frameX2, 0, 2, true);
		}
		
	}
	
	public IntervalView<T> generateSingleVolume(int nInd)
	{
		if(bt.btData.nTotalChannels == 1)
		{
			return generateSingleVolumeSetup(nInd, 0);
		}
		
		ArrayList<IntervalView<T>> nCh_RAI = new ArrayList<>();
		for (int nCh=0;nCh<bt.btData.nTotalChannels; nCh++)
		{
			nCh_RAI.add( generateSingleVolumeSetup(nInd, nCh) );
		}
		RandomAccessibleInterval< T > outRAI = Views.stack( nCh_RAI );
		return Views.interval( outRAI, outRAI );
	}
	
	
	@SuppressWarnings( "null" )
	public IntervalView<T> generateSingleVolumeSetup(int nInd, int nChannel)
	{
		
		FinalInterval roiIntervBox = allIntervals.get( nInd );
		//roiIntervBox = Intervals.addDimension( roiIntervBox, 0, bt.btData.nTotalChannels-1 );
		//FinalInterval test = Intervals.expand( allInt,2);
	
		RandomAccessibleInterval<T> all_RAI =  bt.btData.getAllDataRAI();
		
		Img<T> outImg = Util.getSuitableImgFactory(roiIntervBox, all_RAI.getType() ).create(roiIntervBox);
	
		IntervalView<T> outInterval = Views.translate( outImg, roiIntervBox.minAsLongArray() );
		
		// get only timePoint and channel of the ROI
		IntervalView< T > data_read = Views.hyperSlice( Views.hyperSlice( all_RAI, 3, inputROI.getTimePoint()),3,nChannel);
		RealRandomAccessible<T> interpolate = Views.interpolate( Views.extendZero(data_read), bt.btData.nInterpolatorFactory);
		RealRandomAccess<T> ra = interpolate.realRandomAccess();
		RandomAccess<T> ra_out = outInterval.randomAccess();
		RandomAccess<T> ra_template = null;
		
		if(bUseTemplate)
		{
			ra_template = template.randomAccess();
		}	
		int nRadius = (int) Math.round(0.5*allRois.get(0).getLineThickness());
		ArrayList< ValuePair< int[],RealPoint>  > planeNormZero;
		ArrayList< ValuePair< int[],RealPoint>  > planeNormNew;
		double [] current_point_zero = new double [3];
		double [] current_point_new = new double [3];

		double[] curr_XY_zero = new double [3];
		double [] currXY_zero_mCh = new double[data_read.numDimensions()];
		double[] curr_XY_new = new double [3];
		long [] posInt = new long[outInterval.numDimensions()];
		double newVal;
		double oldVal;
		for (int nPoint = 0;nPoint<allSegments.get( 0 ).size();nPoint++)
		{
			allSegments.get( 0 ).get(nPoint).localize(current_point_zero); 
			planeNormZero = StraightenCurve.getNormCircleGridXYPairs(nRadius, bt.btData.dMinVoxelSize, allFrames.get( 0 )[0][nPoint],allFrames.get( 0 )[1][nPoint], current_point_zero);
			
			allSegments.get( nInd ).get(nPoint).localize(current_point_new); 
			planeNormNew = StraightenCurve.getNormCircleGridXYPairs(nRadius, bt.btData.dMinVoxelSize, allFrames.get( nInd )[0][nPoint],allFrames.get( nInd )[1][nPoint], current_point_new);

			for(int i=0;i<planeNormZero.size();i++)
			{
				planeNormZero.get(i).getB().localize( curr_XY_zero );
				curr_XY_zero = Roi3D.scaleGlobInv(curr_XY_zero, bt.btData.globCal);
				
				planeNormNew.get(i).getB().localize( curr_XY_new );
				curr_XY_new = Roi3D.scaleGlobInv(curr_XY_new, bt.btData.globCal);

				for(int d=0;d<3;d++)
				{
					currXY_zero_mCh[d] = curr_XY_zero[d];
					posInt[d] = Math.round( curr_XY_new[d]);
				}

				ra_out.setPosition( posInt );
				T posFill = ra_out.get();
				if(bUseTemplate)
				{
					ra_template.setPosition( new int [] {nPoint,planeNormNew.get(i).getA()[1],planeNormNew.get(i).getA()[0], nChannel} );
					newVal = ra_template.get().getRealDouble();
				}
				else
				{
					ra.setPosition(currXY_zero_mCh);
					newVal = ra.get().getRealDouble();
				}

				oldVal = posFill.getRealDouble();

				if(newVal>oldVal)
				{
					posFill.setReal(newVal);
				}

				
			}
		}
		
		return outInterval;

	}
	
	public void generateAllVolumesTIFF()
	{
		
		int i;
        setProgress(0);
        setProgressState("Generating volumes...");

		for(i=0;i<nFrames;i++)
		{

			setProgress(100*i/(nFrames-1));
			setProgressState("generating/saving frame ("+Integer.toString(i+1)+"/"+Integer.toString(nFrames)+")");

			final IntervalView< T > outInt = generateSingleVolume(i);	
			//IJ.log( "Processed frame "+ Integer.toString( i ));
			if(nUnCoilExport == 2)
			{
				saveCompressedTIFF(outInt,i);
			}
			else
			{
				saveUncompressedTIFF(outInt,i);
			}
		}

	}
	
	public boolean loadTemplate(String sTemplateTIF)
	{
		final ImagePlus imp = IJ.openImage( sTemplateTIF );
		
		int nBitD = imp.getBitDepth();
		T ff =  bt.btData.getAllDataRAI().getType();
		
		if(ff instanceof UnsignedByteType)
		{
			if(nBitD!=8)
			{
				IJ.log("Provided template bit depth ("+Integer.toString( nBitD )+") does"
						+ " not match dataset bit depth (8)");
				return false;
			}
		}
		if(ff instanceof UnsignedShortType)
		{
			if(nBitD!=16)
			{
				IJ.log("Provided template bit depth ("+Integer.toString( nBitD )+") does"
						+ " not match dataset bit depth (16)");
				return false;
			}
		}

		
		Img<T> templateImg = ImageJFunctions.wrap( imp );
		boolean bTemplateFine = true;
		
		
		
		//do some basic checks on dimensions
		// sizes will be additionally checked later during ROI generation
		if(templateImg.numDimensions()<3 || templateImg.numDimensions()>4)
		{
			bTemplateFine = false;
		}
		if(templateImg.numDimensions()==3 && bt.btData.nTotalChannels>1)
		{
			bTemplateFine = false;
		}
		else
		{
			template = Views.addDimension( templateImg,0 ,0 );
		}
		if(templateImg.numDimensions()==4)
		{
			if(templateImg.dimension( 2 ) != bt.btData.nTotalChannels)
			{
				bTemplateFine = false;
			}
			else
			{
				template = Views.permute( templateImg, 2, 3 );
			}
		}
		if(!bTemplateFine)
		{
			IJ.log( "Provided template dimensions do not fit dataset dimensions"
					+ " for the straighten/uncoil animation! Aborting." );
		}
		return bTemplateFine;
	}
	
	public boolean saveUncompressedTIFF(final IntervalView<T> outInterval, final int nInd)
	{
		final Calibration cal = new Calibration();
		cal.setUnit(bt.btData.sVoxelUnit);
		cal.setTimeUnit(bt.btData.sTimeUnit);
		cal.pixelWidth = bt.btData.globCal[0];
		cal.pixelHeight = bt.btData.globCal[1];
		cal.pixelDepth = bt.btData.globCal[2];
		
		final IntervalView< T > imgOut = Views.zeroMin(  Views.interval( Views.extendZero( outInterval ),unionInterval ));
		final ImagePlus ip = VolumeMisc.wrapImgImagePlusCal(imgOut, inputROI.getName() + "_vol_T"+ String.format("%0"+String.valueOf(nFrames).length()+"d", nInd),cal, bt.btData.nNumTimepoints);
		IJ.saveAsTiff(ip, sSaveFolderPath+ip.getTitle());

		return true;
	}
	
	public boolean saveCompressedTIFF(final IntervalView<T> outInterval, final int nInd)
	{
//		final Calibration cal = new Calibration();
//		cal.setUnit(bt.btData.sVoxelUnit);
//		cal.setTimeUnit(bt.btData.sTimeUnit);
//		cal.pixelWidth= BigTraceData.dMinVoxelSize;
//		cal.pixelHeight= BigTraceData.dMinVoxelSize;
//		cal.pixelDepth= BigTraceData.dMinVoxelSize;
		final SCIFIOConfig config = new SCIFIOConfig();
		config.imgSaverSetWriteRGB(false);

		config.writerSetCompression(CompressionType.ZLIB.getCompression());
		
		ImgSaver saver = new ImgSaver();
		String sPathOutTif = sSaveFolderPath+"/"+inputROI.getName()+"_vol_T"+ String.format("%0"+String.valueOf(nFrames).length()+"d", nInd) +".tif";
		File outTif = new File(sPathOutTif);

		try
		{
			boolean result = Files.deleteIfExists(outTif.toPath());
			if(result)
			{
				IJ.log( "Overwriting "+ sPathOutTif +".");
			}
		}
		catch ( IOException exc )
		{
			IJ.log(exc.getMessage());
			exc.printStackTrace();
			return false;
		}
		
		final AxisType[] axisTypes = new AxisType[]{ Axes.X, Axes.Y, Axes.Z, Axes.CHANNEL};

		final IntervalView< T > imgOut = Views.zeroMin(  Views.interval( Views.extendZero( outInterval ),unionInterval ));
		
		final ImgPlus<?> saveImg = new ImgPlus<>(wrapIntervalForSCIFIO(imgOut),"test",axisTypes);
		
		saveImg.setCompositeChannelCount((int)imgOut.dimension(3));
		StopWatch stopwatch = StopWatch.createAndStart();
		saver.saveImg( sPathOutTif, saveImg, config );
		System.out.println( "saving time: " + stopwatch );

		return true;
	}
	
	public Img< T > wrapIntervalForSCIFIO(IntervalView<T> intView)
	{
		ArrayList< RandomAccessibleInterval< T > > allZC = new ArrayList<>();
		
		long [] dims = intView.dimensionsAsLongArray();
		if(dims.length!=4)
		{
			System.err.println("Only XYZC Imgs are supported for now");
			return null;
		}
		
		if(dims[3]==0)
		{
			return ImgView.wrap(intView);
		}

		for(int z=0;z<intView.dimension( 2 );z++)
		{		
			for(int c=0;c<intView.dimension( 3 );c++)
			{
				allZC.add( Views.hyperSlice(Views.hyperSlice( intView, 3, c ),2,z) );
			}
		}
		
		//make a version using Views.concatenate( ) ???
		return ImgView.wrap( Views.stack( allZC ));
	}
	
	public void generateSpiralROI(double R, double dz, double dAngleStep)
	{		
		//double dz = 2;
		//double R = 10;
		//double dAngleStep = 0.2;
		
		// add a ROI to ROI manager
		LineTrace3D firstLine = (LineTrace3D) bt.roiManager.makeRoi(Roi3D.LINE_TRACE, 0);
		//LineTrace3D firstLine = new LineTrace3D((Roi3DGroup)bt.roiManager.groups.get( 0 ), 0);
		
		ArrayList<RealPoint> segment = new ArrayList<>();
		double [] point = new double [3];
		
		double dAngleCurr = 0;

		firstLine.addFirstPoint( new RealPoint(point) );
		segment.add( new RealPoint(point) );
		int nTotPoints = 100;
		for (int nPoint = 1; nPoint<nTotPoints; nPoint++)
		{
			dAngleCurr += dAngleStep;
			point[0] += R*Math.cos( dAngleCurr );
			point[1] += R*Math.sin( dAngleCurr );
			point[2] += dz;
			segment.add( new RealPoint(point) );
		}
		firstLine.addPointAndSegment( segment.get( nTotPoints-1 ), segment );
		bt.roiManager.addRoi( firstLine );
	}
	
	/** calculates a PI minus angle between vectors p1-p2 and p3-p1 **/
	public static double calculateAngle(RealPoint p1, RealPoint p2, RealPoint p3)
	{
		double [] vect1 = new double[3];
		double [] vect2 = new double[3];
		LinAlgHelpers.subtract( p1.positionAsDoubleArray(), p2.positionAsDoubleArray(), vect1 );
		LinAlgHelpers.subtract( p3.positionAsDoubleArray(), p2.positionAsDoubleArray(), vect2 );
		LinAlgHelpers.normalize( vect1 );
		LinAlgHelpers.normalize( vect2 );
		double dDot = Math.min( Math.max( LinAlgHelpers.dot( vect1, vect2 ),-1.0),1.0);
		return Math.PI-Math.acos(dDot);
		
	}
	
	/** adds a new LineTrace ROI from ArrayList of RealPoints to the ROI manager **/
	public static LineTrace3D addROIsegment(final BigTrace< ? > bt, ArrayList<RealPoint> segment, int nTimeFrame, int nThickness, boolean bAddROIs)
	{
		LineTrace3D aLine = (LineTrace3D) bt.roiManager.makeRoi(Roi3D.LINE_TRACE, nTimeFrame);
		aLine.addFirstPoint( segment.get( 0 ));
		aLine.addPointAndSegment( segment.get( segment.size()-1 ), segment );
		aLine.setLineThickness( nThickness );
		if(bAddROIs)
		{
			bt.roiManager.addRoi( aLine );	
		}
		return aLine;
	}
	

	
//	public static void main( final String[] args )
//	{
//		new ImageJ();
//		BigTrace<UnsignedShortType> bt = new BigTrace<>(); 
//		
//		//bt.run("/home/eugene/Desktop/projects/BigTrace/BigTrace_progress/20240726_bending/HyperStack.tif");
//		bt.run("/home/eugene/Desktop/projects/BigTrace/BigTrace_progress/20240726_bending/snake/Chlorophis_irregularis-XYZ_divby8.tif");
//		//bt.run("/home/eugene/Desktop/projects/BigTrace/BigTrace_progress/20240726_bending/snake/Chlorophis_irregularis-XYZ_divby8_8bit_3fr.tif");
//
//		try
//		{
//			bt.btMacro.macroLoadROIs( "/home/eugene/Desktop/projects/BigTrace/BigTrace_progress/20240726_bending/snake/Chlorophis_irregularis-XYZ_divby8.tif_btrois.csv", "Clean" );
//			bt.btMacro.macroShapeInterpolation( "Spline", 20 );
//		}
//		catch ( InterruptedException exc )
//		{
//			// TODO Auto-generated catch block
//			exc.printStackTrace();
//		}
//		//wait to load things
//		while(bt.bInputLock)
//		{
//			try
//			{
//				Thread.sleep(100);
//			}
//			catch ( InterruptedException exc )
//			{
//				// TODO Auto-generated catch block
//				exc.printStackTrace();
//			}
//		}
//
//		LineTrace3D firstLine = (LineTrace3D) bt.roiManager.rois.get( 0 );
//		
//		UnCurveAnimation<UnsignedShortType> unBend = new UnCurveAnimation<>(bt);
//		
//		unBend.generateROIs( firstLine );	
//		//unBend.testFrames();
//		unBend.loadTemplate( "/home/eugene/Desktop/projects/BigTrace/BigTrace_progress/20240726_bending/snake/clean_X_trace172232825_straight.tif" );
//		
//		unBend.generateAllVolumes("/home/eugene/Desktop/projects/BigTrace/BigTrace_progress/20240726_bending/snake/out/");
//		//unBend.generateSingleVolume(1);
//		
//	}
	

}