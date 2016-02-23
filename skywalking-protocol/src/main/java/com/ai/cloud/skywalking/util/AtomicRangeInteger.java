package com.ai.cloud.skywalking.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 原子性，带范围性自增的整数
 * 
 * @author wusheng
 *
 */
public class AtomicRangeInteger extends Number implements java.io.Serializable {
	private static final long serialVersionUID = -4099792402691141643L;

	private AtomicInteger value;

	private int startValue;
	private int endValue;

	/**
	 * Creates a new AtomicInteger with the given initial value and max value
	 *
	 * @param initialValue
	 *            the initial value
	 */
	public AtomicRangeInteger(int startValue, int endValue) {
		value = new AtomicInteger(startValue);
		this.startValue = startValue;
		this.endValue = endValue;
	}

	/**
	 * Atomically increments by one the current value.
	 *
	 * @return the previous value
	 */
	public final int getAndIncrement() {
		for (;;) {
			int current = get();
			int next = current + 1;
			if (next >= this.endValue) {
				next = this.startValue;
			}
			if (value.compareAndSet(current, next))
				return current;
		}
	}

	public final int get() {
		return value.get();
	}

	public int intValue() {
		return value.intValue();
	}

	public long longValue() {
		return value.longValue();
	}

	public float floatValue() {
		return value.floatValue();
	}

	public double doubleValue() {
		return value.doubleValue();
	}
}
