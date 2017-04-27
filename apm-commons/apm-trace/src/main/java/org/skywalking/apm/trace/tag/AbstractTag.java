package org.skywalking.apm.trace.tag;

import org.skywalking.apm.trace.Span;

/**
 * This is the abstract tag.
 * All span's tags inherit from {@link AbstractTag},
 * which provide an easy way to
 * {@link Span#setTag(String, String)} ,
 * {@link Span#setTag(String, Integer)}
 * {@link Span#setTag(String, boolean)} ,
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public abstract class AbstractTag<T> {
    /**
     * The key of this Tag.
     */
    protected final String key;

    public AbstractTag(String tagKey) {
        this.key = tagKey;
    }

    protected abstract void set(Span span, T tagValue);

    public abstract T get(Span span);
}
