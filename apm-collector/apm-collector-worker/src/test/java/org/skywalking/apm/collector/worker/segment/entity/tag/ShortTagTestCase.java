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
public class ShortTagTestCase {

    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException {
        ShortTag shortTag = new ShortTag("short");

        Map<String, Integer> tagsWithInt = new LinkedHashMap<>();

        Span span = new Span();
        Field testAField = span.getClass().getDeclaredField("tagsWithInt");
        testAField.setAccessible(true);
        testAField.set(span, tagsWithInt);

        Short tag = shortTag.get(span);
        Assert.assertEquals(null, tag);

        tagsWithInt.put("short", 10);
        tag = shortTag.get(span);
        Assert.assertEquals(10, tag.intValue());
    }
}
