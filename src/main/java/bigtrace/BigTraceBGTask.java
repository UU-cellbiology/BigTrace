package bigtrace;

import javax.swing.SwingWorker;

public class BigTraceBGTask extends SwingWorker<Void, String>
{
	private String progressState;
	
	public String getProgressState()
	{
		return progressState;
	}

	public void setProgressState(String state_)
	{
		progressState = state_;
	}
	
	@Override
	protected Void doInBackground() throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

}
