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
public class BooleanTagTestCase {

    @Test
    public void test() throws NoSuchFieldException, IllegalAccessException {
        BooleanTag booleanTag = new BooleanTag("test", false);

        Map<String, Boolean> tagsWithInt = new LinkedHashMap<>();

        Span span = new Span();
        Field testAField = span.getClass().getDeclaredField("tagsWithBool");
        testAField.setAccessible(true);
        testAField.set(span, tagsWithInt);

        Assert.assertEquals(false, booleanTag.get(span));

        tagsWithInt.put("test", true);
        Assert.assertEquals(true, booleanTag.get(span));
    }
}
