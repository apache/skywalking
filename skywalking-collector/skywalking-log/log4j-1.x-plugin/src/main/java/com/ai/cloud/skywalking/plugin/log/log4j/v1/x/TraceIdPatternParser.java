package com.ai.cloud.skywalking.plugin.log.log4j.v1.x;

import org.apache.log4j.helpers.PatternParser;

/**
 * Created by astraea on 2015/12/31.
 */
public class TraceIdPatternParser extends PatternParser {
    public TraceIdPatternParser(String pattern) {
        super(pattern);
    }

    @Override
    protected void finalizeConverter(char c) {
        if ('x' == c) {
            addConverter(new TraceIdPatternConverter());
        } else {
            super.finalizeConverter(c);
        }
    }
}
