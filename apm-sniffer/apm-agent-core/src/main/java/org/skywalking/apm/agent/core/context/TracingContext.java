package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.sampling.SamplingService;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.TraceSegment;
import org.skywalking.apm.trace.TraceSegmentRef;
import org.skywalking.apm.trace.tag.Tags;

import java.util.LinkedList;
import java.util.List;

/**
 * {@link TracingContext} maintains the context.
 * You manipulate (create/finish/get) spans and (inject/extract) context.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public final class TracingContext implements AbstractTracingContext {
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
    TracingContext() {
        this.segment = new TraceSegment(Config.Agent.APPLICATION_CODE);
        ServiceManager.INSTANCE.findService(SamplingService.class).trySampling(this.segment);
        this.spanIdGenerator = 0;
    }

    /**
     * Create a new span, as an active span, by the given operationName
     *
     * @param operationName {@link Span#operationName}
     * @return the new active span.
     */
    public Span createSpan(String operationName, boolean isLeaf) {
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
    public Span createSpan(String operationName, long startTime, boolean isLeaf) {
        Span parentSpan = peek();
        Span span;
        if (parentSpan == null) {
            if (isLeaf) {
                span = new LeafSpan(spanIdGenerator++, operationName, startTime);
            } else {
                span = new Span(spanIdGenerator++, operationName, startTime);
            }
            push(span);
        } else if (parentSpan.isLeaf()) {
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
        return span;
    }

    /**
     * @return the active span of current context.
     */
    public Span activeSpan() {
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
    public void stopSpan(Span span) {
        stopSpan(span, System.currentTimeMillis());
    }

    /**
     * @return the current trace id.
     */
    public String getGlobalTraceId() {
        return segment.getRelatedGlobalTraces().get(0).get();
    }

    public void stopSpan(Span span, Long endTime) {
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

    /**
     * Finish this context, and notify all {@link TracerContextListener}s, managed by {@link ListenerManager}
     */
    private void finish() {
        ListenerManager.notifyFinish(segment.finish());
    }

    /**
     * Give a snapshot of this {@link TracingContext},
     * and save current state to the given {@link ContextCarrier}.
     *
     * @param carrier holds the snapshot
     */
    public void inject(ContextCarrier carrier) {
        carrier.setTraceSegmentId(this.segment.getTraceSegmentId());
        Span span = this.activeSpan();
        carrier.setSpanId(span.getSpanId());
        carrier.setApplicationCode(Config.Agent.APPLICATION_CODE);
        String host = Tags.PEER_HOST.get(span);
        if (host != null) {
            Integer port = Tags.PEER_PORT.get(span);
            carrier.setPeerHost(host + ":" + port);
        } else {
            carrier.setPeerHost(Tags.PEERS.get(span));
        }
        carrier.setDistributedTraceIds(this.segment.getRelatedGlobalTraces());
        carrier.setSampled(this.segment.isSampled());
    }

    /**
     * Ref this {@link ContextCarrier} to this {@link TraceSegment}
     *
     * @param carrier holds the snapshot, if get this {@link ContextCarrier} from remote, make sure {@link
     * ContextCarrier#deserialize(String)} called.
     */
    public AbstractTracingContext extract(ContextCarrier carrier) {
        if (carrier.isValid()) {
            this.segment.ref(getRef(carrier));
            ServiceManager.INSTANCE.findService(SamplingService.class).setSampleWhenExtract(this.segment, carrier);
            this.segment.relatedGlobalTraces(carrier.getDistributedTraceIds());
        }
        return this;
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
