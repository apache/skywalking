package org.skywalking.apm.sniffer.mock.trace.tag;

import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.IntTag;

/**
 * Test case util for getting the {@link Integer} type of the tag value.
 *
 * @author wusheng
 */
public class IntTagGetter {
    public static Integer get(Span span, IntTag tag) {
        Integer tagValue = span.getIntTag(tag.key());
        if (tagValue == null) {
            return null;
        } else {
            return tagValue;
        }
    }
}
