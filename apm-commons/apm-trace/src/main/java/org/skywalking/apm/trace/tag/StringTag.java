package org.skywalking.apm.trace.tag;

import org.skywalking.apm.trace.Span;

/**
 * A subclass of {@link AbstractTag},
 * represent a tag with a {@link String} value.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class StringTag extends AbstractTag<String> {
    public StringTag(String tagKey) {
        super(tagKey);
    }

    @Override
    public void set(Span span, String tagValue) {
        span.setTag(key, tagValue);
    }

    @Override
    public String get(Span span) {
        return span.getStrTag(super.key);
    }
}
