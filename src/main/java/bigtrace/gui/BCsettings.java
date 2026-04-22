package bigtrace.gui;

import bvvpg.source.converters.RealARGBColorGammaConverterSetup;
import bvvpg.vistools.BvvStackSource;

/** class to store and apply brightness and contrast settings **/
public class BCsettings {
	
	double [] dBrightnessRange;
	double [] dAlphaRange;
	double dGammaDisplay;
	double dGammaAlpha;
	public boolean bInit;
	
	public BCsettings()
	{
		dBrightnessRange = new double [2];
		dAlphaRange = new double [2];
		
		bInit = false;
	}
	
	/** stores currect brightness and alpha settings from provided source **/
	public void storeBC(final BvvStackSource<?> source)
	{
		bInit  = true;
		
		
		RealARGBColorGammaConverterSetup converterS = (RealARGBColorGammaConverterSetup)source.getConverterSetups().get(0);
		dBrightnessRange[0] = converterS.getDisplayRangeMin();
		dBrightnessRange[1] = converterS.getDisplayRangeMax();
		dAlphaRange[0] = converterS.getAlphaRangeMin();
		dAlphaRange[1] = converterS.getAlphaRangeMax();
		dGammaDisplay = converterS.getDisplayGamma();
		dGammaAlpha = converterS.getAlphaGamma();

	}
	public void setBC(final BvvStackSource<?> source)
	{
		if(bInit)
		{
			source.setDisplayRange(0,dBrightnessRange[1]);
			source.setDisplayRange(dBrightnessRange[0],dBrightnessRange[1]);
			source.setDisplayGamma(dGammaDisplay);
			source.setAlphaRange(0,dAlphaRange[1]);
			source.setAlphaRange(dAlphaRange[0],dAlphaRange[1]);			
			source.setAlphaGamma(dGammaAlpha);
		}
	}
}
