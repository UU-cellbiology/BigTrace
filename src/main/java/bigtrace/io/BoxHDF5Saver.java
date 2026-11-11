package bigtrace.io;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import bdv.export.ExportMipmapInfo;
import bdv.export.ExportScalePyramid;
import bdv.export.ExportScalePyramid.LoopbackHeuristic;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.ij.util.PluginHelper;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bigtrace.BigTrace;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.VoxelDimensions;

/** Saves a box extracted from the data (XYZTC), e.g. around ROI or clipped volume, as BDV XML/HDF5.
 * The registration of each view places the box at its position in the original volume
 * (registration of the original BDV view, or voxel * voxel size for other data),
 * so it can be shown together with the original data.
 * Timepoint ids and channel numbers are the ones of the original data. **/
public class BoxHDF5Saver < T extends RealType< T > & NativeType< T > >
{
	final BigTrace<T> bt;

	/** box data, XYZTC, zero min **/
	final RandomAccessibleInterval<T> boxXYZTC;

	/** position of the box in the original data, XYZTC **/
	final long [] boxMin;

	/** output path and file name without extension **/
	final String sFullPathNoExt;

	/**
	 * @param bt_ BigTrace instance
	 * @param boxXYZTC_ box data, XYZTC (any min, it is ignored)
	 * @param boxMin_ min of the box in the original data, XYZTC
	 * @param sFullPathNoExt_ path and name of output files without extension (.xml/.h5 are added)
	 */
	public BoxHDF5Saver(final BigTrace<T> bt_, final RandomAccessibleInterval<T> boxXYZTC_, final long [] boxMin_, final String sFullPathNoExt_)
	{
		bt = bt_;
		boxXYZTC = Views.zeroMin( boxXYZTC_ );
		boxMin = boxMin_.clone();
		sFullPathNoExt = sFullPathNoExt_;
	}

	public void export()
	{
		final File hdf5File = new File( sFullPathNoExt + ".h5" );
		final File xmlFile = new File( sFullPathNoExt + ".xml" );
		final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( bt.btData.sVoxelUnit, bt.btData.globCal[0], bt.btData.globCal[1], bt.btData.globCal[2] );
		final FinalDimensions size = new FinalDimensions( boxXYZTC.dimension( 0 ), boxXYZTC.dimension( 1 ), boxXYZTC.dimension( 2 ) );

		// propose reasonable mipmap settings
		final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );
		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export of " + hdf5File.getName() + "..." );
		final BoxImgLoader imgLoader = new BoxImgLoader();
		final int numTimepoints = (int) boxXYZTC.dimension( 3 );
		final int numSetups = (int) boxXYZTC.dimension( 4 );

		final HashMap< Integer, BasicViewSetup > setups = new HashMap<>( numSetups );
		for ( int s = 0; s < numSetups; ++s )
		{
			final int nChannel = (int) boxMin[ 4 ] + s + 1;
			final BasicViewSetup setup = new BasicViewSetup( s, String.format( "channel %d", nChannel ), size, voxelSize );
			setup.setAttribute( new Channel( nChannel ) );
			setups.put( s, setup );
		}
		//original timepoint ids
		final ArrayList< TimePoint > timepoints = new ArrayList<>( numTimepoints );
		for ( int t = 0; t < numTimepoints; ++t )
			timepoints.add( new TimePoint( (int) boxMin[ 3 ] + t ) );

		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );
		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap<>();
		for ( final BasicViewSetup setup : seq.getViewSetupsOrdered() )
			perSetupExportMipmapInfo.put( setup.getId(), autoMipmapSettings );

		final int numCellCreatorThreads = Math.max( 1, PluginHelper.numThreads() - 1 );
		// use the loopback image if it saves more than 8x on pixel reads
		final LoopbackHeuristic loopbackHeuristic = new LoopbackHeuristic()
		{
			@Override
			public boolean decide( final RandomAccessibleInterval< ? > originalImg, final int[] factorsToOriginalImg, final int previousLevel, final int[] factorsToPreviousLevel, final int[] chunkSize )
			{
				if ( previousLevel < 0 )
					return false;

				if ( Intervals.numElements( factorsToOriginalImg ) / Intervals.numElements( factorsToPreviousLevel ) >= 8 )
					return true;

				return false;
			}
		};
		final ExportScalePyramid.AfterEachPlane afterEachPlane = usedLoopBack ->
		{ };

		WriteSequenceToHdf5.writeHdf5File( seq, perSetupExportMipmapInfo, true, hdf5File, loopbackHeuristic, afterEachPlane, numCellCreatorThreads, new SubTaskProgressWriter( progressWriter, 0, 0.95 ) );

		// write xml sequence description
		final Hdf5ImageLoader hdf5Loader = new Hdf5ImageLoader( hdf5File, null, null, false );
		final SequenceDescriptionMinimal seqh5 = new SequenceDescriptionMinimal( seq, hdf5Loader );
		final ArrayList< ViewRegistration > registrations = new ArrayList<>();
		for ( final TimePoint tp : timepoints )
			for ( int s = 0; s < numSetups; ++s )
				registrations.add( new ViewRegistration( tp.getId(), s, getTransform( tp.getId(), (int) boxMin[ 4 ] + s ) ) );
		final File basePath = xmlFile.getParentFile();
		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seqh5, new ViewRegistrations( registrations ) );
		try
		{
			new XmlIoSpimDataMinimal().save( spimData, xmlFile.getAbsolutePath() );
			progressWriter.setProgress( 1.0 );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}
		progressWriter.out().println( "done" );
	}

	/** Transform of the box voxels to the coordinates of the original volume
	 * for timepoint nTP and channel nCh (indices in BigTrace data).
	 * Data from BDV XML/BioFormats (not deskewed): registration of the original view 
	 * (includes its voxel size, translation, etc.) followed by the shift of the box.
	 * Otherwise: voxel size scaling and the shift of the box (BigTrace coordinates). **/
	AffineTransform3D getTransform(final int nTP, final int nCh)
	{
		final AffineTransform3D boxShift = new AffineTransform3D();
		boxShift.translate( boxMin[ 0 ], boxMin[ 1 ], boxMin[ 2 ] );
		
		if(bt.btData.bSpimSource && bt.spimData != null && !bt.bApplyLLSTransform)
		{
			try
			{
				//BigTrace uses setup and timepoint indices as ids
				final int nSetupId = bt.spimData.getSequenceDescription().getViewSetupsOrdered().get( nCh ).getId();
				final int nTPId = bt.spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( nTP ).getId();
				final ViewRegistration reg = bt.spimData.getViewRegistrations().getViewRegistration( nTPId, nSetupId );
				reg.updateModel();
				final AffineTransform3D sourceTransform = reg.getModel().copy();
				sourceTransform.concatenate( boxShift );
				return sourceTransform;
			}
			catch(final Exception e)
			{
				//no registration for this view, use voxel size scaling below
			}
		}
		
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		sourceTransform.set(
				bt.btData.globCal[ 0 ], 0, 0, 0,
				0, bt.btData.globCal[ 1 ], 0, 0,
				0, 0, bt.btData.globCal[ 2 ], 0 );
		sourceTransform.concatenate( boxShift );
		return sourceTransform;
	}
	
	/** 3D volume of the box for the timepoint id (original numbering) and setup (channel index in the box) **/
	RandomAccessibleInterval< T > getVolume( final int timepointId, final int setupId )
	{
		return Views.hyperSlice( Views.hyperSlice( boxXYZTC, 4, setupId ), 3, timepointId - boxMin[ 3 ] );
	}

	class BoxImgLoader implements BasicImgLoader
	{
		@Override
		public BasicSetupImgLoader< T > getSetupImgLoader( final int setupId )
		{
			return new SetupImgLoader< T >()
			{
				@Override
				public RandomAccessibleInterval< T > getImage( final int timepointId, final ImgLoaderHint... hints )
				{
					return getVolume( timepointId, setupId );
				}

				@Override
				public T getImageType()
				{
					return boxXYZTC.getType().createVariable();
				}

				@Override
				public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, final boolean normalize, final ImgLoaderHint... hints )
				{
					return null;
				}

				@Override
				public Dimensions getImageSize( final int timepointId )
				{
					return new FinalDimensions( boxXYZTC.dimension( 0 ), boxXYZTC.dimension( 1 ), boxXYZTC.dimension( 2 ) );
				}

				@Override
				public VoxelDimensions getVoxelSize( final int timepointId )
				{
					return new FinalVoxelDimensions( bt.btData.sVoxelUnit, bt.btData.globCal[0], bt.btData.globCal[1], bt.btData.globCal[2] );
				}
			};
		}
	}
}
