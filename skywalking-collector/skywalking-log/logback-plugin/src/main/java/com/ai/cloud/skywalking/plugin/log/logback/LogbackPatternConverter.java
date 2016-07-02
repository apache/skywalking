package com.ai.cloud.skywalking.plugin.log.logback;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import com.ai.cloud.skywalking.api.Tracing;
import com.ai.cloud.skywalking.conf.AuthDesc;
/**
 * 
 * @author yushuqiang
 *
 */
public class LogbackPatternConverter extends ClassicConverter {

	@Override
	public String convert(ILoggingEvent event) {
		if (AuthDesc.isAuth()) {
			return "TID:" + Tracing.getTraceId();
		}

		return "TID: N/A";
	}
}