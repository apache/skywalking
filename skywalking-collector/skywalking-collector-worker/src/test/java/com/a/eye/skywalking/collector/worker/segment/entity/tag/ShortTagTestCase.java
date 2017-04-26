package com.a.eye.skywalking.collector.worker.segment.entity.tag;

import com.a.eye.skywalking.collector.worker.segment.entity.Span;
import com.a.eye.skywalking.collector.worker.storage.SegmentData;
import com.a.eye.skywalking.collector.worker.storage.WindowData;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

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
