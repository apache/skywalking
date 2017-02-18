package com.a.eye.skywalking.trace.tag;

import com.a.eye.skywalking.trace.Span;

/**
 * Do the same thing as {@link StringTag}, just with a {@link Short} value.
 *
 * Created by wusheng on 2017/2/17.
 */
public class ShortTag extends AbstractTag<Short> {
    public ShortTag(String key) {
        super(key);
    }

    @Override
    public void set(Span span, Short tagValue) {
        span.setTag(super.key, tagValue);
    }

    @Override public Short get(Span span) {
        return (Short)span.getTag(super.key);
    }
}
