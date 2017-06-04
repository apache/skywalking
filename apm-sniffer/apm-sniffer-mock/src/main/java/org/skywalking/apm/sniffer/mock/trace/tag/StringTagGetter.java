package org.skywalking.apm.sniffer.mock.trace.tag;

import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.StringTag;

/**
 * Test case util for getting the {@link String} type of the tag value.
 *
 * @author wusheng
 */
public class StringTagGetter {
    public static String get(Span span, StringTag tag) {
        return span.getStrTag(tag.key());
    }
}
