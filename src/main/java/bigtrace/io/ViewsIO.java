package bigtrace.io;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import javax.swing.JOptionPane;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

import bdv.viewer.ConverterSetupBounds;
import bvvpg.source.converters.ConverterSetupBoundsAlpha;
import bvvpg.source.converters.ConverterSetupBoundsGamma;
import bvvpg.source.converters.ConverterSetupBoundsGammaAlpha;
import bvvpg.source.converters.ConverterSetupsPG;
import bvvpg.source.converters.RealARGBColorGammaConverterSetup;
import bvvpg.vistools.BvvStackSource;
import ij.IJ;
import ij.Prefs;

import bigtrace.BigTrace;
import bigtrace.BigTraceData;
import bigtrace.animation.Scene;

public class ViewsIO
{

	/** saving current view as csv **/
	public static < T extends RealType< T > & NativeType< T > > void saveView(final BigTrace<T> bt, String sFilename)
	{		
		bt.bInputLock = true;
		bt.setLockMode(true);
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		DecimalFormat df3 = new DecimalFormat ("#.#####", symbols);
        try {
			final File file = new File(sFilename);
			
			try (FileWriter writer = new FileWriter(file))
			{
				writer.write("BigTrace_View,version," + BigTraceData.sVersion + "\n");
				writer.write("FileNameFull," + bt.btData.sFileNameFullImg+"\n");
				writer.write("Render Type," + Integer.toString( bt.btData.nRenderMethod )+"\n");
				writer.write("Canvas BG color," + Integer.toString( bt.btData.canvasBGColor.getRGB() )+"\n");
				writer.write("nHalfClickSizeWindow," + Integer.toString( bt.btData.nHalfClickSizeWindow )+"\n");
				writer.write("Zoom Box Size," + Integer.toString( bt.btData.nZoomBoxSize)+"\n");
				writer.write("Zoom Box Fraction," + df3.format(bt.btData.dZoomBoxScreenFraction)+"\n");
				writer.write("bROIDoubleClickClip," + Integer.toString( bt.btData.bROIDoubleClickClip ? 1 : 0 )+"\n");
				writer.write("nROIDoubleClickClipExpand," + Integer.toString( bt.btData.nROIDoubleClickClipExpand )+"\n");				
				writer.write("bStartFullScreen," + Integer.toString( bt.btData.bStartFullScreen ? 1 : 0 )+"\n");

				
				writer.write("Intensity Interpolation,"+Integer.toString( bt.btData.intensityInterpolation )+"\n");
				writer.write("ROI Shape Interpolation,"+Integer.toString( bt.btData.shapeInterpolation )+"\n");
				writer.write("Rotation min frame type,"+Integer.toString( bt.btData.rotationMinFrame )+"\n");
				writer.write("Smooth window,"+Integer.toString( bt.btData.nSmoothWindow )+"\n");
				writer.write("Sector number,"+Integer.toString( bt.btData.sectorN )+"\n");
				writer.write("wireCountourStep,"+Integer.toString( bt.btData.wireCountourStep )+"\n");
				writer.write("crossSectionGridStep,"+Integer.toString( bt.btData.crossSectionGridStep )+"\n");
				writer.write("surfaceRender,"+Integer.toString( bt.btData.surfaceRender )+"\n");
				writer.write("silhouetteRender,"+Integer.toString( bt.btData.silhouetteRender )+"\n");
				writer.write("silhouetteDecay,"+df3.format(bt.btData.silhouetteDecay)+"\n");
				writer.write("wireAntiAliasing,"+Integer.toString( bt.btData.wireAntiAliasing ? 1 : 0)+"\n");
				writer.write("timeRender,"+Integer.toString( bt.btData.timeRender )+"\n");
				writer.write("timeFade,"+Integer.toString( bt.btData.timeFade )+"\n");
				//tracing parameters
				writer.write("nTraceBoxSize," + Integer.toString( bt.btData.nTraceBoxSize )+"\n");
				writer.write("fTraceBoxScreenFraction," + df3.format(bt.btData.fTraceBoxScreenFraction)+"\n");
				writer.write("fTraceBoxAdvanceFraction," + df3.format(bt.btData.fTraceBoxAdvanceFraction)+"\n");
				writer.write("nVertexPlacementPointN," + Integer.toString( bt.btData.nVertexPlacementPointN )+"\n");
				writer.write("dDirectionalityOneClick," + df3.format(bt.btData.dDirectionalityOneClick)+"\n");
				writer.write("bOCIntensityStop," + Integer.toString(bt.btData.bOCIntensityStop ? 1 : 0)+"\n");
				writer.write("dOCIntensityThreshold," + df3.format(bt.btData.dOCIntensityThreshold)+"\n");
				for (int d = 0; d < 3; d++)
				{
					writer.write("Trace sigma,"+Integer.toString( d )+ ","+ df3.format(bt.btData.sigmaTrace[d])+"\n");					
				}
				
				writer.write("bEstimateROIThicknessFromParams," + Integer.toString( bt.btData.bEstimateROIThicknessFromParams ? 1 : 0 )+"\n");
				writer.write("dTraceROIThicknessCoeff," + df3.format( bt.btData.dTraceROIThicknessCoeff)+"\n");
				writer.write("nTraceROIThicknessMode," + Integer.toString( bt.btData.nTraceROIThicknessMode )+"\n");
				writer.write("bOneClickUseMask," + Integer.toString( bt.btData.bOneClickUseMask ? 1 : 0 )+"\n");
				writer.write("bTrackingUseMask," + Integer.toString( bt.btData.bTrackingUseMask ? 1 : 0 )+"\n");

				writer.write("dAutoMinStartTraceInt," + Integer.toString( (int)Prefs.get("BigTrace.dAutoMinStartTraceInt",128.0) )+"\n");
				writer.write("nAutoMinPointsCurve," + Integer.toString( (int)Prefs.get("BigTrace.nAutoMinPointsCurve",3) )+"\n");
				
				writer.write("Scene, current\n");
				bt.getCurrentScene().save( writer );
				int nSourcesN = bt.bvv_sources.size();
				writer.write("Sources number," + Integer.toString(nSourcesN)+"\n");
				ConverterSetupsPG convSet = (ConverterSetupsPG) bt.bvv_main.getBvvHandle().getConverterSetups();
				final ConverterSetupBounds boundsMap = convSet.getBounds();
				final ConverterSetupBoundsGamma boundsGamma = convSet.getBoundsGamma();
				final ConverterSetupBoundsAlpha boundsAlpha = convSet.getBoundsAlpha();
				final ConverterSetupBoundsGammaAlpha boundsAlphaGamma = convSet.getBoundsGammaAlpha();
				for (int i=0; i<nSourcesN; i++)
				{
					writer.write("Source,"+Integer.toString(i+1)+"\n");
					RealARGBColorGammaConverterSetup converter = (RealARGBColorGammaConverterSetup)bt.bvv_sources.get( i ).getConverterSetups().get( 0 );
					
					writer.write("Display bounds," + df3.format(boundsMap.getBounds( converter ).getMinBound())
								  + "," + df3.format(boundsMap.getBounds( converter ).getMaxBound()) + "\n");
					writer.write("Display range," + df3.format(converter.getDisplayRangeMin())
					  + "," + df3.format(converter.getDisplayRangeMax()) + "\n");
					
					writer.write("Gamma bounds," + df3.format(boundsGamma.getBounds( converter ).getMinBound())
					  + "," + df3.format(boundsGamma.getBounds( converter ).getMaxBound()) + "\n");					
					writer.write("Gamma," + df3.format(converter.getDisplayGamma()) + "\n");
					
					writer.write("Alpha bounds," + df3.format(boundsAlpha.getBounds( converter ).getMinBound())
					  + "," + df3.format(boundsAlpha.getBounds( converter ).getMaxBound()) + "\n");	
					writer.write("Alpha range," + df3.format(converter.getAlphaRangeMin())
					  + "," + df3.format(converter.getAlphaRangeMax()) + "\n");
					
					writer.write("Alpha Gamma bounds," + df3.format(boundsAlphaGamma.getBounds( converter ).getMinBound())
					  + "," + df3.format(boundsAlphaGamma.getBounds( converter ).getMaxBound()) + "\n");	
					writer.write("Alpha Gamma," + df3.format(converter.getAlphaGamma()) + "\n");
					
					writer.write("Color," + Integer.toString(converter.getColor().get()) + "\n");

					writer.write("LUT size," + Integer.toString(converter.getLUTSize()) + "\n");
					writer.write("LUT," + converter.getLUTName() + "\n");
					
					writer.write("Voxel interpolation," + Integer.toString(converter.getVoxelRenderInterpolation()) + "\n");
					writer.write("Clip active," + Integer.toString( converter.getClipState()) + "\n");
					
					if(converter.getClipState() > 0)
					{
						AffineTransform3D clipTransform = new AffineTransform3D();
						converter.getClipTransform(clipTransform);
						final double [] transform = new double [12];
						clipTransform.toArray(transform);
						writer.write("ClipTransform");
						for (int m = 0; m<12; m++)
						{
							writer.write("," + df3.format(transform[m]));
						}
						writer.write("\n");
					}
					
					
				}
				writer.write("End of BigTrace View\n");
				writer.close();
			}

			bt.btPanel.progressBar.setString("saving view done.");
		} catch (IOException e) {	
			IJ.log(e.getMessage());
			//e.printStackTrace();
		}
		bt.bInputLock = false;
		bt.setLockMode(false);
	}	
	/** loading current view from csv **/
	public static < T extends RealType< T > & NativeType< T > > void loadView(final BigTrace<T> bt, String sFilename)
	{
		String[] line_array;
        bt.bInputLock = true;
        bt.setLockMode(true);
        int nSourcesN = bt.bvv_sources.size();
        
        String [] sigmaTraceName = new String [] {"sigmaTraceX", "sigmaTraceY", "sigmaTraceZ"};
		try ( BufferedReader br = new BufferedReader(new FileReader(sFilename));) 
		{			       
			String line = "";
			
			//initial part check
			line = br.readLine();
			line_array = line.split(",");
			if(!line_array[0].equals("BigTrace_View"))
			{
				System.err.println("Not a BigTrace View file format, aborting");
				return;
			}
			
			if(!line_array[2].equals(BigTraceData.sVersion))
			{
				System.out.println("Version mismatch: ROI file "+line_array[2]+", plugin " + BigTraceData.sVersion + 
						". It should be fine in theory, so loading view anyway.");
			}		
			//second line
			line = br.readLine();
			line_array = line.split(",");
			if(!line_array[1].equals(bt.btData.sFileNameFullImg))
			{
				if (JOptionPane.showConfirmDialog(null, "The view was stored for " + line_array[1] +
						" file.\nCurrently " + bt.btData.sFileNameFullImg + 
						" file is loaded.\nDo you want to load it anyway?", "Filename mismatch",
				        JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) 
				{
				    // no
					return;
				} 
			}
			//read the rest
			
		    line = br.readLine();
			while (line != null) 
			{				
				// process the line.
				line_array = line.split(",");
				switch (line_array[0])
				{
				case "Render Type":
					bt.btPanel.setRenderMethod( Integer.parseInt( line_array[1]) );
					break;
				case "Canvas BG color":					
					final Color bgColor = new Color(Integer.parseInt(line_array[1]));
					bt.btPanel.setCanvasBGColor( bgColor );
					break;
				case "nHalfClickSizeWindow":	
					bt.btData.nHalfClickSizeWindow =  Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nHalfClickSizeWindow", bt.btData.nHalfClickSizeWindow);
					break;
				case "bStartFullScreen":	
					bt.btData.bStartFullScreen =  line_array[1].equals( "1" )? true : false;
					Prefs.set("BigTrace.bStartFullScreen", bt.btData.bStartFullScreen);
					break;
				case "Camera":	
					bt.btPanel.setCameraParameters(line_array[1], line_array[2], line_array[3]);
					break;					
				case "Zoom Box Size":
					bt.btData.nZoomBoxSize =  Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nZoomBoxSize", bt.btData.nZoomBoxSize);
					break;
				case "Zoom Box Fraction":
					bt.btData.dZoomBoxScreenFraction =  Double.parseDouble( line_array[1] );
					Prefs.set("BigTrace.dZoomBoxScreenFraction", bt.btData.dZoomBoxScreenFraction);
					break;
				case "bROIDoubleClickClip":
					bt.btData.bROIDoubleClickClip = line_array[1].equals( "1" )? true : false;
					Prefs.set("BigTrace.bROIDoubleClickClip", bt.btData.bROIDoubleClickClip );
					break;
				case "nROIDoubleClickClipExpand":
					bt.btData.nROIDoubleClickClipExpand =  Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nROIDoubleClickClipExpand", bt.btData.nROIDoubleClickClipExpand);
					break;
				case "Intensity Interpolation":
					bt.btData.intensityInterpolation =  Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.IntInterpolation",bt.btData.intensityInterpolation);
					bt.btData.setInterpolationFactory();
					break;
				case "ROI Shape Interpolation":
					bt.btData.shapeInterpolation =  Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.ShapeInterpolation",bt.btData.shapeInterpolation);
					break;
				case "Rotation min frame type":
					bt.btData.rotationMinFrame = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.RotationMinFrame",bt.btData.rotationMinFrame);
					break;
				case "Smooth window":
					bt.btData.nSmoothWindow = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nSmoothWindow", bt.btData.nSmoothWindow);
					break;
				case "Sector number":
					bt.btData.sectorN = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nSectorN", bt.btData.sectorN);
					break;
				case "wireCountourStep":
					bt.btData.wireCountourStep = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.wireCountourStep", bt.btData.wireCountourStep);
					break;
				case "crossSectionGridStep":
					bt.btData.crossSectionGridStep = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.crossSectionGridStep", bt.btData.crossSectionGridStep);
					break;
				case "surfaceRender":
					bt.btData.surfaceRender = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.surfaceRender", bt.btData.surfaceRender);
					break;
				case "silhouetteRender":
					bt.btData.silhouetteRender = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.silhouetteRender", bt.btData.silhouetteRender);
					break;
				case "silhouetteDecay":
					bt.btData.silhouetteDecay = Double.parseDouble( line_array[1] );
					Prefs.set("BigTrace.silhouetteDecay", bt.btData.silhouetteDecay);
					break;
				case "wireAntiAliasing":
					bt.btData.wireAntiAliasing = line_array[1].equals( "1" )? true : false;
					Prefs.set("BigTrace.wireAntiAliasing", bt.btData.wireAntiAliasing);
					break;
				case "timeRender":
					bt.btData.timeRender = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.timeRender", bt.btData.timeRender);
					break;
				case "timeFade":
					bt.btData.timeFade = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.timeFade", bt.btData.timeFade);
					break;
				case "nTraceBoxSize":
					bt.btData.nTraceBoxSize = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nTraceBoxSize", bt.btData.nTraceBoxSize);
					break;	
				case "fTraceBoxScreenFraction":
					bt.btData.fTraceBoxScreenFraction = Float.parseFloat( line_array[1] );
					Prefs.set("BigTrace.fTraceBoxScreenFraction", bt.btData.fTraceBoxScreenFraction);
					break;	
				case "fTraceBoxAdvanceFraction":
					bt.btData.fTraceBoxAdvanceFraction = Float.parseFloat( line_array[1] );
					Prefs.set("BigTrace.fTraceBoxAdvanceFraction", bt.btData.fTraceBoxAdvanceFraction);
					break;						
				case "nVertexPlacementPointN":
					bt.btData.nVertexPlacementPointN = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nVertexPlacementPointN", bt.btData.nVertexPlacementPointN);
					break;	
				case "dDirectionalityOneClick":
					bt.btData.dDirectionalityOneClick = Double.parseDouble( line_array[1] );
					Prefs.set("BigTrace.dDirectionalityOneClick", bt.btData.dDirectionalityOneClick);
					break;					
				case "bOCIntensityStop":
					bt.btData.bOCIntensityStop = line_array[1].equals( "1" )? true : false;
					Prefs.set("BigTrace.bOCIntensityStop", bt.btData.bOCIntensityStop);
					break;
				case "dOCIntensityThreshold":
					bt.btData.dOCIntensityThreshold = Double.parseDouble( line_array[1] );
					Prefs.set("BigTrace.dOCIntensityThreshold", bt.btData.dOCIntensityThreshold);
					break;	
				case "Trace sigma":
					int dA = Integer.parseInt( line_array[1]);
					bt.btData.sigmaTrace[dA] = Double.parseDouble( line_array[2] ) ;
					Prefs.set("BigTrace."+sigmaTraceName[dA], bt.btData.sigmaTrace[dA]);
					break;	
				case "bEstimateROIThicknessFromParams":	
					bt.btData.bEstimateROIThicknessFromParams =  line_array[1].equals( "1" )? true : false;
					Prefs.set("BigTrace.bEstimateROIThicknessFromParams", bt.btData.bEstimateROIThicknessFromParams);
					break;
				case "dTraceROIThicknessCoeff":
					bt.btData.dTraceROIThicknessCoeff = Double.parseDouble( line_array[1] );
					Prefs.set("BigTrace.dTraceROIThicknessCoeff", bt.btData.dTraceROIThicknessCoeff);
					break;
				case "nTraceROIThicknessMode":
					bt.btData.nTraceROIThicknessMode = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nTraceROIThicknessMode", bt.btData.nTraceROIThicknessMode);
					break;	
				case "bOneClickUseMask":	
					bt.btData.bOneClickUseMask =  line_array[1].equals( "1" )? true : false;
					Prefs.set("BigTrace.bOneClickUseMask", bt.btData.bOneClickUseMask);
					break;	
				case "bTrackingUseMask":	
					bt.btData.bTrackingUseMask =  line_array[1].equals( "1" )? true : false;
					Prefs.set("BigTrace.bTrackingUseMask", bt.btData.bTrackingUseMask);
					break;	
				case "dAutoMinStartTraceInt":
					final int dAutoMinStartTraceInt = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.dAutoMinStartTraceInt", dAutoMinStartTraceInt);
					break;	
				case "nAutoMinPointsCurve":
					final int nAutoMinPointsCurve = Integer.parseInt( line_array[1] );
					Prefs.set("BigTrace.nAutoMinPointsCurve", nAutoMinPointsCurve);
					break;		
					
					
				case "Scene":
					 final Scene scLoad = new Scene();
					 scLoad.load( br );
					 bt.setScene( scLoad );
					break;
				case "Source":
					int nSource = Integer.parseInt( line_array[1] ) - 1 ;
					if(nSource + 1 > nSourcesN)
						{break;}
					BvvStackSource< ? > btS = bt.bvv_sources.get( nSource );
					line = br.readLine();
					line_array = line.split(",");
					btS.setDisplayRangeBounds( Double.parseDouble( line_array[1] ), Double.parseDouble( line_array[2] ));
					line = br.readLine();
					line_array = line.split(",");
					btS.setDisplayRange( Double.parseDouble( line_array[1] ), Double.parseDouble( line_array[2] ));
					line = br.readLine();
					line_array = line.split(",");
					btS.setDisplayGammaRangeBounds( Double.parseDouble( line_array[1] ), Double.parseDouble( line_array[2] ));
					line = br.readLine();
					line_array = line.split(",");
					btS.setDisplayGamma( Double.parseDouble( line_array[1] ));
					line = br.readLine();
					line_array = line.split(",");
					btS.setAlphaRangeBounds( Double.parseDouble( line_array[1] ), Double.parseDouble( line_array[2] ));
					line = br.readLine();
					line_array = line.split(",");
					btS.setAlphaRange( Double.parseDouble( line_array[1] ), Double.parseDouble( line_array[2] ));
					line = br.readLine();
					line_array = line.split(",");
					btS.setAlphaGammaRangeBounds( Double.parseDouble( line_array[1] ), Double.parseDouble( line_array[2] ));
					line = br.readLine();
					line_array = line.split(",");
					btS.setAlphaGamma( Double.parseDouble( line_array[1] ));
					line = br.readLine();
					line_array = line.split(",");
					ARGBType valARGB = new ARGBType(Integer.parseInt( line_array[1] ));
					btS.setColor(valARGB);
					line = br.readLine();
					line_array = line.split(",");
					int nLutSize = Integer.parseInt( line_array[1] );
					line = br.readLine();
					line_array = line.split(",");
					if(nLutSize > 0)
						btS.setLUT( line_array[1] );
					line = br.readLine();
					line_array = line.split(",");
					btS.setVoxelRenderInterpolation( Integer.parseInt( line_array[1] ) );
					line = br.readLine();
					line_array = line.split(",");
					final int nClipState = Integer.parseInt( line_array[1]);
					if(nClipState > 0 )
					{
						btS.setClipState( nClipState );
						line = br.readLine();
						line_array = line.split(",");
						final double [] transform = new double [12];
						for(int m=0;m<12;m++)
						{
							transform[m] = Double.parseDouble( line_array[m+1] );
						}
						final AffineTransform3D af = new AffineTransform3D();
						af.set( transform );
						btS.setClipTransform(af);
					}
					break;
				}

				line = br.readLine();
			}

			br.close();
			bt.btPanel.progressBar.setString("loading view done.");
			bt.repaintBVV();
			bt.roiManager.updateROIsDisplay();
		}
		//catching errors in file opening
		catch (FileNotFoundException e) {
			System.err.print(e.getMessage());
		}	        
		catch (IOException e) {
			System.err.print(e.getMessage());
		}
    	bt.bInputLock = false;
        bt.setLockMode(false);
	}
}
