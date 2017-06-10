package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.context.trace.Span;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * {@link TracerContext} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context.
 * <p> What is 'ChildOf'? {@see
 * https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans}
 * <p> Also, {@link
 * ContextManager} delegates to all {@link TracerContext}'s major methods: {@link TracerContext#createSpan(String,
 * boolean)}, {@link TracerContext#activeSpan()}, {@link TracerContext#stopSpan(Span)}
 * <p>
 *
 * @author wusheng
 */
public class ContextManager implements TracerContextListener, BootService {
    private static ThreadLocal<AbstractTracerContext> CONTEXT = new ThreadLocal<AbstractTracerContext>();

    private static AbstractTracerContext get() {
        AbstractTracerContext segment = CONTEXT.get();
        if (segment == null) {
            segment = new TracerContext();
            CONTEXT.set(segment);
        }
        return segment;
    }

    /**
     * @see {@link TracerContext#inject(ContextCarrier)}
     */
    public static void inject(ContextCarrier carrier) {
        get().inject(carrier);
    }

    /**
     * @see {@link TracerContext#extract(ContextCarrier)}
     */
    public static void extract(ContextCarrier carrier) {
        get().extract(carrier);
    }

    /**
     * @return the first global trace id if exist. Otherwise, "N/A".
     */
    public static String getGlobalTraceId() {
        AbstractTracerContext segment = CONTEXT.get();
        if (segment == null) {
            return "N/A";
        } else {
            return segment.getGlobalTraceId();
        }
    }

    public static Span createSpan(String operationName) {
        return get().createSpan(operationName, false);
    }

    public static Span createSpan(String operationName, long startTime) {
        return get().createSpan(operationName, startTime, false);
    }

    public static Span createLeafSpan(String operationName) {
        return get().createSpan(operationName, true);
    }

    public static Span createLeafSpan(String operationName, long startTime) {
        return get().createSpan(operationName, startTime, true);
    }

    public static Span activeSpan() {
        return get().activeSpan();
    }

    public static void stopSpan(Span span) {
        get().stopSpan(span);
    }

    public static void stopSpan(Long endTime) {
        get().stopSpan(activeSpan(), endTime);
    }

    public static void stopSpan() {
        stopSpan(activeSpan());
    }

    @Override
    public void bootUp() {
        TracerContext.ListenerManager.add(this);
    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        CONTEXT.remove();
    }

    /**
     * The <code>ContextSwitcher</code> gives the chance to switch {@link AbstractTracerContext} in {@link #CONTEXT}.
     */
    enum ContextSwitcher {
        INSTANCE;

        void toNew(AbstractTracerContext context) {
            CONTEXT.set(context);
        }
    }
}
