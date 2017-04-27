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
        Assert.assertEquals("db", span.getStrTag("span.layer"));

        Tags.SPAN_LAYER.asRPCFramework(span);
        Assert.assertEquals("rpc", span.getStrTag("span.layer"));

        Tags.SPAN_LAYER.asHttp(span);
        Assert.assertEquals("http", span.getStrTag("span.layer"));
    }

    @Test
    public void testBooleanTag() {
        BooleanTag tag = new BooleanTag("test.key", false);
        Span span = new Span(1, "/test");
        Assert.assertFalse(tag.get(span));

        tag.set(span, true);
        Assert.assertTrue(tag.get(span));
    }

    @Test
    public void testIntTag() {
        IntTag tag = new IntTag("test.key");
        Span span = new Span(1, "/test");
        Assert.assertNull(tag.get(span));

        tag.set(span, 123);
        Assert.assertEquals(123, tag.get(span).intValue());
    }

    @Test
    public void testShortTag() {
        ShortTag tag = new ShortTag("test.key");
        Span span = new Span(1, "/test");
        Assert.assertNull(tag.get(span));

        short value = 123;
        tag.set(span, value);
        Assert.assertEquals(value, tag.get(span).intValue());
    }
}
