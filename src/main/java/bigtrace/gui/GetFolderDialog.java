package bigtrace.gui;

import java.io.File;

import javax.swing.JFileChooser;

import bigtrace.BigTrace;
import ij.Prefs;

public class GetFolderDialog
{
	public static String getSelectedFolder(final BigTrace<?> bt, final String sTitle)
	{
		final JFileChooser fc = new JFileChooser();
		
		fc.setDialogTitle( sTitle );
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setCurrentDirectory( new File(bt.btData.lastDir) );
		
		int returnVal = fc.showSaveDialog( null );
		
		if(returnVal == JFileChooser.APPROVE_OPTION) 
		{
		    File saveFolder = fc.getSelectedFile();
		    bt.btData.lastDir = saveFolder.getAbsolutePath();
		    Prefs.set( "BigTrace.lastDir", bt.btData.lastDir );
		    return saveFolder.getAbsolutePath() + File.separator;

		}
		return null;
	}
}
