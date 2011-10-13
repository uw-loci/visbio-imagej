//
// OrthoStack.java
//

/*
VisBio plugins for ImageJ.

Copyright (c) 2011, UW-Madison LOCI
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the UW-Madison LOCI nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

package loci.visbio;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.rmi.RemoteException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;
import visad.ConstantMap;
import visad.DataReference;
import visad.DataReferenceImpl;
import visad.Display;
import visad.GraphicsModeControl;
import visad.ImageFlatField;
import visad.ProjectionControl;
import visad.RealType;
import visad.ScalarMap;
import visad.VisADException;
import visad.bom.ImageRendererJ3D;
import visad.java3d.DisplayImplJ3D;
import visad.java3d.MouseBehaviorJ3D;

/**
 * Visualizes a stack of image planes in an aligned parallel projection.
 * 
 * @author Curtis Rueden
 */
public class OrthoStack extends JPanel implements PlugIn, AdjustmentListener,
	WindowListener
{

	private static final double INITIAL_STRETCH = 50;
	private static final double MIN_STRETCH = 1;
	private static final double MAX_STRETCH = 1000;
	private static final double STEP_STRETCH = 0.5;

	private static final double INITIAL_ANGLE = 75;
	private static final double MIN_ANGLE = 0;
	private static final double MAX_ANGLE = 90;
	private static final double STEP_ANGLE = 0.2;

	private static final double INITIAL_ZOOM = 0.75;
	private static final double MIN_ZOOM = 0.01;
	private static final double MAX_ZOOM = 1.5;
	private static final double STEP_ZOOM = 0.002;

	private ImagePlus imp;
	private DisplayImplJ3D display;

	private double physicalX, physicalY, physicalZ;
	private double physicalMax;

	private DoubleSlider stretchSlider;
	private DoubleSlider angleSlider;
	private DoubleSlider zoomSlider;

	public ImagePlus getImagePlus() {
		return imp;
	}

	public DisplayImplJ3D getDisplay() {
		return display;
	}

	public void setImagePlus(final ImagePlus imp) throws VisADException,
		RemoteException
	{
		this.imp = imp;
		final int sizeX = imp.getWidth();
		final int sizeY = imp.getHeight();
		final int sizeC = imp.getNChannels();
		final int sizeZ = imp.getNSlices();
		final int sizeT = imp.getNFrames();
		final int[] lengths = { sizeC, sizeZ, sizeT };

		final int posC = imp.getChannel() - 1;
		final int posZ = imp.getSlice() - 1;
		final int posT = imp.getFrame() - 1;
		final int[] pos = { posC, posZ, posT };

		// create VisAD display
		if (display != null) display.destroy();
		display = new DisplayImplJ3D("display");
		final GraphicsModeControl gmc = display.getGraphicsModeControl();
		gmc.setProjectionPolicy(DisplayImplJ3D.PARALLEL_PROJECTION);

		// compute initial aspect ratio
		physicalX = imp.getCalibration().getX(sizeX);
		physicalY = imp.getCalibration().getY(sizeY);
		physicalZ = imp.getCalibration().getZ(sizeZ);
		physicalMax = Math.max(Math.max(physicalX, physicalY), physicalZ);
		final double aspectX = physicalX / physicalMax;
		final double aspectY = physicalY / physicalMax;
		final double aspectZ = physicalZ / physicalMax;
		final double[] aspect = new double[] { aspectX, aspectY, aspectZ };
		final ProjectionControl pc = display.getProjectionControl();
		pc.setAspectCartesian(aspect);

		// add planes to display
		final ImageStack stack = imp.getStack();
		for (int z = 0; z < sizeZ; z++) {
			pos[1] = z;

			// convert plane to BufferedImage
			final int n = positionToRaster(pos, lengths) + 1;
			final ImageProcessor ip = stack.getProcessor(n);
			final Image image = ip.createImage();
			final BufferedImage bufImage = makeBuffered(image);
			final BufferedImage goodImage = ImageFlatField.make3ByteRGB(bufImage);

			// convert BufferedImage to VisAD field
			final ImageFlatField field = new ImageFlatField(goodImage);

			// add display mappings
			if (z == 0) addMaps(field);

			// add field to display
			final DataReference ref = new DataReferenceImpl("ref" + z);
			ref.setData(field);
			final double zPlane = aspectZ * 2 * z / (sizeZ - 1) - aspectZ;
			final ConstantMap[] cmaps = { new ConstantMap(zPlane, Display.ZAxis) };
			display.addReferences(new ImageRendererJ3D(), ref, cmaps);
		}

		// create stretch slider
		final JLabel stretchLabel = new JLabel("Stretch");
		stretchSlider =
			new DoubleSlider(INITIAL_STRETCH, MIN_STRETCH, MAX_STRETCH, STEP_STRETCH);
		stretchSlider.addAdjustmentListener(this);

		// create angle slider
		final JLabel angleLabel = new JLabel("Angle");
		angleSlider =
			new DoubleSlider(INITIAL_ANGLE, MIN_ANGLE, MAX_ANGLE, STEP_ANGLE);
		angleSlider.addAdjustmentListener(this);

		// create zoom slider
		final JLabel zoomLabel = new JLabel("Zoom");
		zoomSlider = new DoubleSlider(INITIAL_ZOOM, MIN_ZOOM, MAX_ZOOM, STEP_ZOOM);
		zoomSlider.addAdjustmentListener(this);

		// build a panel to house the sliders
		final JPanel sliderPanel = new JPanel();
		final String layout = "fillx,wrap 2";
		final String cols = "[pref|200px,fill,grow]";
		final String rows = "[pref|pref|pref]";
		sliderPanel.setLayout(new MigLayout(layout, cols, rows));
		sliderPanel.add(stretchLabel);
		sliderPanel.add(stretchSlider);
		sliderPanel.add(angleLabel);
		sliderPanel.add(angleSlider);
		sliderPanel.add(zoomLabel);
		sliderPanel.add(zoomSlider);

		// add components to main panel
		setLayout(new BorderLayout());
		add(display.getComponent(), BorderLayout.CENTER);
		add(sliderPanel, BorderLayout.EAST);

		updateProjection();
	}

	// -- PlugIn methods --

	@Override
	public void run(final String arg) {
		final ImagePlus currentImage = WindowManager.getCurrentImage();
		if (currentImage == null) {
			IJ.showMessage("No Image", "There are no images open.");
			return;
		}

		try {
			setImagePlus(currentImage);
		}
		catch (final VisADException exc) {
			IJ.handleException(exc);
		}
		catch (final RemoteException exc) {
			IJ.handleException(exc);
		}

		// add ortho stack to a window frame
		final JFrame frame = new JFrame(getImagePlus().getTitle());
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.addWindowListener(this);
		frame.setContentPane(this);

		// size frame and display onscreen
		frame.pack();
		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setSize(frame.getWidth(), 9 * screenSize.height / 10);
		frame.setVisible(true);
	}

	// -- AdjustmentListener methods --

	@Override
	public void adjustmentValueChanged(final AdjustmentEvent e) {
		updateProjection();
	}

	// -- WindowListener methods --

	@Override
	public void windowActivated(final WindowEvent e) {
		// no action needed
	}

	@Override
	public void windowClosed(final WindowEvent e) {
		try {
			display.destroy();
			display = null;
		}
		catch (final RemoteException exc) {
			IJ.handleException(exc);
		}
		catch (final VisADException exc) {
			IJ.handleException(exc);
		}
	}

	@Override
	public void windowClosing(final WindowEvent e) {
		// no action needed
	}

	@Override
	public void windowDeactivated(final WindowEvent e) {
		// no action needed
	}

	@Override
	public void windowDeiconified(final WindowEvent e) {
		// no action needed
	}

	@Override
	public void windowIconified(final WindowEvent e) {
		// no action needed
	}

	@Override
	public void windowOpened(final WindowEvent e) {
		// no action needed
	}

	// -- Helper methods --

	private void addMaps(final ImageFlatField field) throws VisADException,
		RemoteException
	{
		final RealType[] xy = field.getDomainTypes();
		final RealType[] v = field.getRangeTypes();
		display.addMap(new ScalarMap(xy[0], Display.XAxis));
		display.addMap(new ScalarMap(xy[1], Display.YAxis));
		if (v.length == 3) {
			display.addMap(new ScalarMap(v[0], Display.Red));
			display.addMap(new ScalarMap(v[1], Display.Green));
			display.addMap(new ScalarMap(v[2], Display.Blue));
		}
		else {
			for (int i = 0; i < v.length; i++) {
				display.addMap(new ScalarMap(v[i], Display.RGB));
			}
		}
	}

	/**
	 * Creates a buffered image from the given AWT image object. If the AWT image
	 * is already a buffered image, no new object is created.
	 */
	private BufferedImage makeBuffered(final Image image) {
		if (image instanceof BufferedImage) return (BufferedImage) image;

		loadImage(image);
		final BufferedImage img =
			new BufferedImage(image.getWidth(this), image.getHeight(this),
				BufferedImage.TYPE_INT_RGB);
		final Graphics g = img.getGraphics();
		g.drawImage(image, 0, 0, this);
		g.dispose();
		return img;
	}

	/** Ensures the given AWT image is fully loaded. */
	private boolean loadImage(final Image image) {
		if (image instanceof BufferedImage) return true;
		final MediaTracker tracker = new MediaTracker(this);
		tracker.addImage(image, 0);
		try {
			tracker.waitForID(0);
		}
		catch (final InterruptedException exc) {
			return false;
		}
		if (MediaTracker.COMPLETE != tracker.statusID(0, false)) return false;
		return true;
	}

	private void updateProjection() {
		final double stretch = stretchSlider.getDoubleValue();
		final double angle = angleSlider.getDoubleValue();
		final double zoom = zoomSlider.getDoubleValue();

		final double rotX = angle, rotY = 0, rotZ = 0;
		final double scale = zoom, scaleZ = scale * stretch;
		final double transX = 0, transY = 0, transZ = 0;
		final double[] matrix =
			MouseBehaviorJ3D.static_make_matrix(rotX, rotY, rotZ, scale, scale,
				scaleZ, transX, transY, transZ);
		try {
			display.getProjectionControl().setMatrix(matrix);
		}
		catch (final VisADException exc) {
			exc.printStackTrace();
		}
		catch (final RemoteException exc) {
			exc.printStackTrace();
		}
	}

	/**
	 * Computes a unique 1-D index corresponding to the given multidimensional
	 * position.
	 * 
	 * @param pos position along each dimensional axis
	 * @param lengths the maximum value for each positional dimension
	 * @return rasterized index value
	 */
	public static int positionToRaster(final int[] pos, final int... lengths) {
		int offset = 1;
		int raster = 0;
		for (int i = 0; i < pos.length; i++) {
			raster += offset * pos[i];
			offset *= lengths[i];
		}
		return raster;
	}

	/**
	 * Computes a unique N-D position corresponding to the given rasterized index
	 * value.
	 * 
	 * @param raster rasterized index value
	 * @param lengths the maximum value at each positional dimension
	 * @return position along each dimensional axis
	 */
	public static int[] rasterToPosition(final int raster, final int... lengths) {
		return rasterToPosition(new int[lengths.length], raster, lengths);
	}

	/**
	 * Computes a unique N-D position corresponding to the given rasterized index
	 * value.
	 * 
	 * @param pos preallocated position array to populate with the result
	 * @param raster rasterized index value
	 * @param lengths the maximum value at each positional dimension
	 * @return position along each dimensional axis
	 */
	public static int[] rasterToPosition(final int[] pos, final int raster,
		final int... lengths)
	{
		int offset = 1;
		int r = raster;
		for (int i = 0; i < pos.length; i++) {
			final int nextOffset = offset * lengths[i];
			final int q = i < pos.length - 1 ? r % nextOffset : r;
			pos[i] = q / offset;
			r -= q;
			offset = nextOffset;
		}
		return pos;
	}

}
