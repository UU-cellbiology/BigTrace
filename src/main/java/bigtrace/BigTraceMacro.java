package bigtrace;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import bigtrace.gui.TaskBT;
import bigtrace.io.ROIsIO;
import bigtrace.measure.ROIsMeasureBG;
import bigtrace.measure.RoiMeasure3D;
import bigtrace.rois.AbstractCurve3D;
import bigtrace.rois.Roi3D;
import bigtrace.volume.StraightenCurve;
import bvvpg.vistools.BvvStackSource;
import ij.IJ;
import ij.ImageJ;
import ij.Prefs;
import ij.macro.ExtensionDescriptor;
import ij.macro.MacroExtension;


public class BigTraceMacro < T extends RealType< T > & NativeType< T > > 
{
	/** plugin instance **/
	BigTrace<T> bt;
	
	/** macro extensions **/
	public ExtensionDescriptor[] extensions;
	
	/** whether we run in the macro mode **/
	public volatile boolean bMacroMode = false;
	
	private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
	
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	
	public BigTraceMacro(final BigTrace<T> bt_)
	{
		bt = bt_;
		
		extensions = new ExtensionDescriptor[17];
		extensions[0] = ExtensionDescriptor.newDescriptor("btLoadROIs", bt, MacroExtension.ARG_STRING, MacroExtension.ARG_STRING);
		extensions[1] = ExtensionDescriptor.newDescriptor("btSaveROIs", bt, MacroExtension.ARG_STRING, MacroExtension.ARG_STRING + MacroExtension.ARG_OPTIONAL);
		extensions[2] = ExtensionDescriptor.newDescriptor("btStraighten", bt, MacroExtension.ARG_NUMBER, MacroExtension.ARG_STRING, MacroExtension.ARG_STRING + MacroExtension.ARG_OPTIONAL);
		extensions[3] = ExtensionDescriptor.newDescriptor("btShapeInterpolation", bt, MacroExtension.ARG_STRING, MacroExtension.ARG_NUMBER);
		extensions[4] = ExtensionDescriptor.newDescriptor("btIntensityInterpolation", bt, MacroExtension.ARG_STRING);
		extensions[5] = ExtensionDescriptor.newDescriptor("btSetActiveChannel", bt,  MacroExtension.ARG_NUMBER);
		extensions[6] = ExtensionDescriptor.newDescriptor("btSetTracingThickness", bt,  MacroExtension.ARG_NUMBER, //sigmax  
																						MacroExtension.ARG_NUMBER, //sigmay
																						MacroExtension.ARG_NUMBER); //sigmaz
		extensions[7] = ExtensionDescriptor.newDescriptor("btSetTracingROI", bt,  MacroExtension.ARG_STRING, //boolean  
																					MacroExtension.ARG_NUMBER, //coeff
																					MacroExtension.ARG_STRING); //method
		extensions[8] = ExtensionDescriptor.newDescriptor("btSetOneClickParameters", bt,  MacroExtension.ARG_NUMBER,  
				  																		  MacroExtension.ARG_NUMBER, 
				  																		  MacroExtension.ARG_STRING + MacroExtension.ARG_OPTIONAL, 
				  																		  MacroExtension.ARG_NUMBER + MacroExtension.ARG_OPTIONAL);		
		extensions[9] = ExtensionDescriptor.newDescriptor("btRunFullAutoTrace", bt, MacroExtension.ARG_NUMBER,
																					MacroExtension.ARG_NUMBER,
																					MacroExtension.ARG_NUMBER + MacroExtension.ARG_OPTIONAL,
																					MacroExtension.ARG_NUMBER + MacroExtension.ARG_OPTIONAL);
		
		extensions[10] = ExtensionDescriptor.newDescriptor("btSetMeasurements", bt, MacroExtension.ARG_STRING);
		extensions[11] = ExtensionDescriptor.newDescriptor("btMeasureAndSave", bt, MacroExtension.ARG_STRING);
		extensions[12] = ExtensionDescriptor.newDescriptor("btSetDisplayRangeGamma", bt,  MacroExtension.ARG_NUMBER,
																						  MacroExtension.ARG_NUMBER,
																						  MacroExtension.ARG_NUMBER + MacroExtension.ARG_OPTIONAL,
																						  MacroExtension.ARG_NUMBER + MacroExtension.ARG_OPTIONAL);
		extensions[13] = ExtensionDescriptor.newDescriptor("btSetAlphaRangeGamma", bt, MacroExtension.ARG_NUMBER,
																					   MacroExtension.ARG_NUMBER,
																					   MacroExtension.ARG_NUMBER + MacroExtension.ARG_OPTIONAL,
																					   MacroExtension.ARG_NUMBER + MacroExtension.ARG_OPTIONAL);
		extensions[14] = ExtensionDescriptor.newDescriptor("btOpenNext", bt, MacroExtension.ARG_STRING);
		extensions[15] = ExtensionDescriptor.newDescriptor("btTest", bt);
		extensions[16] = ExtensionDescriptor.newDescriptor("btClose", bt);
		
	}
	
	public synchronized void enqueue(Runnable task) 
	{

	    tail = tail
	            .exceptionally(ex -> {
	                ex.printStackTrace();
	                return null;
	            })
	            .thenRunAsync(() -> {
	                try {
	                    task.run();
	                } catch (Exception e) {
	                    e.printStackTrace();
	                    throw e;
	                }
	            }, executor);
		tail.join();
		return;
	     //   return tail;
	}
	
	public String handleExtension(String name, Object[] args) 
	{

		if (name.equals("btLoadROIs")) 
		{
			enqueue (()-> 
			{
				macroLoadROIs( (String)args[0],(String)args[1]);
			});
		}
		if (name.equals("btSaveROIs")) 
		{
			enqueue (()-> 
			{
				macroSaveROIs( (String)args[0],(String)args[1]);
			});
		}
		if (name.equals("btStraighten")) 
		{
			enqueue (()-> 
			{
				if(args[2] == null)
				{
					//backwards compartibility
					macroStraighten((int)Math.round(((Double)args[0]).doubleValue()), (String)args[1], "Square");					
				}
				else
				{
					macroStraighten((int)Math.round(((Double)args[0]).doubleValue()), (String)args[1], (String)args[2]);
				}
			});
		}
		if (name.equals("btShapeInterpolation")) 
		{
			enqueue (()-> 
			{
				macroShapeInterpolation( (String)args[0],(int)Math.round(((Double)args[1]).doubleValue()));
			});
		}
		if (name.equals("btIntensityInterpolation")) 
		{
			enqueue (()-> 
			{
				macroIntensityInterpolation( (String)args[0]);
			});
		}
		if (name.equals("btSetActiveChannel")) 
		{
			enqueue (()-> 
			{
				macroSetActiveChannel( (int) Math.abs(Math.round(((Double)args[0]).doubleValue())));
			});
		}
		if (name.equals("btSetTracingThickness")) 
		{
			enqueue (()-> 
			{
				final double [] sigmas = new double[3];
				for(int d = 0; d < 3; d++)
				{
					sigmas[d] = Math.abs(((Double)args[d]).doubleValue());
				}

				macroSetTracingThickness(sigmas);
			});
		}
		if (name.equals("btSetTracingROI")) 
		{
			enqueue (()-> 
			{
				macroSetTracingROI((String)args[0], Math.abs(((Double)args[1]).doubleValue()), (String)args[2]);
			});
		}
		
		if (name.equals("btSetOneClickParameters")) 
		{
			if(args[2] == null || args[3] == null)
			{
				enqueue (()-> 
				{
					macroSetOneClickParameters((int)Math.round(((Double)args[0]).doubleValue()), ((Double)args[1]).doubleValue(), "false", 0.0);
				});
				return null;
			}
			enqueue (()-> 
			{
				macroSetOneClickParameters((int)Math.round(((Double)args[0]).doubleValue()), ((Double)args[1]).doubleValue(), (String)args[2],((Double)args[3]).doubleValue());					
			});
		}

		if (name.equals("btRunFullAutoTrace")) 
		{

			enqueue (()-> 
			{
				int nFirstTPi = 0;
				int nLastTPi = bt.btData.nNumTimepoints - 1;
				if(args[2] != null && args[3] != null )
				{
					nFirstTPi = (int)Math.round(((Double)args[2]).doubleValue());
					nLastTPi = (int)Math.round(((Double)args[3]).doubleValue());
				}
				final int nFirstTP = nFirstTPi;
				final int nLastTP = nLastTPi;

				macroRunFullAutoTrace(((Double)args[0]).doubleValue(), (int)Math.round(((Double)args[1]).doubleValue()), nFirstTP, nLastTP);
			});
		}
		
		if (name.equals("btSetMeasurements")) 
		{
			enqueue (()-> 
			{
				macroSetMeasurements( (String)args[0]);
			});
		}
		
		if (name.equals("btMeasureAndSave")) 
		{
			enqueue (()-> 
			{
				macroMeasureAndSave( (String)args[0]);
			});
		}
		
		if (name.equals("btSetDisplayRangeGamma")) 
		{
			final double[] dGamma =  new double [] {1.0};
			if(args[2] != null)
			{
				dGamma[0] = ((Double)args[2]).doubleValue();
			}
			final int [] nCh = new int [] { -1 }; //all channels
			if(args[3] != null)
			{
				nCh[0] = (int)Math.round(((Double)args[3]).doubleValue());
			}			
			enqueue (()-> 
			{
				macroSetDisplayRangeGamma(((Double)args[0]).doubleValue(), ((Double)args[1]).doubleValue(), dGamma[0], nCh[0]);
			});			
		}
		
		if (name.equals("btSetAlphaRangeGamma")) 
		{
			final double[] dGamma =  new double [] {1.0};
			if(args[2] != null)
			{
				dGamma[0] = ((Double)args[2]).doubleValue();
			}
			final int [] nCh = new int [] { -1 }; //all channels
			if(args[3] != null)
			{
				nCh[0] = (int)Math.round(((Double)args[3]).doubleValue());
			}			
			enqueue (()-> 
			{
				macroSetAlphaRangeGamma(((Double)args[0]).doubleValue(), ((Double)args[1]).doubleValue(), dGamma[0], nCh[0]);
			});			
		}

		if (name.equals("btOpenNext")) 
		{
			enqueue (()-> 
			{
				macroOpenNext( (String)args[0]);
			});
		}
		
		if (name.equals("btClose")) 
		{
			enqueue (()-> 
			{
				macroClose();			
			});
		}
		
		return null;
	}
	/** macro function runs full auto on all time points 
	 * @param dMinIntensity 
	 * 	the minimum intensity to start tracing curve 
	 * @param nMinNumPoints
	 * 	specifies the minimum number of points in a curve
	 **/
	public void macroRunFullAutoTrace(final Double dMinIntensity, final Integer nMinNumPoints)
	{
		macroRunFullAutoTrace(dMinIntensity, nMinNumPoints, 0, bt.btData.nNumTimepoints - 1);		
	}
	/** macro function runs full auto on specified time points range. 
	 * @param dMinIntensity 
	 * 	the minimum intensity to start tracing curve 
	 * @param nMinNumPoints
	 * 	specifies the minimum number of points in a curve
	 * @param nFirstFrame 
	 * 	in case of timelapse data, first frame to start (numbering from 0) 
	 * @param nLastFrame
	 *  in case of timelapse data, the last frame to trace  **/
	public void macroRunFullAutoTrace(final Double dMinIntensity, final Integer nMinNumPoints, final Integer nFirstFrame, final Integer nLastFrame)
	{

		int nFirstTP = 0;
		int nLastTP = 0;
		bMacroMode = true;

		if (bt.btData.nNumTimepoints != 1)
		{
			nFirstTP = Math.min(nFirstFrame, nLastFrame);
			nLastTP = Math.max(nFirstFrame, nLastFrame);
			nFirstTP = Math.max( 0, nFirstTP );
			nLastTP = Math.min( bt.btData.nNumTimepoints - 1, nLastTP );
		}
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "BigTrace macro: running full autotrace with parameters:" );
			IJ.log( "   Min intensity trace start: " + Double.toString( dMinIntensity ));
			IJ.log( "   Min # points in curve: " + Integer.toString( nMinNumPoints ));
			IJ.log( "   First time frame: " + Integer.toString( nFirstFrame ));
			IJ.log( "   Last time frame: " + Integer.toString( nLastFrame ));
			printShapeInterpolation();
			IJ.log( "One-click tracing parameters" );
			printOneClickParams();
		});
		bt.roiManager.panelFullAutoTrace.launchFullAutoTrace( dMinIntensity, nMinNumPoints, nFirstFrame, nLastFrame );
		bMacroMode = false;
	}
	
	/** macro function sets the approximate thickness of curves in each dimension for semi-auto, one-click and full-auto tracings (in pixels!)
	 * @param sigmas
	 *  array of SD in each dimension (in pixels) **/
	public void macroSetTracingThickness(final double [] sigmas)
	{
		bt.bInputLock = true;
		bMacroMode = true;

		String [] axes = new String[] {"X","Y","Z"};

		String out ="Axis SDs: ";
		for(int d = 0; d < 3; d++)
		{
			bt.btData.sigmaTrace[d] = sigmas[d];
			Prefs.set("BigTrace.sigmaTrace" + axes[d], bt.btData.sigmaTrace[d]);
			out += axes[d] + " " + Double.toString( bt.btData.sigmaTrace[d]) + " ";
		}
		final String outS = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "Setting tracing thickness:" );
			IJ.log( outS );
		});
		bt.bInputLock = false;
		bMacroMode = false;
	} 
	
	/** macro function sets the channel used for tracing and measurements 
	 * @param nChannel
	 * 	channel, taking into account that numbering starts from 1 **/
	public void macroSetActiveChannel (final Integer nChannel) 
	{
		bt.bInputLock = true;
		bMacroMode = true;

		int nFinCh = Math.max(nChannel,1);
		nFinCh = Math.min( nFinCh, bt.btData.nTotalChannels );
		
		final int nCh = nFinCh;
		TaskBT.runOnEDTAndWait(()->
		{
			bt.roiManager.setActiveChannel( nCh - 1 );
			IJ.log( "The active tracing/measuring channel is set to " + Integer.toString( nCh ) );
		});
		bt.bInputLock = false;
		bMacroMode = false;

	}
	
	/** macro function defining ROI thickness (diameter) during auto tracing 
	 * @param sEnable
	 * 	string, must contain "true" or "false". activates the feature
	 * @param dCoeff
	 * 	 a multiplication factor used to calculate the diameter
	 * @param sMethod
	 * 	("MAX", "AVG" or "MIN") of provided tracing SD thickness along all dimensions.
	 * **/	
	public void macroSetTracingROI(final String sEnable, final Double dCoeff, final String sMethod)
	{
		bt.bInputLock = true;
		bMacroMode = true;

		IJ.log( "Setting ROI thickness from tracing parameters: " );
		bt.btData.bEstimateROIThicknessFromParams  = false;
		if(sEnable.equals( "true" ))
		{
			bt.btData.bEstimateROIThicknessFromParams = true;	
		}
		else
		{
			IJ.log( "Disabled");
		}
		Prefs.set("BigTrace.bEstimateROIThicknessFromParams", bt.btData.bEstimateROIThicknessFromParams);	
		
		if(bt.btData.bEstimateROIThicknessFromParams)
		{
			String out = "Enabled, coefficient ";
			bt.btData.dTraceROIThicknessCoeff = dCoeff;
			Prefs.set("BigTrace.dTraceROIThicknessCoeff", bt.btData.dTraceROIThicknessCoeff);
			out = out + Double.toString( bt.btData.dTraceROIThicknessCoeff );
			out = out +" method ";
			int nMethod = 0;
			switch (sMethod.toLowerCase())
			{
			case "avg":
				nMethod = 1;
				out = out + " AVG";
				break;
			case "min":
				nMethod = 2;
				out = out + " MIN";
				break;
			default:
				out = out +" MAX";
			}
			bt.btData.nTraceROIThicknessMode = nMethod;
			Prefs.set("BigTrace.nTraceROIThicknessMode", (double)bt.btData.nTraceROIThicknessMode);
			IJ.log( out );
		}
		bt.bInputLock = false;
		bMacroMode = false;
	}
	/** macro function sets one click parameters
	 * @param nVertexPlacementPointN 
	 * 	 "distance" between intermediate vertex placement (pixels, more or equal to 3). 
	 *    Specifies how often intermediate points (vertices) will be placed on the curve during auto-tracing.
	 * @param dDirectionality
	 * 	the value for the directionality constraint (between 0 and 1). 
	 * @param sOCIntensityStop
	 *  string, must contain "true" or "false". defines whether to stop tracing if the current curve passes through a voxel with min intensity (next param) 
	 *  @param dMinIntensityThreshold 
	 *   minimum intensity value of the trace. Once encountered, tracing stops. 
	 *  **/
	public void macroSetOneClickParameters(final Integer nVertexPlacementPointN, final Double dDirectionality, final String sOCIntensityStop, final Double dMinIntensityThreshold)
	{
		bt.bInputLock = true;
		bMacroMode = true;
		
		bt.btData.nVertexPlacementPointN = Math.max(3, nVertexPlacementPointN);
		Prefs.set("BigTrace.nVertexPlacementPointN", (double)(bt.btData.nVertexPlacementPointN));
		
		bt.btData.dDirectionalityOneClick = Math.min(1.0, (Math.max(0, Math.abs(dDirectionality))));
		Prefs.set("BigTrace.dDirectionalityOneClick", bt.btData.dDirectionalityOneClick);		
		
		String sIntStop = sOCIntensityStop.toLowerCase();
		bt.btData.bOCIntensityStop = false;
		if(sIntStop.toLowerCase().equals( "true" ))
		{
			bt.btData.bOCIntensityStop = true;
		}
		Prefs.set("BigTrace.bOCIntensityStop", bt.btData.bOCIntensityStop);	
		
		if(bt.btData.bOCIntensityStop)
		{
			bt.btData.dOCIntensityThreshold = Math.max(0, Math.abs( dMinIntensityThreshold ));
			Prefs.set("BigTrace.dOCIntensityThreshold",bt.btData.dOCIntensityThreshold);
		}
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "Setting one-click tracing parameters:" );
			printOneClickParams();
		});
		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	
	/** macro function loads ROIs. 
	 * @param sFileName
	 * 	  Full path and filename of the loaded ROI file.
	 * @param sMode 
	 * can be "Clean", i.e. delete all ROIs and load new ones from the file. Or it can be "Append", so the newly loaded ROIs will be added to existing ones.
		**/
	public void macroLoadROIs(final String sFileName, final String sMode)
	{
		bMacroMode = true;
		bt.bInputLock = true;
        if(sMode == null)
        	return;
        int nLoadMode = 0;
        switch (sMode)
        {
        	case "Clean":
            	nLoadMode = 0;
        		break;
        	case "Append":
        		nLoadMode = 1;
        		break;  
        	default:
        		IJ.log( "Error! ROIs loading mode should be either Clean or Append. Loading failed." );
        		return;
        }
        ROIsIO.loadROIs( sFileName, nLoadMode, bt );
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "BigTrace ROIs loaded from " + sFileName);
		});
		bMacroMode = false;
		bt.bInputLock = false;

	}
	
	public void macroSaveROIs(final String sFileName)
	{
		macroSaveROIs(sFileName, "BigTrace");
	}
	/** macro function save ROIs to disk 
	 * @param sFileName 
	 * the full path + filename for saving.
	 * @param sMode
		output format, can be "BigTrace" (default in case of null), "CSV", or "SWC".**/
	public void macroSaveROIs(final String sFileName, final String sMode)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		bt.setLockMode(true);
		String out = "";
        if(sMode == null)
        {
        	out = "bigtrace";
        }
        else
        {
        	out = sMode.toLowerCase();
        }
        int nLoadMode = 0;
        switch (out)
        {
        	case "bigtrace":
            	nLoadMode = 0;
        		break;
        	case "csv":
        		nLoadMode = 1;
        		break;
           	case "swc":
        		nLoadMode = 2;
        		break;
        		
        	default:
        		IJ.log( "Error! ROIs saving mode should be either BigTrace, CSV or SWC. Saving aborted." );
        		return;
        }
        ROIsIO.saveROIs( sFileName, nLoadMode, bt );


		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "BigTrace ROIs saved to " + sFileName);
		});
		bt.setLockMode(false);
		bMacroMode = false;
		bt.bInputLock = false;
	}

	public void macroStraighten(final Integer nStraightenAxis, final String sOutputDir)
	{		
		macroStraighten(nStraightenAxis, sOutputDir, "Square");
	}
	
	/** macro function performs straightening of all ROIs and saves them as tif files
	 * @param nStraightenAxis
	 * 	specifies the axis of the output, which becomes the centerline of an ROI curve. The value of 0 corresponds to the X axis, 1 to Y, and 2 to Z.
	 * @param sOutputDir
	 * 	the path to the folder to save the output TIFs.
	 * @param sShape
	 *  the shape of the extracted volume around the centerline, it could be either "Square" (default) or "Round". 
	 * **/
	public void macroStraighten(final Integer nStraightenAxis, final String sOutputDir, final String sShape)
	{	
		bt.bInputLock = true;
		bMacroMode = true;

		bt.setLockMode(true);
		//build list of ROIs
		final ArrayList<AbstractCurve3D> curvesOut = new ArrayList<>();

		for (int nRoi = 0; nRoi < bt.roiManager.rois.size(); nRoi++)
		{
			Roi3D roi = bt.roiManager.rois.get(nRoi);
			if(bt.roiManager.groups.get(roi.getGroupInd()).bVisible)
			{
				if((roi.getType() == Roi3D.LINE_TRACE) || (roi.getType() == Roi3D.POLYLINE))
				{
					curvesOut.add((AbstractCurve3D) roi);
				}
			}
		}
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log("BigTrace macro: running straighten command on all ROIs");
			IJ.log( "  Total " + Integer.toString(bt.roiManager.rois.size()) +" ROIs" );
			IJ.log( "  Found " + Integer.toString(curvesOut.size()) + " curve ROIs" );
			printShapeInterpolation();
			printIntensityInterpolation();
		});
		int nAxis = nStraightenAxis;
		if(nStraightenAxis < 0 || nStraightenAxis > 2)
		{
			nAxis = 0;
			IJ.log( "First axis parameter should be in the range of 0-2, wher 0 = X axis, 1 = Y axis, 2 = Z axis" );
			IJ.log( "Setting the value to 0, X axis." );
		}
		int nShape = 0 ;
		if(sShape == "Round")
		{
			nShape  = 1;
		}
		if(curvesOut.size() > 0)
		{	
			StraightenCurve<T> straightBG = new StraightenCurve<>(curvesOut, bt, -1.0f, nAxis, nShape, 0, 1, sOutputDir);
			straightBG.addPropertyChangeListener(bt.btPanel);
			straightBG.runStraightenCurve();
		}
		else
		{
			TaskBT.runOnEDTAndWait(()->
			{
				IJ.log("Cannot find proper curve ROIs to straighten.");
				bt.btPanel.progressBar.setString("curve straightening aborted.");
			});
		}
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log("Straightened ROIs saved to " + sOutputDir + " folder" );
		});
		bt.setLockMode(false);
		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	/** macro function sets current shape interpolation.
	 * @param sShapeInterpolation
	 * 	must be one of the "Voxel", "Smooth" or "Spline"
	 * @param nSmoothWindow
	 * 	smoothing window in voxel steps along the line **/
	public void macroShapeInterpolation(final String sShapeInterpolation, final Integer nSmoothWindow)
	{
		bt.bInputLock = true;
		bMacroMode = true;
		String out = "BigTrace ROI Shape Interpolation set to ";
		switch ( sShapeInterpolation )
		{
		case "Voxel":
			bt.btData.shapeInterpolation = BigTraceData.SHAPE_Voxel;
			out = out + "Voxel.";
			break;
		case "Smooth":
			bt.btData.shapeInterpolation = BigTraceData.SHAPE_Smooth;
			out = out + "Smooth.";
			break;
		case "Spline":
			bt.btData.shapeInterpolation = BigTraceData.SHAPE_Spline;
			out = out + "Spline.";
			break;
		default:
			IJ.log( "Error! ROI Shape Interpolation values should be either Voxel, Smooth or Spline." );
			return;
		}
		bt.btData.nSmoothWindow = Math.max( 1, Math.abs( Math.round( nSmoothWindow ) ));

		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			bt.roiManager.updateROIsDisplay();
			IJ.log(sOut);
			IJ.log("BigTrace ROI smoothing window set to " + Integer.toString( bt.btData.nSmoothWindow ) + ".");
		});
		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	/** macro function sets current intensity interpolation settings.
	 * @param sInterpolation
	 * 	input values are Neighbor, Linear, Lanczos **/
	public void macroIntensityInterpolation(final String sInterpolation)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		String out = "BigTrace Intensity Interpolation set to ";
		switch (sInterpolation)
		{
		case "Neighbor":
			bt.btData.intensityInterpolation = BigTraceData.INT_NearestNeighbor;
			out = out + "Nearest Neighbor.";
			break;
		case "Linear":
			bt.btData.intensityInterpolation = BigTraceData.INT_NLinear;
			out = out + "Linear.";
			break;
		case "Lanczos":
			bt.btData.intensityInterpolation = BigTraceData.INT_Lanczos;
			out = out + "Lanczos.";
			break;
		default:
			IJ.log( "Error! Intensity interpolation values should be either Nearest, Linear or Lanczos." );
			return;
		}
		bt.btData.setInterpolationFactory();

		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log(sOut);
		});
		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	/** macro function specifies which parameters are measured. 
	 * @param sListMeasurements
	 * A string with a set of (any) delimeter separated measurements names **/
	public void macroSetMeasurements(final String sListMeasurements)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		String sFinSet = "";
		for (int i = 0; i < RoiMeasure3D.labels.length; i++)
		{
			if(sListMeasurements.toLowerCase().contains( RoiMeasure3D.labels[i].toLowerCase() ))
			{
				sFinSet = sFinSet + RoiMeasure3D.labels[i] +" ";
				RoiMeasure3D.systemMeasurements |= RoiMeasure3D.listMeasurements[i];
			}
		}

		final String sMeasure = sFinSet;
		TaskBT.runOnEDTAndWait(()->
		{
			if(sMeasure.equals( "" ))
			{
				IJ.log( "Macro command \"Set measurements\" error cannot detect proper names.");
			}
			else
			{
				IJ.log( "BigTrace macro: measurements set to: "+ sMeasure +".");
			}
		});
		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	/** macro function measures all ROIs and saves results to CSV
	 * @param sFilename 
	 * full path + filename to store results (in CSV) **/
	public void macroMeasureAndSave(final String sFilename)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		bt.setLockMode(true);
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "BigTrace macro: running measure.");
			IJ.log( "   Active channel: " + Integer.toString( bt.btData.nChAnalysis + 1 ));
			printShapeInterpolation();
			printIntensityInterpolation();
		});
		ROIsMeasureBG roiMeasure = new ROIsMeasureBG();
		roiMeasure.bt = bt;
		roiMeasure.rois = bt.roiManager.rois;
		roiMeasure.runMeasure();
		roiMeasure.saveMeasurementsCSV( sFilename );

		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log("BigTrace macro: measured " + Integer.toString( bt.roiManager.rois.size() ) + " ROIs, saved to " + sFilename + ".");
		});
		bt.setLockMode(false);
		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	/** macro function to adjust LUT or color mapping and gamma value 
	 * @param minIntensity
	 * 	minimum intensity for LUT
	 * @param maxIntensity
	 * 	maximum intensity for LUT
	 * @param dGamma
	 * 	gamma correction coefficient 
	 * @param nChannel
	 * 	apply only to selected channel (numbering from 1) or 0 to apply to all
	 *  **/
	public void macroSetDisplayRangeGamma(final Double minIntensity, final Double maxIntensity, final Double dGamma, final Integer nChannel)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		final double dMin = Math.min( minIntensity, maxIntensity ); 
		final double dMax = Math.max( minIntensity, maxIntensity ); 
		String out;
		if(nChannel <= 0 || nChannel > bt.bvv_sources.size())
		{
			for (final BvvStackSource< ? > bvvSource : bt.bvv_sources)
			{
				bvvSource.setDisplayRange( dMin, dMax );
				bvvSource.setDisplayGamma( dGamma );					
			}				
			out = "BigTrace macro set display range and gamma to all channels.";
		}
		else
		{
			final BvvStackSource< ? > bvvSource = bt.bvv_sources.get( nChannel );
			bvvSource.setDisplayRange( dMin, dMax );
			bvvSource.setDisplayGamma( dGamma );
			out = "BigTrace macro set display range and gamma to channel " + Integer.toString( nChannel ) + ".";	
		}

		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log(sOut);
		});
		
		bt.bInputLock = false;
		bMacroMode = false;
	}

	public void macroSetDisplayRangeGamma(final Double minIntensity, final Double maxIntensity, final Double dGamma)
	{
		macroSetDisplayRangeGamma(minIntensity, maxIntensity, dGamma, -1);
	}
	public void macroSetDisplayRangeGamma(final Double minIntensity, final Double maxIntensity)
	{
		macroSetDisplayRangeGamma(minIntensity, maxIntensity, 1.0);

	}
	
	/** macro function to adjust alpha (opacity) mapping and its gamma value 
	 * @param minAlpha
	 * 	minimum intensity for LUT
	 * @param maxAlpha
	 * 	maximum intensity for LUT
	 * @param dGamma
	 * 	gamma correction coefficient 
	 * @param nChannel
	 * 	apply only to selected channel (numbering from 1) or 0 to apply to all
	 *  **/
	public void macroSetAlphaRangeGamma(final Double minAlpha, final Double maxAlpha, final Double dGamma, final Integer nChannel)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		final double dMin = Math.min( minAlpha, maxAlpha ); 
		final double dMax = Math.max( minAlpha, maxAlpha ); 
		String out;
		if(nChannel <= 0 || nChannel > bt.bvv_sources.size())
		{
			for (final BvvStackSource< ? > bvvSource : bt.bvv_sources)
			{
				bvvSource.setAlphaRange( dMin, dMax );
				bvvSource.setAlphaGamma( dGamma );					
			}				
			out = "BigTrace macro set alpha range and gamma to all channels.";
		}
		else
		{
			final BvvStackSource< ? > bvvSource = bt.bvv_sources.get( nChannel );
			bvvSource.setAlphaRange( dMin, dMax );
			bvvSource.setAlphaGamma( dGamma );
			out = "BigTrace macro set alpha range and gamma to channel " + Integer.toString( nChannel ) + ".";				
		}

		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log(sOut);
		});
		bt.bInputLock = false;
		bMacroMode = false;
	}

	public void macroSetAlphaRangeGamma(final Double minAlpha, final Double maxAlpha)
	{
		macroSetAlphaRangeGamma(minAlpha, maxAlpha, 1.0, -1);
	}

	public void macroSetAlphaRangeGamma(final Double minAlpha, final Double maxAlpha, final Double dGamma)
	{
		macroSetAlphaRangeGamma(minAlpha, maxAlpha, dGamma, -1);

	}

	/** macro function removes current loaded image and loads a new one to BigTrace.
	 * also deletes all existing ROIs 
	 * @param sFilename
	 * 	full path + filename to the new volumetric image **/
	public void macroOpenNext(final String sFilename)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		
		//remove all the bvv sources
		for(int i = 0; i < bt.bvv_sources.size(); i++)
		{
			bt.bvv_sources.get( i ).removeFromBdv();
		}
		bt.bvv_sources.clear();
		bt.roiManager.rois.clear();
		TaskBT.runOnEDTAndWait(()->
		{
			bt.roiManager.updateRoiListModel();

			bt.btData = new BigTraceData<>(bt);
			bt.btLoad = new BigTraceLoad<>(bt);
			bt.btData.sFileNameFullImg = sFilename;
			bt.loadSources();
			bt.initSourcesCanvas( false );
			
			bt.visualBoxes.clipBox.setBtData( bt.btData );
			bt.visualBoxes.traceBox.setBtData( bt.btData );
			bt.visualBoxes.volumeBox.setBtData( bt.btData );
			bt.btPanel.voxelSizePanel.setVoxelSize( bt.btData.globCal, bt.btData.sVoxelUnit );
			bt.btPanel.clipPanel.resetBounds( bt.btData.nDimCurr[1] );
			
			bt.btPanel.updateViewDataSources();

			bt.bvvFrame.setTitle( sFilename );
		});

		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	/** macro function closes BigTrace **/
	public void macroClose()
	{
		bt.closeWindows();
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log("BigTrace closed.");
		});
	}
	
	//LOGGING HELPERS
	
	void printOneClickParams()
	{
		IJ.log( "   Active channel: " + Integer.toString(bt.btData.nChAnalysis + 1) );
		IJ.log( "   Tracing curve thickness estimate (px, XYZ): [" + 
		Double.toString(  bt.btData.sigmaTrace[0]) +" "+
		Double.toString(  bt.btData.sigmaTrace[1]) +" "+
		Double.toString(  bt.btData.sigmaTrace[2]) +"]");
		IJ.log( "   Directionality constrain: " + bt.btData.dDirectionalityOneClick );
		IJ.log( "   Intermediate vertex placement: " + bt.btData.nVertexPlacementPointN );
		IJ.log( "   Use intensity threshold: " + bt.btData.bOCIntensityStop );	
		IJ.log( "   Trace only clipped volume: " + bt.btData.bTraceOnlyClipped );
		if(bt.btData.bOCIntensityStop)
		{
			IJ.log( "   Intensity threshold min value: " + Double.toString( bt.btData.dOCIntensityThreshold));
		}		
		if(!bt.btData.bEstimateROIThicknessFromParams)
		{
			IJ.log( "   New ROI diameter auto disabled" );
		}
		else
		{
			IJ.log( "   New ROI diameter auto enabled" );
			String out = "      Method: ";
			switch(bt.btData.nTraceROIThicknessMode)
			{
			case 0:
				out = out + "MAX";
				break;
			case 1:
				out = out + "AVG";
				break;
			case 2:
				out = out + "MIN";
				break;
			}
			IJ.log(out);
			IJ.log("      Multiplication coefficient: " + Double.toString( bt.btData.dTraceROIThicknessCoeff ));
		}
	}	
	
	void printIntensityInterpolation()
	{
		String out = "   ROI intensity interpolation: ";
		switch(bt.btData.intensityInterpolation)
		{
		case BigTraceData.INT_NearestNeighbor:
			out = out + "Nearest Neighbor";
			break;
		case BigTraceData.INT_NLinear:
			out = out + "Linear";
			break;
		case BigTraceData.INT_Lanczos:
			out = out + "Lanczos";
			break;
		}
		IJ.log(out);
	}
	
	void printShapeInterpolation()
	{
		String out = "   ROI shape interpolation: ";
		switch(bt.btData.shapeInterpolation)
		{
		case BigTraceData.SHAPE_Voxel:
			out = out + "Voxel";
			break;
		case BigTraceData.SHAPE_Smooth:
			out = out + "Smooth";
			break;
		case BigTraceData.SHAPE_Spline:
			out = out + "Spline";
			break;
		}
		IJ.log(out);
	}
	
	@SuppressWarnings({ "rawtypes" })
	public static void main(String... args) throws Exception
	{
		
		new ImageJ();
		BigTrace testI = new BigTrace(); 
		
		//testI.run("/home/eugene/Desktop/projects/BigTrace/BigTrace_data/ExM_MT.tif");
		testI.run("/home/eugene/Desktop/projects/BigTrace/BT_time_Oane/tracefile_3TP.tif");
		String [] loadS = new String [] {"/home/eugene/Desktop/projects/BigTrace/macro/test_fulltrace_bt.csv", "Clean"};
		testI.btMacro.handleExtension( "btLoadROIs", loadS );
		
		//String [] loadNext = new String [] {"/home/eugene/Desktop/projects/BigTrace/BT_time_Oane/tracefile_3TP.tif"};
		String [] loadNext = new String [] {"/home/eugene/Desktop/projects/BigTrace/BigTrace_data/ExM_MT.tif"};

		testI.btMacro.handleExtension( "btOpenNext", loadNext );
//
//		Object [] intS = new Object [] {new Double(0.), new Double(200.), 0.42, null};
//		testI.btMacro.handleExtension( "btSetAlphaRangeGamma", intS );
//
		Object [] traceS = new Object [] {new Double(230.), new Double(10.), null, null};
		testI.btMacro.handleExtension( "btRunFullAutoTrace", traceS );
//		String [] loadM = new String [] {"Volume Length SD of Intensity Straightness Ends coordinates End-end direction" };
//		testI.btMacro.handleExtension( "btSetMeasurements", loadM);
//
//		String [] pathM = new String [] {"/home/eugene/Desktop/testM.csv" };
//		testI.btMacro.handleExtension( "btMeasureAndSave", pathM);

		//testI.btMacro.handleExtension( "btSetDisplayRangeGamma", intS );
		//testI.btMacro.handleExtension( "btSetDisplayRangeGamma", intS );

	}
}
