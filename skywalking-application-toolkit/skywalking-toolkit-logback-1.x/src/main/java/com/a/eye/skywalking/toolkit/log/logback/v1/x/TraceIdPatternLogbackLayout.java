package com.a.eye.skywalking.toolkit.log.logback.v1.x;

import ch.qos.logback.classic.PatternLayout;

/**
 * Based on the logback-compoenent convert register mechanism,
 * register {@link LogbackPatternConverter} as a new convert, match to "tid".
 * You can use "%tid" in logback config file, "Pattern" section.
 * <p>
 * Created by wusheng on 2016/12/7.
 */
public class TraceIdPatternLogbackLayout extends PatternLayout {
    static {
        defaultConverterMap.put("tid", LogbackPatternConverter.class.getName());
    }
}
