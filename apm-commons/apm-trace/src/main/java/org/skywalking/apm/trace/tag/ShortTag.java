package org.skywalking.apm.trace.tag;

import org.skywalking.apm.trace.Span;

/**
 * Do the same thing as {@link StringTag}, just with a {@link Short} value.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class ShortTag extends AbstractTag<Short> {
    public ShortTag(String key) {
        super(key);
    }

    @Override
    public void set(Span span, Short tagValue) {
        span.setTag(super.key, (int) tagValue.shortValue());
    }

    /**
     * Get a tag value, type of {@link Short}.
     * After akka-message/serialize, all tags values are type of {@link String}, convert to {@link Short}, if necessary.
     *
     * @param span
     * @return tag value
     */
    @Override
    public Short get(Span span) {
        Integer tagValue = span.getIntTag(super.key);
        if (tagValue == null) {
            return null;
        } else {
            return Short.valueOf(tagValue.toString());
        }
    }
}
