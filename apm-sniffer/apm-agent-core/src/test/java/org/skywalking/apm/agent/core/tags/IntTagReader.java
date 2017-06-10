package org.skywalking.apm.agent.core.tags;

import java.lang.reflect.Field;
import java.util.List;
import org.skywalking.apm.agent.core.context.trace.Span;
import org.skywalking.apm.agent.core.context.tag.IntTag;
import org.skywalking.apm.agent.core.context.tag.IntTagItem;

/**
 * @author wusheng
 */
public class IntTagReader {
    public static Integer get(Span span, IntTag tag) {
        List<IntTagItem> tagsWithIntList = null;
        try {
            Field tagsWithInt = Span.class.getDeclaredField("tagsWithInt");
            tagsWithInt.setAccessible(true);
            tagsWithIntList = (List<IntTagItem>)tagsWithInt.get(span);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        for (IntTagItem item : tagsWithIntList) {
            if (tag.key().equals(item.getKey())) {
                return item.getValue();
            }
        }
        return null;
    }

}
