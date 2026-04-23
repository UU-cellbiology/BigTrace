import ij.IJ
import ij.io.DirectoryChooser
import ij.text.TextWindow
import ij.WindowManager
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import bigtrace.BigTrace

//define file suffix here, can be different, czi, lif, xml, etc
def suffix = ".tif"
//define autotrace min intensity start 
def dMinIntStart = 30000 as double
//minimum lenght for autotrace
def nMinLength = 100 as int

// Choose input directory
def dc = new DirectoryChooser("Input directory")
def input = dc.getDirectory()

//clear the log window
def logWindow = WindowManager.getWindow("Log")
if (logWindow != null) {
    def textPanel = logWindow.getTextPanel()
    textPanel.clear()
}
// Create timestamp to save log later
def sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
def timestamp = sdf.format(new Date())

// Get file list
def dir = new File(input)
def list = dir.list()

int nCount = 0
boolean bFirst = true

BT = new BigTrace(); 
list.each { filename ->

    if (filename.endsWith(suffix)) {

        def basecurr = input + filename

        // filename without extension
        def noExtFilename = filename[0..-(suffix.length()+1)]

        nCount++

        if (bFirst) {
            BT.run( basecurr )
            bFirst = false
        } else {
            BT.btMacro.macroOpenNext(basecurr)
        }
		//make a folder to store things
        def saveDir = input + nCount + File.separator
        new File(saveDir).mkdirs()
        
        //process image in BigTrace

		//set active channel for tracing (if multichannel)
        BT.btMacro.macroSetActiveChannel(1)
        //maybe set other parameters, otherwise current stored (last used) will be used
		//....
		
		//run auto-trace (all frames)
        BT.btMacro.macroRunFullAutoTrace(dMinIntStart, nMinLength)
        //save ROIs in CSV
        BT.btMacro.macroSaveROIs(saveDir + "rois.csv", "CSV")
        //obtain and save measurements
        BT.btMacro.macroMeasureAndSave(saveDir + "Results.csv")
        //save straightened versions of ROIs
        //BT.btMacro.macroStraighten(0, saveDir, "Round")
        //can save log file here too, after each volume, in case something goes wrong
    }
}
BT.btMacro.macroClose()

//save log
// Create filename
def filePath = input + "BigTrace_groovy_Log_" + timestamp + ".txt"

// Save log to file
new File(filePath).text = IJ.getLog()