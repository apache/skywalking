package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.trace.tag.Tags;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/18.
 */
public class SpanTestCase {
    @Test
    public void testConstructors() {
        Span span1 = new Span(0, "serviceA");
        Span span2 = new Span(2, span1, "serviceA");
        span2.setOperationName("serviceA-2");
        Assert.assertEquals("serviceA-2", span2.getOperationName());

        Assert.assertEquals(-1, span1.getParentSpanId());
        Assert.assertEquals(0, span2.getParentSpanId());
        Assert.assertTrue(span1.getStartTime() > 0);
        Assert.assertTrue(span2.getStartTime() > 0);
    }

    @Test
    public void testFinish() {
        TraceSegment owner = new TraceSegment("trace_1", "billing_app");

        Span span1 = new Span(0, "serviceA");

        Assert.assertTrue(span1.getEndTime() == 0);

        span1.finish(owner);
        Assert.assertEquals(span1, owner.getSpans().get(0));
        Assert.assertTrue(span1.getEndTime() > 0);
    }

    @Test
    public void testSetTag() {
        Span span1 = new Span(0, "serviceA");
        Tags.SPAN_LAYER.asHttp(span1);
        Tags.COMPONENT.set(span1, "Spring");
        Tags.PEER_HOST.set(span1, "127.0.0.1");
        Tags.ERROR.set(span1, true);
        Tags.STATUS_CODE.set(span1, 302);
        Tags.URL.set(span1, "http://127.0.0.1/serviceA");
        Tags.DB_STATEMENT.set(span1, "select * from users");

        Map<String, Object> tags = span1.getTags();
        Assert.assertEquals(8, tags.size());
        Assert.assertTrue(Tags.SPAN_LAYER.isHttp(span1));
        Assert.assertEquals("127.0.0.1", Tags.PEER_HOST.get(span1));
        Assert.assertTrue(Tags.ERROR.get(span1));
    }

    @Test
    public void testLogException(){
        Span span1 = new Span(0, "serviceA");
        Exception exp = new Exception("exception msg");
        span1.log(exp);
        List<LogData> logs = span1.getLogs();

        Assert.assertEquals("java.lang.Exception", logs.get(0).getFields().get("error.kind"));
        Assert.assertEquals("exception msg", logs.get(0).getFields().get("message"));
        Assert.assertNotNull(logs.get(0).getFields().get("stack"));
    }
}
