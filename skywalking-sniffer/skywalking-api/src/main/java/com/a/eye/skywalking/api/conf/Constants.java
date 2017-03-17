package com.a.eye.skywalking.api.conf;

import com.a.eye.skywalking.trace.GlobalIdGenerator;

public class Constants {
	/**
	 * This is the version, which will be the first segment of traceid.
	 * Ref {@link GlobalIdGenerator#generate()}
	 */
	public final static String SDK_VERSION = "302017";
}
