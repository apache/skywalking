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

    /**
     * Get a tag value, type of {@link Short}.
     * After akka-message/serialize, all tags values are type of {@link String}, convert to {@link Short}, if necessary.
     *
     * @param span
     * @return tag value
     */
    @Override public Short get(Span span) {
        Object tagValue = span.getTag(super.key);
        if (tagValue == null) {
            return null;
        } else if(tagValue instanceof Short){
            return (Short)tagValue;
        }else {
            return Short.valueOf(tagValue.toString());
        }
    }
}
