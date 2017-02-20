package com.a.eye.skywalking.sniffer.mock.trace.builders;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilder;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;

/**
 * A Trace contains only one span, which represent a tomcat server side span.
 *
 * Created by wusheng on 2017/2/20.
 */
public enum SingleTomcat200TraceBuilder implements TraceSegmentBuilder {
    INSTANCE;

    @Override public TraceSegment build() {
        Span webSpan = ContextManager.INSTANCE.createSpan("/web/serviceA");
        {
            Tags.COMPONENT.set(webSpan, "tomcat");
            Tags.URL.set(webSpan, "http://10.21.9.35/web/serviceA");
            Tags.SPAN_KIND.set(webSpan, Tags.SPAN_KIND_SERVER);
            Tags.STATUS_CODE.set(webSpan, 200);
        }
        return null;
    }
}
