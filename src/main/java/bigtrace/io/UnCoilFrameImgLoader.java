package bigtrace.io;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import bigtrace.BigTrace;
import bigtrace.animation.UnCoilAnimation;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;

public class UnCoilFrameImgLoader < T extends RealType< T > & NativeType< T > > implements BasicImgLoader
{
	final UnCoilAnimation<T> unCoil;
	final BigTrace<T> bt;
	public UnCoilFrameImgLoader(final UnCoilAnimation<T> unCoil_, final BigTrace<T> bt_)
	{
		bt = bt_;
		unCoil = unCoil_;		
	}
	
	@Override
	public BasicSetupImgLoader< UnsignedShortType> getSetupImgLoader( int setupId )
	{
		
		return new SetupImgLoader< UnsignedShortType >()
		{

			@SuppressWarnings( "unchecked" )
			@Override
			public RandomAccessibleInterval< UnsignedShortType > getImage( int timepointId, ImgLoaderHint... hints )
			{
				final RandomAccessibleInterval< ? > raiXYZ
				= Views.zeroMin(unCoil.generateSingleVolumeSetup( timepointId, setupId ));
				if ( raiXYZ.getType() instanceof UnsignedShortType )
				{
					return (RandomAccessibleInterval <UnsignedShortType >) raiXYZ;
				}
				else if ( raiXYZ.getType() instanceof UnsignedByteType )
				{
					return Converters.convert(
							raiXYZ,
							( i, o ) -> o.setInteger( ((UnsignedByteType) i).get() ),
							new UnsignedShortType( ) );
				}
				else
				{
					return null;
				}
		
			}

			@Override
			public UnsignedShortType getImageType()
			{
				return new UnsignedShortType();
			}

			@Override
			public RandomAccessibleInterval< FloatType > getFloatImage( int timepointId, boolean normalize, ImgLoaderHint... hints )
			{
				return null;
			}

			@Override
			public Dimensions getImageSize( int timepointId )
			{
				final FinalInterval currInt = unCoil.allIntervals.get( setupId );
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
