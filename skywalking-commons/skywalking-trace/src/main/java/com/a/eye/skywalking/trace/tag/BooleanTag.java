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

    /**
     * Get a tag value, type of {@link Boolean}.
     * After akka-message/serialize, all tags values are type of {@link String}, convert to {@link Boolean}, if necessary.
     *
     * @param span
     * @return tag value
     */
    @Override
    public Boolean get(Span span) {
        Object tagValue = span.getTag(super.key);
        if(tagValue instanceof Boolean){
            return (Boolean)tagValue;
        }else {
            return Boolean.valueOf(tagValue.toString());
        }
    }
}
