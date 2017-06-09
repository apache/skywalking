package org.skywalking.apm.trace;

import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.trace.tag.BooleanTagReader;
import org.skywalking.apm.trace.tag.StringTagReader;
import org.skywalking.apm.trace.tag.Tags;

import java.util.List;

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
        TraceSegment owner = new TraceSegment("billing_app");

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
        span1.setPeerHost("127.0.0.1");
        Tags.ERROR.set(span1, true);
        Tags.STATUS_CODE.set(span1, 302);
        Tags.URL.set(span1, "http://127.0.0.1/serviceA");
        Tags.DB_STATEMENT.set(span1, "select * from users");

        Assert.assertEquals("http", StringTagReader.get(span1, Tags.SPAN_LAYER.SPAN_LAYER_TAG));
        Assert.assertEquals("127.0.0.1", span1.getPeerHost());
        Assert.assertTrue(BooleanTagReader.get(span1, Tags.ERROR));
    }

    @Test
    public void testLogException() throws NoSuchFieldException, IllegalAccessException {
        Span span1 = new Span(0, "serviceA");
        Exception exp = new Exception("exception msg");
        span1.log(exp);

        Field logsField = Span.class.getDeclaredField("logs");
        logsField.setAccessible(true);
        List<LogData> logs = (List<LogData>)logsField.get(span1);

        Assert.assertEquals("java.lang.Exception", logs.get(0).getFields().get("error.kind"));
        Assert.assertEquals("exception msg", logs.get(0).getFields().get("message"));
        Assert.assertNotNull(logs.get(0).getFields().get("stack"));
    }
}
