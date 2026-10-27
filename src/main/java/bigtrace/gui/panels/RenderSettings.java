package bigtrace.gui.panels;


import ij.Prefs;

public class RenderSettings {
	
	
	public static final double defRenderWidth=600.;
	public static final double defRenderHeight=600.;
	public static final double defDitherWidth=3.;
	public static final double defNumDitherSamples=3;
	public static final double defCacheBlockSize=32.;
	public static final double defMaxCacheSizeInMB=300.;
	
	/** screen render width for BVV **/
	public int renderWidth = (int) Prefs.get("BigTrace.renderWidth",defRenderWidth);
	
	/** screen render height for BVV **/
	public int renderHeight = (int) Prefs.get("BigTrace.renderHeight",defRenderHeight);
	
	/** dither width BVV **/
	public int ditherWidth = (int) Prefs.get("BigTrace.ditherWidth",defDitherWidth);
	
	public int numDitherSamples = (int) Prefs.get("BigTrace.numDitherSamples",defNumDitherSamples);

	public int cacheBlockSize = (int) Prefs.get("BigTrace.cacheBlockSize",32);

	public int maxCacheSizeInMB = (int) Prefs.get("BigTrace.maxCacheSizeInMB",300);
	
	public RenderSettings()
	{
		
	}
}
