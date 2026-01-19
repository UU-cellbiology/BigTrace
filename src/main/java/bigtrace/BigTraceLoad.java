package bigtrace;

import java.awt.Color;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.process.LUT;

import loci.common.DebugTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;

import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;

import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewSetup;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class BigTraceLoad < T extends RealType< T > & NativeType< T > >
{
	
	/** plugin instance **/
	BigTrace<T> bt;
	
	BigTraceData<T> btdata;
	
	/** colors for each channel **/
	public Color [] colorsCh;
	
	/** intensity range for channels **/
	public double [][] channelRanges;	
	
	public BigTraceLoad(final BigTrace<T> bt_)
	{
		
		bt = bt_;
		btdata = bt.btData;
	}
	
	public boolean initDataSourcesHDF5() throws SpimDataException
	{
					
		bt.spimData = new XmlIoSpimData().load( btdata.sFileNameFullImg );
		
		bt.spimData.getSequenceDescription().getViewSetups();
		
		final SequenceDescription seq = bt.spimData.getSequenceDescription();
		
		//get voxel size
		for (int d =0;d<3;d++)
		{
			bt.btData.globCal[d] = seq.getViewDescription(0,0).getViewSetup().getVoxelSize().dimension(d);
		}
		//number of timepoints
		bt.btData.nNumTimepoints = seq.getTimePoints().size();
		bt.btData.dMinVoxelSize = Math.min(Math.min(bt.btData.globCal[0], bt.btData.globCal[1]), bt.btData.globCal[2]);
		

		@SuppressWarnings( "cast" )
		FinalInterval rai_int = new FinalInterval(((BasicImgLoader)seq.getImgLoader()).getSetupImgLoader(0).getImage(0));			
		
		List< ViewSetup > allViewSetups = seq.getViewSetupsOrdered();
		for (int nV = 0; nV < allViewSetups.size();nV++)
		{
			//Dimensions nDim = allViewSetups.get( nV ).getSize();
			final long [] nDim = allViewSetups.get( nV ).getSize().dimensionsAsLongArray();
			for (int d=0;d<3;d++)
			{
				nDim[d]--;
			}
			if(rai_int == null)
			{
				rai_int = new FinalInterval(new long[3],nDim);
			}
			else
			{
				rai_int = Intervals.union( rai_int, new FinalInterval(new long[3],nDim) );
			}
		}
		rai_int.min( btdata.nDimIni[0] );
		rai_int.max( btdata.nDimIni[1] );
		rai_int.min( btdata.nDimCurr[0] );
		rai_int.max( btdata.nDimCurr[1] );
		
		btdata.sVoxelUnit = seq.getViewSetupsOrdered().get(0).getVoxelSize().unit();
		
		btdata.nTotalChannels = seq.getViewSetupsOrdered().size();

		
		//in the end always 16-bit
		btdata.nBitDepth = 16;
		colorsCh = new Color[btdata.nTotalChannels];
		channelRanges = new double [2][btdata.nTotalChannels];
		
		
		return true;
		
	}
	
	@SuppressWarnings({ "unchecked" })
	public String initDataSourcesBioFormats() 
	{
		DebugTools.setRootLevel("INFO");
	
		//analyze file a bit
		int nSeriesCount = 0;
	    
		String[] seriesName = null;
		
	    int[] seriesZsize = null;
	    int[] seriesBitDepth = null;
	    
		// check if multiple files inside, like LIF
	    try (ImageProcessorReader r = new ImageProcessorReader(
			      new ChannelSeparator(LociPrefs.makeImageReader()));)
	    {
	        ServiceFactory factory = new ServiceFactory();
	        OMEXMLService service = factory.getInstance(OMEXMLService.class);
	        
	        r.setMetadataStore(service.createOMEXMLMetadata());      
	        r.setId(btdata.sFileNameFullImg);
	        
	        nSeriesCount = r.getSeriesCount();
	        seriesName = new String[nSeriesCount];
	        seriesZsize = new int[nSeriesCount];
	        seriesBitDepth = new int[nSeriesCount];

	        MetadataRetrieve retrieve = (MetadataRetrieve) r.getMetadataStore();
	        for (int nS = 0; nS < nSeriesCount; nS++)
	        {
	        	r.setSeries(nS);
	        	seriesZsize[nS] = r.getSizeZ();
	        	seriesName[nS] = retrieve.getImageName(nS);
	        	seriesBitDepth[nS] = r.getPixelType();
	        }

		  }
	    catch (FormatException exc) {
	      return "Sorry, an error occurred: " + exc.getMessage();
	    
	    }
	    catch (IOException exc) {
	      return "Sorry, an error occurred: " + exc.getMessage();
	    }
	    catch (DependencyException de) { 
	    	 return "Sorry, an error occurred: " + de.getMessage();
	    }
	    catch (ServiceException se) { 
	    	return "Sorry, an error occurred: " + se.getMessage();
	    }
	    
		int nOpenSeries = 0;
		if(nSeriesCount == 1)
		{
			if(seriesZsize[ 0 ] > 1)
			{
				nOpenSeries = 0;
			}
			else
			{
				return "Sorry, an error occurred: only 3D datasets are supported.";
			}
		}
		else
		{
			//make a list of 3D series
			int outCount = 0;
			for(int nS = 0; nS < nSeriesCount; nS++)
			{
				if(seriesZsize[ nS ] > 1)
				{
					outCount++;
				}
			}
			if(outCount == 0)
			{
				return "Sorry, an error occurred: cannot find 3D datasets in provided file\n"+btdata.sFileNameFullImg;
			}
			
			String [] sDatasetNames = new String[outCount];
			int [] nDatasetIDs = new int[outCount];
			int [] nDatasetType = new int[outCount];
			int nCurrDS = 0;
			for(int nS = 0; nS < nSeriesCount; nS++)
			{
				if(seriesZsize[ nS ] > 1)
				{
					sDatasetNames[nCurrDS] = seriesName[nS];
					nDatasetIDs[nCurrDS] = nS;
					nDatasetType[nCurrDS] = seriesBitDepth[nS];
					nCurrDS++;
				}
			}
			GenericDialog openDatasetN = new GenericDialog("Choose dataset..");
			openDatasetN.addChoice("Name: ",sDatasetNames, sDatasetNames[0]);
			openDatasetN.showDialog();
			if (openDatasetN.wasCanceled())
	            return "Dataset opening was cancelled.";
			
			nOpenSeries = nDatasetIDs[openDatasetN.getNextChoiceIndex()];
			
		}
		
		final int nBitDepth = seriesBitDepth[ nOpenSeries ];
		if (nBitDepth == FormatTools.UINT16 || nBitDepth == FormatTools.UINT8 || nBitDepth == FormatTools.FLOAT)
		{
			OpenerSettings settings = OpenerSettings.BioFormats()
					.location(new File(btdata.sFileNameFullImg))
					.unit("MICROMETER")
					.setSerie(nOpenSeries)
					.positionConvention("TOP LEFT")
					.pyramidize( true );
			bt.spimData = (SpimData) OpenersToSpimData.getSpimData(settings);
			
		}
		else
		{
			return "Sorry, only 8-, 16- or 32-bit BioFormats images are supported.\nClosing BigTrace.";
		}

		final SequenceDescription seq = bt.spimData.getSequenceDescription();

		//get voxel size
		for (int d = 0; d < 3; d++)
		{
			bt.btData.globCal[d] = seq.getViewDescription(0,0).getViewSetup().getVoxelSize().dimension(d);
		}
		bt.btData.dMinVoxelSize = Math.min(Math.min(bt.btData.globCal[0], bt.btData.globCal[1]), bt.btData.globCal[2]);
		
		//number of timepoints
		bt.btData.nNumTimepoints = seq.getTimePoints().size();
		
		//see if data comes from LLS7
		String sTestLLS = seq.getViewDescription(0, 0).getViewSetup().getName();
		if(sTestLLS.length() > 3)
		{
			if((sTestLLS.contains("LLS")||sTestLLS.contains("LatticeLightsheet")) && btdata.sFileNameFullImg.endsWith(".czi"))
			{
				if (JOptionPane.showConfirmDialog(null, "Looks like the input comes from Zeiss LLS7.\nDo you want to deskew it?\n"
						+ "(if it is already deskewed, click No)", "Loading option",
				        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				    // yes option
					bt.bApplyLLSTransform = true;
				} 
			}

		}
		
		FinalInterval rai_int;
		RandomAccessibleInterval<T> raitest = (RandomAccessibleInterval<T>) seq.getImgLoader().getSetupImgLoader(0).getImage(0);
		
		if(bt.bApplyLLSTransform)
		{
			//build LLS transform
			rai_int = makeLLS7Transform(raitest);
		}
		else
		{
			rai_int = new FinalInterval(raitest);
		}
		
		rai_int.min( btdata.nDimIni[0] );
		rai_int.max( btdata.nDimIni[1] );
		rai_int.min( btdata.nDimCurr[0] );
		rai_int.max( btdata.nDimCurr[1] );
		
		btdata.sVoxelUnit = seq.getViewSetupsOrdered().get(0).getVoxelSize().unit();
		
		btdata.nTotalChannels = seq.getViewSetupsOrdered().size();	
		
		
		//TODO FOR NOW, get it from the class
		//not really needed later, but anyway
		btdata.nBitDepth = 16;
		colorsCh = new Color[btdata.nTotalChannels];
		channelRanges = new double [2][btdata.nTotalChannels];
	
		return null;
		
	}
	
	/** function assigns new LLS7 transform to bt.afDataTransform (using provided voxel size of original data) 
	 * and returns the new interval of transformed source **/
	FinalInterval makeLLS7Transform(final Interval orig_rai)
	{
		bt.afDataTransform = new AffineTransform3D();
		AffineTransform3D tShear = new AffineTransform3D();
		AffineTransform3D tRotate = new AffineTransform3D();
	
		
		//rotate 30 degrees
		tRotate.rotate(0, (-1.0)*Math.PI/6.0);
		//shearing transform
		tShear.set(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.7320508075688767, 0.0, 0.0, 0.0, 1.0, 0.0);
		//Z-step adjustment transform
		bt.afDataTransform.set(bt.btData.globCal[0]/bt.btData.dMinVoxelSize, 0.0, 0.0, 0.0, 
								0.0, bt.btData.globCal[1]/bt.btData.dMinVoxelSize, 0.0, 0.0, 
								0.0, 0.0, 0.5 * bt.btData.globCal[2]/bt.btData.dMinVoxelSize, 0.0);
		
		bt.afDataTransform = tShear.concatenate(bt.afDataTransform);
		bt.afDataTransform = tRotate.concatenate(bt.afDataTransform);
		FinalRealInterval finReal = bt.afDataTransform.estimateBounds(orig_rai);
		double [][] dBounds = new double [2][3]; 
		long [][] lBounds = new long [2][3]; 
		finReal.realMin(dBounds[0]);
		AffineTransform3D tZeroMin = new AffineTransform3D();
		for (int i = 0;i<3;i++)
		{
			dBounds[0][i] = dBounds[0][i]*(-1);
		}			
		tZeroMin.translate(dBounds[0]);
		bt.afDataTransform = bt.afDataTransform.preConcatenate(tZeroMin);
		finReal = bt.afDataTransform.estimateBounds(orig_rai);
		finReal.realMin(dBounds[0]);
		finReal.realMax(dBounds[1]);
		for (int i = 0;i<3;i++)
			{
				lBounds[0][i] = (long) Math.floor(dBounds[0][i]);
				lBounds[1][i] = (long) Math.ceil(dBounds[1][i]);
			}
		return new FinalInterval(lBounds[0],lBounds[1]);
	}
	
	public boolean initDataSourcesImageJ()
	{
				
		final ImagePlus imp = IJ.openImage( btdata.sFileNameFullImg );
		
		if (imp == null)
		{
			IJ.error("BigTrace: cannot open selected TIF file. Plugin terminated.");
			return false;
		}
		if(imp.getNSlices() == 1)
		{
			IJ.error("Error, input image has only one Z slice (2D?).\nBigTrace works only with 3D data. Plugin terminated.");			
			return false;
		}
		
		if(imp.getType() != ImagePlus.GRAY8 && imp.getType() != ImagePlus.GRAY16 && imp.getType() != ImagePlus.GRAY32)
		{
			IJ.error("Only 8-, 16- and 32-bit images supported for now.");
			return false;
		}

		
		bt.btData.globCal[0] = imp.getCalibration().pixelWidth;
		bt.btData.globCal[1] = imp.getCalibration().pixelHeight;
		bt.btData.globCal[2] = imp.getCalibration().pixelDepth;
		bt.btData.dMinVoxelSize = Math.min(Math.min(bt.btData.globCal[0], bt.btData.globCal[1]), bt.btData.globCal[2]);
		btdata.sVoxelUnit = imp.getCalibration().getUnit();
		btdata.sTimeUnit = imp.getCalibration().getTimeUnit();
		btdata.dFrameInterval = imp.getCalibration().frameInterval;
		bt.btData.nNumTimepoints = imp.getNFrames();
		
		Img<T> img_ImageJ;
				
		btdata.nBitDepth = imp.getBitDepth();
		img_ImageJ = ImageJFunctions.wrap( imp );//.wrapNumeric(imp);
//		if(btdata.nBitDepth<=16)
//		{
//			img_ImageJ = ImageJFunctions.wrap( imp );//.wrapNumeric(imp);
//		}
//		else
//		{
//			
//			img_ImageJ =   VolumeMisc.convertFloatToUnsignedShort(ImageJFunctions.wrapReal(imp));
//		}
		//long[] test = img_ImageJ.dimensionsAsLongArray();
		
		//let's convert it to XYZTC for BVV to understand
		
		
		btdata.nTotalChannels = imp.getNChannels();
		if(btdata.nTotalChannels == 1)
		{
			//add dimension for the channels
			bt.all_ch_RAI = Views.addDimension(img_ImageJ, 0, 0);
		}
		else
		{
			//change the order of C and Z
			bt.all_ch_RAI = Views.permute(img_ImageJ, 2,3);
		}
		//test = all_ch_RAI.dimensionsAsLongArray();
		

		getChannelsColors(imp);

		if(bt.btData.nNumTimepoints == 1)
		{
			bt.all_ch_RAI = Views.addDimension(bt.all_ch_RAI, 0, 0);
			//test = all_ch_RAI.dimensionsAsLongArray();
			if(btdata.nTotalChannels==1)
			{
				bt.all_ch_RAI =Views.permute(bt.all_ch_RAI, 3,4);
			}
			//test = all_ch_RAI.dimensionsAsLongArray();
		}
		//finally change C and T (or it can be already fine, if we added C dimension)
		if(btdata.nTotalChannels>1)
		{
			bt.all_ch_RAI =Views.permute(bt.all_ch_RAI, 4,3);
		}
		//test = all_ch_RAI.dimensionsAsLongArray();

		FinalInterval testRAI = new FinalInterval(Views.hyperSlice(Views.hyperSlice(bt.all_ch_RAI,4,0),3,0));
		
		testRAI.min( btdata.nDimIni[0] );
		testRAI.max( btdata.nDimIni[1] );
		testRAI.min( btdata.nDimCurr[0] );
		testRAI.max( btdata.nDimCurr[1] );
		
		return true;
	}
	
	/** creates and fills array colorsCh with channel colors,
	 * taken from Christian Tischer reply in this thread
	 * https://forum.image.sc/t/composite-image-channel-color/45196/3 **/
	public void getChannelsColors(ImagePlus imp)
	{
		colorsCh = new Color[imp.getNChannels()];
		channelRanges = new double [2][imp.getNChannels()];
	      for ( int c = 0; c < imp.getNChannels(); ++c )
	        {
	            if ( imp instanceof CompositeImage )
	            {
	                CompositeImage compositeImage = ( CompositeImage ) imp;
					LUT channelLut = compositeImage.getChannelLut( c + 1 );
					int mode = compositeImage.getMode();
					if ( channelLut == null || mode == CompositeImage.GRAYSCALE )
					{
						colorsCh[c] = Color.WHITE;
					}
					else
					{
						IndexColorModel cm = channelLut.getColorModel();
						if ( cm == null )
						{
							colorsCh[c] = Color.WHITE;
						}
						else
						{
							int i = cm.getMapSize() - 1;
							colorsCh[c] = new Color(cm.getRed( i ) ,cm.getGreen( i ) ,cm.getBlue( i ) );

						}

					}

					compositeImage.setC( c + 1 );
	            }
	            else
	            {
	            	colorsCh[c] = Color.WHITE;

	            }
            	if(btdata.nBitDepth!=32)
				{
            		channelRanges[0][c]=(int)imp.getDisplayRangeMin();
            		channelRanges[1][c]=(int)imp.getDisplayRangeMax();
				}
            	else
				{
					channelRanges[0][c]=0;
					channelRanges[1][c]=65535;						
				}
	        }
	}

}
