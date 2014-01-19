/*
 * #%L
 * VisBio plugins for ImageJ.
 * %%
 * Copyright (C) 2011 - 2014 Board of Regents of the University of
 * Wisconsin-Madison.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package loci.visbio;

import java.awt.Adjustable;

import javax.swing.JScrollBar;

/**
 * A slider widget for selecting a double-precision value between certain
 * bounds.
 */
public class DoubleSlider extends JScrollBar {

	private double min, max, step;

	public DoubleSlider(final double value, final double min, final double max,
		final double step)
	{
		super(Adjustable.HORIZONTAL, valueToIndex(value, min, step), 1, 0,
			valueToIndex(max, min, step) + 1);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public double getDoubleValue() {
		return indexToValue(getValue());
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getStep() {
		return step;
	}

	public void setDoubleValue(final double value) {
		setValue(valueToIndex(value));
	}

	public void setMin(final double min) {
		final double value = getDoubleValue();
		this.min = min;
		setDoubleValue(value);
	}

	public void setMax(final double max) {
		this.max = max;
		super.setMaximum(valueToIndex(max));
	}

	public void setStep(final double step) {
		final double value = getDoubleValue();
		this.step = step;
		setDoubleValue(value);
	}

	// -- Helper methods --

	private int valueToIndex(final double value) {
		return valueToIndex(value, min, step);
	}

	private double indexToValue(final int index) {
		return indexToValue(index, min, step);
	}

	private static int valueToIndex(final double value, final double offset,
		final double step)
	{
		return (int) ((value - offset) / step);
	}

	private static double indexToValue(final int index, final double offset,
		final double step)
	{
		return index * step + offset;
	}

}
