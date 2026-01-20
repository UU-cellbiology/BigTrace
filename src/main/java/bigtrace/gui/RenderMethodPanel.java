package bigtrace.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ij.Prefs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.BigTrace;


public class RenderMethodPanel < T extends RealType< T > & NativeType< T > > extends JPanel implements ActionListener
{
	
	private static final long serialVersionUID = 7367842640615289454L;

	public final JComboBox<String> cbRenderMethod;
	
	public final JComboBox<String> cbVolumeLight;
	
	public final JComboBox<String> cbSurfaceRenderList; 
	
	String[] sSurfaceRenderType = {"plain", "shaded", "shiny", "silhouette"};
	
	BigTrace<T> bt;
	
	public RenderMethodPanel(BigTrace<T> bt_)
	{
		super();
		bt = bt_;
		
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints cd = new GridBagConstraints();
		String[] sRenderMethods = new String[2];
		sRenderMethods[0] = "max intensity";
		sRenderMethods[1] = "volumetric";

		String[] sLightOptions = new String[3];
		sLightOptions[0] = "plain";
		sLightOptions[1] = "shaded";
		sLightOptions[2] = "shiny";	
		
		cbRenderMethod = new JComboBox<>(sRenderMethods);
		cbRenderMethod.setSelectedIndex(bt.btData.nRenderMethod);
		cbRenderMethod.addActionListener(this);
		
		cbVolumeLight = new JComboBox<>(sLightOptions);
		cbVolumeLight.setSelectedIndex(bt.btData.nVolumeLight);
		cbVolumeLight.addActionListener(this);

		cbSurfaceRenderList = new JComboBox<>(sSurfaceRenderType);
		cbSurfaceRenderList.setSelectedIndex(bt.btData.surfaceRender);
		cbSurfaceRenderList.addActionListener(this);
		
		
		setLayout(gridbag);
		
		cd.gridx = 0;
		cd.gridy = 0;
		GBCHelper.alighLoose(cd);
		this.add(new JLabel("Data render:"),cd);
		cd.gridx++;
		this.add(cbRenderMethod,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("Volume light:"),cd);
		cd.gridx++;
		this.add(cbVolumeLight,cd);
		
		cd.gridx = 0;
		cd.gridy++;
		this.add(new JLabel("ROI surface:"),cd);
		cd.gridx++;
		this.add(cbSurfaceRenderList,cd);	
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == cbRenderMethod)
		{
			bt.btPanel.setRenderMethod(cbRenderMethod.getSelectedIndex());		
		}
		
		if(e.getSource() == cbVolumeLight)
		{
			bt.btPanel.setVolumeLight(cbVolumeLight.getSelectedIndex());		
		}
		
		if(e.getSource() == cbSurfaceRenderList)
		{
			if(bt.btData.surfaceRender != cbSurfaceRenderList.getSelectedIndex())
			{
//	
				bt.btData.surfaceRender = cbSurfaceRenderList.getSelectedIndex();
				Prefs.set("BigTrace.surfaceRender", bt.btData.surfaceRender);
				bt.viewer.showMessage("ROI surface: "+ sSurfaceRenderType[bt.btData.surfaceRender]);
				//long start1 = System.currentTimeMillis();
				bt.repaintBVV();
				
				//long end1 = System.currentTimeMillis();
				//System.out.println("Mesh update in milli seconds: "+ (end1-start1));
			}
		}
	}

}
