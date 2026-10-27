package bigtrace.gui.panels;

import java.awt.Color;

import javax.swing.border.TitledBorder;

public class PanelTitle extends TitledBorder{


	public PanelTitle(String paramString) {
		super(paramString);
		this.setTitleColor(Color.DARK_GRAY);
		this.setTitleJustification(TitledBorder.CENTER);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

}
