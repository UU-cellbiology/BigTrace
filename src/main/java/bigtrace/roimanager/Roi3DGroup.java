package bigtrace.roimanager;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import bigtrace.rois.Roi3D;

public class Roi3DGroup 
{

	public Color pointColor = Color.GREEN;
	public Color lineColor = Color.BLUE;
	public String sName;

	public float lineThickness = 6.0f;
	public float pointSize = 3.0f;
	public int renderType = Roi3D.SURFACE;
	public boolean bVisible = true;

	public Roi3DGroup(String sGroupName_,  float pointSize_, Color pointColor_, float lineThickness_, Color lineColor_,  int renderType_)
	{
		sName = new String(sGroupName_);
		lineThickness = lineThickness_;
		pointSize = pointSize_;
		renderType = renderType_;
		setPointColor(pointColor_);
		setLineColor(lineColor_);
		bVisible = true;
	}
	
	public Roi3DGroup(Roi3DGroup preset_in, String name)
	{
		sName = new String(name);
		lineThickness = preset_in.getLineThickness();
		pointSize = preset_in.getPointSize();
		renderType = preset_in.getRenderType();
		setPointColor(preset_in.getPointColor());
		setLineColor(preset_in.getLineColor());		 
	}
	
	public Roi3DGroup(Roi3D preset_in, String name)
	{
		sName = new String(name);
		lineThickness = preset_in.getLineThickness();
		pointSize = preset_in.getPointSize();
		renderType = preset_in.getRenderType();
		setPointColor(preset_in.getPointColor());
		setLineColor(preset_in.getLineColor());		 
	}

	 
	public void setPointColor(Color pointColor_) {
		
		pointColor = new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor_.getAlpha());		
	}
	
	public void setLineColor(Color lineColor_) {
		
		lineColor = new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor_.getAlpha());
	
	}

	public void setPointColorRGB(Color pointColor_){
		setPointColor(new Color(pointColor_.getRed(),pointColor_.getGreen(),pointColor_.getBlue(),pointColor.getAlpha()));
	}
	

	public void setLineColorRGB(Color lineColor_){
		setLineColor(new Color(lineColor_.getRed(),lineColor_.getGreen(),lineColor_.getBlue(),lineColor.getAlpha()));
	}
	
	public void setPointSize(float point_size) {
		pointSize=point_size;		
	}
	
	public void setLineThickness(float line_thickness) {
		lineThickness=line_thickness;
	}
	
	public void setOpacity(float fOpacity)
	{
		setPointColor(new Color(pointColor.getRed(),pointColor.getGreen(),pointColor.getBlue(),(int)(fOpacity*255)));
		setLineColor(new Color(lineColor.getRed(),lineColor.getGreen(),lineColor.getBlue(),(int)(fOpacity*255)));
	}
	
	public void setRenderType(int nRenderType){		
		renderType=nRenderType;
	}
	public String getName() {
		return new String(sName);
	}

	
	public void setName(String name) {
		this.sName = new String(name);		
	}
	
	public float getLineThickness() {

		return lineThickness;
	}

	public float getPointSize() {

		return pointSize;
	}
	
	public float getOpacity()
	{
		return (pointColor.getAlpha()/255.0f);
	}
	
	public Color getPointColor()
	{
		return new Color(pointColor.getRed(),pointColor.getGreen(),pointColor.getBlue(),pointColor.getAlpha());
	}
	
	public Color getLineColor()
	{
		return new Color(lineColor.getRed(),lineColor.getGreen(),lineColor.getBlue(),lineColor.getAlpha());
	}
	
	public int getRenderType()
	{
		return renderType;
	}
	
	public void saveGroup(final FileWriter writer)
	{
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		DecimalFormat df3 = new DecimalFormat ("#.###", symbols);
		try {
			
			writer.write("Name," + this.getName()+"\n");
			writer.write("PointSize," + df3.format(this.getPointSize())+"\n");
			writer.write("PointColor,"+ Integer.toString(pointColor.getRed()) +","
									  +	Integer.toString(pointColor.getGreen()) +","
									  +	Integer.toString(pointColor.getBlue()) +","
									  +	Integer.toString(pointColor.getAlpha()) +"\n");
			writer.write("LineThickness," + df3.format(this.getLineThickness())+"\n");
			writer.write("LineColor,"+ Integer.toString(lineColor.getRed()) +","
									  +	Integer.toString(lineColor.getGreen()) +","
									  +	Integer.toString(lineColor.getBlue()) +","
									  +	Integer.toString(lineColor.getAlpha()) +"\n");
			writer.write("RenderType,"+ Integer.toString(this.getRenderType())+"\n");

			

		}
		catch (IOException e) {	
			System.err.print(e.getMessage());
			
		}
	
	}
}
