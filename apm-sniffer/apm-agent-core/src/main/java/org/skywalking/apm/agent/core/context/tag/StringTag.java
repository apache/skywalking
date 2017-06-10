package org.skywalking.apm.agent.core.context.tag;

import org.skywalking.apm.agent.core.context.trace.Span;

/**
 * A subclass of {@link AbstractTag},
 * represent a tag with a {@link String} value.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class StringTag extends AbstractTag<String> {
    public StringTag(String tagKey) {
        super(tagKey);
    }

    @Override
    public void set(Span span, String tagValue) {
        span.setTag(key, tagValue);
    }
}
