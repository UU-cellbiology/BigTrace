package bigtrace.rois;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import bdv.tools.brightness.ColorIcon;
import bigtrace.BigTraceData;
import bigtrace.gui.NumberField;
import ij.IJ;
import ij.Prefs;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.plugin.LutLoader;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;



public class Roi3DGroupManager < T extends RealType< T > & NativeType< T > > implements ListSelectionListener, ActionListener {

	private JDialog dialog;
	private JOptionPane optionPane;
	
	public DefaultListModel<String> listModel; 
	JList<String> jlist;
	JScrollPane listScroller;
	
	JPanel presetList;
	
	JButton butEdit;
	JButton butCopyNew;
	JButton butDelete;
	JButton butMerge;
	JButton butColor;
	JButton butSave;
	JButton butLoad;
	
	
	
	RoiManager3D <T> roiManager;
	
	public ColorUserSettings selectColors = new ColorUserSettings();
	
	public Roi3DGroupManager(RoiManager3D<T> roiManager_)	
	{
		 roiManager  = roiManager_;	 
    }
	
	public void initGUI()
	{
		 listModel = new  DefaultListModel<>();
		 jlist = new JList<>(listModel);
		 jlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		 jlist.setLayoutOrientation(JList.VERTICAL);
		 jlist.setVisibleRowCount(-1);
		 jlist.addListSelectionListener(this);
		 listScroller = new JScrollPane(jlist);
		 listScroller.setPreferredSize(new Dimension(170, 400));
		 listScroller.setMinimumSize(new Dimension(170, 250));
		 	 
		 for (int i = 0;i<roiManager.groups.size();i++)
		 {
			 listModel.addElement(roiManager.groups.get(i).getName());
		 }		 
		 
		 presetList = new JPanel(new GridBagLayout());
		 //presetList.setBorder(new PanelTitle(" Groups Manager "));

		 butEdit = new JButton("Edit");
		 butEdit.addActionListener(this);
		 butCopyNew = new JButton("Copy/New");
		 butCopyNew.addActionListener(this);	 
		 butDelete = new JButton("Delete");
		 butDelete.addActionListener(this);
		 butMerge = new JButton("Merge");
		 butMerge.addActionListener(this);
		 butColor = new JButton("Color");
		 butColor.addActionListener(this);
		 butSave = new JButton("Save");
		 butSave.addActionListener(this);
		 butLoad = new JButton("Load");
		 butLoad.addActionListener(this);

		 jlist.setSelectedIndex(roiManager.nActiveGroup);
		 
		 GridBagConstraints cr = new GridBagConstraints();
		 cr.gridx=0;
		 cr.gridy=0;
		 //cr.weighty=0.5;
		 cr.gridheight=GridBagConstraints.REMAINDER;
		 presetList.add(listScroller,cr);

		 cr.gridx++;
		 cr.gridy++;
		 cr.gridheight=1;
		 //cr.fill = GridBagConstraints.NONE;
		 presetList.add(butEdit,cr);		 
		 cr.gridy++;
		 presetList.add(butCopyNew,cr);		 	 
		 cr.gridy++;
		 presetList.add(butDelete,cr);
		 cr.gridy++;
		 presetList.add(butMerge,cr);
		 cr.gridy++;
		 presetList.add(butColor,cr);
		 cr.gridy++;
		 presetList.add(butSave,cr);
		 cr.gridy++;
		 presetList.add(butLoad,cr);
		 
		 
	     // Blank/filler component
		 cr.gridx++;
		 cr.gridy++;
		 cr.weightx = 0.01;
	     cr.weighty = 0.01;
	     presetList.add(new JLabel(), cr);		
		 
		 optionPane = new JOptionPane(presetList);
	     dialog = optionPane.createDialog("ROI Groups manager");
	     dialog.setModal(true);
	}
	
	public void show()
	{ 
		dialog.setVisible(true); 
	}

	/** show Group Properties dialog**/
	void dialogMerge(int indList)	
	{
		
		JPanel dialMerge = new JPanel(new GridBagLayout());
		GridBagConstraints cd = new GridBagConstraints();
		//make a selection list of non-selected groups
		//ask user what he wants to do with ROIs
		String [] sGroupToMerge = new String [roiManager.groups.size()-1];		
		int nCount = 0;
		for(int i=0;i<roiManager.groups.size();i++)
		{
			if(i!=indList)
			{
				sGroupToMerge[nCount] = roiManager.groups.get( i ).getName();
			    nCount++;
			}
		}
		JComboBox<String> cbGroup = new JComboBox<>(sGroupToMerge);
		
		JCheckBox cbDeleteGroup = new JCheckBox();
		cbDeleteGroup .setSelected( Prefs.get( "BigTrace.bMergeDeleteGroup", true ) ); 
		cd.gridx=0;
		cd.gridy=0;
		cd.gridwidth = 2; 
		dialMerge.add(new JLabel("Move ROIs from group "+roiManager.groups.get( indList ).getName() +" to"),cd);
		cd.gridy++;
		dialMerge.add(cbGroup,cd);
		if(indList!=0)
		{
			cd.gridwidth = 1;
			cd.gridy++;
			dialMerge.add(new JLabel("and delete this group"),cd);
			cd.gridx++;
			dialMerge.add(cbDeleteGroup,cd);
			
		}
		int reply = JOptionPane.showConfirmDialog(null, dialMerge, "Merge Groups", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);		
		
		if (reply == JOptionPane.OK_OPTION) 
		{
			boolean bDeleteGroup = false;
			if(indList!=0)
			{
				bDeleteGroup = cbDeleteGroup.isSelected();
				Prefs.set( "BigTrace.bMergeDeleteGroup", bDeleteGroup );
			}
			//get the index of the group to merge
			int nGroupMergeInd = cbGroup.getSelectedIndex();
			
			if(nGroupMergeInd>=indList)
			{
				nGroupMergeInd++;
			}
			roiManager.moveROIsGroups(indList,nGroupMergeInd);
			//System.out.println(roiManager.groups.get(nGroupMergeInd).getName());
			if(bDeleteGroup)
			{
				deleteGroupAndCorrectIndex(indList);
			}
		}
	}
	
	/** show Group Properties dialog**/
	void dialogColor(int indList)	
	{
		String [] luts = IJ.getLuts();
		JComboBox<String> cbLUTs = new JComboBox<>(luts);
		int reply = JOptionPane.showConfirmDialog(null, cbLUTs, "Color ROIs in group using LUT", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);	
		if (reply == JOptionPane.OK_OPTION) 
		{
			
			String sLUTName = ( String ) cbLUTs.getSelectedItem();
			ArrayList<Roi3D> groupROI = new ArrayList<>();
		
			for (int i=0;i<roiManager.rois.size();i++)
			{
				if(roiManager.rois.get(i).getGroupInd() == indList)
				{
					groupROI.add(roiManager.rois.get(i));
				}
			}
			final int nTotRois = groupROI.size();
			IndexColorModel icm = LutLoader.getLut(sLUTName);
			final int nMapSize = icm.getMapSize();
			if (nTotRois>0)
			{
				for (int i=0;i<nTotRois;i++)
				{
					int nIndex = ( int ) Math.round( (i+0.5)*(nMapSize-1)/(nTotRois) );
					Color newColor = new Color(icm.getRed( nIndex ),icm.getGreen( nIndex ),icm.getBlue( nIndex ),255);
					groupROI.get( i ).setLineColor( newColor );
				}
			}
			roiManager.bt.repaintBVV();
		}
	}
    
	/** show Group Properties dialog**/
	public boolean dialogPropertiesShow(final Roi3DGroup preset, boolean bNameChangable)	
	{
		JPanel dialProperties = new JPanel(new GridBagLayout());
		GridBagConstraints cd = new GridBagConstraints();
		JTextField tfName = new JTextField(10); 
		NumberField nfPointSize = new NumberField(4);
		NumberField nfLineThickness = new NumberField(4);
		NumberField nfOpacity = new NumberField(4);

		String[] sRenderType = { "Outline", "Wire", "Surface" };
		JComboBox<String> renderTypeList = new JComboBox<>(sRenderType);
		
		
		tfName.setText(preset.getName());
		tfName.setEnabled(bNameChangable);
		
		nfPointSize.setText(Float.toString(preset.getPointSize()));
		nfLineThickness.setText(Float.toString(preset.getLineThickness()));
		DecimalFormatSymbols decimalFormatSymbols = DecimalFormatSymbols.getInstance();
		decimalFormatSymbols.setDecimalSeparator('.');
		DecimalFormat df = new DecimalFormat("0.00", decimalFormatSymbols);
		nfOpacity.setText(df.format(preset.getOpacity()));
		nfOpacity.setLimits(0.0, 1.0);
		
		JButton butPointColor = new JButton( new ColorIcon( preset.getPointColor() ) );
		selectColors.setColor(preset.getPointColor(), 0);
		butPointColor.addActionListener( e -> {
			Color newColor = JColorChooser.showDialog(dialog, "Choose point color", preset.getPointColor() );
			if (newColor!=null)
			{
				selectColors.setColor(newColor, 0);
				butPointColor.setIcon(new ColorIcon(newColor));
			}
			
		});
		
		JButton butLineColor  = new JButton( new ColorIcon( preset.getLineColor()) );

		selectColors.setColor(preset.getLineColor(), 1);
		butLineColor.addActionListener( e -> {
				Color newColor = JColorChooser.showDialog(dialog, "Choose line color", preset.getPointColor() );
				if (newColor!=null)
				{	
					selectColors.setColor(newColor, 1);							
					butLineColor.setIcon(new ColorIcon(newColor));
				}
				
		});		

		JButton butSaveAsDefault = new JButton ("Save as new default");
		
		butSaveAsDefault.addActionListener( 
				new ActionListener() 
				{
					@Override
					public void actionPerformed( ActionEvent e )
					{
						Prefs.set("BigTrace.undefPointSize",Float.parseFloat(nfPointSize.getText()));	
						Prefs.set("BigTrace.undefLineThickness",Float.parseFloat(nfLineThickness.getText()));	
						Prefs.set("BigTrace.undefRenderType",renderTypeList.getSelectedIndex());
						Prefs.set("BigTrace.undefPointColor",selectColors.getColor(0).getRGB());
						Prefs.set("BigTrace.undefLineColor",selectColors.getColor(1).getRGB());
					}
				});
		
		
		cd.gridx=0;
		cd.gridy=0;

		dialProperties.add(new JLabel("Name: "),cd);
		cd.gridx++;
		dialProperties.add(tfName,cd);				
		
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Point size: "),cd);
		cd.gridx++;
		dialProperties.add(nfPointSize,cd);		
				
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Point color: "),cd);
		cd.gridx++;
		dialProperties.add(butPointColor,cd);
		
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Line thickness: "),cd);
		cd.gridx++;
		dialProperties.add(nfLineThickness,cd);
		
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Line color: "),cd);
		cd.gridx++;
		dialProperties.add(butLineColor,cd);

		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Opacity: "),cd);
		cd.gridx++;
		dialProperties.add(nfOpacity,cd);
		
		cd.gridx=0;
		cd.gridy++;
		dialProperties.add(new JLabel("Render as: "),cd);
		renderTypeList.setSelectedIndex(preset.getRenderType());
		cd.gridx++;
		dialProperties.add(renderTypeList,cd);
		
		if(!bNameChangable)
		{
			cd.gridx=0;
			cd.gridy++;
			dialProperties.add(butSaveAsDefault,cd);
		}

		
		
		int reply = JOptionPane.showConfirmDialog(null, dialProperties, "Group Properties", 
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		
		
		if (reply == JOptionPane.OK_OPTION) 
		{
			
			//name 
			
			String newName = tfName.getText();
			if ((newName != null) && (newName.length() > 0)) 
			{
				preset.setName(newName);
			}
			//point size 
			preset.setPointSize(Float.parseFloat(nfPointSize.getText()));
			
			//point color
			if(selectColors.getColor(0)!=null)
			{
				preset.setPointColorRGB(selectColors.getColor(0));
				selectColors.setColor(null, 0);
				//newPointColor = null;
			}
			//opacity
			float fNewOpacity= Float.parseFloat(nfOpacity.getText());
			if(fNewOpacity<0.0f)
				{fNewOpacity=0.0f;}
			if(fNewOpacity>1.0f)
				{fNewOpacity=1.0f;}
			preset.setOpacity(fNewOpacity);

			//line

			//line thickness
			float fNewLineThickess = Float.parseFloat(nfLineThickness.getText());
			if(Math.abs(fNewLineThickess-preset.getLineThickness())>0.00001)
			{
				preset.setLineThickness(fNewLineThickess );
			}
			//line color
			if(selectColors.getColor(1)!=null)
			{				
				preset.setLineColorRGB(selectColors.getColor(1));
				selectColors.setColor(null, 1);
			
			}
			//render type
			if(renderTypeList.getSelectedIndex()!=preset.getRenderType())
			{
				preset.setRenderType(renderTypeList.getSelectedIndex());
			}
			roiManager.repaintBVV();
			return true;
			
			
		}
		
		return false;			
	}
	
	/** Save Groups dialog and saving **/
	public void dialogSaveGroups()
	{
		String filename;
		//int nGroupN, nGroup;
		
		filename = roiManager.bt.btData.sFileNameFullImg + "_btgroups";
		SaveDialog sd = new SaveDialog("Save ROIs ", filename, ".csv");
        String path = sd.getDirectory();
        if (path==null)
        	return;
        filename = path+sd.getFileName();


        final File file = new File(filename);

        try (FileWriter writer = new FileWriter(file))
        {
        	saveGroups(writer);

        	writer.write("End of BigTrace Groups\n");
        	writer.close();
        }

        catch (IOException e) {	
        	System.err.print(e.getMessage());
        	//e.printStackTrace();
        }
        return;

	}
	
	public void saveGroups (FileWriter writer)
	{
		int nGroupN, nGroup;
		
		try {
			writer.write("BigTrace_groups,version," + BigTraceData.sVersion + "\n");
			nGroupN=roiManager.groups.size();
			writer.write("GroupsNumber,"+Integer.toString(nGroupN)+"\n");
			for(nGroup=0;nGroup<nGroupN;nGroup++)
			{
				  //Sleep for up to one second.
				try {
					Thread.sleep(1);
				} catch (InterruptedException ignore) {}
				writer.write("BT_Group,"+Integer.toString(nGroup+1)+"\n");
				
				roiManager.groups.get(nGroup).saveGroup(writer);
			}
			writer.write("End of BigTrace Groups\n");
		} catch (IOException e) {	
			System.err.print(e.getMessage());
			//e.printStackTrace();
		}
	}
	
	/** Load Groups dialog **/
	public void dialogLoadGroups()
	{
		String filename;

        int bFirstPartCheck = 0;

		OpenDialog openDial = new OpenDialog("Load BigTrace Groups","", "*.csv");
	
        String path = openDial.getDirectory();
        if (path==null)
        	return;
        
        
        String [] sGroupLoadOptions = new String [] {"Overwrite current groups","Append to list"};
		
        String input = (String) JOptionPane.showInputDialog(optionPane, "Loading ROI Groups",
                "Loaded groups:", JOptionPane.QUESTION_MESSAGE, null, // Use
                                                                                // default
                                                                                // icon
                sGroupLoadOptions, // Array of choices
                sGroupLoadOptions[(int)Prefs.get("BigTrace.LoadGroup", 0)]);
        
        if(input.isEmpty())
        	 return;
        int nLoadMode;
        if(input.equals("Overwrite current groups"))
        {
        	nLoadMode = 0;
        	roiManager.groups = new ArrayList<>();
        	listModel.removeAllElements();
        }
        else
        {
        	nLoadMode = 1;
        }
        
        Prefs.set("BigTrace.LoadGroup", nLoadMode);
	
        filename = path+openDial.getFileName();
        
		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
			
	        bFirstPartCheck = loadGroups(br);
	        roiManager.updateGroupsList();
	        br.close();
		}
		//catching errors in file opening
		catch (FileNotFoundException e) {
			System.err.print(e.getMessage());
		}	        
		catch (IOException e) {
			System.err.print(e.getMessage());
		}
        
		//some error reading the file
        if(bFirstPartCheck<1)
        {
        	 System.err.println("Not a BigTrace ROI Group file format \n Groups are not loaded.");
             //bt.bInputLock = false;
             //bt.roiManager.setLockMode(false);
        }
        if(bFirstPartCheck==1)
        {
        	 System.err.println("Plugin/file version mismatch,\n loading Groups may be corrupted.");
             //bt.bInputLock = false;
             //bt.roiManager.setLockMode(false);
        }
		return;

	}
	
	public int loadGroups(BufferedReader br)
	{
        float pointSize = 0.0f;
        float lineThickness = 0.0f;
        Color pointColor = Color.BLACK;
        Color lineColor = Color.BLACK;
        String sName = "";
        int nRenderType = 0;
        int nLineN = 0;
        
        int nLoadedGroupsN = 0;

        
		String[] line_array;
        int bFirstPartCheck = 0;
    	Roi3DGroup readGroup;
        String line;
        try {
        	line = br.readLine();
			while (line != null) 
				{
					//end of group segment
					if(line.equals("End of BigTrace Groups"))
					{
						break;
					}
					// process the line.
					line_array = line.split(",");
					nLineN++;
					//first line check
					if(line_array.length==3 && nLineN==1)
					{
						if(line_array[0].equals("BigTrace_groups"))
						{
							bFirstPartCheck++;
						}
						if(line_array[2].equals(BigTraceData.sVersion))
						{
							bFirstPartCheck++; 
						}					  
					}
  
					if(line_array[0].equals("BT_Group"))
					{
	
					}
					if(line_array[0].equals("Name"))
					{						  
						sName = line_array[1];
					}
					if(line_array[0].equals("PointSize"))
					{						  
						pointSize = Float.parseFloat(line_array[1]);
					}
					if(line_array[0].equals("LineThickness"))
					{						  
						lineThickness = Float.parseFloat(line_array[1]);
					}
					if(line_array[0].equals("PointColor"))
					{						  
						pointColor = new Color(Integer.parseInt(line_array[1]),
								Integer.parseInt(line_array[2]),
								Integer.parseInt(line_array[3]),
								Integer.parseInt(line_array[4]));
					}
					if(line_array[0].equals("LineColor"))
					{						  
						lineColor = new Color(Integer.parseInt(line_array[1]),
								Integer.parseInt(line_array[2]),
								Integer.parseInt(line_array[3]),
								Integer.parseInt(line_array[4]));
					}
					if(line_array[0].equals("RenderType")&& bFirstPartCheck>0)
					{						  
						nRenderType = Integer.parseInt(line_array[1]);
						//read it all hopefully
						readGroup = new Roi3DGroup(sName, pointSize, pointColor, lineThickness, lineColor,  nRenderType);
						roiManager.groups.add(readGroup);
						nLoadedGroupsN++;
						if(listModel==null)
						{
							listModel = new  DefaultListModel<>(); 
						}
						listModel.addElement(readGroup.getName());
					}
					line = br.readLine();  				  
				}
        }
        //catching errors in file opening
        catch (FileNotFoundException e) {
        	System.err.print(e.getMessage());
        }	        
        catch (IOException e) {
        	System.err.print(e.getMessage());
        }
        
        if(nLoadedGroupsN>0)
        {
        	return bFirstPartCheck;
        }
		return -1;
	}


	@Override
	public void valueChanged(ListSelectionEvent e) 
	{
        if (jlist.getSelectedIndex() == -1) 
        {
        //No selection
        //should not happen 	

        } else if (jlist.getSelectedIndices().length > 1) {
        //Multiple selection: 
        //should not happen 
        } 
        else 
        {
        	//Single selection:
        	//undefined group, cannot edit or delete
        	if(jlist.getSelectedIndex() == 0)
        	{
        		//butEdit.setEnabled(false);
        		butDelete.setEnabled(false);
        	}
        	else
        	{
        		//butEdit.setEnabled(true);
        		butDelete.setEnabled(true);        		
        	}

        }
        updateMergeButton();
    
	}

	@Override
	public void actionPerformed(ActionEvent ae) 
	{
		
		int indList = jlist.getSelectedIndex();
		
		if(indList>-1)
		{
			//EDIT
			if(ae.getSource() == butEdit)
			{
				boolean bNameChange;
				if(indList==0)
				{
					bNameChange= false;
				}
				else
				{
					bNameChange = true;
				}
				if(dialogPropertiesShow(roiManager.groups.get(indList),bNameChange))
				{
					listModel.set(indList,roiManager.groups.get(indList).getName());
					roiManager.updateROIsGroupDisplay(indList);
				}
			}
			//COPY/NEW
			if(ae.getSource() == butCopyNew)
			{
				Roi3DGroup newGroup;
				if(jlist.getSelectedIndex()==0)
				{
					newGroup = new Roi3DGroup(roiManager.groups.get(indList), "new_group"); 
				}
				else
				{
					newGroup = new Roi3DGroup(roiManager.groups.get(indList), roiManager.groups.get(indList).getName()+"_copy"); 
				}
				if(dialogPropertiesShow(newGroup, true))
				{
					addGroup(newGroup);
				}
				
			}			
			//DELETE
			if(ae.getSource() == butDelete)
			{				
				deleteGroup(indList);
			}
			//DELETE
			if(ae.getSource() == butMerge)
			{				
				dialogMerge(indList);
			}
			//Color
			if(ae.getSource() == butColor)
			{				
				dialogColor(indList);
			}
			//SAVE
			if(ae.getSource() == butSave)
			{
			
				dialogSaveGroups();
			}
			//LOAD
			if(ae.getSource() == butLoad)
			{			
				dialogLoadGroups();
			}
			
		}
	}

	/** adds a new Group **/ 
	public void addGroup(Roi3DGroup newGroup_)
	{
		roiManager.groups.add(newGroup_);
		listModel.addElement(newGroup_.getName());
		updateMergeButton();

	}
	
	void updateMergeButton()
	{
		if(roiManager.groups.size()<2)
		{
			butMerge.setEnabled( false );
		}
		else
		{
			butMerge.setEnabled( true );
		}	
	}
	/** deletes Group and asks what to do with ROIs **/ 
	void deleteGroup(int indList)
	{
		boolean bGroupPresent = false;
		
		//there should be at least one group
		if(jlist.getModel().getSize()>1)
		{
			//let's check if ROIs from this group are present
			for (Roi3D roi : roiManager.rois)
			{
				if(roi.getGroupInd()==indList)
				{
					bGroupPresent = true;
					break;
				}
			}	

			// there are ROIs from this group
			if(bGroupPresent)
			{
				//ask user what he wants to do with ROIs
				String [] sGroupDeleteOptions = new String [] {"Mark these ROIs as *undefined*","Delete group's ROIs"};

				String input = (String) JOptionPane.showInputDialog(optionPane, "There are ROIS from this group in ROI Manager:",
						"Delete ROI Group", JOptionPane.QUESTION_MESSAGE, null, // Use
						// default
						// icon
						sGroupDeleteOptions, // Array of choices
						sGroupDeleteOptions[(int)Prefs.get("BigTrace.DeleteGroup", 0)]);

				if(input == null)
					return;
				if(input.equals("Mark these ROIs as *undefined*"))
				{
					//roiManager.markROIsUndefined(indList);
					roiManager.moveROIsGroups( indList, 0 );
					Prefs.set("BigTrace.DeleteGroup", 0);
				}
				else
				{
					Prefs.set("BigTrace.DeleteGroup", 1);
					roiManager.deleteROIsBelongingToGroup(indList);
					
				}
			}
			deleteGroupAndCorrectIndex(indList);

		}
		updateMergeButton();
	}
	
	void deleteGroupAndCorrectIndex(int indList)
	{
		//correct indexing
		for (Roi3D roi : roiManager.rois)
		{
			if(roi.getGroupInd()>indList)
			{
				roi.setGroupInd(roi.getGroupInd()-1);
			}
		}	
		
		//delete group itself
		roiManager.groups.remove(indList);
		listModel.removeElementAt(indList);


		//select previous Group
		if(indList==0)
		{
			jlist.setSelectedIndex(0);
		}
		else
		{
			jlist.setSelectedIndex(indList-1);
		}
	}
	
}
