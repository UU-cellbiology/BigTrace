package bigtrace.io;

import java.util.ArrayList;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.BigTrace;

public class ROIsIO
{
	public static <T extends RealType< T > & NativeType< T > > void loadROIs(String filename, final int nLoadMode, final BigTrace<T> bt)
	{
		if( nLoadMode == 0 )
		{
        	bt.roiManager.groups = new ArrayList<>();
        	bt.roiManager.rois.clear();
        	bt.roiManager.listModel.clear();
		}
		
        bt.bInputLock = true;
        bt.setLockMode(true);
        
        ROIsLoadBG<T> loadTask = new ROIsLoadBG<>();
        
        loadTask.sFilename = filename;
        loadTask.nLoadMode = nLoadMode;
        loadTask.bt = bt;
        loadTask.addPropertyChangeListener(bt.btPanel);
        loadTask.execute();	
	}

	public static <T extends RealType< T > & NativeType< T > > void saveROIs(String fullFilename, final int nSaveMode, final BigTrace<T> bt)
	{
		
		bt.setLockMode(true);
		bt.bInputLock = true;
		
		switch (nSaveMode)
		{
		case 0: 
			ROIsSaveBG<T> saveTask = new ROIsSaveBG<>();
			saveTask.sFilename = fullFilename;
			saveTask.bt = bt;
			saveTask.addPropertyChangeListener(bt.btPanel);
			saveTask.execute();
			break;
		case 1: 	
			ROIsExportCSV<T> exportTask = new ROIsExportCSV<>();
			exportTask.sFilename = fullFilename;
			exportTask.bt = bt;
			exportTask.addPropertyChangeListener(bt.btPanel);
			exportTask.execute();
			break;
		case 2: 
			ROIsExportSWC<T> exportSWCTask = new ROIsExportSWC<>();
			exportSWCTask.sFilename = fullFilename;
			exportSWCTask.bt = bt;
			exportSWCTask.addPropertyChangeListener(bt.btPanel);
			exportSWCTask.execute();
			break;
		}
	}
}
