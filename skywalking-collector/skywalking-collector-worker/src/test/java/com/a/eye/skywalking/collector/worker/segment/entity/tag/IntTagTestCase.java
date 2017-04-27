package com.a.eye.skywalking.collector.worker.segment.entity.tag;

import com.a.eye.skywalking.collector.worker.segment.entity.Span;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class IntTagTestCase {

    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException {
        IntTag intTag = new IntTag("test");

        Map<String, Integer> tagsWithInt = new LinkedHashMap<>();

        Span span = new Span();
        Field testAField = span.getClass().getDeclaredField("tagsWithInt");
        testAField.setAccessible(true);
        testAField.set(span, tagsWithInt);

        Assert.assertEquals(null, intTag.get(span));

        tagsWithInt.put("test", 10);
        Assert.assertEquals(10, intTag.get(span).intValue());
    }
}
