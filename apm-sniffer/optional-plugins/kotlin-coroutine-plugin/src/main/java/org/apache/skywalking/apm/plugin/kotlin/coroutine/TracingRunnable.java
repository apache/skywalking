package org.apache.skywalking.apm.plugin.kotlin.coroutine;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link Runnable} wrapper with trace context snapshot, it will create span
 * with context snapshot around {@link Runnable} runs.
 * <p>
 * A class implementation will be cheaper cost than lambda with captured
 * variables implementation.
 */
class TracingRunnable implements Runnable {
    private static final String COROUTINE = "/Kotlin/Coroutine";

    private ContextSnapshot snapshot;
    private Runnable delegate;
    private String tracingId;

    private TracingRunnable(ContextSnapshot snapshot, Runnable delegate, String tracingId) {
        this.snapshot = snapshot;
        this.delegate = delegate;
        this.tracingId = tracingId;
    }

    private TracingRunnable(ContextSnapshot snapshot, Runnable delegate) {
        this(snapshot, delegate, ContextManager.getGlobalTraceId());
    }

    /**
     * Wrap {@link Runnable} by {@link TracingRunnable} if active trace context
     * existed.
     *
     * @param delegate {@link Runnable} to wrap.
     *
     * @return Wrapped {@link TracingRunnable} or original {@link Runnable} if
     * trace context not existed.
     */
    public static Runnable wrapOrNot(Runnable delegate) {
        // Just wrap continuation with active trace context
        if (ContextManager.isActive()) {
            return new TracingRunnable(ContextManager.capture(), delegate);
        } else {
            return delegate;
        }
    }

    @Override
    public void run() {
        if (ContextManager.getGlobalTraceId().equals(tracingId)) {
            // Trace id same with before dispatching, skip restore snapshot.
            delegate.run();
            return;
        }

        // Create local coroutine span
        AbstractSpan span = ContextManager.createLocalSpan(COROUTINE);
        span.setComponent(ComponentsDefine.KT_COROUTINE);

        // Recover with snapshot
        ContextManager.continued(snapshot);

        try {
            delegate.run();
        } finally {
            ContextManager.stopSpan(span);
        }
    }
}
