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

	public JComboBox<String> cbRenderMethod;
	
	public JComboBox<String> cbSurfaceRenderList; 
	
	String[] sSurfaceRenderType = {"plain", "shaded", "shiny", "silhouette"};
	
	BigTrace<T> bt;
	
	public RenderMethodPanel(BigTrace<T> bt_)
	{
		super();
		bt = bt_;
		
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints cd = new GridBagConstraints();
		String[] sMethods = new String[2];
		sMethods[0]="Max intensity";
		sMethods[1]="Volumetric";
	
		cbRenderMethod = new JComboBox<>(sMethods);
		cbRenderMethod.setSelectedIndex(bt.btData.nRenderMethod);
		cbRenderMethod.addActionListener(this);
		
		setLayout(gridbag);
		
		cd.gridx=0;
		cd.gridy=0;
		GBCHelper.alighLoose(cd);
		this.add(new JLabel("Data:"),cd);
		cd.gridx++;
		this.add(cbRenderMethod,cd);
		
		
		cbSurfaceRenderList = new JComboBox<>(sSurfaceRenderType);
		cbSurfaceRenderList.setSelectedIndex(bt.btData.surfaceRender);
		cbSurfaceRenderList.addActionListener(this);
		cd.gridx=0;
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
