package org.skywalking.apm.agent.core.tags;

import java.lang.reflect.Field;
import java.util.List;
import org.skywalking.apm.agent.core.context.tag.StringTag;

/**
 * @author wusheng
 */
public class StringTagReader {
    public static String get(Span span, StringTag tag) {
        List<StringTagItem> tagsWithStrList = null;
        try {
            Field tagsWithStr = Span.class.getDeclaredField("tagsWithStr");
            tagsWithStr.setAccessible(true);
            tagsWithStrList = (List<StringTagItem>)tagsWithStr.get(span);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        for (StringTagItem item : tagsWithStrList) {
            if (tag.key().equals(item.getKey())) {
                return item.getValue();
            }
        }
        return null;
    }

}
