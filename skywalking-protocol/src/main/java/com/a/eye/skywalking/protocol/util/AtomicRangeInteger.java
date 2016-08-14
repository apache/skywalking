package com.a.eye.skywalking.protocol.util;

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
	 * @param startValue
	 *            the initial value
	 * @param maxValue
	 * 
	 * AtomicRangeInteger在startValue和maxValue循环取值（ startValue <= value <  maxValue）
	 */
	public AtomicRangeInteger(int startValue, int maxValue) {
		value = new AtomicInteger(startValue);
		this.startValue = startValue;
		this.endValue = maxValue - 1;
	}

	/**
	 * Atomically increments by one the current value.
	 *
	 * @return the previous value
	 */
	public final int getAndIncrement() {
		for (;;) {
			int current = value.get();
			int next = current >= this.endValue ? this.startValue : current + 1;
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
