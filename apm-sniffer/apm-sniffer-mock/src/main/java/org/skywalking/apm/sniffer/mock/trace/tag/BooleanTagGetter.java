package org.skywalking.apm.sniffer.mock.trace.tag;

import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.BooleanTag;

/**
 * Test case util for getting the {@link Boolean} type of the tag value.
 *
 * @author wusheng
 */
public class BooleanTagGetter {
    public static Boolean get(Span span, BooleanTag tag) {
        return span.getBoolTag(tag.key());
    }
}
