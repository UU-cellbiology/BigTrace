package bigtrace.gui;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.TransformEventHandler3D;
import bdv.TransformState;
import bigtrace.BigTrace;
import bvvpg.core.VolumeViewerPanel;
import bvvpg.vistools.BvvHandle;

public class TransformHandlerBT
{

	final private static double step = Math.PI / 180;

	private static final double[] speedRotate = { 0.75, 2.0, 0.1 };

	private static final double[] speedZoom = { 2.0, 4.0, 0.2 };

	/**
	 * Copy of transform when mouse dragging started.
	 */
	private final AffineTransform3D affineDragStart = new AffineTransform3D();

	/**
	 * Current transform during mouse dragging.
	 */
	private final AffineTransform3D affineDragCurrent = new AffineTransform3D();
	
	/**
	 * Coordinates where mouse dragging started.
	 */
	private double oX, oY;

	/**
	 * Screen coordinates to keep centered while zooming or rotating with the
	 * keyboard. These are set to <em>(canvasW/2, canvasH/2)</em>
	 */
	private int centerX = 0, centerY = 0;
	
	/** orientation of X and Y axis in the current view **/
	final double [][] vXY = new double [2][3]; 
	
	/** rotation angles from mouse displacement **/
	final double [] rotationXY = new double [2];
	
	private final TransformState transform;
	
	final BigTrace<?> bt;
	final BvvHandle bvvHandle;
	final VolumeViewerPanel bvvViewer;
	
	// -- behaviours --
	private final Rotate dragRotate;
	private final Rotate dragRotateFast;
	private final Rotate dragRotateSlow;
	private final Zoom zoom;
	private final Zoom zoomFast;
	private final Zoom zoomSlow;
	
	public TransformHandlerBT( final BigTrace<?> bt)
	{
		this.bt = bt;
		this.bvvHandle = bt.bvvHandle;
		bvvViewer = bvvHandle.getViewerPanel();
		this.transform = TransformState.from( bvvViewer.state()::getViewerTransform, bvvViewer.state()::setViewerTransform );
		dragRotate     = new Rotate( speedRotate[ 0 ] );
		dragRotateFast = new Rotate( speedRotate[ 1 ] );
		dragRotateSlow = new Rotate( speedRotate[ 2 ] );
		zoom     = new Zoom( speedZoom[0] );
		zoomFast = new Zoom( speedZoom[1]  );
		zoomSlow = new Zoom( speedZoom[2]  );

	}
	
	public void install( final Behaviours behaviours )
	{		
		behaviours.behaviour( dragRotate, TransformEventHandler3D.DRAG_ROTATE, TransformEventHandler3D.DRAG_ROTATE_KEYS);
		behaviours.behaviour( dragRotateFast, TransformEventHandler3D.DRAG_ROTATE_FAST, TransformEventHandler3D.DRAG_ROTATE_FAST_KEYS );
		behaviours.behaviour( dragRotateSlow, TransformEventHandler3D.DRAG_ROTATE_SLOW, TransformEventHandler3D.DRAG_ROTATE_SLOW_KEYS );
		behaviours.behaviour( zoom, TransformEventHandler3D.SCROLL_Z, TransformEventHandler3D.SCROLL_Z_KEYS);
		behaviours.behaviour( zoomFast, TransformEventHandler3D.SCROLL_Z_FAST, TransformEventHandler3D.SCROLL_Z_FAST_KEYS );
		behaviours.behaviour( zoomSlow, TransformEventHandler3D.SCROLL_Z_SLOW, TransformEventHandler3D.SCROLL_Z_SLOW_KEYS);
	}
	
	private class Rotate implements DragBehaviour
	{
		private final double speed;
		
		private boolean isDrag;

		public Rotate( final double speed )
		{
			this.speed = speed;
		}
		
		@Override
		public void init( final int x, final int y )
		{
			isDrag = false;
			
			oX = x;
			oY = y;
			centerX = bvvHandle.getViewerPanel().getDisplay().getWidth()/2;
			centerY = bvvHandle.getViewerPanel().getDisplay().getHeight()/2;
			
			transform.get( affineDragStart );
			
			for (int d = 0; d < 3; d++)
			{
				vXY[0][d] = 0.0;
				vXY[1][d] = 0.0;			
			}
			vXY[0][0] = 1.0;
			vXY[1][1] = 1.0;
			
			final AffineTransform3D viewTransform = affineDragStart.copy();
			transform.get( viewTransform );
			//let's remove translation
			for(int d = 0; d < 3; d++)
			{
				viewTransform.set( 0, d, 3);					
			}
			
			for (int i = 0; i < 2; i++)
			{
				viewTransform.applyInverse( vXY[i], vXY[i]);
				LinAlgHelpers.normalize( vXY[i] );
			}
			
		}

		@Override
		public void drag( final int x, final int y )
		{
			isDrag = true;
			
			rotationXY[0]  = (y - oY) * step * speed;
			rotationXY[1]  = (oX - x) * step * speed;

			affineDragCurrent.set( affineDragStart );

			// center shift
			affineDragCurrent.set( affineDragCurrent.get( 0, 3 ) - centerX, 0, 3 );
			affineDragCurrent.set( affineDragCurrent.get( 1, 3 ) - centerY, 1, 3 );	

			affineDragCurrent.rotate( 0, rotationXY[0] );
			affineDragCurrent.rotate( 1, rotationXY[1] );

			// center un-shift
			affineDragCurrent.set( affineDragCurrent.get( 0, 3 ) + centerX, 0, 3 );
			affineDragCurrent.set( affineDragCurrent.get( 1, 3 ) + centerY, 1, 3 );
			//apply rotation to the view transform
			transform.set( affineDragCurrent );

		}

		@Override
		public void end( final int x, final int y )
		{
			if( !isDrag )
			{
				final int nAxis = bt.axisOverlay.getHighlightedAxis();
				if( nAxis >= 0 )
				{
					bt.btActions.alignToAxis( nAxis );
				}
			}
		}

	}
		
	private class Zoom implements ScrollBehaviour
	{
		private final double speed;
		
		public Zoom(final double speed )
		{
			this.speed = speed;
		}
		
		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			final double s = speed * wheelRotation;
			final double dScale = 1.0 + 0.1 * Math.abs( s );
			centerX = bvvHandle.getViewerPanel().getDisplay().getWidth()/2;
			centerY = bvvHandle.getViewerPanel().getDisplay().getHeight()/2;
			//final double dScale = 1.0 + 0.05;
			if ( s > 0 )
				scale( 1.0 / dScale, centerX, centerY );
			else
				scale( dScale, centerX, centerY );
		}
	}
	
	private void scale( final double s, final double x, final double y )
	{
		final AffineTransform3D affine = transform.get();

		// center shift
		affine.set( affine.get( 0, 3 ) - x, 0, 3 );
		affine.set( affine.get( 1, 3 ) - y, 1, 3 );

		// scale
		affine.scale( s );

		// center un-shift
		affine.set( affine.get( 0, 3 ) + x, 0, 3 );
		affine.set( affine.get( 1, 3 ) + y, 1, 3 );

		transform.set( affine );
	}


}
