package com.a.eye.skywalking.trace.tag;

import com.a.eye.skywalking.trace.Span;

/**
 * A subclass of {@link AbstractTag},
 * represent a tag with a {@link String} value.
 *
 * Created by wusheng on 2017/2/17.
 */
public class StringTag extends AbstractTag<String> {
    public StringTag(String tagKey) {
        super(tagKey);
    }

    @Override
    protected void set(Span span, String tagValue) {
        span.setTag(key, tagValue);
    }

    @Override public String get(Span span) {
        return (String)span.getTag(super.key);
    }
}
