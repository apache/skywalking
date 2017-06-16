package org.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.LeafSpan;
import org.skywalking.apm.agent.core.context.trace.Span;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.sampling.SamplingService;

/**
 * {@link TracerContext} maintains the context.
 * You manipulate (create/finish/get) spans and (inject/extract) context.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public final class TracerContext implements AbstractTracerContext {
    private SamplingService samplingService;

    private TraceSegment segment;

    /**
     * Active spans stored in a Stack, usually called 'ActiveSpanStack'.
     * This {@link LinkedList} is the in-memory storage-structure.
     * <p>
     * I use {@link LinkedList#removeLast()}, {@link LinkedList#addLast(Object)} and {@link LinkedList#last} instead of
     * {@link #pop()}, {@link #push(Span)}, {@link #peek()}
     */
    private LinkedList<Span> activeSpanStack = new LinkedList<Span>();

    private int spanIdGenerator;

    /**
     * Create a {@link TraceSegment} and init {@link #spanIdGenerator} as 0;
     */
    TracerContext() {
        this.segment = new TraceSegment(Config.Agent.APPLICATION_CODE);
        this.spanIdGenerator = 0;
        if (samplingService == null) {
            samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
        }
    }

    /**
     * Create a new span, as an active span, by the given operationName
     *
     * @param operationName {@link Span#operationName}
     * @return the new active span.
     */
    public AbstractSpan createSpan(String operationName, boolean isLeaf) {
        return this.createSpan(operationName, System.currentTimeMillis(), isLeaf);
    }

    /**
     * Create a new span, as an active span, by the given operationName and startTime;
     *
     * @param operationName {@link Span#operationName}
     * @param startTime {@link Span#startTime}
     * @param isLeaf is true, if the span is a leaf in trace tree.
     * @return
     */
    public AbstractSpan createSpan(String operationName, long startTime, boolean isLeaf) {
        Span parentSpan = peek();
        Span span;
        if (parentSpan == null) {
            if (operationName != null) {
                int suffixIdx = operationName.lastIndexOf(".");
                if (suffixIdx > -1 && Config.Agent.IGNORE_SUFFIX.contains(operationName.substring(suffixIdx))) {
                    ContextManager.ContextSwitcher.INSTANCE.toNew(new IgnoredTracerContext(1));
                    return ContextManager.activeSpan();
                }
            }

            if (isLeaf) {
                span = new LeafSpan(spanIdGenerator++, operationName, startTime);
            } else {
                span = new Span(spanIdGenerator++, operationName, startTime);
            }
            push(span);
        } else {
            /**
             * Don't have ref yet, means this isn't part of distributed trace.
             * Use sampling mechanism
             * Only check this on the second span,
             * because the {@link #extract(ContextCarrier)} invoke before create the second span.
             */
            if (spanIdGenerator == 1) {
                if (segment.hasRef()) {
                    samplingService.forceSampled();
                } else {
                    if (!samplingService.trySampling()) {
                        /**
                         * Don't sample this trace.
                         * Now, switch this trace as an {@link IgnoredTracerContext},
                         * further more, we will provide an analytic tracer context for all metrics in this trace.
                         */
                        ContextManager.ContextSwitcher.INSTANCE.toNew(new IgnoredTracerContext(2));
                        return ContextManager.activeSpan();
                    }
                }
            }

            if (parentSpan.isLeaf()) {
                span = parentSpan;
                LeafSpan leafSpan = (LeafSpan)span;
                leafSpan.push();
            } else {
                if (isLeaf) {
                    span = new LeafSpan(spanIdGenerator++, parentSpan, operationName, startTime);
                } else {
                    span = new Span(spanIdGenerator++, parentSpan, operationName, startTime);
                }
                push(span);
            }
        }
        return span;
    }

    /**
     * @return the active span of current context.
     */
    public AbstractSpan activeSpan() {
        Span span = peek();
        if (span == null) {
            throw new IllegalStateException("No active span.");
        }
        return span;
    }

    /**
     * Stop the span. And finish the {@link #segment} if all {@link #activeSpanStack} elements are finished.
     *
     * @param span to finish. It must the the top element of {@link #activeSpanStack}.
     */
    public void stopSpan(AbstractSpan span) {
        stopSpan(span, System.currentTimeMillis());
    }

    /**
     * @return the current trace id.
     */
    public String getGlobalTraceId() {
        return segment.getRelatedGlobalTraces().get(0).get();
    }

    public void stopSpan(AbstractSpan span, Long endTime) {
        Span lastSpan = peek();
        if (lastSpan.isLeaf()) {
            LeafSpan leafSpan = (LeafSpan)lastSpan;
            leafSpan.pop();
            if (!leafSpan.isFinished()) {
                return;
            }
        }
        if (lastSpan == span) {
            pop().finish(segment, endTime);
        } else {
            throw new IllegalStateException("Stopping the unexpected span = " + span);
        }

        if (activeSpanStack.isEmpty()) {
            this.finish();
        }
    }

    @Override
    public void dispose() {
        this.segment = null;
        this.activeSpanStack = null;
    }

    /**
     * Finish this context, and notify all {@link TracerContextListener}s, managed by {@link ListenerManager}
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
        ListenerManager.notifyFinish(finishedSegment);
    }

    /**
     * Give a snapshot of this {@link TracerContext},
     * and save current state to the given {@link ContextCarrier}.
     *
     * @param carrier holds the snapshot
     */
    public void inject(ContextCarrier carrier) {
        carrier.setTraceSegmentId(this.segment.getTraceSegmentId());
        Span span = (Span)this.activeSpan();
        carrier.setSpanId(span.getSpanId());
        carrier.setApplicationCode(Config.Agent.APPLICATION_CODE);
        String host = span.getPeerHost();
        if (host != null) {
            Integer port = span.getPort();
            carrier.setPeerHost(host + ":" + port);
        } else {
            carrier.setPeerHost(span.getPeers());
        }
        carrier.setDistributedTraceIds(this.segment.getRelatedGlobalTraces());
    }

    /**
     * Ref this {@link ContextCarrier} to this {@link TraceSegment}
     *
     * @param carrier holds the snapshot, if get this {@link ContextCarrier} from remote, make sure {@link
     * ContextCarrier#deserialize(String)} called.
     */
    public void extract(ContextCarrier carrier) {
        if (carrier.isValid()) {
            this.segment.ref(getRef(carrier));
            this.segment.relatedGlobalTraces(carrier.getDistributedTraceIds());
        }
    }

    private TraceSegmentRef getRef(ContextCarrier carrier) {
        TraceSegmentRef ref = new TraceSegmentRef();
        ref.setTraceSegmentId(carrier.getTraceSegmentId());
        ref.setSpanId(carrier.getSpanId());
        ref.setApplicationCode(carrier.getApplicationCode());
        ref.setPeerHost(carrier.getPeerHost());
        return ref;
    }

    /**
     * @return the top element of 'ActiveSpanStack', and remove it.
     */
    private Span pop() {
        return activeSpanStack.removeLast();
    }

    /**
     * Add a new Span at the top of 'ActiveSpanStack'
     *
     * @param span
     */
    private void push(Span span) {
        activeSpanStack.addLast(span);
    }

    /**
     * @return the top element of 'ActiveSpanStack' only.
     */
    private Span peek() {
        if (activeSpanStack.isEmpty()) {
            return null;
        }
        return activeSpanStack.getLast();
    }

    public static class ListenerManager {
        private static List<TracerContextListener> LISTENERS = new LinkedList<TracerContextListener>();

        /**
         * Add the given {@link TracerContextListener} to {@link #LISTENERS} list.
         *
         * @param listener the new listener.
         */
        public static synchronized void add(TracerContextListener listener) {
            LISTENERS.add(listener);
        }

        /**
         * Notify the {@link ListenerManager} about the given {@link TraceSegment} have finished.
         * And trigger {@link ListenerManager} to notify all {@link #LISTENERS} 's
         * {@link TracerContextListener#afterFinished(TraceSegment)}
         *
         * @param finishedSegment
         */
        static void notifyFinish(TraceSegment finishedSegment) {
            for (TracerContextListener listener : LISTENERS) {
                listener.afterFinished(finishedSegment);
            }
        }

        /**
         * Clear the given {@link TracerContextListener}
         */
        public static synchronized void remove(TracerContextListener listener) {
            LISTENERS.remove(listener);
        }
    }
}
