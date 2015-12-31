package com.ai.cloud.skywalking.plugin.log.log4j.v1.x;


import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternParser;

public class TraceIdPatternLayout extends PatternLayout {
    @Override
    protected PatternParser createPatternParser(String pattern) {
        return new TraceIdPatternParser(pattern);
    }
}
