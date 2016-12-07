package com.a.eye.skywalking.toolkit.logback.v1.x;

import ch.qos.logback.classic.PatternLayout;

/**
 * Created by wusheng on 2016/12/7.
 */
public class TraceIdPatternLogbackLayout extends PatternLayout {
    static {
        defaultConverterMap.put("tid", LogbackPatternConverter.class.getName());
    }
}
