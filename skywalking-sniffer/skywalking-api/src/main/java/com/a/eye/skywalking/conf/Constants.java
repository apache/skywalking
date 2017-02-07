package com.a.eye.skywalking.conf;

import com.a.eye.skywalking.util.TraceIdGenerator;

public class Constants {
	/**
	 * This is the version, which will be the first segment of traceid.
	 * Ref {@link TraceIdGenerator#generate()}
	 */
	public static int SDK_VERSION = 212017;

    public static final String CONTEXT_DATA_SEGMENT_SPILT_CHAR = "#&";
}
