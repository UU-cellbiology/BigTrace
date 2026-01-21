package mcu;


import java.util.function.IntConsumer;

import net.imglib2.realtransform.AffineTransform3D;

import org.janelia.saalfeldlab.control.ButtonControl;
import org.janelia.saalfeldlab.control.VPotControl;
import org.janelia.saalfeldlab.control.mcu.MCUControlPanel;


import bdv.viewer.Interpolation;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.AbstractViewerPanel.AlignPlane;
import bvvpg.core.VolumeViewerPanel;

public class MCUBVVControls
{
	/**
	 * One step of rotation (radian).
	 */
	final private static double step = Math.PI / 180;

	private final VolumeViewerPanel viewerPanel;
	public MCUBVVControls(final VolumeViewerPanel viewer, final MCUControlPanel panel) {

		this.viewerPanel = viewer;

		/* add handlers */
		VPotControl control = panel.getVPotControl(0);
		control.setAbsolute(false);
		control.setMinMax(-10, 10);
		control.setDisplayType(VPotControl.DISPLAY_TRIM);
		control.addListener(new VPotAxisShiftHandler(0));

		control = panel.getVPotControl(1);
		control.setAbsolute(false);
		control.setMinMax(-10, 10);
		control.setDisplayType(VPotControl.DISPLAY_TRIM);
		control.addListener(new VPotAxisShiftHandler(1));

		control = panel.getVPotControl(2);
		control.setAbsolute(false);
		control.setMinMax(-10, 10);
		control.setDisplayType(VPotControl.DISPLAY_TRIM);
		control.addListener(new VPotAxisShiftHandler(2));

		control = panel.getVPotControl(3);
		control.setAbsolute(false);
		control.setMinMax(-10, 10);
		control.setDisplayType(VPotControl.DISPLAY_TRIM);
		control.addListener(new VPotAxisRotationHandler(0));

		control = panel.getVPotControl(4);
		control.setAbsolute(false);
		control.setMinMax(-10, 10);
		control.setDisplayType(VPotControl.DISPLAY_TRIM);
		control.addListener(new VPotAxisRotationHandler(1));

		control = panel.getVPotControl(5);
		control.setAbsolute(false);
		control.setMinMax(-10, 10);
		control.setDisplayType(VPotControl.DISPLAY_TRIM);
		control.addListener(new VPotAxisRotationHandler(2));

		control = panel.getVPotControl(6);
		control.setAbsolute(false);
		control.setMinMax(-10, 10);
		control.setDisplayType(VPotControl.DISPLAY_TRIM);
		control.addListener(new VPotZoomHandler());

		ButtonControl key = panel.getButtonControl(0);
		key.setToggle(true);
		key.addListener(new InterpolationSwitcher());

		key = panel.getButtonControl(18);
		key.setToggle(false);
		key.addListener(i -> {
			if (i != 0) viewer.align(AlignPlane.ZY);
		});

		key = panel.getButtonControl(19);
		key.setToggle(false);
		key.addListener(i -> {
			if (i != 0) viewer.align(AlignPlane.XZ);
		});

		key = panel.getButtonControl(20);
		key.setToggle(false);
		key.addListener(i -> {
			if (i != 0) viewer.align(AlignPlane.XY);
		});

		System.out.println(viewerPanel.getRootPane().getParent());
	}
	public class VPotAxisRotationHandler implements IntConsumer {

		private final int axis;

		public VPotAxisRotationHandler(final int axis) {

			this.axis = axis;
		}

		@Override
		public void accept(final int value) {

			final SynchronizedViewerState state = viewerPanel.state();
			final AffineTransform3D viewerTransform = state.getViewerTransform();

			// center shift
			final double cX = 0.5 * viewerPanel.getWidth();
			final double cY = 0.5 * viewerPanel.getHeight();
			viewerTransform.set(viewerTransform.get( 0, 3 ) - cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) - cY, 1, 3);

			// rotate
			viewerTransform.rotate(axis, value * step);

			// center un-shift
			viewerTransform.set(viewerTransform.get( 0, 3 ) + cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) + cY, 1, 3);

			state.setViewerTransform(viewerTransform);
		}
	}

	public class VPotAxisShiftHandler implements IntConsumer {

		private final int axis;

		public VPotAxisShiftHandler(final int axis) {

			this.axis = axis;
		}

		@Override
		public void accept(final int value) {

			final SynchronizedViewerState state = viewerPanel.state();
			final AffineTransform3D viewerTransform = state.getViewerTransform();

			viewerTransform.set(viewerTransform.get( axis, 3 ) + value, axis, 3);

			state.setViewerTransform(viewerTransform);
		}
	}

	public class VPotZoomHandler implements IntConsumer {

		@Override
		public void accept(final int value) {

			final SynchronizedViewerState state = viewerPanel.state();
			final AffineTransform3D viewerTransform = state.getViewerTransform();

			// center shift
			final double cX = 0.5 * viewerPanel.getWidth();
			final double cY = 0.5 * viewerPanel.getHeight();
			viewerTransform.set(viewerTransform.get( 0, 3 ) - cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) - cY, 1, 3);

			// rotate
			final double dScale = 1.0 + 0.05;
			viewerTransform.scale(Math.pow(dScale, value));

			// center un-shift
			viewerTransform.set(viewerTransform.get( 0, 3 ) + cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) + cY, 1, 3);

			state.setViewerTransform(viewerTransform);
		}
	}

	public class InterpolationSwitcher implements IntConsumer {

		@Override
		public void accept(final int value) {

			final SynchronizedViewerState state = viewerPanel.state();
			state.setInterpolation(value == 0 ? Interpolation.NEARESTNEIGHBOR : Interpolation.NLINEAR);
		}
	}
}