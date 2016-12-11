package com.a.eye.skywalking.toolkit.log4j.v2.x;


import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

/**
 * Created by wusheng on 2016/12/7.
 */
@Plugin(name = "TraceIdConverter", category = "Converter")
@ConverterKeys({"tid"})
public class TraceIdConverter extends LogEventPatternConverter {

    /**
     * Constructs an instance of LoggingEventPatternConverter.
     *
     * @param name  name of converter.
     * @param style CSS style for output.
     */
    protected TraceIdConverter(String name, String style) {
        super(name, style);
    }

    public static TraceIdConverter newInstance(String[] options) {
        return new TraceIdConverter("tid", "tid");
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        Log4j2OutputAppender.append(toAppendTo);
    }
}
