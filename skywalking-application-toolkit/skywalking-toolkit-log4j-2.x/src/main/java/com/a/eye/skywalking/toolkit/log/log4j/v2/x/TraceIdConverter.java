package com.a.eye.skywalking.toolkit.log.log4j.v2.x;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

/**
 * {@link TraceIdConverter} is a log4j2 plugin, by annotation as {@link Plugin}.
 * It convert the pattern key: traceId.
 * Use '%traceId' in log4j2's config: <PatternLayout pattern="%d [%traceId] %-5p %c{1}:%L - %m%n"/>,
 * '%traceId' will output as TID:xxxx
 *
 * Created by wusheng on 2016/12/7.
 */
@Plugin(name = "TraceIdConverter", category = "Converter")
@ConverterKeys({"traceId"})
public class TraceIdConverter extends LogEventPatternConverter {

    /**
     * Constructs an instance of LoggingEventPatternConverter.
     *
     * @param name name of converter.
     * @param style CSS style for output.
     */
    protected TraceIdConverter(String name, String style) {
        super(name, style);
    }

    public static TraceIdConverter newInstance(String[] options) {
        return new TraceIdConverter("traceId", "traceId");
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        Log4j2OutputAppender.append(toAppendTo);
    }
}
