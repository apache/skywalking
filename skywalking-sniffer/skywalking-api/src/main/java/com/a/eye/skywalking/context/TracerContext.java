package com.a.eye.skywalking.context;

import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.util.TraceIdGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link TracerContext} maintains the context.
 * You manipulate (create/finish/get) spans and (inject/extract) context.
 *
 * Created by wusheng on 2017/2/17.
 */
public final class TracerContext {
    private TraceSegment segment;

    /**
     * Active spans stored in a Stack, usually called 'ActiveSpanStack'.
     * This {@link ArrayList} is the in-memory storage-structure.
     *
     * I use {@link ArrayList#size()} as a pointer, to the top element of 'ActiveSpanStack'.
     * And provide the top 3 important methods of stack:
     * {@link #pop()}, {@link #push(Span)}, {@link #peek()}
     */
    private List<Span> activeSpanStack = new ArrayList<Span>(20);

    private int spanIdGenerator;

    TracerContext() {
        this.segment = new TraceSegment(TraceIdGenerator.generate());
        this.spanIdGenerator = 0;
    }

    /**
     * Create a new span, as an active span, by the given operationName
     *
     * @param operationName {@link Span#operationName}
     * @return the new active span.
     */
    public Span createSpan(String operationName) {
        Span parentSpan = peek();
        Span span;
        if (parentSpan == null) {
            span = new Span(spanIdGenerator++, operationName);
        } else {
            span = new Span(spanIdGenerator++, parentSpan, operationName);
        }
        push(span);
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
        Span lastSpan = peek();
        if (lastSpan == span) {
            segment.archive(pop());
        } else {
            throw new IllegalStateException("Stopping the unexpected span = " + span);
        }

        if (activeSpanStack.isEmpty()) {
            segment.finish();
        }
    }

    /**
     * Give a snapshot of this {@link TracerContext},
     * and save current state to the given {@link ContextCarrier}.
     *
     * @param carrier holds the snapshot
     */
    private void inject(ContextCarrier carrier) {
        carrier.setTraceSegmentId(this.segment.getTraceSegmentId());
        carrier.setSpanId(this.activeSpan().getSpanId());
    }

    /**
     * Ref this {@link ContextCarrier} to this {@link TraceSegment}
     *
     * @param carrier holds the snapshot, if get this {@link ContextCarrier} from remote, make sure {@link
     * ContextCarrier#deserialize(String)} called.
     */
    private void extract(ContextCarrier carrier) {
        this.segment.ref(carrier);
    }

    /**
     * @return the top element of 'ActiveSpanStack', and remove it.
     */
    private Span pop() {
        return activeSpanStack.remove(getTopElementIdx());
    }

    /**
     * Add a new Span at the top of 'ActiveSpanStack'
     *
     * @param span
     */
    private void push(Span span) {
        activeSpanStack.add(activeSpanStack.size(), span);
    }

    /**
     * @return the top element of 'ActiveSpanStack' only.
     */
    private Span peek() {
        if (activeSpanStack.isEmpty()) {
            return null;
        }
        return activeSpanStack.get(getTopElementIdx());
    }

    /**
     * Get the index of 'ActiveSpanStack'
     *
     * @return the index
     */
    private int getTopElementIdx() {
        return activeSpanStack.size() - 1;
    }
}
