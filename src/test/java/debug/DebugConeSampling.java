package debug;

import java.awt.Color;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import org.joml.Matrix4f;

import bigtrace.BigTrace;
import bigtrace.scene.VisCone;
import bigtrace.volume.ConeCursor;
import bvvpg.core.VolumeViewerPanel;
import bvvpg.core.util.MatrixMath;
import bvvpg.vistools.BvvFunctions;
import bvvpg.vistools.BvvHandle;
import bvvpg.vistools.BvvOptions;
import bvvpg.vistools.BvvSource;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

public class DebugConeSampling
{
	@SuppressWarnings("rawtypes")
	public static void main(String... args) throws Exception
	{
		
		new ImageJ();
		
		BigTrace testI = new BigTrace(); 

		//testI.run("/home/eugene/Desktop/projects/BigTrace/junctions/blank10.tif");
		testI.run("/home/eugene/Desktop/projects/BigTrace/junctions/Niels/crossing_compicated_crop.tif");
		final ImagePlus imp = IJ.openImage( "/home/eugene/Desktop/projects/BigTrace/junctions/Niels/crossing_compicated_crop.tif" );
		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );
		ArrayImg< FloatType, FloatArray > showRAI = ArrayImgs.floats( 151, 151, 150 );

		
		double fConeAngle  = 45.*Math.PI/180.;
		double [] direction = new double[3];
		double [] position = new double[3];
		
		
		for(int d = 1; d < 3; d++)
		{
			position[d] = 10*0.1082600;
		}
		direction[0] = 20.*0.1082600;
		direction[1] = 20.*0.1082600;
		direction[2] = 20.*0.1082600;
		final VisCone testCone = new VisCone(testI.btData, direction,  position, fConeAngle, Color.YELLOW);	
		
		//final ConeIterator coneIterator = new ConeIterator( direction,  position, fConeAngle );
		final ConeCursor< FloatType > cc = new ConeCursor<>( showRAI.randomAccess(), position, direction, fConeAngle,
				new double[] { 0.1082600, 0.1082600,  0.2600000 }, showRAI, true, 0.5 );
		while ( cc.hasNext() )
			cc.next().set( 255 );

		//AffineTransform3D scale = new AffineTransform3D();
		//scale.scale( 0.1082600, 0.1082600, 0.2600000 );
		final BvvSource source = BvvFunctions.show( showRAI, "CONE");
		source.setDisplayRange( 0, 255 );
		BvvHandle handle = source.getBvvHandle();
		 BvvFunctions.show(img, "img", BvvOptions.options().addTo( handle ));
		//source.setVoxelRenderInterpolation( 0 );
		final VolumeViewerPanel viewer = source.getBvvHandle().getViewerPanel();
		AffineTransform3D scale = viewer.state().getViewerTransform();
		scale.scale( 0.1082600, 0.1082600, 0.2600000 );
		viewer.state().setViewerTransform( scale );
		viewer.setRenderScene( ( gl, data ) -> {
			int [] screen_size = new int [] {(int)data.getScreenWidth(), (int) data.getScreenHeight()};		
			final Matrix4f pvm = new Matrix4f( data.getPv() );
			final Matrix4f view = MatrixMath.affine( data.getRenderTransformWorldToScreen(), new Matrix4f() );
			final Matrix4f camview = MatrixMath.screen( testI.btData.dCam, screen_size[0], screen_size[1], new Matrix4f() ).mul( view );

			testCone.draw( gl, pvm, camview, testI.btData, false );
			//final Matrix4f cubetransform = new Matrix4f().translate( 140, 150, 65 ).scale( 80 );
			//cube.draw( gl, new Matrix4f( data.getPv() ).mul( cubetransform ) );
		} );
	}
	
}
