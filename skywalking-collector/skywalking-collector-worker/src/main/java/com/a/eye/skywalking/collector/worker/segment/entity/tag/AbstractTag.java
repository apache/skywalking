package com.a.eye.skywalking.collector.worker.segment.entity.tag;

import com.a.eye.skywalking.collector.worker.segment.entity.Span;

public abstract class AbstractTag<T> {
    /**
     * The key of this Tag.
     */
    protected final String key;

    public AbstractTag(String tagKey) {
        this.key = tagKey;
    }

    public abstract T get(Span span);
}
