package com.a.eye.skywalking.toolkit.log.logback.v1.x;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Created by wusheng on 2016/12/7.
 */
public class LogbackPatternConverter extends ClassicConverter {
    /**
     * As default, return "TID: N/A" to the output message,
     * if sky-walking agent in active mode, return the real traceId in the recent Context, if existed.
     *
     * @param iLoggingEvent
     * @return the traceId: N/A, empty String, or the real traceId.
     */
    @Override
    public String convert(ILoggingEvent iLoggingEvent) {
        return "TID: N/A";
    }
}
