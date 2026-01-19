package bigtrace.gui;

import java.awt.Color;

import bigtrace.BigTrace;
import bigtrace.rois.Box3D;

/** class containing boxes for visualization **/
public class VisualBoxes
{
	final BigTrace<?> bt;
	
	/** box around volume **/
	final public Box3D volumeBox;
	
	/** helper box to visualize one-click tracing things **/
	final public Box3D traceBox;
	
	/** helper box to visualize one-click tracing things **/
	public final Box3D clipBox;
	
	public boolean bShowTraceBox = false;
	
	public VisualBoxes(final BigTrace<?> bt_)
	{
		bt = bt_;
		volumeBox = new Box3D(bt.btData, 1.0f);
		clipBox = new Box3D(bt.btData, 0.5f);
		traceBox = new Box3D(bt.btData, 1.0f);
	}
	
	public void setColor(final Color colorin)
	{
		volumeBox.setLineColor( colorin );
		clipBox.setLineColor( colorin.darker() );
		traceBox.setLineColor( colorin.darker() );		
	}
}
