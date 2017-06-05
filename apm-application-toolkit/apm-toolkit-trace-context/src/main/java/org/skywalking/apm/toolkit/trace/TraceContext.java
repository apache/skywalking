package org.skywalking.apm.toolkit.trace;

/**
 * Try to access the sky-walking tracer context.
 * The context is not existed, always.
 * only the middleware, component, or rpc-framework are supported in the current invoke stack, in the same thread,
 * the context will be available.
 * <p>
 * Created by xin on 2016/12/15.
 */
public class TraceContext {

    /**
     * Try to get the traceId of current trace context.
     *
     * @return traceId, if it exists, or empty {@link String}.
     */
    public static String traceId() {
        return "";
    }
}
