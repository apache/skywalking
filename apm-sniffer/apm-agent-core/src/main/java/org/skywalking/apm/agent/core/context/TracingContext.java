package org.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.SpanType;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.dictionary.DictionaryManager;
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
        this.segment = new TraceSegment(DictionaryManager.getApplicationDictionary().findId(Config.Agent.APPLICATION_CODE));
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

    }

    @Override
    public String getGlobalTraceId() {
        return segment.getRelatedGlobalTraces().get(0).get();
    }

    @Override
    public AbstractSpan createSpan(String operationName, SpanType spanType) {
        return null;
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

    }

    @Override
    public void dispose() {
        this.segment = null;
        this.activeSpanStack = null;
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
}
