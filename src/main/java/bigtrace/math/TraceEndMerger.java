package bigtrace.math;

import java.util.ArrayList;

import bigtrace.BigTrace;
import bigtrace.rois.LineTrace3D;
import bigtrace.rois.Roi3D;

import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;

/** Merging of a new trace with an existing line trace that continues it across a junction
 * (auto-tracing): a junction passage of the new trace ends at the end of the other trace,
 * and the end direction of the other trace points back along the passage. **/
public class TraceEndMerger
{
	/** max angle (degrees) between the passage heading and the reversed end direction
	 * of the other trace **/
	double dMaxAngle = 35.0;

	/** another trace to merge with the current one **/
	static class MergeRequest
	{
		final LineTrace3D roi;
		/** true: first vertex of roi is the matching end, false: last vertex **/
		final boolean bFirstEnd;
		MergeRequest(final LineTrace3D roi_, final boolean bFirstEnd_)
		{
			roi = roi_;
			bFirstEnd = bFirstEnd_;
		}
	}

	/** merges found during the current tracing **/
	final ArrayList<MergeRequest> requests = new ArrayList<>();

	public void clear()
	{
		requests.clear();
	}

	/** Looks for the end of another line trace (timepoint nTP, not current) near the passage
	 * (currpoint followed by path, full resolution), within dR, whose outward end direction
	 * (over nK points) points back along the passage heading (within dMaxAngle).
	 * If found, requests the merge and returns the number of path points to keep
	 * (up to the one closest to that end), otherwise -1. **/
	public int findMergeEnd(final BigTrace<?> bt, final LineTrace3D current, final int nTP, final double dR, final int nK,
			final RealPoint currpoint, final ArrayList<RealPoint> path)
	{
		final ArrayList<double[]> pts = new ArrayList<>();
		pts.add(currpoint.positionAsDoubleArray());
		for(final RealPoint p : path)
		{
			pts.add(p.positionAsDoubleArray());
		}
		final double dCosMax = Math.cos(Math.toRadians(dMaxAngle));

		double dBestDist = Double.MAX_VALUE;
		int nBestK = -1;
		LineTrace3D bestRoi = null;
		boolean bBestFirst = true;
		final double [] tOut = new double[3];
		final double [] heading = new double[3];
		for(final Roi3D roi : new ArrayList<>(bt.roiManager.rois))
		{
			if(roi == current || roi.getType() != Roi3D.LINE_TRACE || roi.getTimePoint() != nTP)
				continue;
			boolean bAlready = false;
			for(final MergeRequest m : requests)
			{
				if(m.roi == roi)
					bAlready = true;
			}
			if(bAlready)
				continue;
			final ArrayList<double[]> js = ((LineTrace3D) roi).makeJointSegmentDouble();
			if(js == null || js.size() < 3)
				continue;
			for(int nEnd = 0; nEnd < 2; nEnd++)
			{
				final boolean bFirst = (nEnd == 0);
				final double [] end = bFirst ? js.get(0) : js.get(js.size() - 1);
				final double [] inner = bFirst ? js.get(Math.min(nK, js.size() - 1)) : js.get(Math.max(js.size() - 1 - nK, 0));
				LinAlgHelpers.subtract(end, inner, tOut);
				if(LinAlgHelpers.length(tOut) < 1e-6)
					continue;
				LinAlgHelpers.normalize(tOut);
				//closest passage point
				int nK0 = 0;
				double dMin = Double.MAX_VALUE;
				for(int k = 0; k < pts.size(); k++)
				{
					final double dist = LinAlgHelpers.distance(pts.get(k), end);
					if(dist < dMin)
					{
						dMin = dist;
						nK0 = k;
					}
				}
				if(dMin > dR || dMin >= dBestDist)
					continue;
				//passage heading at that point
				LinAlgHelpers.subtract(pts.get(nK0), pts.get(Math.max(nK0 - nK, 0)), heading);
				if(LinAlgHelpers.length(heading) < 1e-6)
					continue;
				LinAlgHelpers.normalize(heading);
				if(LinAlgHelpers.dot(heading, tOut) > -dCosMax)
					continue;
				dBestDist = dMin;
				nBestK = nK0;
				bestRoi = (LineTrace3D) roi;
				bBestFirst = bFirst;
			}
		}
		if(bestRoi == null)
			return -1;
		requests.add(new MergeRequest(bestRoi, bBestFirst));
		//pts index 0 is the current point, path points 1..nBestK are kept
		return nBestK;
	}

	/** merges the requested traces into current: current oriented with the merge end last,
	 * the other one with its matching end first, connected by a straight segment;
	 * the other ROI is removed **/
	public void applyMerges(final BigTrace<?> bt, final LineTrace3D current)
	{
		if(requests.isEmpty() || current == null || current.vertices.size() < 2)
			return;
		if(!bt.roiManager.rois.contains(current))
			return;
		for(final MergeRequest m : requests)
		{
			final LineTrace3D other = m.roi;
			if(!bt.roiManager.rois.contains(other) || other.vertices.size() < 2)
				continue;
			final double [] otherEnd = m.bFirstEnd ? other.vertices.get(0).positionAsDoubleArray()
					: other.vertices.get(other.vertices.size() - 1).positionAsDoubleArray();
			final double dFirst = LinAlgHelpers.distance(current.vertices.get(0).positionAsDoubleArray(), otherEnd);
			final double dLast = LinAlgHelpers.distance(current.vertices.get(current.vertices.size() - 1).positionAsDoubleArray(), otherEnd);
			if(dFirst < dLast)
			{
				current.reversePoints();
			}
			if(!m.bFirstEnd)
			{
				other.reversePoints();
			}
			//connecting segment
			final ArrayList<RealPoint> bridge = new ArrayList<>();
			bridge.add(new RealPoint(current.vertices.get(current.vertices.size() - 1)));
			bridge.add(new RealPoint(other.vertices.get(0)));
			current.addPointAndSegmentNoUpdate(other.vertices.get(0), bridge);
			for(int i = 0; i < other.segments.size(); i++)
			{
				current.addPointAndSegmentNoUpdate(other.vertices.get(i + 1), new ArrayList<>(other.segments.get(i)));
			}
			bt.roiManager.removeRoi(other);
		}
		current.updateRenderVertices();
		requests.clear();
	}
}
