package org.skywalking.apm.agent.core.tags;

import java.lang.reflect.Field;
import java.util.List;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.BooleanTag;
import org.skywalking.apm.trace.tag.BooleanTagItem;

/**
 * @author wusheng
 */
public class BooleanTagReader {
    public static Boolean get(Span span, BooleanTag tag) {
        List<BooleanTagItem> tagsWithBoolList = null;
        try {
            Field tagsWithBool = Span.class.getDeclaredField("tagsWithBool");
            tagsWithBool.setAccessible(true);
            tagsWithBoolList = (List<BooleanTagItem>)tagsWithBool.get(span);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        for (BooleanTagItem item : tagsWithBoolList) {
            if (tag.key().equals(item.getKey())) {
                return item.getValue();
            }
        }
        return tag.defaultValue();
    }

}
