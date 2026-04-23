

//define file suffix here, can be different, czi, lif, xml, etc
suffix = ".tif";
//define autotrace min intensity start 
dMinIntStart = 30000.;
//minimum lenght for autotrace
nMinLength = 100;

// Choose input directory
input = getDirectory("Input directory");

//clear the log window
print("\\Clear");

// Create timestamp to save log later
timestamp = getTimeStamp_sec();

// Get file list
list = getFileList(input);

nCount = 0;
bFirst = true;

for (nFile = 0; nFile < list.length; nFile++) 
{
	if(endsWith(list[nFile], suffix))	
	{		
		basecurr = input + list[nFile];
		//in case you need only filename without extension somewhere later
		noExtFilename = substring(list[nFile], 0, lengthOf(list[nFile])-lengthOf(suffix));
		nCount++;
		if(bFirst)
		{
			run("Open 3D image", "open=[" + basecurr + "]"); 
			bFirst = false;
		}
		else 
		{
			Ext.btOpenNext(basecurr); 
		}

		//make a folder to store things
		saveDir = input + toString(nCount) + "/";		
		File.makeDirectory(saveDir); 
		
		//process image in BigTrace
		///set active channel for tracing (if multichannel)
		Ext.btSetActiveChannel(1);
		//maybe set other parameters, otherwise current stored (last used) will be used
		//....
		
		//run auto-trace (all frames)
		Ext.btRunFullAutoTrace(dMinIntStart, nMinLength);
		//save ROIs in CSV
		Ext.btSaveROIs(saveDir + "rois.csv", "CSV");
		//obtain and save measurements
		Ext.btMeasureAndSave (saveDir + "Results.csv");
		//save straightened versions of ROIs
		//Ext.btStraighten(0, saveDir, "Round");
		//can save log file here too, after each volume, in case something goes wrong
	}

}
Ext.btClose(); 
selectWindow("Log");
saveAs("Text", input + "BigTrace_IJmacro_" + timestamp + ".txt");
	
function getTimeStamp_sec() 
{ 
	// returns timestamp: yearmonthdayhourminutesecond
	getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);
	
	TimeStamp = toString(year) + "-" + IJ.pad(month+1,2) + "-" + IJ.pad(dayOfMonth,2) + "-";
	TimeStamp = TimeStamp + IJ.pad(hour,2) + "-" + IJ.pad(minute,2) + "-" + IJ.pad(second,2);
	return TimeStamp;
}