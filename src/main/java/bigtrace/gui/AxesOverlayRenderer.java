package bigtrace.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Arrays;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

import bdv.util.Affine3DHelpers;
import bdv.viewer.AbstractViewerPanel;
import bdv.viewer.OverlayRenderer;
import bigtrace.BigTraceActions.AlignPlaneBT;

public class AxesOverlayRenderer implements OverlayRenderer
{
	private AbstractViewerPanel viewer = null;

	private volatile boolean isEnabled = false;

	boolean isHover = false;

	int nHighlightedAxis = -1;

	final Color [] axesColors = new Color[] 
			{new Color(255,54,83,255), new Color(118,178,23,255), new Color(48,121,204,255),
			new Color(255,54,83,128), new Color(118,178,23,128), new Color(48,121,204,128)};

	final Color hoverBGColor = new Color( 0xb0bbbbbb, true );
	final BasicStroke vectorStroke = new BasicStroke(2.5f);
	final BasicStroke ovalStroke = new BasicStroke(1);
	final BasicStroke letterStroke = new BasicStroke(1.3f);
	
	final double [] qRotation = new double[4];
	
	/** center of the gizmo **/
	final Point center = new Point();
	
	final int vectRadius = 40;
	final int circleAxisRadius = 9;	
	final int fullRadiusSquared = (vectRadius + circleAxisRadius) * (vectRadius + circleAxisRadius);
	
	/** current centers of circles with axes labels
	 * 0,1,2 - positive direction
	 * 3,4,5 - negative **/
	final double [][] dAxisCircleCenter = new double [6][3];
	
	/** current ends of vector "sticks" from origin to the  circles with axis labels
	 * 0,1,2 - positive direction
	 * 3,4,5 - negative **/	
	final double [][] dAxisVector = new double [6][3];

	/** ordered indexes of circles/axes after sorting with respect to depth **/
	final double [][] axisOrder = new double [6][2];
	
	private final static int xLetterHalf = 3;
	
	private final static int yLetterHalf = 4;

	/** set of stored quaternions corresponding to the aligned planes **/
	private final double [][] quaterLibrary = new double[6][4];
	
	/** is equal to the axis index if the current orientation
	 * aligns with one of the determined align planes or -1 otherwise **/
	private int nAlignIndex;
	
	public AxesOverlayRenderer()
	{
		super();
		//current plane align quaternions
		for(int d = 0; d < 4; d++)
		{
			LinAlgHelpers.quaternionInvert( AlignPlaneBT.ZY.qAlign, quaterLibrary[0] );
			LinAlgHelpers.quaternionInvert( AlignPlaneBT.XZ.qAlign, quaterLibrary[1] );
			LinAlgHelpers.quaternionInvert( AlignPlaneBT.XY.qAlign, quaterLibrary[2] );
			LinAlgHelpers.quaternionInvert( AlignPlaneBT.YZ.qAlign, quaterLibrary[3] );
			LinAlgHelpers.quaternionInvert( AlignPlaneBT.ZX.qAlign, quaterLibrary[4] );
			LinAlgHelpers.quaternionInvert( AlignPlaneBT.YX.qAlign, quaterLibrary[5] );
		}
	}
	
	public void bindViewer(final AbstractViewerPanel viewer_)
    {
    	this.viewer = viewer_;
    }
    
	@SuppressWarnings( "null" )
	@Override
	public void drawOverlays( Graphics g )
	{
		if(viewer == null || !isEnabled)
			return;

		Graphics2D graphics = (Graphics2D) g;
		
		graphics.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		
		final Rectangle clipBounds = g.getClipBounds();
		
		center.setLocation( clipBounds.getWidth() - 80, 100 );
		
		isHover = false;
		
		final Point pMouse = viewer.getMousePosition();
		
		//draw a grey background circle if mouse is on top of the gizmo
		if (pMouse != null)
		{
			final float dMouseX = pMouse.x - center.x;
			final float dMouseY = pMouse.y - center.y;

			if( dMouseX * dMouseX + dMouseY * dMouseY < fullRadiusSquared)
			{
				graphics.setColor( hoverBGColor );
				graphics.fillOval( center.x - vectRadius - circleAxisRadius, 
						center.y - vectRadius - circleAxisRadius, 
						2 * (vectRadius + circleAxisRadius) + 1, 
						2 * (vectRadius + circleAxisRadius) + 1);
				isHover = true;
			}
		}
		
		AffineTransform3D transform = new AffineTransform3D();
		
		viewer.state().getViewerTransform(transform);

		Affine3DHelpers.extractRotationAnisotropic( transform, qRotation );
		
		for(int d = 0; d < 6; d++)
		{
			for(int i = 0; i < 3; i ++)
			{
				dAxisCircleCenter[d][i] = 0.0;
				dAxisVector[d][i] = 0.0;
			}
			if(d < 3)
			{
				dAxisCircleCenter[d][d] = vectRadius;
				dAxisVector[d][d] = vectRadius - circleAxisRadius - 2;
			}
			else
			{
				dAxisCircleCenter[d][d - 3] = -vectRadius;
				dAxisVector[d][d - 3] = circleAxisRadius - vectRadius + 2 ;
			}
			LinAlgHelpers.quaternionApply( qRotation, dAxisCircleCenter[d], dAxisCircleCenter[d] );
			LinAlgHelpers.quaternionApply( qRotation, dAxisVector[d], dAxisVector[d] );

			dAxisCircleCenter[d][0] += center.x;
			dAxisCircleCenter[d][1] += center.y;
			dAxisVector[d][0] += center.x;
			dAxisVector[d][1] += center.y;

			//prepare array for the sorting along depth 
			//z-coordinate
			axisOrder[d][0] = dAxisCircleCenter[d][2]; 
			//index
			axisOrder[d][1] = d; 			
		}

		//sort axes circles positions by depth (z-coord) for rendering
		Arrays.sort(axisOrder, (a, b) -> (-1) * Double.compare(a[0], b[0]));

		//find highlight axis circle index that closest to the mouse pointer, if hover is active
		if( isHover )
		{
			float dx, dy;
			dx = (float)dAxisCircleCenter[0][0] - pMouse.x;
			dy = (float)dAxisCircleCenter[0][1] - pMouse.y;
			float fDistance = dx * dx + dy * dy;
			nHighlightedAxis = 0;
			for(int d = 1; d < 6; d++)
			{
				dx = (float)dAxisCircleCenter[d][0] - pMouse.x;
				dy = (float)dAxisCircleCenter[d][1] - pMouse.y;
				if(dx * dx + dy * dy < fDistance)
				{
					nHighlightedAxis = d;
					fDistance = dx * dx + dy * dy;
				}
			}
		}
		else
		{
			nHighlightedAxis = -1;
		}
		// see if the current orientation is aligned to predetermined library
		nAlignIndex = isAlignedWithPlanes(qRotation);
		
		for( int d = 0; d < 6; d++)
		{
			graphics.setStroke(vectorStroke);
			
			final int index = (int)axisOrder[d][1];

			int x = (int) Math.round( dAxisCircleCenter[index][0]);
			int y = (int) Math.round( dAxisCircleCenter[index][1]);
			
			//positive axes
			if(index < 3)
			{
				//vector's body
				graphics.setColor(axesColors[index]);					 
				graphics.drawLine(center.x, center.y, x, y);
				//circle at the end of vector
				graphics.setStroke(ovalStroke);
				graphics.fillOval( x - circleAxisRadius, y - circleAxisRadius , 2 * circleAxisRadius + 2 , 2 * circleAxisRadius + 2);
				drawLetter(graphics, x + 1 , y + 1, index);
			}
			//negative axes
			else
			{
				graphics.setColor(axesColors[index].darker());
				//if aligned, draw vectors, otherwise not
				if(nAlignIndex >= 0)
				{
					graphics.drawLine(center.x, center.y, (int) Math.round( dAxisVector[index][0]), (int) Math.round( dAxisVector[index][1]));

					if(nAlignIndex == index)
						graphics.setColor(axesColors[index - 3]);
				}
				//draw transparent circles	
				graphics.setStroke(ovalStroke);
				graphics.fillOval( x - circleAxisRadius, y - circleAxisRadius , 2 * circleAxisRadius + 2 , 2 * circleAxisRadius + 2);
				graphics.setColor(axesColors[index - 3]);
				graphics.drawOval(x - circleAxisRadius, y - circleAxisRadius , 2 * circleAxisRadius + 2 , 2 * circleAxisRadius + 2);					
				drawLetter(graphics, x + 1 , y + 1, index);
			}
		}
	}
	
	/** since fonts appearance etc depends on OS, labels (letters) of axes are drawn with lines.**/
	void drawLetter(final Graphics2D graphics, final int x, final int y, final int index)
	{
		if( nHighlightedAxis == index )
			graphics.setPaint( Color.WHITE );
		else
			graphics.setPaint( Color.BLACK );
		
		graphics.setStroke(letterStroke);
		int nFinalX = x;
		int nFinIndex = index;
		if (index > 2)
		{
			if(nHighlightedAxis == index)
			{
				//draw minus sign
				graphics.drawLine( nFinalX -2*xLetterHalf, y, nFinalX - xLetterHalf, y);

				nFinIndex = index - 3;
				nFinalX += 3;
			}
			else if(nAlignIndex == index )
			{
				//draw minus
				graphics.drawLine( nFinalX -2*xLetterHalf, y, nFinalX - xLetterHalf, y);
				//shift letter
				nFinIndex = index - 3;
				nFinalX += 3;				
			}
			else
			{
				return;
			}
		}
		switch (nFinIndex)
		{
		//x
		case 0:
			graphics.drawLine( nFinalX - xLetterHalf, y - yLetterHalf, nFinalX + xLetterHalf, y + yLetterHalf);
			graphics.drawLine( nFinalX - xLetterHalf, y + yLetterHalf, nFinalX + xLetterHalf, y - yLetterHalf);
			break;
		//y
		case 1:
			graphics.drawLine( nFinalX - xLetterHalf, y - yLetterHalf, nFinalX, y + 1 );
			graphics.drawLine( nFinalX + xLetterHalf, y - yLetterHalf, nFinalX, y + 1);
			graphics.drawLine( nFinalX, y + 1,nFinalX, y + yLetterHalf);
			break;
		//z
		case 2:
			graphics.drawLine( nFinalX - xLetterHalf, y - yLetterHalf, nFinalX + xLetterHalf, y - yLetterHalf);
			graphics.drawLine( nFinalX - xLetterHalf, y + yLetterHalf, nFinalX + xLetterHalf, y - yLetterHalf);
			graphics.drawLine( nFinalX - xLetterHalf, y + yLetterHalf, nFinalX + xLetterHalf, y + yLetterHalf);
			break;

		default:
			break;
		}
	}

    @Override
    public void setCanvasSize(int width, int height) 
    {
    }  
	
	public synchronized void setEnabled (final boolean bEnabled)
	{
		isEnabled = bEnabled;
	}
	
	public boolean isEnabled()
	{
		return isEnabled;
	}
	
	public int getHighlightedAxis()
	{
		return nHighlightedAxis;
	}
	/** function checks if provided quaternion is close
	 * to the ones defining the alignment planes.
	 * Returns the plane number or -1 if quaternion is far**/
	int isAlignedWithPlanes (final double [] qCurrent)
	{
		int nIndex = -1;
		for(int i = 0; i < 6; i++)
		{
			double dot = Math.abs( LinAlgHelpers.dot( qCurrent, quaterLibrary[i] ));
			if(dot > 0.99999)
			{
				nIndex = i;
			}
		}
		return nIndex;
	}
}
