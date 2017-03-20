package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.tools.HttpClientTools;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.trace.tag.Tags;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;

/**
 * @author pengys5
 */
public class SegmentPostTestCase {

    @Test
    public void testPostSegment() throws Exception {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        TraceSegment webSegment = new TraceSegment("WebApplication");

        Span span = new Span(0, "/Web/GetUser", new Date().getTime());
        Tags.SPAN_LAYER.asHttp(span);
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
        Tags.URL.set(span, "http://10.218.9.86:8080/Web/GetUser");
        Tags.STATUS_CODE.set(span, 200);
        Tags.COMPONENT.set(span, "Tomcat");
        Tags.PEERS.set(span, "202.135.4.12");
        Thread.sleep(300);
        webSegment.archive(span);
        Thread.sleep(300);

        Span span1 = new Span(1, span, "com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)", new Date().getTime());
        Tags.SPAN_LAYER.asRPCFramework(span1);
        Tags.URL.set(span1, "motan://10.20.3.15:3000/com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)");
        Tags.SPAN_KIND.set(span1, Tags.SPAN_KIND_CLIENT);
        Tags.COMPONENT.set(span1, "Motan");
        Tags.PEERS.set(span1, "10.20.3.15:3000");
        Thread.sleep(300);
        webSegment.archive(span1);
        Thread.sleep(300);

        Thread.sleep(300);
        webSegment.finish();
        Thread.sleep(300);

        String webJsonStr = gson.toJson(webSegment);
        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", webJsonStr);

        TraceSegment motanSegment = new TraceSegment("MotanServiceApplication");

        TraceSegmentRef segmentRef = new TraceSegmentRef();
        segmentRef.setApplicationCode("WebApplication");
        segmentRef.setPeerHost("10.20.3.15:3000");
        segmentRef.setTraceSegmentId(webSegment.getTraceSegmentId());
        motanSegment.ref(segmentRef);

        Span span2 = new Span(0, "com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)", new Date().getTime());
        Tags.SPAN_LAYER.asRPCFramework(span2);
        Tags.SPAN_KIND.set(span2, Tags.SPAN_KIND_SERVER);
        Tags.URL.set(span2, "motan://10.20.3.15:3000/com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)");
        Tags.COMPONENT.set(span2, "Motan");
        Tags.PEERS.set(span2, "10.218.9.86");
        Thread.sleep(300);
        motanSegment.archive(span2);
        Thread.sleep(300);

        Span span3 = new Span(1, span2, "com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)", new Date().getTime());
        Tags.SPAN_LAYER.asDB(span3);
        Tags.SPAN_KIND.set(span3, Tags.SPAN_KIND_CLIENT);
        Tags.COMPONENT.set(span3, "Mysql");
        Tags.PEERS.set(span3, "10.5.34.18");
        Thread.sleep(300);
        motanSegment.archive(span3);
        Thread.sleep(300);

        Thread.sleep(300);
        motanSegment.finish();
        Thread.sleep(300);

        String motanJsonStr = gson.toJson(motanSegment);
        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", motanJsonStr);
    }
}
