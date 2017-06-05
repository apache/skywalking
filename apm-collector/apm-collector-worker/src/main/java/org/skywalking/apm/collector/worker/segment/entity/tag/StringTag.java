package org.skywalking.apm.collector.worker.segment.entity.tag;


import org.skywalking.apm.collector.worker.segment.entity.Span;

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
    public String get(Span span) {
        return span.getStrTag(super.key);
    }
}
