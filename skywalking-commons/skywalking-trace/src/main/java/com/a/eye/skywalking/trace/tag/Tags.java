package com.a.eye.skywalking.trace.tag;

/**
 * The span tags are supported by sky-walking engine.
 * As default, all tags will be stored, but these ones have particular meanings.
 *
 * Created by wusheng on 2017/2/17.
 */
public final class Tags {
    private Tags() {
    }

    /**
     *  SPAN_KIND hints at the relationship between spans.
     *  e.g. cl = client; se = server.
     */
    public static StringTag SPAN_KIND = new StringTag("span.kind");

    /**
     *  COMPONENT is a low-cardinality identifier of the module, library, or package that is instrumented.
     *  Like dubbo/dubbox/motan
     */
    public static final StringTag COMPONENT  = new StringTag("component");

    /**
     * ERROR indicates whether a Span ended in an error state.
     */
    public static final BooleanTag ERROR = new BooleanTag("error");
}
