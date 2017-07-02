package org.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.EntrySpan;
import org.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.skywalking.apm.agent.core.context.trace.LocalSpan;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.dictionary.DictionaryManager;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.agent.core.dictionary.PossibleFound;
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
        AbstractTracingSpan span = this.activeSpan();
        if (!span.isExit()) {
            throw new IllegalStateException("Inject can be done only in Exit Span");
        }
        ExitSpan exitSpan = (ExitSpan)span;

        carrier.setTraceSegmentId(this.segment.getTraceSegmentId());
        carrier.setSpanId(span.getSpanId());

        carrier.setApplicationId(segment.getApplicationId());

        if (DictionaryUtil.isNull(exitSpan.getPeerId())) {
            carrier.setPeerHost(exitSpan.getPeer());
        } else {
            carrier.setPeerId(exitSpan.getPeerId());
        }
        List<TraceSegmentRef> refs = this.segment.getRefs();
        int operationId;
        String operationName;
        if (refs != null && refs.size() > 0) {
            TraceSegmentRef ref = refs.get(0);
            operationId = ref.getOperationId();
            operationName = ref.getOperationName();
        } else {
            AbstractTracingSpan firstSpan = first();
            operationId = firstSpan.getOperationId();
            operationName = firstSpan.getOperationName();
        }
        if (operationId == DictionaryUtil.nullValue()) {
            carrier.setEntryOperationName(operationName);
        } else {
            carrier.setEntryOperationId(operationId);
        }

        carrier.setDistributedTraceIds(this.segment.getRelatedGlobalTraces());
    }

    @Override
    public void extract(ContextCarrier carrier) {
        this.segment.ref(new TraceSegmentRef(carrier));
        this.segment.relatedGlobalTraces(carrier.getDistributedTraceIds());
    }

    @Override
    public String getGlobalTraceId() {
        return segment.getRelatedGlobalTraces().get(0).get();
    }

    @Override
    public AbstractSpan createEntrySpan(final String operationName) {
        AbstractTracingSpan entrySpan;
        final AbstractTracingSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        if (parentSpan == null) {
            entrySpan = (AbstractTracingSpan)DictionaryManager.findOperationNameCodeSection()
                .find(segment.getApplicationId(), operationName)
                .doInCondition(new PossibleFound.FoundAndObtain() {
                    @Override public Object doProcess(int operationId) {
                        return new EntrySpan(spanIdGenerator++, parentSpanId, operationId);
                    }
                }, new PossibleFound.NotFoundAndObtain() {
                    @Override public Object doProcess() {
                        return new EntrySpan(spanIdGenerator++, parentSpanId, operationName);
                    }
                });
            entrySpan.start();
            return push(entrySpan);
        } else if (parentSpan.isEntry()) {
            entrySpan = (AbstractTracingSpan)DictionaryManager.findOperationNameCodeSection()
                .find(segment.getApplicationId(), operationName)
                .doInCondition(new PossibleFound.FoundAndObtain() {
                    @Override public Object doProcess(int operationId) {
                        return parentSpan.setOperationId(operationId);
                    }
                }, new PossibleFound.NotFoundAndObtain() {
                    @Override public Object doProcess() {
                        return parentSpan.setOperationName(operationName);
                    }
                });
            return entrySpan.start();
        } else {
            throw new IllegalStateException("The Entry Span can't be the child of Non-Entry Span");
        }
    }

    @Override
    public AbstractSpan createLocalSpan(final String operationName) {
        AbstractTracingSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        AbstractTracingSpan span = (AbstractTracingSpan)DictionaryManager.findOperationNameCodeSection()
            .find(segment.getApplicationId(), operationName)
            .doInCondition(new PossibleFound.FoundAndObtain() {
                @Override
                public Object doProcess(int operationId) {
                    return new LocalSpan(spanIdGenerator++, parentSpanId, operationId);
                }
            }, new PossibleFound.NotFoundAndObtain() {
                @Override
                public Object doProcess() {
                    return new LocalSpan(spanIdGenerator++, parentSpanId, operationName);
                }
            });
        span.start();
        return push(span);
    }

    @Override
    public AbstractSpan createExitSpan(final String operationName, final String remotePeer) {
        AbstractTracingSpan exitSpan;
        AbstractTracingSpan parentSpan = peek();
        if (parentSpan != null && parentSpan.isExit()) {
            exitSpan = parentSpan;
        } else {
            final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
            exitSpan = (AbstractTracingSpan)DictionaryManager.findApplicationCodeSection()
                .find(remotePeer).doInCondition(
                    new PossibleFound.FoundAndObtain() {
                        @Override
                        public Object doProcess(final int applicationId) {
                            return DictionaryManager.findOperationNameCodeSection()
                                .find(applicationId, operationName)
                                .doInCondition(
                                    new PossibleFound.FoundAndObtain() {
                                        @Override
                                        public Object doProcess(int peerId) {
                                            return new ExitSpan(spanIdGenerator++, parentSpanId, applicationId, peerId);
                                        }
                                    }, new PossibleFound.NotFoundAndObtain() {
                                        @Override
                                        public Object doProcess() {
                                            return new ExitSpan(spanIdGenerator++, parentSpanId, applicationId, remotePeer);
                                        }
                                    });
                        }
                    },
                    new PossibleFound.NotFoundAndObtain() {
                        @Override
                        public Object doProcess() {
                            return new ExitSpan(spanIdGenerator++, parentSpanId, operationName, remotePeer);
                        }
                    });
            push(exitSpan);
        }
        exitSpan.start();
        return exitSpan;
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
     * TracingContext.ListenerManager}
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
        TracingContext.ListenerManager.notifyFinish(finishedSegment);
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
         * Notify the {@link TracingContext.ListenerManager} about the given {@link TraceSegment} have finished.
         * And trigger {@link TracingContext.ListenerManager} to notify all {@link #LISTENERS} 's
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
    private AbstractTracingSpan push(AbstractTracingSpan span) {
        activeSpanStack.addLast(span);
        return span;
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

    private AbstractTracingSpan first() {
        return activeSpanStack.getFirst();
    }
}
