package com.a.eye.skywalking.toolkit.log.log4j.v1.x;


import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggingEvent;

/**
 * The log4j extend pattern.
 * By using this pattern, if sky-walking agent is also active, {@link PatternParser#finalizeConverter(char)} method will be override dynamic.
 * <p>
 * Created by wusheng on 2016/12/7.
 */
public class TraceIdPatternLayout extends PatternLayout {
    @Override
    protected PatternParser createPatternParser(String pattern) {
        return new TraceIdPatternParser(pattern);
    }
}
