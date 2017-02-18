package com.a.eye.skywalking.trace.tag;

import com.a.eye.skywalking.trace.Span;

/**
 * This is the abstract tag.
 * All span's tags inherit from {@link AbstractTag},
 * which provide an easy way to
 *      {@link Span#setTag(String, String)} ,
 *      {@link Span#setTag(String, Number)} ,
 *      {@link Span#setTag(String, boolean)} ,
 *
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
