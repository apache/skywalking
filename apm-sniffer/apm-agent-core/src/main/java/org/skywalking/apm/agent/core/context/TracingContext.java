package org.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.EntrySpan;
import org.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.skywalking.apm.agent.core.context.trace.LocalSpan;
import org.skywalking.apm.agent.core.context.trace.SpanType;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.sampling.SamplingService;

/**
 * @author wusheng
 */
public class TracingContext implements AbstractTracerContext {
    private SamplingService samplingService;

    private TraceSegment segment;

    /**
     * Active spans stored in a Stack, usually called 'ActiveSpanStack'.
     * This {@link LinkedList} is the in-memory storage-structure.
     * <p>
     * I use {@link LinkedList#removeLast()}, {@link LinkedList#addLast(Object)} and {@link LinkedList#last} instead of
     * {@link #pop()}, {@link #push(AbstractTracingSpan)}, {@link #peek()}
     */
    private LinkedList<AbstractTracingSpan> activeSpanStack = new LinkedList<AbstractTracingSpan>();

    private int spanIdGenerator;

    TracingContext() {
        this.segment = new TraceSegment();
        this.spanIdGenerator = 0;
        if (samplingService == null) {
            samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
        }
    }

    @Override
    public void inject(ContextCarrier carrier) {

    }

    @Override
    public void extract(ContextCarrier carrier) {
        this.segment.ref(getRef(carrier));
        this.segment.relatedGlobalTraces(carrier.getDistributedTraceIds());
    }

    @Override
    public String getGlobalTraceId() {
        return segment.getRelatedGlobalTraces().get(0).get();
    }

    @Override
    public AbstractSpan createSpan(String operationName, SpanType spanType) {
        return createSpan(operationName, spanType, null);
    }

    @Override
    public AbstractSpan createSpan(String operationName, SpanType spanType, Injectable injectable) {
        AbstractTracingSpan parentSpan = peek();
        AbstractTracingSpan span = createByType(spanIdGenerator++, -1, operationName,
            spanType, injectable.getPeer(), parentSpan);
        return span.start();
    }

    private AbstractTracingSpan createByType(int spanId, int parentSpanId,
        String operationName, SpanType spanType,
        String peerHost, AbstractTracingSpan parentSpan) {
        switch (spanType) {
            case LOCAL:
                return new LocalSpan(spanId, parentSpanId, operationName);
            case EXIT:
                if (parentSpan != null && parentSpan.isExit()) {
                    return parentSpan;
                } else {
                    return new ExitSpan(spanId, parentSpanId, operationName, peerHost);
                }
            case ENTRY:
                if (parentSpan.isEntry()) {
                    return parentSpan;
                } else if (parentSpan == null) {
                    return new EntrySpan(spanId, parentSpanId, operationName);
                } else {
                    throw new IllegalStateException("The Entry Span can't be the child of Non-Entry Span");
                }
            default:
                throw new IllegalStateException("Unsupported Span type:" + spanType);
        }
    }

    @Override
    public AbstractTracingSpan activeSpan() {
        AbstractTracingSpan span = peek();
        if (span == null) {
            throw new IllegalStateException("No active span.");
        }
        return span;
    }

    @Override
    public void stopSpan(AbstractSpan span) {
        AbstractTracingSpan lastSpan = peek();
        if (lastSpan == span) {
            if (lastSpan.finish(segment)) {
                pop();
            }
        } else {
            throw new IllegalStateException("Stopping the unexpected span = " + span);
        }

        if (activeSpanStack.isEmpty()) {
            this.finish();
        }
    }

    /**
     * Finish this context, and notify all {@link TracingContextListener}s, managed by {@link
     * TracerContext.ListenerManager}
     */
    private void finish() {
        TraceSegment finishedSegment = segment.finish();
        /**
         * Recheck the segment if the segment contains only one span.
         * Because in the runtime, can't sure this segment is part of distributed trace.
         *
         * @see {@link #createSpan(String, long, boolean)}
         */
        if (!segment.hasRef() && segment.isSingleSpanSegment()) {
            if (!samplingService.trySampling()) {
                finishedSegment.setIgnore(true);
            }
        }
        TracerContext.ListenerManager.notifyFinish(finishedSegment);
    }

    @Override
    public void dispose() {
        this.segment = null;
        this.activeSpanStack = null;
    }

    public static class ListenerManager {
        private static List<TracingContextListener> LISTENERS = new LinkedList<TracingContextListener>();

        /**
         * Add the given {@link TracingContextListener} to {@link #LISTENERS} list.
         *
         * @param listener the new listener.
         */
        public static synchronized void add(TracingContextListener listener) {
            LISTENERS.add(listener);
        }

        /**
         * Notify the {@link TracerContext.ListenerManager} about the given {@link TraceSegment} have finished.
         * And trigger {@link TracerContext.ListenerManager} to notify all {@link #LISTENERS} 's
         * {@link TracingContextListener#afterFinished(TraceSegment)}
         *
         * @param finishedSegment
         */
        static void notifyFinish(TraceSegment finishedSegment) {
            for (TracingContextListener listener : LISTENERS) {
                listener.afterFinished(finishedSegment);
            }
        }

        /**
         * Clear the given {@link TracingContextListener}
         */
        public static synchronized void remove(TracingContextListener listener) {
            LISTENERS.remove(listener);
        }

    }

    /**
     * @return the top element of 'ActiveSpanStack', and remove it.
     */
    private AbstractTracingSpan pop() {
        return activeSpanStack.removeLast();
    }

    /**
     * Add a new Span at the top of 'ActiveSpanStack'
     *
     * @param span
     */
    private void push(AbstractTracingSpan span) {
        activeSpanStack.addLast(span);
    }

    /**
     * @return the top element of 'ActiveSpanStack' only.
     */
    private AbstractTracingSpan peek() {
        if (activeSpanStack.isEmpty()) {
            return null;
        }
        return activeSpanStack.getLast();
    }

    private TraceSegmentRef getRef(ContextCarrier carrier) {
        TraceSegmentRef ref = new TraceSegmentRef();
        ref.setTraceSegmentId(carrier.getTraceSegmentId());
        ref.setSpanId(carrier.getSpanId());
        ref.setApplicationCode(carrier.getApplicationCode());
        ref.setPeerHost(carrier.getPeerHost());
        return ref;
    }
}
