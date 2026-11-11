package bigtrace.io;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import bigtrace.BigTrace;
import bigtrace.animation.UnCoilAnimation;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;

/** image loader of uncoiled volumes for BDV HDF5 export,
 * volumes are provided in the type of the original data (8-, 16-bit, float) **/
public class UnCoilFrameImgLoader < T extends RealType< T > & NativeType< T > > implements BasicImgLoader
{
	final UnCoilAnimation<T> unCoil;
	final BigTrace<T> bt;
	/** pixel type of the data **/
	final T type;
	public UnCoilFrameImgLoader(final UnCoilAnimation<T> unCoil_, final BigTrace<T> bt_)
	{
		bt = bt_;
		unCoil = unCoil_;
		type = bt.btData.getAllDataRAI().getType().createVariable();
	}
	
	@Override
	public BasicSetupImgLoader< T > getSetupImgLoader( int setupId )
	{
		
		return new SetupImgLoader< T >()
		{

			@Override
			public RandomAccessibleInterval< T > getImage( int timepointId, ImgLoaderHint... hints )
			{
				return Views.zeroMin(unCoil.generateSingleVolumeSetup( timepointId, setupId ));
			}

			@Override
			public T getImageType()
			{
				return type.createVariable();
			}

			@Override
			public RandomAccessibleInterval< FloatType > getFloatImage( int timepointId, boolean normalize, ImgLoaderHint... hints )
			{
				return null;
			}

			@Override
			public Dimensions getImageSize( int timepointId )
			{
				//volume size of this frame
				final FinalInterval currInt = unCoil.allIntervals.get( timepointId );
				return new FinalDimensions(currInt.dimension( 0 ),
										   currInt.dimension( 1 ),
										   currInt.dimension( 2 ));
			}
			@Override
			public VoxelDimensions getVoxelSize( int timepointId )
			{
				return new VoxelDimensions()
				{
					@Override
					public String unit()
					{
						return bt.btData.sVoxelUnit;
					}

					@Override
					public void dimensions( double[] dimensions )
					{
						for ( int d = 0; d < dimensions.length; ++d )
							dimensions[ d ] = bt.btData.globCal[ d ];
					}

					@Override
					public double dimension( int d )
					{
						return bt.btData.globCal[ d ];
					}

					@Override
					public int numDimensions()
					{
						return 3;
					}
				};
			}
		
		};

	}

}
