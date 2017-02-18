package com.a.eye.skywalking.trace.tag;

import com.a.eye.skywalking.trace.Span;

/**
 * Do the same thing as {@link StringTag}, just with a {@link Integer} value.
 *
 * Created by wusheng on 2017/2/18.
 */
public class IntTag extends AbstractTag<Integer> {
    public IntTag(String key) {
        super(key);
    }

    @Override
    public void set(Span span, Integer tagValue) {
        span.setTag(super.key, tagValue);
    }

    @Override public Integer get(Span span) {
        return (Integer)span.getTag(super.key);
    }
}
