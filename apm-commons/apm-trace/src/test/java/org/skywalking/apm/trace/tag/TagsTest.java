package org.skywalking.apm.trace.tag;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.trace.Span;

/**
 * @author wusheng
 */
public class TagsTest {
    @Test
    public void testLayer() {
        Span span = new Span(1, "/test");
        Tags.SPAN_LAYER.asDB(span);
        Assert.assertEquals("db", StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG));

        span = new Span(1, "/test");
        Tags.SPAN_LAYER.asRPCFramework(span);
        Assert.assertEquals("rpc", StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG));

        span = new Span(1, "/test");
        Tags.SPAN_LAYER.asHttp(span);
        Assert.assertEquals("http", StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG));
    }

    @Test
    public void testBooleanTag() {
        BooleanTag tag = new BooleanTag("test.key", false);
        Span span = new Span(1, "/test");
        Assert.assertFalse(BooleanTagReader.get(span, tag));

        span = new Span(1, "/test");
        tag.set(span, true);
        Assert.assertTrue(BooleanTagReader.get(span, tag));
    }

    @Test
    public void testIntTag() {
        IntTag tag = new IntTag("test.key");
        Span span = new Span(1, "/test");
        Assert.assertNull(IntTagReader.get(span, tag));
    }
}
