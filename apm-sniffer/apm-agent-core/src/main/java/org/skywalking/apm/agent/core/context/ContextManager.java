package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanType;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.agent.core.sampling.SamplingService;
import org.skywalking.apm.util.StringUtil;

/**
 * {@link ContextManager} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context. <p> What is 'ChildOf'? {@see
 * https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans}
 *
 * <p> Also, {@link ContextManager} delegates to all {@link AbstractTracerContext}'s major methods.
 *
 * @author wusheng
 */
public class ContextManager implements TracingContextListener, BootService, IgnoreTracerContextListener {
    private static ThreadLocal<AbstractTracerContext> CONTEXT = new ThreadLocal<AbstractTracerContext>();

    private static AbstractTracerContext getOrCreate(String operationName, boolean forceSampling) {
        if (StringUtil.isEmpty(operationName)) {
            throw new IllegalArgumentException("No operation name");
        }
        AbstractTracerContext context = CONTEXT.get();
        if (context == null) {
            if (RemoteDownstreamConfig.Agent.APPLICATION_ID == DictionaryUtil.nullValue()) {
                /**
                 * Can't register to collector, no need to trace anything.
                 */
                context = new IgnoredTracerContext();
            } else {
                int suffixIdx = operationName.lastIndexOf(".");
                if (suffixIdx > -1 && Config.Agent.IGNORE_SUFFIX.contains(operationName.substring(suffixIdx))) {
                    context = new IgnoredTracerContext();
                } else {
                    SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
                    if (forceSampling || samplingService.trySampling()) {
                        context = new TracingContext();
                    } else {
                        context = new IgnoredTracerContext();
                    }
                }
            }
            CONTEXT.set(context);
        }
        return context;
    }

    private static AbstractTracerContext get() {
        return CONTEXT.get();
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

    public static AbstractSpan createSpan(String operationName, ContextCarrier carrier) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
        AbstractTracerContext context;
        if (carrier.isValid()) {
            samplingService.forceSampled();
            context = getOrCreate(operationName, true);
            context.extract(carrier);
        } else {
            context = getOrCreate(operationName, false);
        }
        return context.createSpan(operationName, SpanType.ENTRY);
    }

    public static AbstractSpan createSpan(String operationName) {
        AbstractTracerContext context = getOrCreate(operationName, false);
        return context.createSpan(operationName, SpanType.LOCAL);
    }

    public static AbstractSpan createSpan(String operationName, Injectable injectable) {
        if (injectable == null) {
            throw new IllegalArgumentException("Injectable can't be null.");
        }
        AbstractTracerContext context = getOrCreate(operationName, false);
        AbstractSpan span = context.createSpan(operationName, SpanType.EXIT);
        context.inject(injectable.getCarrier());
        return span;
    }

    public static AbstractSpan activeSpan() {
        return get().activeSpan();
    }

    public static void stopSpan() {
        get().stopSpan(activeSpan());
    }

    @Override
    public void bootUp() {
        TracingContext.ListenerManager.add(this);
        IgnoredTracerContext.ListenerManager.add(this);
    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        CONTEXT.remove();
    }

    @Override
    public void afterFinished(IgnoredTracerContext traceSegment) {
        CONTEXT.remove();
    }
}
