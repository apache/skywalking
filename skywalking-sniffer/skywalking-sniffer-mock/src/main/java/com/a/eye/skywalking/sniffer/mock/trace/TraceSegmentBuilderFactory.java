package com.a.eye.skywalking.sniffer.mock.trace;

import com.a.eye.skywalking.sniffer.mock.trace.builders.SingleTomcat200TraceBuilder;
import com.a.eye.skywalking.trace.TraceSegment;
import java.util.HashMap;
import java.util.Map;

/**
 * The <code>TraceSegmentBuilderFactory</code> contains all {@link TraceSegmentBuilder} implementations.
 * All the implementations can build a true {@link TraceSegment} object, and contain all necessary spans, with all tags/events, all refs.
 *
 * Created by wusheng on 2017/2/20.
 */
public enum TraceSegmentBuilderFactory {
    INSTANCE;

    private Map<String, TraceSegmentBuilder> allBuilders;

    TraceSegmentBuilderFactory(){
        allBuilders = new HashMap<String, TraceSegmentBuilder>();
        initialize();
    }

    public TraceSegment singleTomcat200Trace(){
        return allBuilders.get(SingleTomcat200TraceBuilder.INSTANCE).build();
    }

    private void initialize(){
        initBuilder(SingleTomcat200TraceBuilder.INSTANCE);
    }

    private void initBuilder(TraceSegmentBuilder builder){
        allBuilders.put(builder.toString(), builder);
    }
}
