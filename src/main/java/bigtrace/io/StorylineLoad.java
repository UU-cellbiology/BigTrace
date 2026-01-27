package bigtrace.io;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.BigTrace;
import bigtrace.BigTraceData;
import bigtrace.animation.AnimationPanel;
import bigtrace.animation.KeyFrame;
import bigtrace.animation.KeyFrameAnimation;
import bigtrace.animation.Scene;


public class StorylineLoad < T extends RealType< T > & NativeType< T > > 
{
	
	/** plugin instance **/
	final BigTrace<T> bt;
	
	final AnimationPanel< T > aPanel;
	
	public StorylineLoad(final BigTrace<T> bt_,  AnimationPanel< T > aPanel_)
	{
		bt = bt_;	
		aPanel = aPanel_;
	}
	public void loadAnimation(String sFilename)
	{
		String[] line_array;
		
		KeyFrameAnimation kfAnim = aPanel.kfAnim;
		
		KeyFrame kfLoad = new KeyFrame("loading");
		
		try ( BufferedReader br = new BufferedReader(new FileReader(sFilename));) 
		{
			 String line = br.readLine();
			 line_array = line.split(",");
			 if(!line_array[0].equals("BigTrace_StoryLine"))
			 {
				 System.err.println("Not a BigTrace storyline file format, aborting");
				 return;
			 }
			 if(!line_array[2].equals(BigTraceData.sVersion))
			 {
				 System.out.println("Version mismatch: storyline file "+line_array[2]+", plugin "+BigTraceData.sVersion+". It should be fine in theory, so loading anyway.");
			 }
			 
			 aPanel.listModel.clear();
		     
			 line = br.readLine();
			 
			 while (line != null) 
			 {				
				 // process the line.
				 line_array = line.split(",");
				 switch (line_array[0])
				 {
				 case "TotalTime":
					 kfAnim.keyFrames.clear();
					 aPanel.nfTotalTime.setText( line_array[1] );
					 aPanel.setNewTotalTime( Integer.parseInt(line_array[1]) );	
					 break;
				 
				 case "KeyFrameName":
					 kfLoad = new KeyFrame(line_array[1]);
					 break;
				 case "KeyFrameTime":
					 kfLoad.setMovieTimePoint( Float.parseFloat(  line_array[1] ));
					 Scene scLoad = new Scene();
					 scLoad.load( br );
					 kfLoad.setScene( scLoad );
					 aPanel.listModel.addElement( kfLoad );
					 break;
				 }
				 line = br.readLine(); 
			 }
			 br.close();
		}
		catch ( FileNotFoundException exc )
		{
			System.err.print(exc.getMessage());
			exc.printStackTrace();
		}
		catch ( IOException exc )
		{
			System.err.print(exc.getMessage());
			exc.printStackTrace();
		}
		
		//let's update everything
		aPanel.updateKeyIndices();
		aPanel.updateKeyMarks();
		kfAnim.updateTransitionTimeline();
		
		return;
	}
}
