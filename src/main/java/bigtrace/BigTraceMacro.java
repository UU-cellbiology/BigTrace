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
	
	public synchronized CompletableFuture<Void> enqueue(Runnable task) 
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

	        return tail;
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
			final double [] sigmas = new double[3];
			for(int d = 0; d < 3; d++)
			{
				sigmas[d] = Math.abs(((Double)args[d]).doubleValue());
			}
			enqueue (()-> 
			{
				macroSetTracingThickness(sigmas);
			});
		}
		if (name.equals("btSetTracingROI")) 
		{
			enqueue (()-> 
			{
				macroSetTracingROI((String)args[0],Math.abs(((Double)args[1]).doubleValue()), (String)args[2]);
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
			int nFirstTPi = 0;
			int nLastTPi = bt.btData.nNumTimepoints - 1;
			if(args[2] != null && args[3] != null )
			{
				nFirstTPi = (int)Math.round(((Double)args[2]).doubleValue());
				nLastTPi = (int)Math.round(((Double)args[3]).doubleValue());
			}
			final int nFirstTP = nFirstTPi;
			final int nLastTP = nLastTPi;
			enqueue (()-> 
			{
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
				macroCloseBT();			
			});
		}
		
		return null;
	}
	
	public void macroRunFullAutoTrace(final double dMinIntensity, final int nMinNumPoints, final int nFirstFrame, final int nLastFrame)
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
			IJ.log( "Running full autotrace with parameters:" );
			IJ.log( "Min intensity trace start:" + Double.toString( dMinIntensity ));
			IJ.log( "Min # points in curve:" + Integer.toString( nMinNumPoints ));
			IJ.log( "First time frame: " + Integer.toString( nFirstFrame ));
			IJ.log( "Last time frame: " + Integer.toString( nLastFrame ));
			IJ.log( " -- One-click tracing parameters --" );
			printOneClickParams();
		});
		bt.roiManager.panelFullAutoTrace.launchFullAutoTrace( dMinIntensity, nMinNumPoints, nFirstFrame, nLastFrame );
		bMacroMode = false;
	}
	
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
	
	public void macroSetActiveChannel (final int nChannel) 
	{
		bt.bInputLock = true;
		bMacroMode = true;

		int nFinCh = Math.max(nChannel,1);
		nFinCh = Math.min( nFinCh, bt.btData.nTotalChannels );
		bt.roiManager.setActiveChannel( nFinCh - 1 );
		bt.bInputLock = false;
		bMacroMode = false;
		final int nCh = nFinCh;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "The active tracing/measuring channel is set to " + Integer.toString( nCh ) );
		});

	}
	
	public void macroSetTracingROI(final String sEnable, final double coeff, final String sMethod)
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
			bt.btData.dTraceROIThicknessCoeff = coeff;
			Prefs.set("BigTrace.dTraceROIThicknessCoeff",bt.btData.dTraceROIThicknessCoeff);
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
	
	public void macroSetOneClickParameters(final int nVertexPlacementPointN, final double dDirectionalityOneClick, final String sOCIntensityStop, double dOCIntensityThreshold)
	{
		bt.bInputLock = true;
		bMacroMode = true;
		
		bt.btData.nVertexPlacementPointN = Math.max(3, nVertexPlacementPointN);
		Prefs.set("BigTrace.nVertexPlacementPointN", (double)(bt.btData.nVertexPlacementPointN));
		
		bt.btData.dDirectionalityOneClick = Math.min(1.0, (Math.max(0, Math.abs(dDirectionalityOneClick))));
		Prefs.set("BigTrace.dDirectionalityOneClick",bt.btData.dDirectionalityOneClick);		
		
		String sIntStop = sOCIntensityStop.toLowerCase();
		bt.btData.bOCIntensityStop = false;
		if(sIntStop.equals( "true" ))
		{
			bt.btData.bOCIntensityStop = true;
		}
		Prefs.set("BigTrace.bOCIntensityStop", bt.btData.bOCIntensityStop);	
		
		if(bt.btData.bOCIntensityStop)
		{
			bt.btData.dOCIntensityThreshold = Math.max(0, Math.abs( dOCIntensityThreshold ));
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
	
	void printOneClickParams()
	{
		IJ.log( "Directionality constrain: " + bt.btData.dDirectionalityOneClick );
		IJ.log( "Intermediate vertex placement: " + bt.btData.nVertexPlacementPointN );
		IJ.log( "Use intensity threshold: " + bt.btData.bOCIntensityStop );	
		if(bt.btData.bOCIntensityStop)
		{
			IJ.log( "Intensity threshold min value:" + Double.toString( bt.btData.dOCIntensityThreshold));
		}		
	}	
	
	/** macro loads ROIs **/
	public void macroLoadROIs(final String sFileName, final String input)
	{
		bMacroMode = true;
        if(input == null)
        	return;
        int nLoadMode = 0;
        switch (input)
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
		bMacroMode = false;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "BigTrace ROIs loaded from " + sFileName);
		});
	}
	
	public void macroSaveROIs(final String sFileName, final String output)
	{
		bMacroMode = true;

		String out = "";
        if(output == null)
        {
        	out = "bigtrace";
        }
        else
        {
        	out = output.toLowerCase();
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
		bMacroMode = false;

		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log( "BigTrace ROIs saved to " + sFileName);
		});
	}
	
	public void macroStraighten(final int nStraightenAxis, String sSaveDir, String sShape)
	{	
		bt.bInputLock = true;
		bMacroMode = true;

		bt.setLockMode(true);
		//build list of ROIs
		final ArrayList<AbstractCurve3D> curvesOut = new ArrayList<>();
		IJ.log( "Total " + Integer.toString(bt.roiManager.rois.size()) +" ROIs" );
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
		IJ.log( "Found " + Integer.toString(curvesOut.size()) + " curve ROIs" );

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
			StraightenCurve<T> straightBG = new StraightenCurve<>(curvesOut, bt, -1.0f, nAxis, nShape, 0, 1, sSaveDir);
			straightBG.addPropertyChangeListener(bt.btPanel);
			straightBG.runStraightenCurve();
		}
		else
		{
			IJ.log("Cannot find proper curve ROIs to straighten.");
			bt.btPanel.progressBar.setString("curve straightening aborted.");
		}
		bt.setLockMode(false);
		bt.bInputLock = false;
		bMacroMode = false;
	}
	
	/** sets current shape interpolation .
	 * input values for the sShapeInterpol are Voxel, Smooth, Spline
	 * plus integer nSmoothWindow **/
	public void macroShapeInterpolation(final String sShapeInterpol, final int nSmoothWindow)
	{
		bt.bInputLock = true;
		bMacroMode = true;
		String out = "BigTrace ROI Shape Interpolation set to ";
		switch (sShapeInterpol)
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
		bt.roiManager.updateROIsDisplay();
		bt.bInputLock = false;
		bMacroMode = false;
		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log(sOut);
			IJ.log("BigTrace ROI smoothing window set to " + Integer.toString( bt.btData.nSmoothWindow ) + ".");
		});

	}
	
	/** sets current intensity interpolation settings.
	 * possible input values are Neighbor, Linear, Lanczos **/
	public void macroIntensityInterpolation(final String sInterpol)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		String out = "BigTrace Intensity Interpolation set to ";
		switch (sInterpol)
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
		bt.bInputLock = false;
		bMacroMode = false;
		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log(sOut);
		});
	}
	/** A string with a set of (any) delimeter separated measurements names should be provided as input **/
	public void macroSetMeasurements(final String sMeasurements)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		String sFinSet = "";
		for (int i = 0; i < RoiMeasure3D.labels.length; i++)
		{
			if(sMeasurements.toLowerCase().contains( RoiMeasure3D.labels[i].toLowerCase() ))
			{
				sFinSet = sFinSet + RoiMeasure3D.labels[i] +" ";
				RoiMeasure3D.systemMeasurements |= RoiMeasure3D.listMeasurements[i];
			}
		}
		bt.bInputLock = false;
		bMacroMode = false;
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
	}
	
	/** Needs full path + filenames as a parameter **/
	public void macroMeasureAndSave(final String sFilename)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		ROIsMeasureBG roiMeasure = new ROIsMeasureBG();
		roiMeasure.bt = bt;
		roiMeasure.rois = bt.roiManager.rois;
		roiMeasure.runMeasure();
		roiMeasure.saveMeasurementsCSV( sFilename );
		bt.bInputLock = false;
		bMacroMode = false;

		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log("BigTrace macro: measured " + Integer.toString( bt.roiManager.rois.size() ) + " ROIs, saved to " + sFilename + ".");
		});
	}
	
	public void macroSetDisplayRangeGamma(final double dMin_, final double dMax_, final double dGamma, final int nCh)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		final double dMin = Math.min( dMin_, dMax_ ); 
		final double dMax = Math.max( dMin_, dMax_ ); 
		String out;
		if(nCh <= 0 || nCh > bt.bvv_sources.size())
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
			final BvvStackSource< ? > bvvSource = bt.bvv_sources.get( nCh );
			bvvSource.setDisplayRange( dMin, dMax );
			bvvSource.setDisplayGamma( dGamma );
			out = "BigTrace macro set display range and gamma to channel " + Integer.toString( nCh ) + ".";	
		}
		bt.bInputLock = false;
		bMacroMode = false;
		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log(sOut);
		});
	}
	
	public void macroSetAlphaRangeGamma(final double dMin_, final double dMax_, final double dGamma, final int nCh)
	{
		bMacroMode = true;
		bt.bInputLock = true;
		final double dMin = Math.min( dMin_, dMax_ ); 
		final double dMax = Math.max( dMin_, dMax_ ); 
		String out;
		if(nCh <= 0 || nCh > bt.bvv_sources.size())
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
			final BvvStackSource< ? > bvvSource = bt.bvv_sources.get( nCh );
			bvvSource.setAlphaRange( dMin, dMax );
			bvvSource.setAlphaGamma( dGamma );
			out = "BigTrace macro set alpha range and gamma to channel " + Integer.toString( nCh ) + ".";				
		}

		bt.bInputLock = false;
		bMacroMode = false;
		final String sOut = out;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log(sOut);
		});
	}
	
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
			bt.btPanel.clipPanel.setBounds( bt.btData.nDimCurr[1] );
			bt.btPanel.updateViewDataSources();

			bt.bvvFrame.setTitle( sFilename );
		});
		bt.bInputLock = false;
		bMacroMode = false;
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log("BigTrace macro: opened new file " + sFilename + ".");
		});
	}
	
	/** closes BigTrace **/
	public void macroCloseBT()
	{
		bt.closeWindows();
		TaskBT.runOnEDTAndWait(()->
		{
			IJ.log("BigTrace closed.");
		});
	}
	
	@SuppressWarnings({ "rawtypes" })
	public static void main(String... args) throws Exception
	{
		
		new ImageJ();
		BigTrace testI = new BigTrace(); 
		
		testI.run("/home/eugene/Desktop/projects/BigTrace/BigTrace_data/ExM_MT.tif");
		
		String [] loadS = new String [] {"/home/eugene/Desktop/projects/BigTrace/macro/test_fulltrace_bt.csv", "Clean"};
		testI.btMacro.handleExtension( "btLoadROIs", loadS );
		
		String [] loadNext = new String [] {"/home/eugene/Desktop/projects/BigTrace/BT_time_Oane/tracefile_3TP.tif"};
		testI.btMacro.handleExtension( "btOpenNext", loadNext );
//
//		Object [] intS = new Object [] {new Double(0.), new Double(200.), 0.42, null};
//		testI.btMacro.handleExtension( "btSetAlphaRangeGamma", intS );
//
//		Object [] traceS = new Object [] {new Double(230.), new Double(10.), null, null};
//		testI.btMacro.handleExtension( "btRunFullAutoTrace", traceS );
//		String [] loadM = new String [] {"Volume Length SD of Intensity Straightness Ends coordinates End-end direction" };
//		testI.btMacro.handleExtension( "btSetMeasurements", loadM);
//
//		String [] pathM = new String [] {"/home/eugene/Desktop/testM.csv" };
//		testI.btMacro.handleExtension( "btMeasureAndSave", pathM);

		//testI.btMacro.handleExtension( "btSetDisplayRangeGamma", intS );
		//testI.btMacro.handleExtension( "btSetDisplayRangeGamma", intS );

	}
}
