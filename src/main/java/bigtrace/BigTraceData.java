package bigtrace;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import bigtrace.gui.BCsettings;
import bigtrace.gui.RenderSettings;
import bigtrace.rois.Roi3D;
import ij.Prefs;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.LanczosInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import mpicbg.spim.data.generic.sequence.BasicImgLoader;

/** class that stores settings and main data from BigTrace **/
public class BigTraceData < T extends RealType< T > & NativeType< T > > 
{

	/** current plugin version **/
	public static String sVersion = "0.8.0";
	
	/** plugin instance **/
	BigTrace<T> bt;
	
	//////////////input dataset characteristics 
	
	/** path and full input filename **/
	public String sFileNameFullImg;
	
	/** if the source is BDV HDF file, it is true, otherwise we take it from ImageJ **/
	public boolean bSpimSource = false;
	
	/** bit depth of the sources **/
	public int nBitDepth = 8;
	
	/** total number of channels in the dataset**/
	public int nTotalChannels = 0;
	
	/** total number of TimePoints**/
	public int nNumTimepoints = 0;
	
	/** use lazy loading for TIFF files **/
	public boolean bUseLazyLoadForTiff = Prefs.get( "BigTrace.bUseLazyLoadForTiff", true );
	
	///////////////////////DATASET PROCESSING/MEASURE SETTING

	/** voxel size determined from input file  **/
	public double [] inputCal = new double [3];
	
	/** global voxel size (for now one for all)  **/
	public double [] globCal = new double [3];
	
	/** minimum voxel size  **/
	public double dMinVoxelSize = 0.0;
	
	/** units of voxel **/
	public String sVoxelUnit = "pixel";
	
	/** units of time **/
	public String sTimeUnit = "frame";
	
	/** frame interval  **/
	public double dFrameInterval = 1.0;
	
	/** the number of current channel used for analysis/tracing **/
	public int nChAnalysis = 0;
	
	/** currently selected timePoint**/
	public int nCurrTimepoint = 0;	
	
	/** interpolation factory for intensity/volume quantification **/
	public InterpolatorFactory<T, RandomAccessible< T >> nInterpolatorFactory;
	
	/** Intensity interpolation types **/
	public static final int INT_NearestNeighbor = 0, INT_NLinear = 1, INT_Lanczos = 2; 
	
	/** current intensity interpolation type **/	
	public static int intensityInterpolation = (int)Prefs.get("BigTrace.IntInterpolation",INT_NLinear);
	
	///////////////////////////// VOLUME RENDERING
	
	/** dimensions of the volume/image (without clipping) **/
	public long [][] nDimIni = new long [2][3];
	
	/** current dimensions of the volume/image (after clipping) **/
	public long [][] nDimCurr = new long [2][3];

	/** whether or not display color coded origin of coordinates **/
	public boolean bShowOrigin = true;
	
	public Color canvasBGColor = new Color((int)Prefs.get( "BigTrace.canvasBGColor", Color.BLACK.getRGB() ));
	
	/** whether or not display a box around volume/image **/
	public boolean bVolumeBox = true;
	
	/** whether or not display a box for clipped view **/
	public boolean bClipBox = false;
	
	/** also clip ROIs**/
	public static int nClipROI = 0;
	
	/** camera position for BVV **/
	public double dCam = Prefs.get("BigTrace.dCam", 2000.0);
	
	/** near clip plane position for BVV **/
	public double dClipNear = Prefs.get("BigTrace.dClipNear",1000.0);
	
	/** far clip plane position for BVV  **/
	public double dClipFar = Prefs.get("BigTrace.dClipFar",1000.0);
	
	public static RenderSettings renderParams = new RenderSettings();	
	
	/** object to store brightness/alpha range of the channel during tracing**/
	public BCsettings bcTraceChannel = new BCsettings();
	
	/** object to store brightness/alpha range of the tracebox **/
	public BCsettings bcTraceBox = new BCsettings();	
	
	public static final int DATA_RENDER_MAX_INT = 0, DATA_RENDER_VOLUMETRIC = 1; 
	/** dataset rendering method 
	 * 0 maximum intensity, 
	 * 1 volumetric **/
	public int nRenderMethod = (int)Prefs.get("BigTrace.nRenderMethod", DATA_RENDER_MAX_INT);
	
	/////////////////////////////////GLOBAL ROI APPEARANCE SETTINGS
	
	/** whether or not deselect ROI on time point change **/
	public boolean bDeselectROITime = true;
	
	/** ROI shape interpolation types **/
	public static final int SHAPE_Voxel = 0, SHAPE_Smooth = 1, SHAPE_Spline = 2; 
	
	/** current ROI shape interpolation **/
	public static int shapeInterpolation = (int) Prefs.get("BigTrace.ShapeInterpolation", SHAPE_Spline);
	
	/** algorithm to build rotation minimizing frame **/
	public static int rotationMinFrame = (int) Prefs.get("BigTrace.RotationMinFrame",0);
	
	/** size of moving average window to smooth traces (in points) **/
	public static int nSmoothWindow = (int) Prefs.get("BigTrace.nSmoothWindow", 5);

	/** number of segments in the cylinder cross-section (for polyline/trace ROIs),
	 *  3 = prism, 4 = cuboid, etc.
	 *  The more the number, more smooth is surface **/
	public static int sectorN = (int) Prefs.get("BigTrace.nSectorN", 32);	
	
	/** whether to render wire countour using antialiased lines **/
	public static boolean wireAntiAliasing = Prefs.get( "BigTrace.wireAntiAliasing", true );
	
	/** approximate distance between contours along the pipe visualizing a curve
	 *  in minimum voxel size units **/
	public static int wireCountourStep = (int) Prefs.get("BigTrace.wireCountourStep",50);
	
	/** step of gridline displaying cross-section ROI in wired mode (in voxels)**/
	public static int crossSectionGridStep = (int) Prefs.get("BigTrace.crossSectionGridStep", 50);
	
	/** available surface render types **/
	public static final int SURFACE_PLAIN = 0, SURFACE_SHADE = 1, SURFACE_SHINY = 2, SURFACE_SILHOUETTE = 3; 
	
	/** current surface render type **/
	public static int surfaceRender = (int) Prefs.get("BigTrace.surfaceRender", SURFACE_SHADE);

	/** available silhouette render types **/
	public static final int silhouette_TRANSPARENT = 0, silhouette_CULLED = 1; 

	/** current silhouette render type **/
	public static int silhouetteRender = (int) Prefs.get("BigTrace.silhouetteRender", silhouette_CULLED);
	
	/** current silhouette render type **/
	public static double silhouetteDecay = Prefs.get("BigTrace.silhouetteDecay", 2.0);
	
	//////rendering ROI over time
	
	/** time rendering option **/
	public static int timeRender = (int) Prefs.get("BigTrace.timeRender", 0);
	
	/** time rendering fade in frames **/
	public static int timeFade = (int) Prefs.get("BigTrace.timeFade", 0);
		
	/////////////////////////////////USER INTERFACE "CLICKING"
	
	public boolean bStartFullScreen = Prefs.get("BigTrace.bStartFullScreen", true);
	
	/** half size of rectangle around click point (in screen pixels)
	 * used to find maximum intensity voxel **/
	public int nHalfClickSizeWindow = (int)Prefs.get("BigTrace.nHalfClickSizeWindow", 5.0);
		
	/** whether to clip volume when zooming **/
	public boolean bZoomClip = Prefs.get("BigTrace.bZoomClip", false);
	
	/** characteristic size of zoom in area (in pixels of original volume) **/
	public int nZoomBoxSize = 150;
	
	/** fraction of screen occupied by zoom box **/
	public double dZoomBoxScreenFraction = 0.6;
	
	/** animation speed, i.e. duration of transform **/
	public long nAnimationDuration =  (int)Prefs.get("BigTrace.nAnimationDuration", 400);
	
	/** whether to clip volume when double clicking on ROI **/
	public static boolean bROIDoubleClickClip = Prefs.get("BigTrace.bROIDoubleClickClip", false);
	
	/** whether to clip volume when double clicking on ROI **/
	public static int nROIDoubleClickClipExpand = (int)Prefs.get("BigTrace.nROIDoubleClickClipExpand", 0.0);

	
///////////////////////////// TRACING SETTINGS GENERAL
	
	/** characteristic size (SD) of lines (for each dimension)**/
	public double [] sigmaTrace = new double [ 3 ];
	
	/** whether to limit tracing to clipped area**/
	public boolean bTraceOnlyClipped = false;
	
	/** whether to automatically pick traced ROI thickness(diameter) **/	
	public boolean bEstimateROIThicknessFromParams = Prefs.get("BigTrace.bEstimateROIThicknessFromParams", true);

	/** coefficient value **/	
	public double dTraceROIThicknessCoeff = Prefs.get("BigTrace.dTraceROIThicknessCoeff", 6.0);

	/** estimation mode value 0 - MAX, 1 - AVG, 2 - MIN **/	
	public int nTraceROIThicknessMode = (int)Prefs.get("BigTrace.nTraceROIThicknessMode", 0.0);

	
	///////////////////////////// TRACING SETTINGS SEMI AUTO
	
	/** whether (1) or not (0) remove visibility of volume data during tracing **/
	public int nTraceBoxView = 1;
	
	/** half size of tracing box (for now in all dimensions) **/
	public int nTraceBoxSize;
	
	/** fraction of screen occupied by trace box **/
	public float fTraceBoxScreenFraction;	
	
	/** After advancing tracebox, this parameter defines 
	 * how much tracebox is going to follow the last direction of trace (with respect to the last added point):
	 * in the range [0..1], 0 = last point in the center of new tracebox, 1 = previous point is at the edge of the new tracebox**/
	public float fTraceBoxAdvanceFraction;
	
	/** weight of orientation vs saliency in the tracing**/
	public double gammaTrace;
	
	/** current number of vertices in the tracebox **/
	public int nPointsInTraceBox = 0;
	
	/** storage of the dataset orientation before entering TraceBox mode **/
	public AffineTransform3D transformBeforeTracing = new AffineTransform3D(); 

	/** weights of curve probability (saliency) for the trace box**/
	public IntervalView< UnsignedByteType > trace_weights = null;
	
	/** directions of curve at each voxel for the trace box**/
	public IntervalView< FloatType > trace_vectors = null;
	
	/**special points Dijkstra search for the trace box**/
	public ArrayList<long []> jump_points = null;
	
	
	///////////////////////////// TRACING SETTINGS ONE CLICK
	
	/** current number of vertices in the tracebox **/
	public int nVertexPlacementPointN;
	
	/** directionality constrain for one click **/
	public double dDirectionalityOneClick;

	/** Use minimum intensity threshold when tracing **/
	public boolean bOCIntensityStop;
	
	/** value of the intensity threshold to stop One click tracing **/
	public double dOCIntensityThreshold;	
	
	/** use collision mask with existing ROIs during one-click **/
	public boolean bOneClickUseMask = Prefs.get( "BigTrace.bOneClickUseMask", false );

	/** use collision mask with existing ROIs during tracking**/
	public boolean bTrackingUseMask = Prefs.get( "BigTrace.bTrackingUseMask", false );

	
	//IO default/last folder
	
	public String lastDir = Prefs.get( "BigTrace.lastDir", "" );
	
	
	///////////////// DEFAULT SETTINGS FOR UNDEFINED GROUP
	
	public float undefPointSize;// = 4.0f;
	public float undefLineThickness;// = 7.0f;
	public int undefRenderType;// = Roi3D.SURFACE;
	public int undefPointColor ;// = Color.GREEN.getRGB();
	public int undefLineColor;//  = Color.BLUE.getRGB();
	
	///////////////// DEFAULT SETTINGS FOR ACTIVE ROI COLORS
	
	public BigTraceData(final BigTrace<T> bt_)
	{
		
		bt = bt_;
		//default scale
		globCal[0]= 1.0;
		globCal[1]= 1.0;
		globCal[2]= 1.0;
		
		//view 
		nZoomBoxSize = (int) Prefs.get("BigTrace.nZoomBoxSize", 150);
		dZoomBoxScreenFraction = Prefs.get("BigTrace.dZoomBoxScreenFraction", 1.0);

		//tracing
		sigmaTrace = new double [3];
		sigmaTrace[0] = Prefs.get("BigTrace.sigmaTraceX", 3.0);
		sigmaTrace[1] = Prefs.get("BigTrace.sigmaTraceY", 3.0);
		sigmaTrace[2] = Prefs.get("BigTrace.sigmaTraceZ", 3.0);
		bTraceOnlyClipped = Prefs.get("BigTrace.bTraceOnlyClipped", false);
		
		nTraceBoxSize = (int) Prefs.get("BigTrace.nTraceBoxSize", 50);				
		fTraceBoxAdvanceFraction = (float) Prefs.get("BigTrace.fTraceBoxAdvanceFraction", 0.9);
		fTraceBoxScreenFraction = (float)Prefs.get("BigTrace.fTraceBoxScreenFraction", 0.5);
		gammaTrace =  Prefs.get("BigTrace.gammaTrace", 0.0);
		
		//one-click
		nVertexPlacementPointN = (int) Prefs.get("BigTrace.nVertexPlacementPointN", 10);
		dDirectionalityOneClick = Prefs.get("BigTrace.dDirectionalityOneClick", 0.6);
		bOCIntensityStop = Prefs.get("BigTrace.bOCIntensityStop", false);
		dOCIntensityThreshold = Prefs.get("BigTrace.dOCIntensityThreshold", 100.);
		
		//undefined group properties
		undefPointSize = ( float ) Prefs.get( "BigTrace.undefPointSize", 4.0 );
		undefLineThickness = ( float ) Prefs.get( "BigTrace.undefLineThickness", 7.0 );
		undefPointColor = ( int ) Prefs.get( "BigTrace.undefPointColor", Color.GREEN.getRGB() );
		undefLineColor = ( int ) Prefs.get( "BigTrace.undefLineColor", Color.BLUE.getRGB() );
		undefRenderType = ( int ) Prefs.get( "BigTrace.undefRenderType", Roi3D.SURFACE );
		
		//init interpolation factory
		setInterpolationFactory();
	}
	
	/** returns data sources for specific channel and time point,
	 * limits output to the current clipped area **/
	public IntervalView< T > getDataSourceClipped(final int nChannel, final int nTimePoint)
	{		
		return Views.interval(getDataSourceFull(nChannel,nTimePoint),nDimCurr[0], nDimCurr[1]);

	}
	
	/** returns data sources for the current channel and time point,
	 * limits output to the current clipped area **/
	public IntervalView< T > getDataCurrentSourceClipped()
	{		
		return Views.interval(getDataSourceFull(nChAnalysis,nCurrTimepoint),nDimCurr[0], nDimCurr[1]);

	}
	
	/** returns data sources for the current channel and time point,
	 * does not limit output to the current clipped area **/
	public RandomAccessibleInterval<T> getDataCurrentSourceFull()
	{		
		return getDataSourceFull(nChAnalysis, nCurrTimepoint);

	}
	
	/** returns data sources for specific channel and time point,
	 * does not limit output to the current clipped area **/
	@SuppressWarnings("unchecked")
	public RandomAccessibleInterval<T> getDataSourceFull(final int nChannel, final int nTimePoint)
	{
		if(bSpimSource)
		{
			final BasicImgLoader imgLoader = bt.spimData.getSequenceDescription().getImgLoader();
			RandomAccessibleInterval<T> full_int = (RandomAccessibleInterval<T>) imgLoader.getSetupImgLoader(nChannel).getImage(nTimePoint);
			if(bt.bApplyLLSTransform)
			{
				RealRandomAccessible<T> rra = Views.interpolate(Views.extendZero(full_int), nInterpolatorFactory);
				RealRandomAccessible<T> rra_tr = RealViews.affine(rra, bt.afDataTransform);
				return Views.interval(Views.raster(rra_tr), new FinalInterval(nDimIni[0],nDimIni[1]));	
			}
			return (RandomAccessibleInterval<T>) imgLoader.getSetupImgLoader(nChannel).getImage(nTimePoint);
		}
		return Views.hyperSlice(Views.hyperSlice(bt.all_ch_RAI,4,nChannel),3,nTimePoint);
	}
	/** output is XYZTC **/
	public RandomAccessibleInterval<T> getAllDataRAI()
	{
		//output should be XYZTC
		
		if(bSpimSource)
		{
			
			List<RandomAccessibleInterval<T>> raiXYZTC = new ArrayList<> ();

			List<RandomAccessibleInterval<T>> raiXYZT;// = new ArrayList<RandomAccessibleInterval<T>> ();
			
			for (int nCh=0; nCh < nTotalChannels; nCh++)
			{
				raiXYZT = new ArrayList<> ();
				for(int nTimePoint = 0; nTimePoint < nNumTimepoints; nTimePoint++)
				{
					raiXYZT.add(getDataSourceFull(nCh,nTimePoint));
				}
				raiXYZTC.add(Views.stack(raiXYZT));
			}
		
			return Views.stack(raiXYZTC);
		}
		return bt.all_ch_RAI;
	}
	
	public void setInterpolationFactory()
	{
		switch (intensityInterpolation)
		{
			case INT_NearestNeighbor:
				nInterpolatorFactory = new NearestNeighborInterpolatorFactory<>();
				break;
			case INT_NLinear:
				nInterpolatorFactory = new ClampingNLinearInterpolatorFactory<>();
				break;
			case INT_Lanczos:
				nInterpolatorFactory = new LanczosInterpolatorFactory<>();
				break;
			default:
				nInterpolatorFactory = new ClampingNLinearInterpolatorFactory<>();
				break;
				
		}
	}
	
	public static Color getInvertedColor(Color color_in)
	{
		
		return  new Color(255-color_in.getRed(),255-color_in.getGreen(),255-color_in.getBlue(),color_in.getAlpha());
	}
	
	public float estimateROIThicknessFromTracing()
	{
		float out = 0.0f;
		switch(nTraceROIThicknessMode)
		{
		case 0:
			out = (-1)*Float.MAX_VALUE;
			for(int d = 0; d < 3; d++)
			{
				out = ( float ) Math.max(this.sigmaTrace[d] * globCal[d] / dMinVoxelSize, out);
			}		
			break;
		case 1:
			for(int d = 0; d < 3; d++)
			{
				out +=( float ) (this.sigmaTrace[d] * globCal[d] / dMinVoxelSize);
			}	
			out /= 3.0;
			break;
		case 2:
			out = Float.MAX_VALUE;
			for(int d = 0; d < 3; d++)
			{
				out = ( float ) Math.min(this.sigmaTrace[d] * globCal[d] / dMinVoxelSize, out);
			}		
			break;
		}
		return ( float ) ( out * dTraceROIThicknessCoeff );
	}
}
