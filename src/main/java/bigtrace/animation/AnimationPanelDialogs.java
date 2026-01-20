package bigtrace.animation;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;

import bigtrace.BigTrace;
import bigtrace.gui.GBCHelper;
import bigtrace.gui.GetFolderDialog;
import bigtrace.gui.NumberField;
import bigtrace.rois.AbstractCurve3D;
import bigtrace.rois.Roi3D;
import ij.IJ;
import ij.Prefs;
import ij.io.OpenDialog;

public class AnimationPanelDialogs< T extends RealType< T > & NativeType< T > >
{
	final BigTrace<T> bt;
	
	final AnimationPanel<T> pan;
	
	public AnimationPanelDialogs(final BigTrace<T> bt_, final AnimationPanel<T> pan_)
	{
		bt = bt_;
		pan = pan_;
	}

	boolean dialRenderSettings()
	{
		final JPanel panRenderSettings = new JPanel();
		panRenderSettings.setLayout(new GridBagLayout());
		
		GridBagConstraints cd = new GridBagConstraints();
		GBCHelper.alighLeft(cd);
		
		final NumberField nfFPS = new NumberField(4);
		nfFPS.setIntegersOnly( true );
		nfFPS.setText(Integer.toString( pan.nRenderFPS ));
		final NumberField nfWidth = new NumberField(4);
		nfWidth.setIntegersOnly( true );
		nfWidth.setText(Integer.toString( pan.nRenderWidth ));
		final NumberField nfHeight = new NumberField(4);
		nfHeight.setIntegersOnly( true );
		nfHeight.setText(Integer.toString( pan.nRenderHeight));
		
		cd.gridx = 0;
		cd.gridy = 0;	
		panRenderSettings.add(new JLabel("Render FPS:"),cd);
		cd.gridx++;
		panRenderSettings.add(nfFPS, cd);	
		
		cd.gridx = 0;
		cd.gridy++;	
		panRenderSettings.add(new JLabel("Render width (px):"),cd);
		cd.gridx++;
		panRenderSettings.add(nfWidth, cd);			
		
		cd.gridx = 0;
		cd.gridy++;	
		panRenderSettings.add(new JLabel("Render height (px):"),cd);
		cd.gridx++;
		panRenderSettings.add(nfHeight, cd);			
		
		int reply = JOptionPane.showConfirmDialog(null, panRenderSettings, "Render settings", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			pan.nRenderFPS = Integer.parseInt( nfFPS.getText());
			Prefs.set("BigTrace.nRenderFPS", (double)pan.nRenderFPS);
			
			pan.nRenderWidth = Integer.parseInt( nfWidth.getText());
			Prefs.set("BigTrace.nRenderWidth", (double)pan.nRenderWidth);
			
			pan.nRenderHeight = Integer.parseInt( nfHeight.getText());
			Prefs.set("BigTrace.nRenderHeight", (double)pan.nRenderHeight);
			
			pan.sRenderSavePath = GetFolderDialog.getSelectedFolder( bt, "Save animation frames to folder.." );
			
			if(pan.sRenderSavePath == null)
			{
				bt.btPanel.progressBar.setString("animation aborted.");
				return false;
			}
			return true;
		}
		return false;
		
	}
	
	void dialPlayerSettings()
	{
		final JPanel panPlayerSettings = new JPanel();
		panPlayerSettings.setLayout(new GridBagLayout());
		
		GridBagConstraints cd = new GridBagConstraints();
		GBCHelper.alighLeft(cd);

		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		
		final NumberField nfSpeedFactor = new NumberField(4);
		nfSpeedFactor.setText(df.format(pan.fPlaySpeedFactor));
		
		cd.gridx=0;
		cd.gridy=0;	
		panPlayerSettings.add(new JLabel("Play speed (0.01-100):"),cd);
		cd.gridx++;
		panPlayerSettings.add(nfSpeedFactor, cd);	
		
		JCheckBox cbBackForth = new JCheckBox();
		cbBackForth.setSelected( Prefs.get("BigTrace.bPlayerBackForth", false) );
		cd.gridy++;
		cd.gridx=0;
		panPlayerSettings.add(new JLabel("Loop back and forth"),cd);
		cd.gridx++;
		panPlayerSettings.add(cbBackForth,cd);
		
		int reply = JOptionPane.showConfirmDialog(null, panPlayerSettings, "Play preview settings", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			
			pan.fPlaySpeedFactor = ( float ) Math.min(Math.max( 0.01,Math.abs( Float.parseFloat( nfSpeedFactor.getText()))),100);
			
			pan.bPlayerBackForth = cbBackForth.isSelected();
			Prefs.set("BigTrace.bPlayerBackForth", pan.bPlayerBackForth);
			
			
		
		}
	}
	
	
	boolean dialChangeTotalTime(boolean bLarger)
	{
		final JPanel panelTotTimeSettings = new JPanel();
		panelTotTimeSettings.setLayout(new GridBagLayout());
		
		GridBagConstraints cd = new GridBagConstraints();
		GBCHelper.alighLeft(cd);
		
		final String[] sTotTimeOptionsL  = { "Add at the start", "Add at the end", "Stretch"};
		final String[] sTotTimeOptionsS = { "Cut at the start", "Cut at the end", "Compress"};
		final JComboBox<String> cbTotTimeOptions;
		if(bLarger)
		{
			cbTotTimeOptions = new JComboBox<>(sTotTimeOptionsL);
		}
		else
		{
			cbTotTimeOptions = new JComboBox<>(sTotTimeOptionsS);
		}
		cbTotTimeOptions.setSelectedIndex(pan.nChangeTotalTimeMode);
		cd.gridx=0;
		cd.gridy=0;	

		panelTotTimeSettings.add(cbTotTimeOptions, cd);	
		int reply = JOptionPane.showConfirmDialog(null, panelTotTimeSettings, "Change total time", 
				JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			pan.nChangeTotalTimeMode = cbTotTimeOptions.getSelectedIndex();
			Prefs.set("BigTrace.nChangeTotalTimeMode", pan.nChangeTotalTimeMode);
			return true;
		}
		return false;
		
	}
	
	public void dialUnCoilAnimation(final Roi3D roiIn)
	{
		
		int nUnCoilTask;
		int nTotFramesUnCoil;
		boolean bFinalVector;
		boolean bCleanVolume;
		int nUnCoilExport;
		
		final JPanel unCoilSettings = new JPanel();
		unCoilSettings.setLayout(new GridBagLayout());
		
		GridBagConstraints cd = new GridBagConstraints();
		GBCHelper.alighLeft(cd);
		
		
		final String[] sUnCoilTask = { "Generate ROIs only", "Generate ROIs and volumes"};
		JComboBox<String> cbUnCoilTask = new JComboBox<>(sUnCoilTask);
		cbUnCoilTask.setSelectedIndex((int)Prefs.get("BigTrace.nUnCoilTask", 0));
		
		cd.gridx=0;
		cd.gridy=0;	
		unCoilSettings.add(new JLabel("Straighten/Uncoil task:"),cd);
		cd.gridx++;
		unCoilSettings.add(cbUnCoilTask, cd);	
		
		final NumberField nfTotFrames = new NumberField(4);
		nfTotFrames.setIntegersOnly(true);
		nfTotFrames.setText(Integer.toString((int)Prefs.get("BigTrace.nTotFramesUnCoil", 60)));
		cd.gridy++;
		cd.gridx=0;
		unCoilSettings.add(new JLabel("Total number of frames (>=2):"),cd);
		cd.gridx++;
		unCoilSettings.add(nfTotFrames,cd);

		JCheckBox cbFinalVector = new JCheckBox();
		cbFinalVector.setSelected( Prefs.get("BigTrace.bFinalVector", false) );
		cd.gridy++;
		cd.gridx=0;
		unCoilSettings.add(new JLabel("Specify final orientation vector?"),cd);
		cd.gridx++;
		unCoilSettings.add(cbFinalVector,cd);
		
		JCheckBox cbAddCleanVolume = new JCheckBox();
		cbAddCleanVolume.setSelected( Prefs.get("BigTrace.bCleanVolume", false) );
		cd.gridy++;
		cd.gridx=0;
		unCoilSettings.add(new JLabel("Use modified straight volume?"),cd);
		cd.gridx++;
		unCoilSettings.add(cbAddCleanVolume,cd);

		final String[] sUnCoilExport = { "Export as BDV HDF5", "Export as TIF","Export as compressed TIF"};
		JComboBox<String> cbUnCoilExport = new JComboBox<>(sUnCoilExport);
		cbUnCoilExport.setSelectedIndex((int)Prefs.get("BigTrace.sUnCoilExport", 0));
		
		cd.gridy++;
		cd.gridx=0;
		unCoilSettings.add(new JLabel("Output format:"),cd);
		cd.gridx++;
		unCoilSettings.add(cbUnCoilExport,cd);
		
		int reply = JOptionPane.showConfirmDialog(null, unCoilSettings, "Straighten animation", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			nUnCoilTask = cbUnCoilTask.getSelectedIndex();
			Prefs.set("BigTrace.nUnCoilTask", nUnCoilTask);
			
			nTotFramesUnCoil = Math.max( 2,Math.abs( Integer.parseInt(nfTotFrames.getText())));
			Prefs.set("BigTrace.nTotFramesUnCoil", nTotFramesUnCoil);
			
			bFinalVector = cbFinalVector.isSelected();
			Prefs.set("BigTrace.bFinalVector", bFinalVector);
			
			bCleanVolume = cbAddCleanVolume.isSelected();
			Prefs.set("BigTrace.bCleanVolume", bCleanVolume);
			
			nUnCoilExport = cbUnCoilExport.getSelectedIndex();
			Prefs.set("BigTrace.nUnCoilExport", nUnCoilExport);
			
			double [] dFinalOrientation = null;
			
			if(bFinalVector)
			{
				dFinalOrientation =  AnimationPanelDialogs.dialFinalVector();
				if(dFinalOrientation == null)
				{
					bt.btPanel.progressBar.setString("straightening animation aborted.");
					return;
				}
			}
			//if need to provide clean volume
			String filenameCleanTIF = null;
			if(bCleanVolume && nUnCoilTask>0)
			{
				OpenDialog openDial = new OpenDialog("Load modified straightened TIF","", "*.tif");
				
		        String path = openDial.getDirectory();
		        if (path==null)
		        	return;

		        filenameCleanTIF = path+openDial.getFileName();
			}
			
			//if saving, ask for the path
			String sSaveDir = "";
			if(nUnCoilTask > 0)
			{
				sSaveDir = IJ.getDirectory("Save animation volumes to..");
				if(sSaveDir == null)
				{
					bt.btPanel.progressBar.setString("straightening animation aborted.");
					return;
				}
			}
			
			UnCoilAnimation<T> unAnim = new UnCoilAnimation<>(bt);
			unAnim.inputROI = ( AbstractCurve3D ) roiIn;
			unAnim.nFrames = nTotFramesUnCoil;
			unAnim.nUnCoilTask = nUnCoilTask;
			unAnim.finalOrientation = dFinalOrientation;
			unAnim.sSaveFolderPath = sSaveDir;
			unAnim.nUnCoilExport = nUnCoilExport;
			if(bCleanVolume && nUnCoilTask>0)
			{
				unAnim.bUseTemplate = true;
				if(!unAnim.loadTemplate( filenameCleanTIF ))
					return;
			}
			unAnim.addPropertyChangeListener(bt.btPanel);
			unAnim.execute();
		}
	}
	
	boolean dialEditKeyFrame(final int nInd)
	{
		final DefaultListModel< KeyFrame > listModel = pan.listModel;
		final KeyFrameAnimation< T > kfAnim = pan.kfAnim;
		
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.0", decimalFormatSymbols);
		
		final JPanel panEdit = new JPanel();
		panEdit.setLayout(new GridBagLayout());
		
		GridBagConstraints cd = new GridBagConstraints();
		GBCHelper.alighLeft(cd);
		
		JTextField tfName = new JTextField(listModel.get( nInd ).name); 
		
		NumberField nfTimePoint = new NumberField(4);		
		nfTimePoint.setText(df.format(listModel.get( nInd ).fMovieTimePoint));
		
		cd.gridx=0;
		cd.gridy=0;	
		panEdit.add(new JLabel("Name:"),cd);
		cd.gridx++;
		panEdit.add(tfName, cd);	
		
		cd.gridx=0;
		cd.gridy++;	
		panEdit.add(new JLabel("Time position:"),cd);
		cd.gridx++;
		panEdit.add(nfTimePoint, cd);	
		
		
		int reply = JOptionPane.showConfirmDialog(null, panEdit, "Edit KeyFrame", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
				
		if (reply == JOptionPane.OK_OPTION) 
		{
		
			if(tfName.getText().length()>0)
			{
				listModel.get( nInd ).name = tfName.getText();
			}
			float fNewTime = Math.min(Math.max(0, Float.parseFloat( nfTimePoint.getText())), kfAnim.nTotalTime);
			if(Math.abs( listModel.get( nInd ).fMovieTimePoint - fNewTime)>0.001)
			{
				listModel.get( nInd ).fMovieTimePoint = fNewTime;
				return true;
			}
			return false;
		}
		return false;
	}
	
	static public double[] dialFinalVector()
	{
		JPanel pFinalVector = new JPanel(new GridLayout(0,2,6,0));
		double [] dFinalVector = new double[3];
		ArrayList<NumberField> nfCoalignVector = new ArrayList<>();
		int d;
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.000", decimalFormatSymbols);
		for(d=0;d<3;d++)
		{
			nfCoalignVector.add( new NumberField(4));
			nfCoalignVector.get(d).setText(df.format(Prefs.get("BigTrace.finalVec"+Integer.toString(d),1.0)));
		}
		pFinalVector.add(new JLabel("X coord: "));
		pFinalVector.add(nfCoalignVector.get(0));
		pFinalVector.add(new JLabel("Y coord: "));
		pFinalVector.add(nfCoalignVector.get(1));
		pFinalVector.add(new JLabel("Z coord: "));
		pFinalVector.add(nfCoalignVector.get(2));
		
		int reply = JOptionPane.showConfirmDialog(null, pFinalVector, "Coalignment vector coordinates", 
		        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (reply == JOptionPane.OK_OPTION) 
		{
			double [] inVector = new double[3];
			for(d = 0; d < 3; d++)
				inVector[d] = Double.parseDouble(nfCoalignVector.get(d).getText());
			double len = LinAlgHelpers.length(inVector);
			if(len < 0.000001)
			{
				IJ.error("Vector length should be more than zero!");
				return null;
			}
			LinAlgHelpers.normalize(inVector);
			for(d = 0; d < 3; d++)
			{
				dFinalVector[d] = inVector[d];
				Prefs.set("BigTrace.finalVec"+Integer.toString(d), dFinalVector[d]);				
			}
			return dFinalVector;
			
		}
		return null;
		
	}
	
	void dialPanelSettings()
	{
		JPanel pAnimSettings = new JPanel();
		
		GridBagConstraints cd = new GridBagConstraints();
	
		pAnimSettings.setLayout(new GridBagLayout());
		
		JCheckBox cbMultiBox = new JCheckBox();
		cbMultiBox.setSelected( pan.bRenderMultiBox);
		
		JCheckBox cbScaleBar = new JCheckBox();
		cbScaleBar.setSelected( pan.bRenderScaleBar);
		
		NumberField nfFrameRenderMax = new NumberField(4);
		nfFrameRenderMax.setIntegersOnly(true);
		nfFrameRenderMax.setText(Integer.toString(pan.nRenderFrameTimeLimit));
		
		cd.gridx = 0;
		cd.gridy = 0;	
		GBCHelper.alighLoose(cd);
		pAnimSettings.add(new JLabel("Render BVV MultiBox: "),cd);
		cd.gridx++;
		pAnimSettings.add(cbMultiBox,cd);
	
		
		cd.gridx = 0;
		cd.gridy++;
		pAnimSettings.add(new JLabel("Render scale bar: "),cd);
		cd.gridx++;
		pAnimSettings.add(cbScaleBar,cd);
		
		cd.gridx=0;
		cd.gridy++;
		pAnimSettings.add(new JLabel("Maximum frame render limit (s): "),cd);
		cd.gridx++;
		pAnimSettings.add(nfFrameRenderMax,cd);
		
		cd.gridx=0;
		cd.gridy++;
		cd.gridwidth = 2;
		pAnimSettings.add(new JLabel("OpenGL viewport resolution "+ 
				Integer.toString( bt.btData.renderParams.renderWidth )
				+"x"+Integer.toString( bt.btData.renderParams.renderHeight) + " (px)"),cd);
		
		int reply = JOptionPane.showConfirmDialog(null, pAnimSettings, "Animation Settings", 
		        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);


		if (reply == JOptionPane.OK_OPTION) 
		{
			//multibox
			pan.bRenderMultiBox = cbMultiBox.isSelected();
			Prefs.set("BigTrace.bRenderMultiBox", pan.bRenderMultiBox );

			//scale bar
			pan.bRenderScaleBar = cbScaleBar.isSelected();
			Prefs.set("BigTrace.bRenderScaleBar", pan.bRenderScaleBar );
			
			pan.nRenderFrameTimeLimit = Integer.parseInt(nfFrameRenderMax.getText());
			Prefs.set("BigTrace.nRenderFrameTimeLimit", pan.nRenderFrameTimeLimit);
		}
	
	}
}
