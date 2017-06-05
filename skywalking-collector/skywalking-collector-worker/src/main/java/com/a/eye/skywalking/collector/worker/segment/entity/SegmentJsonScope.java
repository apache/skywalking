package com.a.eye.skywalking.collector.worker.segment.entity;

/**
 * Copy from {@link com.google.gson.stream.JsonScope}, this class is invisible, {@link SegmentJsonReader} can not use it
 * because of the package is different
 *
 * @author pengys5
 */
public class SegmentJsonScope {

    /**
     * An array with no elements requires no separators or newlines before
     * it is closed.
     */
    static final int EMPTY_ARRAY = 1;

    /**
     * A array with at least one value requires a comma and newline before
     * the next element.
     */
    static final int NONEMPTY_ARRAY = 2;

    /**
     * An object with no name/value pairs requires no separators or newlines
     * before it is closed.
     */
    static final int EMPTY_OBJECT = 3;

    /**
     * An object whose most recent element is a key. The next element must
     * be a value.
     */
    static final int DANGLING_NAME = 4;

    /**
     * An object with at least one name/value pair requires a comma and
     * newline before the next element.
     */
    static final int NONEMPTY_OBJECT = 5;

    /**
     * No object or array has been started.
     */
    static final int EMPTY_DOCUMENT = 6;

    /**
     * A document with at an array or object.
     */
    static final int NONEMPTY_DOCUMENT = 7;

    /**
     * A document that's been closed and cannot be accessed.
     */
    static final int CLOSED = 8;
}
