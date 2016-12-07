package com.a.eye.skywalking.toolkit.log.log4j.v1.x;


import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternParser;

/**
 * Created by wusheng on 2016/12/7.
 */
public class TraceIdPatternLayout extends PatternLayout {
    @Override
    protected PatternParser createPatternParser(String pattern) {
        return new TraceIdPatternParser(pattern);
    }
}
