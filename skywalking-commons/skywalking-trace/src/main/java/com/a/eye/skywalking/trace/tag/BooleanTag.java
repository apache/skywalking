package com.a.eye.skywalking.trace.tag;

import com.a.eye.skywalking.trace.Span;

/**
 * Do the same thing as {@link StringTag}, just with a {@link Boolean} value.
 *
 * Created by wusheng on 2017/2/17.
 */
public class BooleanTag extends AbstractTag<Boolean>{
    public BooleanTag(String key) {
        super(key);
    }

    @Override
    public void set(Span span, Boolean tagValue) {
        span.setTag(key, tagValue);
    }
}
