package bigtrace.animation;

import javax.swing.Timer;

import bigtrace.BigTrace;

public class AnimationPlayer
{
	final BigTrace<?> bt;
	final AnimationPanel aPanel;
	private boolean playing = false;
    private final Timer timer;
    private float playbackSpeed = 1.0f;
    private float currentTime = 0f;
    private long lastTickNanos;
    private float fIncSign = 1;
    
    private boolean bSliderUpdateState;
    
	public AnimationPlayer(final BigTrace<?> bt_, AnimationPanel aPanel_)
	{
		this.bt = bt_;
		this.aPanel = aPanel_;
		timer = new Timer(1000 / 30, e -> tick());
	}
	
    public void play() 
    {
        if (playing) 
        	return;
        
        bSliderUpdateState = aPanel.bUpdateSlider;
        aPanel.bUpdateSlider = false;
		aPanel.butPlayStop.setIcon( aPanel.tabIconStop );
		aPanel.butPlayStop.setToolTipText( "Stop playing" );
		bt.bInputLock = true;
		bt.setLockMode(true);
		aPanel.butPlayStop.setEnabled( true );
        playing = true;
        lastTickNanos = System.nanoTime();
        fIncSign = 1;
        currentTime = aPanel.timeSlider.getValue() * (float) aPanel.kfAnim.nTotalTime / aPanel.tsSpan ;
        timer.start();
    }
    
    public void stop() 
    {
        timer.stop();
        playing = false;
        aPanel.bUpdateSlider = bSliderUpdateState;
        aPanel.butPlayStop.setIcon( aPanel.tabIconPlay );
		aPanel.butPlayStop.setToolTipText( "Play Preview" );
		bt.bInputLock = false;
		bt.setLockMode(false);

    }
    
    private void tick() 
    {
    	
    	long now = System.nanoTime();
    	float deltaSeconds =
    			(now - lastTickNanos) / 1_000_000_000f;

    	lastTickNanos = now;

    	currentTime += fIncSign * deltaSeconds * playbackSpeed;

        if (!playing) 
        {
            timer.stop();
            return;
        }

        if (currentTime > aPanel.kfAnim.nTotalTime) 
        {
        	if(!aPanel.bPlayerBackForth)
			{
        		currentTime = 0;
			}
        	else 
        	{
        		fIncSign = -1;
        		currentTime = aPanel.kfAnim.nTotalTime;
        	}
        }
		if(currentTime < 0)
		{
			fIncSign = 1;
			currentTime = 0;
		}	
        aPanel.updateScene( currentTime );	
        
        // update slider
        int nSliderPosition = ( int ) ( currentTime * aPanel.tsSpan/aPanel.kfAnim.getTotalTime() );
        aPanel.timeSlider.setValue( nSliderPosition );

    }
    
    public boolean isPlaying()
    {
    	return playing;
    }
    
    public void setPlaybackSpeed (final float fSpeed)
    {
    	playbackSpeed = fSpeed;
    }
    public float getPlaybackSpeed ()
    {
    	return playbackSpeed;
    }
}
