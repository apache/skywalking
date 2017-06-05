package org.skywalking.apm.collector.worker.segment.entity.tag;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.segment.entity.Span;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class TagsTestCase {

    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException {
        Span span = new Span();

        Map<String, String> tagsWithStr = new LinkedHashMap<>();
        tagsWithStr.put("span.layer", "db");

        Field testAField = span.getClass().getDeclaredField("tagsWithStr");
        testAField.setAccessible(true);
        testAField.set(span, tagsWithStr);

        Assert.assertEquals("db", Tags.SPAN_LAYER.get(span));
    }
}
