package com.a.eye.skywalking.collector.worker.segment.entity.tag;

import com.a.eye.skywalking.collector.worker.segment.entity.Span;

/**
 * Do the same thing as {@link StringTag}, just with a {@link Boolean} value.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class BooleanTag extends AbstractTag<Boolean> {

    private boolean defaultValue;

    public BooleanTag(String key, boolean defaultValue) {
        super(key);
        this.defaultValue = defaultValue;
    }

    /**
     * Get a tag value, type of {@link Boolean}. After akka-message/serialize, all tags values are type of {@link
     * String}, convert to {@link Boolean}, if necessary.
     *
     * @param span
     * @return tag value
     */
    @Override
    public Boolean get(Span span) {
        Boolean tagValue = span.getBoolTag(super.key);
        if (tagValue == null) {
            return defaultValue;
        } else {
            return tagValue;
        }
    }
}
