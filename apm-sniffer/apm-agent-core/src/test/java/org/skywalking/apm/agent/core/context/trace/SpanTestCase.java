package org.skywalking.apm.agent.core.context.trace;

import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.util.KeyValuePair;
import org.skywalking.apm.agent.core.context.util.KeyValuePairReader;
import org.skywalking.apm.agent.core.context.util.TraceSegmentHelper;
import org.skywalking.apm.agent.core.dictionary.ApplicationDictionary;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ApplicationDictionary.class)
public class SpanTestCase {

    @Test
    public void testConstructors() {
        AbstractTracingSpan span1 = new LocalSpan(0, -1, "serviceA");
        AbstractTracingSpan span2 = new LocalSpan(1, 0, "serviceA");
        span2.setOperationName("serviceA-2");
        span1.start();
        span2.start();
        Assert.assertEquals("serviceA-2", span2.getOperationName());

        Assert.assertEquals(-1, span1.parentSpanId);
        Assert.assertEquals(0, span2.parentSpanId);
        Assert.assertTrue(span1.startTime > 0);
        Assert.assertTrue(span2.startTime > 0);
    }

    @Test
    public void testFinish() {
        TraceSegment owner = new TraceSegment();

        AbstractTracingSpan span1 = new LocalSpan(0, -1, "serviceA");

        Assert.assertTrue(span1.endTime == 0);

        span1.finish(owner);
        Assert.assertEquals(span1, TraceSegmentHelper.getSpans(owner).get(0));
        Assert.assertTrue(span1.endTime > 0);
    }

    @Test
    public void testSetTag() {
        AbstractTracingSpan span1 = new LocalSpan(0, -1, "serviceA");
        SpanLayer.asHttp(span1);
        span1.setComponent("Spring");
        span1.errorOccurred();
        Tags.STATUS_CODE.set(span1, "505");
        Tags.URL.set(span1, "http://127.0.0.1/serviceA");
        Tags.DB_STATEMENT.set(span1, "select * from users");

        Assert.assertEquals(SpanLayer.HTTP, span1.layer);
        Assert.assertTrue(span1.errorOccurred);
    }

    @Test
    public void testLogException() throws NoSuchFieldException, IllegalAccessException {
        AbstractTracingSpan span1 = new LocalSpan(0, -1, "serviceA");
        Exception exp = new Exception("exception msg");
        span1.log(exp);

        LogDataEntity logs = span1.logs.get(0);
        List<KeyValuePair> keyValuePairs = logs.getLogs();

        Assert.assertEquals("java.lang.Exception", KeyValuePairReader.get(keyValuePairs, "error.kind"));
        Assert.assertEquals("exception msg", KeyValuePairReader.get(keyValuePairs, "message"));
        Assert.assertNotNull(KeyValuePairReader.get(keyValuePairs, "stack"));
    }
}
