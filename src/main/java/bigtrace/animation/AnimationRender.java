package bigtrace.animation;


import java.io.File;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.KeyAdapter;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.imageio.ImageIO;

import bdv.ui.splitpanel.SplitPanel;
import bdv.util.Prefs;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGWorker;
import bvvpg.core.render.VolumeRenderer.RepaintType;
import ij.IJ;


public class AnimationRender extends SwingWorker<Void, String> implements BigTraceBGWorker
{
	
	final BigTrace<?> bt;
	
	final AnimationPanel aPanel;
	
	private String progressState;
	
	JButton butRecord = null;
	
	Dimension dimsIni = null;
	
	ImageIcon tabIconRecord = null;
	
	JPanel glass = null;

	@Override
	public String getProgressState()
	{
		return progressState;
	}
	@Override
	public void setProgressState(String state_)
	{
		progressState = state_;
	}
	
	public AnimationRender(BigTrace<?> bt_,  AnimationPanel aPanel_)
	{
		this.bt = bt_;
		this.aPanel = aPanel_;
	}
	
	@Override
	protected Void doInBackground() throws Exception 
	{
		if(aPanel.sRenderSavePath == null)
		{
			return null;
		}

		int nTotFrames = aPanel.kfAnim.nTotalTime*aPanel.nRenderFPS;
		
		if(!aPanel.bRenderMultiBox)
		{
			Prefs.showMultibox(false);
		}
		
		if(aPanel.bRenderScaleBar)
		{
			Prefs.showScaleBar(true);
			Prefs.showScaleBarInMovie( true );
		}

		Prefs.showTextOverlay(false);
		
		
		float dT = aPanel.kfAnim.nTotalTime/(float)(nTotFrames-1);		

		bt.bvvViewer.setRenderMode( true );
		
		SplitPanel splitPanel =  bt.bvvFrame.getSplitPanel();
		
		if(!splitPanel.isCollapsed())
		{
			splitPanel.setCollapsed( true );
		}

		Component component = bt.bvvViewer;	
		
		int nHeight = aPanel.nRenderHeight;
		//check if there is time slider => +25 in height
		if(bt.btData.nNumTimepoints > 1)
		{
			nHeight += 25;
		}
		
		Dimension nRenderDim = new Dimension(aPanel.nRenderWidth, nHeight);

        //install glass pane
		glass = new JPanel();
		glass.setOpaque(false);
		glass.addMouseListener(new MouseAdapter() {});
		glass.addKeyListener(new KeyAdapter() {});
		bt.bvvFrame.setGlassPane( glass );
		glass.setVisible(true);

		bt.bvvFrame.getContentPane().setPreferredSize( nRenderDim );
		bt.bvvFrame.pack();	
		SwingUtilities.invokeAndWait( ()->
		{
			bt.bvvFrame.setResizable( false );
		});
		Rectangle rect = bt.bvvViewer.getDisplayComponent().getBounds();
		BufferedImage bi =
                new BufferedImage(rect.width, rect.height,
                                    BufferedImage.TYPE_INT_ARGB);
		RepaintType status;
		//refresh first frame 
		SwingUtilities.invokeAndWait( ()->
		{
			bt.repaintBVV();
		});
		for(int nFr = 0; nFr < nTotFrames; nFr++)
		{
			setProgress(nFr * 100 / (nTotFrames - 1));
			setProgressState("rendering frames ("+Integer.toString( nFr+1 )+"/"+Integer.toString(nTotFrames)+")");
			final float fTimePoint = nFr * dT;

			SwingUtilities.invokeAndWait( ()->
			{
				bt.setScene(aPanel.kfAnim.getScene(fTimePoint));
			} );
			//bt.repaintBVV();
			long nTotalTime = 0;
			final long nWaitTime = 30;
			final long nTimeLimitmS = aPanel.nRenderFrameTimeLimit * 1000;
			boolean bWait = (bt.bvvViewer.getRepaintStatus() != RepaintType.NONE);
			//while(bt.viewer.getRepaintStatus() != RepaintType.NONE)
			while(bWait)
			{			
				Thread.sleep( nWaitTime );
				status = bt.bvvViewer.getRepaintStatus();
				//System.out.println(status);
				nTotalTime += nWaitTime;
				if(status == RepaintType.NONE)
					{bWait = false;}
				if (nTotalTime > nTimeLimitmS)
				{
					bWait = false;
					IJ.log( "Rendering of frame " + Integer.toString( nFr + 1 ) + " took more than a minute, proceeding with current result." );
				}
				if(isCancelled())
				{
					return null;	
				}	
			}
	        component.paint(bi.getGraphics());
			ImageIO.write( bi, "png", new File( aPanel.sRenderSavePath + 
			String.format("%0"+String.valueOf(nTotFrames).length() + "d", nFr + 1) + ".png") );
			if(isCancelled())
			{
				return null;	
			}	
		}
		return null;
	}
	
	
    /*
     * Executed in event dispatching thread
     */
    @Override
    public void done() 
    {
    	//see if we have some errors
    	try {

    		get();
    	} 
    	catch (ExecutionException e) 
    	{
    		e.getCause().printStackTrace();
    		String msg = String.format("Unexpected error during animation render: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	} 
    	catch (InterruptedException e) 
    	{
    		e.getCause().printStackTrace();
    		String msg = String.format("Unexpected error during animation render: %s", 
    				e.getCause().toString());
    		System.out.println(msg);
    	}
    	catch (Exception e)
    	{
    		System.out.println("Animation render interrupted by user.");
        	setProgress(100);	
        	setProgressState("Render interrupted by user.");
    	}	
    	
    	bt.bvvViewer.setRenderMode( false );
    	
    	if(dimsIni != null)
    	{
    		bt.bvvFrame.getContentPane().setPreferredSize( dimsIni);
    	}

		bt.bvvFrame.pack();
        
		bt.bvvFrame.setResizable( true );
        if(glass != null)
        {
        	glass.setVisible(false);

        }
        //bt.bvvFrame.setEnabled( true );

		//IJ.log( Integer.toString( dimsIni.width ) );
		//IJ.log( Integer.toString( dimsIni.height ) );
    	
		if(butRecord != null && tabIconRecord!= null)
    	{
    		butRecord.setIcon( tabIconRecord );
    		butRecord.setToolTipText( "Render" );
    	}
		if(!aPanel.bRenderMultiBox)
		{
			Prefs.showMultibox(true);
		}
		if(aPanel.bRenderScaleBar)
		{
			Prefs.showScaleBar(false);
			Prefs.showScaleBarInMovie( false);
		}
		Prefs.showTextOverlay(true);
		
    	//unlock user interaction
    	bt.bInputLock = false;
    	bt.setLockMode(false);

    }

	
	
}
