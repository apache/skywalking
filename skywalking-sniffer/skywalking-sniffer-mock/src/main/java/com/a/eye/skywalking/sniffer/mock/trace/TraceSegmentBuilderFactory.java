package com.a.eye.skywalking.sniffer.mock.trace;

import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.trace.builders.trace.SingleTomcat200TraceBuilder;
import com.a.eye.skywalking.sniffer.mock.trace.builders.trace.SingleTomcat404TraceBuilder;
import com.a.eye.skywalking.sniffer.mock.trace.builders.trace.SingleTomcat500TraceBuilder;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * The <code>TraceSegmentBuilderFactory</code> contains all {@link TraceSegmentBuilder} implementations.
 * All the implementations can build a true {@link TraceSegment} object, and contain all necessary spans, with all tags/events, all refs.
 *
 * Created by wusheng on 2017/2/20.
 */
public enum TraceSegmentBuilderFactory {
    INSTANCE;

    /**
     * @see {@link SingleTomcat200TraceBuilder}
     *
     * @return
     */
    public TraceSegment singleTomcat200Trace(){
       return this.build(SingleTomcat200TraceBuilder.INSTANCE);
    }

    /**
     * @see {@link SingleTomcat404TraceBuilder}
     *
     * @return
     */
    public TraceSegment singleTomcat404Trace(){
        return this.build(SingleTomcat404TraceBuilder.INSTANCE);
    }

    /**
     * @see {@link SingleTomcat500TraceBuilder}
     *
     * @return
     */
    public TraceSegment singleTomcat500Trace(){
        return this.build(SingleTomcat500TraceBuilder.INSTANCE);
    }

    private TraceSegment build(TraceSegmentBuilder builder){
        MockTracerContextListener listener = new MockTracerContextListener();
        try{
            TracerContext.ListenerManager.add(listener);
            return builder.build(listener);
        }finally{
            TracerContext.ListenerManager.remove(listener);
        }
    }

}
