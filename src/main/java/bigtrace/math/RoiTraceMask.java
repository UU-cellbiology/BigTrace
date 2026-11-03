package bigtrace.math;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.NativeBoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import bigtrace.BigTrace;
import bigtrace.BigTraceBGTask;
import bigtrace.rois.Roi3D;

public class RoiTraceMask < T extends RealType< T > & NativeType< T > > extends BigTraceBGTask
{
    final BigTrace<T> bt;
    
    public IntervalView< T > traceInterval = null;
    public Interval maxSearchInterval = null;
    boolean bMaskInit = false;
    
    IntervalView< NativeBoolType > traceMask;
    IntervalView< NativeBoolType > traceMaskExtended;
    
    IntervalView< T > startTraceInterval;
    IntervalView< NativeBoolType > startTraceMask;
    
    RandomAccess< NativeBoolType > raMask;
    OutOfBounds< NativeBoolType > raExtended;
    
    public OneClickTrace <T> oneclick = null;
    
    /** BLOCK MAX CACHE (speeds up findMaskedMax).
     * maxSearchInterval is split into cubic blocks; for each block 
     * the maximum of unmasked voxels (and its position) is cached.
     * Marking the mask flags touched blocks as dirty, 
     * only dirty blocks are re-scanned on the next findMaskedMax call. **/
    static final int BLOCK_SIZE = 32;
    final long [] blockGridMin = new long[3];
    final long [] blockGridMax = new long[3];
    final int [] nBlocks = new int[3];
    double [] blockMaxVal;   // -Double.MAX_VALUE if block fully masked
    long [] blockMaxPos;     // flat 3 * N
    boolean [] blockDirty;
    
    
    /** masks larger than this (in bytes) are disk-cached, smaller ones are kept in memory **/
    static final long MAX_IN_MEMORY_MASK_BYTES = 512L * 1024L * 1024L;
    
    /** cell size of the mask image **/
    static final int MASK_CELL_SIZE = 32;
    
    public RoiTraceMask(final BigTrace<T> bt_)
	{
		bt = bt_;
    }
    
	@Override
	protected Void doInBackground() throws Exception
	{
		initTraceMask ( traceInterval, maxSearchInterval, true);
		return null;
	}
	
	/** inits tracing mask.
	 * traceInterval_ is the tracing interval
	 * and optionally maxSearchInterval_ (subregion of traceInterval) can be provided **/
    public void initTraceMask (final IntervalView< T > traceInterval_, final Interval maxSearchInterval_, boolean bReportProgress)
    {
    	//current data
    	traceInterval = traceInterval_;
    	final long[] dim = traceInterval.dimensionsAsLongArray();
    	//make a mask
    	final Img<NativeBoolType> maskArr = createMaskImg( dim );
        	
    	traceMask = Views.translate(maskArr, traceInterval.minAsLongArray());
    	if(maxSearchInterval_ == null)
    	{
	    	maxSearchInterval = traceInterval;
	    	startTraceMask = Views.interval( traceMask, maxSearchInterval );
	    	startTraceInterval = Views.interval( traceInterval, maxSearchInterval );
    	}
    	else
    	{
	    	maxSearchInterval = maxSearchInterval_;
	    	startTraceMask = Views.interval( traceMask, maxSearchInterval );
	    	startTraceInterval = Views.interval( traceInterval, maxSearchInterval );
    	}
    	//fill mask with current ROI shapes
    	final ArrayList<Roi3D> rois = bt.roiManager.rois;
    	int nTotROIs = 0;   	
    	int nCurrRoi = 0;
    	if(bReportProgress)
    	{
    		setProgressState("Creating ROI mask..");
        	
          	for (final Roi3D roi : rois) 
        	{
        		 if (roi.getTimePoint() == bt.btData.nCurrTimepoint)
        		 {
        			 nTotROIs++;
        		 }
        	}
    	}
    	bMaskInit = true;
    	for (final Roi3D roi : rois) 
    	{
    		 if (roi.getTimePoint() == bt.btData.nCurrTimepoint)
    		 {
    			 markROI(roi);
    			 if(bReportProgress)
    			 {
    				 nCurrRoi++;
    				 setProgress( 100 * nCurrRoi / nTotROIs );
    			 }
    		 }
    	}
		
    	raExtended = Views.extendValue( traceMask, true ).randomAccess();
    	raMask = traceMask.randomAccess();
    	initBlockCache();
    	if(bReportProgress)
		{
    		setProgressState("ROI mask created.");
		}
    }
    
    public boolean isOccupied(final RealPoint point)
    {    	
    	//see if the point inside the interval
     	if(bMaskInit)
    	{
     		final long [] posLong = new long[3];
     		for(int d = 0; d < 3; d++)
     		{
     			posLong[d] = Math.round( point.getFloatPosition( d ));
     		}
     		return isOccupied(posLong);
    	}
		System.err.println("auto-trace mask was not initialized!");
		return true;
    }
    
    /** fast method, does not check if the mask is initialized 
     * and fills the out-of-boundaries as occupied **/
    public boolean isOccupied(final long[] posLong)
    {
    	raExtended.setPosition( posLong );
    	return raExtended.get().get();
    }
    
    public void markLocation(final RealPoint point)
    {
     	//see if the point inside the interval
     	if(bMaskInit)
    	{
     		if(!Intervals.contains( traceMask, point ))
     			return;
     		final long [] posLong = new long[3];
     		for(int d = 0; d < 3; d++)
     		{
     			posLong[d] = Math.round( point.getFloatPosition( d ));
     		}
     		raMask.setPosition( posLong );
     		raMask.get().set( true );    
     		markBlockDirty( posLong );
     		return;
     	
    	}
		System.err.println("auto-trace mask was not initialized!");
		return;
    }
    
    public void markInterval(final Interval inputInt)
    {
     	//see if the point inside the interval
     	if(bMaskInit)
    	{
     		final Interval markedInt = Intervals.intersect( inputInt, traceMask );
     		if(Intervals.isEmpty( markedInt ))
     			return;
     		final Cursor< NativeBoolType > markInt = Views.interval( traceMask, markedInt ).cursor();
     		while(markInt.hasNext())
     		{
     			markInt.next().set( true );
     		}
     		markBlocksDirty( markedInt );
     		return;
    	}
		System.err.println("auto-trace mask was not initialized!");
		return;
    }
    
    /** marks ROI in the current mask.
     * does not check timepoint **/
    public void markROI(final Roi3D roi)
    {
    	if(bMaskInit)
    	{
    		final Cursor< NativeBoolType > cursorRoi = roi.getSingle3DVolumeCursor( traceMask );
    		
    		if(cursorRoi != null)
    		{
    			final long [] pos = new long[3];
    			cursorRoi.reset();
    			while ( cursorRoi.hasNext() )
    			{
    				cursorRoi.fwd();
   					cursorRoi.get().set( true );
   					cursorRoi.localize( pos );
   					markBlockDirty( pos );
    			}
    		}
    	}
    	else
    	{
    		System.err.println("auto-trace mask was not initialized!");
    	}
    }
    
    void initBlockCache()
    {
    	int nTotBlocks = 1;
    	for(int d = 0; d < 3; d++)
    	{
    		blockGridMin[d] = maxSearchInterval.min( d );
    		blockGridMax[d] = maxSearchInterval.max( d );
    		nBlocks[d] = (int) ((maxSearchInterval.dimension( d ) + BLOCK_SIZE - 1) / BLOCK_SIZE);
    		nTotBlocks *= nBlocks[d];
    	}
    	blockMaxVal = new double[nTotBlocks];
    	blockMaxPos = new long[3 * nTotBlocks];
    	blockDirty = new boolean[nTotBlocks];
    	java.util.Arrays.fill( blockDirty, true );
    }
    
    /** marks the block containing position as dirty (ignored if outside maxSearchInterval) **/
    void markBlockDirty(final long [] pos)
    {
    	int nInd = 0;
    	for(int d = 2; d >= 0; d--)
    	{
    		if(pos[d] < blockGridMin[d] || pos[d] > blockGridMax[d])
    			return;
    		nInd = nInd * nBlocks[d] + (int)((pos[d] - blockGridMin[d]) / BLOCK_SIZE);
    	}
    	blockDirty[nInd] = true;
    }
    
    /** marks all blocks overlapping the interval as dirty **/
    void markBlocksDirty(final Interval interval)
    {
    	final int [] bMin = new int[3];
    	final int [] bMax = new int[3];
    	for(int d = 0; d < 3; d++)
    	{
    		final long min = Math.max( interval.min( d ), blockGridMin[d] );
    		final long max = Math.min( interval.max( d ), blockGridMax[d] );
    		if(min > max)
    			return;
    		bMin[d] = (int)((min - blockGridMin[d]) / BLOCK_SIZE);
    		bMax[d] = (int)((max - blockGridMin[d]) / BLOCK_SIZE);
    	}
    	for(int z = bMin[2]; z <= bMax[2]; z++)
    		for(int y = bMin[1]; y <= bMax[1]; y++)
    			for(int x = bMin[0]; x <= bMax[0]; x++)
    				blockDirty[x + nBlocks[0] * (y + nBlocks[1] * z)] = true;
    }
    
    /** recomputes max values of all dirty blocks (in parallel) **/
    void updateDirtyBlocks()
    {
    	final int [] dirtyInd = IntStream.range( 0, blockDirty.length ).filter( i -> blockDirty[i] ).toArray();
    	if(dirtyInd.length == 0)
    		return;
    	if(dirtyInd.length == 1)
    		updateBlock(dirtyInd[0]);
    	else
    		IntStream.of( dirtyInd ).parallel().forEach( this::updateBlock );
    }
    
    /** scans one block and stores max of unmasked voxels **/
    void updateBlock(final int nInd)
    {
    	final long [] min = new long[3];
    	final long [] max = new long[3];
    	int nRem = nInd;
    	for(int d = 0; d < 3; d++)
    	{
    		final int bi = nRem % nBlocks[d];
    		nRem /= nBlocks[d];
    		min[d] = blockGridMin[d] + (long)bi * BLOCK_SIZE;
    		max[d] = Math.min( min[d] + BLOCK_SIZE - 1, blockGridMax[d] );
    	}
    	final FinalInterval blockInt = new FinalInterval(min, max);
    	
    	//flatIterable guarantees the same iteration order for both
    	final Cursor< T > cursor = Views.flatIterable( Views.interval( traceInterval, blockInt ) ).localizingCursor();
    	final Cursor< NativeBoolType > cursorMask = Views.flatIterable( Views.interval( traceMask, blockInt ) ).cursor();
    	
    	double maxVal = (-1) * Double.MAX_VALUE;
    	final long [] maxPos = new long[3];
    	double currVal;
    	while(cursor.hasNext())
    	{
    		cursor.fwd();
    		if(!cursorMask.next().get())
    		{
    			currVal = cursor.get().getRealDouble();
    			if(currVal > maxVal)
    			{
    				maxVal = currVal;
    				cursor.localize( maxPos );
    			}
    		}
    	}
    	blockMaxVal[nInd] = maxVal;
    	for(int d = 0; d < 3; d++)
    		blockMaxPos[3 * nInd + d] = maxPos[d];
    	blockDirty[nInd] = false;
    }
    
    public ValuePair<Double, RealPoint> findMaskedMax()
    {
    	double maxVal = (-1) * Double.MAX_VALUE;
    	final double [] maxLocation = new double[3];
    	if(bMaskInit)
    	{
    		updateDirtyBlocks();
    		int nMaxBlock = -1;
    		for(int i = 0; i < blockMaxVal.length; i++)
    		{
    			if(blockMaxVal[i] > maxVal)
    			{
    				maxVal = blockMaxVal[i];
    				nMaxBlock = i;
    			}
    		}
    		if(nMaxBlock >= 0)
    		{
    			for(int d = 0; d < 3; d++)
    				maxLocation[d] = blockMaxPos[ 3 * nMaxBlock + d ];
    		}
    	}
    	else
    	{
    		System.err.println("auto-trace mask was not initialized!");
    	}
    	return new ValuePair< >(maxVal, new RealPoint(maxLocation));
    }
    
    public ValuePair<Double, RealPoint> findMaskedMaxFull()
    {
    	final double [] maxLocation = new double[3];
    	double maxVal = (-1) * Double.MAX_VALUE;
    	if(bMaskInit)
    	{
    		// create a cursor for the image (the order does not matter)
    		final Cursor< T > cursor = startTraceInterval.localizingCursor();
    		final Cursor< NativeBoolType > cursorMask = startTraceMask.localizingCursor();

    		T currVal;
    		while(cursor.hasNext())
    		{
    			cursor.fwd();    			
    			if(!cursorMask.next().get())
    			{ 
    				currVal = cursor.get();
    				if(currVal.getRealDouble() > maxVal)
    				{
    					maxVal = currVal.getRealDouble();
    					cursor.localize( maxLocation );
    				}
    			}
    			
    		}
    	}
    	else
    	{
    		System.err.println("auto-trace mask was not initialized!");
    	}
    	return new ValuePair< >(maxVal, new RealPoint(maxLocation));
    }
    
    /** creates mask image: an in-memory CellImg if it is not larger than
     * MAX_IN_MEMORY_MASK_BYTES, otherwise a disk-cached one **/
    static Img<NativeBoolType> createMaskImg(final long [] dim)
    {
    	//NativeBoolType is stored as one byte per voxel
    	final long nMaskBytes = Intervals.numElements( dim );
    	if(nMaskBytes <= MAX_IN_MEMORY_MASK_BYTES)
    	{
    		return new CellImgFactory<>(new NativeBoolType(), MASK_CELL_SIZE).create( dim );
    	}
    	final DiskCachedCellImgOptions options = DiskCachedCellImgOptions.options()
    			.cellDimensions(MASK_CELL_SIZE, MASK_CELL_SIZE, MASK_CELL_SIZE);
    	return new DiskCachedCellImgFactory<>(new NativeBoolType(), options).create( dim );
    }

	
	@Override
	public void done() 
	{
		//see if we have some errors
		try {

			get();
		} 
		catch (ExecutionException e) 
		{
			e.getCause().printStackTrace();
			String msg = String.format("Unexpected problem during one-click tracing: %s", 
					e.getCause().toString());
			System.out.println(msg);
		} 
		catch (InterruptedException e) 
		{
			// Process e here
		}
		catch (Exception e)
		{
			//System.out.println("Tracing interrupted by user.");
			setProgressState("Tracing interrupted by user.");
			setProgress(100);	
		}
		if(oneclick != null)
		{
			oneclick.traceMask = this;
			oneclick.execute();
		}

	}
}
