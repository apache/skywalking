package com.ai.cloud.skywalking.plugin.log.log4j.v1.x;

import com.ai.cloud.skywalking.api.Tracing;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.spi.LoggingEvent;

public class TraceIdPatternConverter extends PatternConverter {
    @Override
    protected String convert(LoggingEvent loggingEvent) {
        return "TID:" + Tracing.getTraceId();
    }
}
