package org.skywalking.apm.agent.core.context;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.agent.core.tags.BooleanTagReader;
import org.skywalking.apm.agent.core.tags.IntTagReader;
import org.skywalking.apm.agent.core.tags.StringTagReader;
import org.skywalking.apm.trace.tag.BooleanTag;
import org.skywalking.apm.trace.tag.IntTag;
import org.skywalking.apm.trace.tag.StringTag;

/**
 * @author wusheng
 */
public class LeafSpanTestCase {
    @Test
    public void testLeaf() {
        LeafSpan span = new LeafSpan(0, "serviceA", System.currentTimeMillis());
        span.push();
        span.setOperationName("serviceA2");
        span.setTag("key", "value-text");
        span.setTag("key2", false);
        span.setTag("key3", 1);

        //start 2nd span
        span.push();

        Assert.assertFalse(span.isFinished());
        Assert.assertTrue(span.isLeaf());
        span.setOperationName("service123");
        span.setTag("key", "value-text2");
        span.setTag("key2", true);
        span.setTag("key3", 2);
        Assert.assertEquals("serviceA2", span.getOperationName());
        Assert.assertEquals("value-text", StringTagReader.get(span, new StringTag("key")));
        Assert.assertFalse(BooleanTagReader.get(span, new BooleanTag("key2", false)));
        Assert.assertEquals(1, IntTagReader.get(span, new IntTag("key3")).intValue());

        //end 2nd span
        span.pop();

        span.pop();
        Assert.assertTrue(span.isFinished());
    }
}
