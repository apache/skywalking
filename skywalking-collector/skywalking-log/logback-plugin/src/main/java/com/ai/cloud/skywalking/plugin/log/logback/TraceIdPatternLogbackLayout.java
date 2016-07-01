package com.ai.cloud.skywalking.plugin.log.logback;
import ch.qos.logback.classic.PatternLayout;
/**
 * 
 * @author yushuqiang
 *
 */
public class TraceIdPatternLogbackLayout extends PatternLayout {
    static {
        defaultConverterMap.put("tid",LogbackPatternConverter.class.getName());
    }
}