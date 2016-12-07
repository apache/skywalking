package com.a.eye.skywalking.plugin.log.log4j.v1.x;

import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Created by wusheng on 2016/12/7.
 */

public class TraceIdPatternConverter extends PatternConverter {
    @Override
    protected String convert(LoggingEvent loggingEvent) {
        return "TID: N/A";
    }
}
