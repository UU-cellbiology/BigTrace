package bigtrace.gui;

import javax.swing.SwingUtilities;

public class TaskBT
{
	public static void runOnEDT(Runnable r) {
	    if (SwingUtilities.isEventDispatchThread()) {
	        r.run();
	    } else {
	        SwingUtilities.invokeLater(r);
	    }
	}
	
	public static void runOnEDTAndWait(Runnable r) {
	    if (SwingUtilities.isEventDispatchThread()) {
	        r.run();
	    } else {
	        try {
	            SwingUtilities.invokeAndWait(r);
	        } catch (Exception e) {
	            throw new RuntimeException(e);
	        }
	    }
	}
}
