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

    /**
     * Get a tag value, type of {@link Integer}.
     * After akka-message/serialize, all tags values are type of {@link String}, convert to {@link Integer}, if necessary.
     *
     * @param span
     * @return tag value
     */
    @Override
    public Integer get(Span span) {
        Object tagValue = span.getTag(super.key);
        if(tagValue instanceof Integer){
            return (Integer)tagValue;
        }else {
            return Integer.valueOf(tagValue.toString());
        }
    }
}
