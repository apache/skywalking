package org.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.context.trace.Span;

/**
 * The <code>IgnoreTracerContext</code> represent a context should be ignored.
 * So it just maintains the stack with integer depth.
 * All operations through this <code>IgnoreTracerContext</code> will be ignored, with low gc cost.
 *
 * TODO: Can't return null span
 *
 * @author wusheng
 */
public class IgnoreTracerContext implements AbstractTracerContext {
    private int stackDepth;

    public IgnoreTracerContext(int initStackDepth) {
        this.stackDepth = initStackDepth;
    }

    @Override
    public void inject(ContextCarrier carrier) {

    }

    @Override
    public void extract(ContextCarrier carrier) {

    }

    @Override
    public String getGlobalTraceId() {
        return "[Ignored Trace]";
    }

    @Override
    public Span createSpan(String operationName, boolean isLeaf) {
        stackDepth++;
        return null;
    }

    @Override
    public Span createSpan(String operationName, long startTime, boolean isLeaf) {
        return createSpan(operationName, isLeaf);
    }

    @Override
    public Span activeSpan() {
        return null;
    }

    @Override
    public void stopSpan(Span span) {
        stackDepth--;
        if (stackDepth == 0) {

        }
    }

    @Override
    public void stopSpan(Span span, Long endTime) {
        stopSpan(span);
    }

    @Override
    public void dispose() {

    }

    public static class ListenerManager {
        private static List<IgnoreTracerContextListener> LISTENERS = new LinkedList<IgnoreTracerContextListener>();

        /**
         * Add the given {@link IgnoreTracerContextListener} to {@link #LISTENERS} list.
         *
         * @param listener the new listener.
         */
        public static synchronized void add(IgnoreTracerContextListener listener) {
            LISTENERS.add(listener);
        }

        /**
         * Notify the {@link IgnoreTracerContext.ListenerManager} about the given {@link IgnoreTracerContext} have
         * finished. And trigger {@link IgnoreTracerContext.ListenerManager} to notify all {@link #LISTENERS} 's {@link
         * IgnoreTracerContextListener#afterFinished(IgnoreTracerContext)}
         *
         * @param ignoreTracerContext
         */
        static void notifyFinish(IgnoreTracerContext ignoreTracerContext) {
            for (IgnoreTracerContextListener listener : LISTENERS) {
                listener.afterFinished(ignoreTracerContext);
            }
        }

        /**
         * Clear the given {@link IgnoreTracerContextListener}
         */
        public static synchronized void remove(IgnoreTracerContextListener listener) {
            LISTENERS.remove(listener);
        }
    }
}
