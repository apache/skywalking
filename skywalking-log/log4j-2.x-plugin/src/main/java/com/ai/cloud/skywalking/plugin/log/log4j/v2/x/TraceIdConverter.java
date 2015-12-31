package com.ai.cloud.skywalking.plugin.log.log4j.v2.x;

import com.ai.cloud.skywalking.api.Tracing;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

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
        toAppendTo.append("TId:" + Tracing.getTraceId());
    }
}
