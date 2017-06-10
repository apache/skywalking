package org.skywalking.apm.agent.core.context.tag;

import org.skywalking.apm.agent.core.context.trace.Span;

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

    @Override
    public void set(Span span, Boolean tagValue) {
        span.setTag(key, tagValue);
    }

    public boolean defaultValue() {
        return defaultValue;
    }
}
