package org.skywalking.apm.sniffer.mock.trace.tags;

import java.lang.reflect.Field;
import java.util.List;

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

        if (tagsWithBoolList != null) {
            for (BooleanTagItem item : tagsWithBoolList) {
                if (tag.key().equals(item.getKey())) {
                    return item.getValue();
                }
            }
        }
        return tag.defaultValue();
    }

}
