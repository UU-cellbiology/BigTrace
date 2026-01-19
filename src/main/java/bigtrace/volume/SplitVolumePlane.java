package bigtrace.volume;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import javax.swing.SwingWorker;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bigtrace.geometry.Intersections3D;
import bigtrace.rois.Box3D;
import bigtrace.rois.CrossSection3D;
import ij.measure.Calibration;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class SplitVolumePlane < T extends RealType< T > & NativeType< T > > extends SwingWorker<Void, String> implements BigTraceBGWorker{

	private String progressState;
	
	final CrossSection3D crossSection;
	final public BigTrace<T> bt;
	int nSliceType;
	
	
	public SplitVolumePlane(final CrossSection3D crossSection_, final BigTrace<T> bt_, final int nSliceType_)
	{
		super();
		crossSection = crossSection_;
		bt = bt_;
		nSliceType= nSliceType_;
	}
	
	@Override
	public String getProgressState()
	{
		return progressState;
	}
	@Override
	public void setProgressState(String state_)
	{
		progressState=state_;
	}
	
	@Override
	protected Void doInBackground() throws Exception {
		
		int i,d;
		//check if there is a fitted plane
		if (crossSection.fittedPlane == null)
			return null;
		
        bt.bInputLock = true;
        bt.setLockMode(true);
        

		try {
			  Thread.sleep(1);
		  } catch (InterruptedException ignore) {}
		setProgress(1);
		setProgressState("allocating two new volumes..");
	
		//get the all data RAI
		//XYZTC
		RandomAccessibleInterval<T> all_RAI = bt.btData.getAllDataRAI();
		
		//(for now)
		//make two copies
	
		Img<T> out1 =  Util.getSuitableImgFactory(all_RAI, all_RAI.getType()).create(all_RAI);
		LoopBuilder.setImages(out1,all_RAI).forEachPixel(Type::set);
		
		Img<T> out2 =  Util.getSuitableImgFactory(all_RAI, all_RAI.getType()).create(all_RAI);
		LoopBuilder.setImages(out2, all_RAI).forEachPixel(Type::set);
		
		final long [] nTotPixArr = all_RAI.dimensionsAsLongArray();
		
		// total number of pixels for the progress bar 
		long nTotPixCount = nTotPixArr[0];
		for (i=1;i<nTotPixArr.length; i++)
		{
			nTotPixCount *= nTotPixArr[i];
		}
		
		long nCount = 0;
		
		Cursor<T> cursor1 = out1.localizingCursor();
		Cursor<T> cursor2 = out2.localizingCursor();
		RealPoint rp = new RealPoint(3);
		double [] position = new double [out1.numDimensions()];
		double [] position3 = new double [3];
		setProgressState("splitting volume in two..");
		while (cursor1.hasNext())
		{
			cursor1.fwd();
			cursor2.fwd();
			cursor1.localize(position);

			for(d=0;d<3;d++)
			{
					position3[d]=position[d];
			}
			rp.setPosition(position3);
			
			
			if(crossSection.fittedPlane.signedDistance(rp)>0.0)
			{
				cursor1.get().setZero();
			}
			else
			{
				cursor2.get().setZero();
			}
			nCount++;
			setProgress((int)(100.0*nCount/nTotPixCount));
		}
		Calibration cal = new Calibration();
		cal.setUnit(bt.btData.sVoxelUnit);
		cal.setTimeUnit(bt.btData.sTimeUnit);
		cal.pixelWidth= bt.btData.globCal[0];
		cal.pixelHeight= bt.btData.globCal[1];
		cal.pixelDepth= bt.btData.globCal[2];
		Path p = Paths.get(bt.btData.sFileNameFullImg);
		String fileName = p.getFileName().toString()+"_"+crossSection.getName();
		
		//original volume size
		if(nSliceType ==0)
		{

			VolumeMisc.wrapImgImagePlusCal(out1,fileName+"_vol1", cal, bt.btData.nNumTimepoints).show();
			VolumeMisc.wrapImgImagePlusCal(out2,fileName+"_vol2", cal, bt.btData.nNumTimepoints).show();

		}	
		//tight crop, let's calculate corresponding intervals
		else
		{
			//make arrays of points for each subvolume
			ArrayList<RealPoint> vertOut1 = new ArrayList<>();
			ArrayList<RealPoint> vertOut2 = new ArrayList<>();
			
			//add cross-section polygon vertices
			for (i=0;i<crossSection.polygonVert.size();i++)
			{
				vertOut1.add(crossSection.polygonVert.get(i));
				vertOut2.add(crossSection.polygonVert.get(i));
			}
			//add vertices of corners depending on the sign
			ArrayList<RealPoint> boxVert; 

			boxVert = Box3D.getBoxVertices(Views.hyperSlice(out1, 3, 0));
	
			
			//split vertexes of the bounding box based
			//on their relative position to the plane
			for (i=0;i<boxVert.size();i++)
			{
				//inverse here, since before we were putting zeros there 
				if(crossSection.fittedPlane.signedDistance(boxVert.get(i))>0.0)
				{
					vertOut2.add(boxVert.get(i));
				}
				else
				{
					vertOut1.add(boxVert.get(i));					
				}
			}
			long [][][] newBoundBoxes = new long [2][2][3];
			Intersections3D.makeBoundBox(vertOut1,newBoundBoxes[0]);
			Intersections3D.makeBoundBox(vertOut2,newBoundBoxes[1]);
			
			//crop intervals
			FinalInterval [] newCrop = new FinalInterval[2];
			
			long [][] rangeCh = new long[2][5];
			for (int intervN = 0; intervN < 2; intervN ++)
			{
				for(i=0;i<2;i++)
				{
					for(d=0;d<3;d++)
					{
						rangeCh[i][d]= newBoundBoxes[intervN][i][d];
					}
				}
				rangeCh[0][3]=out1.min(3);
				rangeCh[1][3]=out1.max(3);
				rangeCh[0][4]=out1.min(4);
				rangeCh[1][4]=out1.max(4);
				newCrop[intervN] = new FinalInterval(rangeCh[0],rangeCh[1]);
			}
		
			//apply calibration and show
			VolumeMisc.wrapImgImagePlusCal(Views.interval(out1, newCrop[0]),fileName+"_vol1", cal, bt.btData.nNumTimepoints).show();
			VolumeMisc.wrapImgImagePlusCal(Views.interval(out2, newCrop[1]),fileName+"_vol2", cal, bt.btData.nNumTimepoints).show();
		}

		setProgressState("volume splitting done.");
		setProgress(100);

		
		return null;
	}
	
    /*
     * Executed in event dispatching thread
     */
    @Override
    public void done() 
    {
		//unlock user interaction
    	bt.bInputLock = false;
        bt.setLockMode(false);

    }

}
