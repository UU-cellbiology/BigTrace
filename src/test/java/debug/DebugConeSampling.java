package debug;

import java.awt.Color;

import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;

import org.joml.Matrix4f;

import bigtrace.BigTrace;
import bigtrace.scene.VisCone;
import bvvpg.core.VolumeViewerPanel;
import bvvpg.core.util.MatrixMath;
import bvvpg.vistools.BvvFunctions;
import bvvpg.vistools.BvvSource;
import ij.ImageJ;

public class DebugConeSampling
{
	@SuppressWarnings("rawtypes")
	public static void main(String... args) throws Exception
	{
		
		new ImageJ();
		
		BigTrace testI = new BigTrace(); 
		testI.run("/home/eugene/Desktop/projects/BigTrace/junctions/blank10.tif");
		ArrayImg< FloatType, FloatArray > showRAI = ArrayImgs.floats( 100, 100, 100 );

//		ArrayImg< UnsignedByteType, ByteArray > showRAI = ArrayImgs.unsignedBytes( 100, 100, 100 );
		
		double fConeAngle  = 45.*Math.PI/180.;
		double [] direction = new double[3];
		double [] position = new double[3];
		
		
		for(int d = 1; d < 3; d++)
		{
			position[d] = 50.;
		}
		direction[0] = 20.;
		direction[1] = 20.;
		direction[2] = 20.;
		final VisCone testCone = new VisCone(testI.btData, direction,  position, fConeAngle, Color.YELLOW);	
		
		final ConeIterator coneIterator = new ConeIterator( direction,  position, fConeAngle );
		
		
		ArrayCursor< FloatType > cU = showRAI.cursor();
		double [] pos = new double[3];
		while(cU.hasNext())
		{
			cU.fwd();
			cU.localize( pos );
			if(coneIterator.isOnTheCone( pos ))
				cU.get().set( 255);

		}
		

		
		final BvvSource source = BvvFunctions.show( showRAI, "CONE" );
		source.setDisplayRange( 0, 255 );
		//source.setVoxelRenderInterpolation( 0 );
		final VolumeViewerPanel viewer = source.getBvvHandle().getViewerPanel();
		
		
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
