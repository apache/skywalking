package com.ai.cloud.skywalking.plugin.log.log4j.v1.x;

import com.ai.cloud.skywalking.api.Tracing;
import com.ai.cloud.skywalking.conf.AuthDesc;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.spi.LoggingEvent;

public class TraceIdPatternConverter extends PatternConverter {
    @Override
    protected String convert(LoggingEvent loggingEvent) {
        if (AuthDesc.isAuth()) {
            return "TID:" + Tracing.getTraceId();
        }

        return "TID: N/A";
    }
}
