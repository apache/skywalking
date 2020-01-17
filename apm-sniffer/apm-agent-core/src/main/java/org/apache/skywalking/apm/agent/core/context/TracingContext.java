/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.EntrySpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LocalSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.context.trace.WithPeerInfo;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryManager;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.dictionary.PossibleFound;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.profile.ProfileTaskExecutionService;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * The <code>TracingContext</code> represents a core tracing logic controller. It build the final {@link
 * TracingContext}, by the stack mechanism, which is similar with the codes work.
 *
 * In opentracing concept, it means, all spans in a segment tracing context(thread) are CHILD_OF relationship, but no
 * FOLLOW_OF.
 *
 * In skywalking core concept, FOLLOW_OF is an abstract concept when cross-process MQ or cross-thread async/batch tasks
 * happen, we used {@link TraceSegmentRef} for these scenarios. Check {@link TraceSegmentRef} which is from {@link
 * ContextCarrier} or {@link ContextSnapshot}.
 *
 * @author wusheng
 * @author zhang xin
 */
public class TracingContext implements AbstractTracerContext {
    private static final ILog logger = LogManager.getLogger(TracingContext.class);
    private long lastWarningTimestamp = 0;

    /**
     * @see {@link ProfileTaskExecutionService}
     */
    private static ProfileTaskExecutionService PROFILE_TASK_EXECUTION_SERVICE;

    /**
     * @see {@link SamplingService}
     */
    private static SamplingService SAMPLING_SERVICE;

    /**
     * The final {@link TraceSegment}, which includes all finished spans.
     */
    private TraceSegment segment;

    /**
     * Active spans stored in a Stack, usually called 'ActiveSpanStack'. This {@link LinkedList} is the in-memory
     * storage-structure. <p> I use {@link LinkedList#removeLast()}, {@link LinkedList#addLast(Object)} and {@link
     * LinkedList#getLast()} instead of {@link #pop()}, {@link #push(AbstractSpan)}, {@link #peek()}
     */
    private LinkedList<AbstractSpan> activeSpanStack = new LinkedList<AbstractSpan>();

    /**
     * A counter for the next span.
     */
    private int spanIdGenerator;

    /**
     * The counter indicates
     */
    private volatile AtomicInteger asyncSpanCounter;
    private volatile boolean isRunningInAsyncMode;
    private volatile ReentrantLock asyncFinishLock;

    private volatile boolean running;

    private final long createTime;

    /**
     * profiling status
     */
    private volatile boolean profiling;

    /**
     * Initialize all fields with default value.
     */
    TracingContext(String firstOPName) {
        this.segment = new TraceSegment();
        this.spanIdGenerator = 0;
        isRunningInAsyncMode = false;
        createTime = System.currentTimeMillis();
        running = true;

        if (SAMPLING_SERVICE == null) {
            SAMPLING_SERVICE = ServiceManager.INSTANCE.findService(SamplingService.class);
        }

        // profiling status
        if (PROFILE_TASK_EXECUTION_SERVICE == null) {
            PROFILE_TASK_EXECUTION_SERVICE = ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class);
        }
        this.profiling = PROFILE_TASK_EXECUTION_SERVICE.addProfiling(this, segment.getTraceSegmentId(), firstOPName);
    }

    /**
     * Inject the context into the given carrier, only when the active span is an exit one.
     *
     * @param carrier to carry the context for crossing process.
     * @throws IllegalStateException if the active span isn't an exit one. Ref to {@link
     * AbstractTracerContext#inject(ContextCarrier)}
     */
    @Override
    public void inject(ContextCarrier carrier) {
        AbstractSpan span = this.activeSpan();
        if (!span.isExit()) {
            throw new IllegalStateException("Inject can be done only in Exit Span");
        }

        WithPeerInfo spanWithPeer = (WithPeerInfo)span;
        String peer = spanWithPeer.getPeer();
        int peerId = spanWithPeer.getPeerId();

        carrier.setTraceSegmentId(this.segment.getTraceSegmentId());
        carrier.setSpanId(span.getSpanId());

        carrier.setParentServiceInstanceId(segment.getApplicationInstanceId());

        if (DictionaryUtil.isNull(peerId)) {
            carrier.setPeerHost(peer);
        } else {
            carrier.setPeerId(peerId);
        }

        AbstractSpan firstSpan = first();
        String firstSpanOperationName = firstSpan.getOperationName();

        List<TraceSegmentRef> refs = this.segment.getRefs();
        int operationId = DictionaryUtil.inexistence();
        String operationName = "";
        int entryApplicationInstanceId;

        if (refs != null && refs.size() > 0) {
            TraceSegmentRef ref = refs.get(0);
            operationId = ref.getEntryEndpointId();
            operationName = ref.getEntryEndpointName();
            entryApplicationInstanceId = ref.getEntryServiceInstanceId();
        } else {
            if (firstSpan.isEntry()) {
                /**
                 * Since 6.6.0, if first span is not entry span, then this is an internal segment(no RPC),
                 * rather than an endpoint.
                 */
                operationId = firstSpan.getOperationId();
                operationName = firstSpanOperationName;
            }
            entryApplicationInstanceId = this.segment.getApplicationInstanceId();

        }
        carrier.setEntryServiceInstanceId(entryApplicationInstanceId);

        if (operationId == DictionaryUtil.nullValue()) {
            if (!StringUtil.isEmpty(operationName)) {
                carrier.setEntryEndpointName(operationName);
            } else {
                /**
                 * Since 6.6.0, if first span is not entry span, then this is an internal segment(no RPC),
                 * rather than an endpoint.
                 */
            }
        } else {
            carrier.setEntryEndpointId(operationId);
        }

        int parentOperationId = firstSpan.getOperationId();
        if (parentOperationId == DictionaryUtil.nullValue()) {
            if (firstSpan.isEntry() && !StringUtil.isEmpty(firstSpanOperationName)) {
                carrier.setParentEndpointName(firstSpanOperationName);
            } else {
                /**
                 * Since 6.6.0, if first span is not entry span, then this is an internal segment(no RPC),
                 * rather than an endpoint.
                 */
                carrier.setParentEndpointId(DictionaryUtil.inexistence());
            }
        } else {
            carrier.setParentEndpointId(parentOperationId);
        }

        carrier.setDistributedTraceIds(this.segment.getRelatedGlobalTraces());
    }

    /**
     * Extract the carrier to build the reference for the pre segment.
     *
     * @param carrier carried the context from a cross-process segment. Ref to {@link
     * AbstractTracerContext#extract(ContextCarrier)}
     */
    @Override
    public void extract(ContextCarrier carrier) {
        TraceSegmentRef ref = new TraceSegmentRef(carrier);
        this.segment.ref(ref);
        this.segment.relatedGlobalTraces(carrier.getDistributedTraceId());
        AbstractSpan span = this.activeSpan();
        if (span instanceof EntrySpan) {
            span.ref(ref);
        }
    }

    /**
     * Capture the snapshot of current context.
     *
     * @return the snapshot of context for cross-thread propagation Ref to {@link AbstractTracerContext#capture()}
     */
    @Override
    public ContextSnapshot capture() {
        List<TraceSegmentRef> refs = this.segment.getRefs();
        ContextSnapshot snapshot = new ContextSnapshot(segment.getTraceSegmentId(),
            activeSpan().getSpanId(),
            segment.getRelatedGlobalTraces());
        int entryOperationId;
        String entryOperationName = "";
        int entryApplicationInstanceId;
        AbstractSpan firstSpan = first();
        String firstSpanOperationName = firstSpan.getOperationName();

        if (refs != null && refs.size() > 0) {
            TraceSegmentRef ref = refs.get(0);
            entryOperationId = ref.getEntryEndpointId();
            entryOperationName = ref.getEntryEndpointName();
            entryApplicationInstanceId = ref.getEntryServiceInstanceId();
        } else {
            if (firstSpan.isEntry()) {
                entryOperationId = firstSpan.getOperationId();
                entryOperationName = firstSpanOperationName;
            } else {
                /**
                 * Since 6.6.0, if first span is not entry span, then this is an internal segment(no RPC),
                 * rather than an endpoint.
                 */
                entryOperationId = DictionaryUtil.inexistence();
            }
            entryApplicationInstanceId = this.segment.getApplicationInstanceId();
        }
        snapshot.setEntryApplicationInstanceId(entryApplicationInstanceId);

        if (entryOperationId == DictionaryUtil.nullValue()) {
            if (!StringUtil.isEmpty(entryOperationName)) {
                snapshot.setEntryOperationName(entryOperationName);
            } else {
                /**
                 * Since 6.6.0, if first span is not entry span, then this is an internal segment(no RPC),
                 * rather than an endpoint.
                 */
            }
        } else {
            snapshot.setEntryOperationId(entryOperationId);
        }

        int parentOperationId = firstSpan.getOperationId();
        if (parentOperationId == DictionaryUtil.nullValue()) {
            if (firstSpan.isEntry() && !StringUtil.isEmpty(firstSpanOperationName)) {
                snapshot.setParentOperationName(firstSpanOperationName);
            } else {
                /**
                 * Since 6.6.0, if first span is not entry span, then this is an internal segment(no RPC),
                 * rather than an endpoint.
                 */
                snapshot.setParentOperationId(DictionaryUtil.inexistence());
            }
        } else {
            snapshot.setParentOperationId(parentOperationId);
        }
        return snapshot;
    }

    /**
     * Continue the context from the given snapshot of parent thread.
     *
     * @param snapshot from {@link #capture()} in the parent thread. Ref to {@link AbstractTracerContext#continued(ContextSnapshot)}
     */
    @Override
    public void continued(ContextSnapshot snapshot) {
        TraceSegmentRef segmentRef = new TraceSegmentRef(snapshot);
        this.segment.ref(segmentRef);
        this.activeSpan().ref(segmentRef);
        this.segment.relatedGlobalTraces(snapshot.getDistributedTraceId());
    }

    /**
     * @return the first global trace id.
     */
    @Override
    public String getReadableGlobalTraceId() {
        return segment.getRelatedGlobalTraces().get(0).toString();
    }

    /**
     * Create an entry span
     *
     * @param operationName most likely a service name
     * @return span instance. Ref to {@link EntrySpan}
     */
    @Override
    public AbstractSpan createEntrySpan(final String operationName) {
        if (isLimitMechanismWorking()) {
            NoopSpan span = new NoopSpan();
            return push(span);
        }
        AbstractSpan entrySpan;
        TracingContext owner = this;
        final AbstractSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        if (parentSpan != null && parentSpan.isEntry()) {
            entrySpan = (AbstractTracingSpan)DictionaryManager.findEndpointSection()
                .findOnly(segment.getServiceId(), operationName)
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
            entrySpan = (AbstractTracingSpan)DictionaryManager.findEndpointSection()
                .findOnly(segment.getServiceId(), operationName)
                .doInCondition(new PossibleFound.FoundAndObtain() {
                    @Override public Object doProcess(int operationId) {
                        return new EntrySpan(spanIdGenerator++, parentSpanId, operationId, owner);
                    }
                }, new PossibleFound.NotFoundAndObtain() {
                    @Override public Object doProcess() {
                        return new EntrySpan(spanIdGenerator++, parentSpanId, operationName, owner);
                    }
                });
            entrySpan.start();
            return push(entrySpan);
        }
    }

    /**
     * Create a local span
     *
     * @param operationName most likely a local method signature, or business name.
     * @return the span represents a local logic block. Ref to {@link LocalSpan}
     */
    @Override
    public AbstractSpan createLocalSpan(final String operationName) {
        if (isLimitMechanismWorking()) {
            NoopSpan span = new NoopSpan();
            return push(span);
        }
        AbstractSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        /**
         * From v6.0.0-beta, local span doesn't do op name register.
         * All op name register is related to entry and exit spans only.
         */
        AbstractTracingSpan span = new LocalSpan(spanIdGenerator++, parentSpanId, operationName, this);
        span.start();
        return push(span);
    }

    /**
     * Create an exit span
     *
     * @param operationName most likely a service name of remote
     * @param remotePeer the network id(ip:port, hostname:port or ip1:port1,ip2,port, etc.)
     * @return the span represent an exit point of this segment.
     * @see ExitSpan
     */
    @Override
    public AbstractSpan createExitSpan(final String operationName, final String remotePeer) {
        if (isLimitMechanismWorking()) {
            NoopExitSpan span = new NoopExitSpan(remotePeer);
            return push(span);
        }

        AbstractSpan exitSpan;
        AbstractSpan parentSpan = peek();
        TracingContext owner = this;
        if (parentSpan != null && parentSpan.isExit()) {
            exitSpan = parentSpan;
        } else {
            final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
            exitSpan = (AbstractSpan)DictionaryManager.findNetworkAddressSection()
                .find(remotePeer).doInCondition(
                    new PossibleFound.FoundAndObtain() {
                        @Override
                        public Object doProcess(final int peerId) {
                            return new ExitSpan(spanIdGenerator++, parentSpanId, operationName, peerId, owner);
                        }
                    },
                    new PossibleFound.NotFoundAndObtain() {
                        @Override
                        public Object doProcess() {
                            return new ExitSpan(spanIdGenerator++, parentSpanId, operationName, remotePeer, owner);
                        }
                    });
            push(exitSpan);
        }
        exitSpan.start();
        return exitSpan;
    }

    /**
     * @return the active span of current context, the top element of {@link #activeSpanStack}
     */
    @Override
    public AbstractSpan activeSpan() {
        AbstractSpan span = peek();
        if (span == null) {
            throw new IllegalStateException("No active span.");
        }
        return span;
    }

    /**
     * Stop the given span, if and only if this one is the top element of {@link #activeSpanStack}. Because the tracing
     * core must make sure the span must match in a stack module, like any program did.
     *
     * @param span to finish
     */
    @Override
    public boolean stopSpan(AbstractSpan span) {
        AbstractSpan lastSpan = peek();
        if (lastSpan == span) {
            if (lastSpan instanceof AbstractTracingSpan) {
                AbstractTracingSpan toFinishSpan = (AbstractTracingSpan)lastSpan;
                if (toFinishSpan.finish(segment)) {
                    pop();
                }
            } else {
                pop();
            }
        } else {
            throw new IllegalStateException("Stopping the unexpected span = " + span);
        }

        finish();

        return activeSpanStack.isEmpty();
    }

    @Override public AbstractTracerContext awaitFinishAsync() {
        if (!isRunningInAsyncMode) {
            synchronized (this) {
                if (!isRunningInAsyncMode) {
                    asyncFinishLock = new ReentrantLock();
                    asyncSpanCounter = new AtomicInteger(0);
                    isRunningInAsyncMode = true;
                }
            }
        }
        asyncSpanCounter.addAndGet(1);
        return this;
    }

    @Override public void asyncStop(AsyncSpan span) {
        asyncSpanCounter.addAndGet(-1);
        finish();
    }

    /**
     * Re-check current trace need profiling, encase third part plugin change the operation name.
     *
     * @param span current modify span
     * @param operationName change to operation name
     */
    public void profilingRecheck(AbstractSpan span, String operationName) {
        // only recheck first span
        if (span.getSpanId() != 0) {
            return;
        }

        profiling = PROFILE_TASK_EXECUTION_SERVICE.profilingRecheck(this, segment.getTraceSegmentId(), operationName);
    }

    /**
     * Finish this context, and notify all {@link TracingContextListener}s, managed by {@link
     * TracingContext.ListenerManager} and {@link TracingContext.TracingThreadListenerManager}
     */
    private void finish() {
        if (isRunningInAsyncMode) {
            asyncFinishLock.lock();
        }
        try {
            boolean isFinishedInMainThread = activeSpanStack.isEmpty() && running;
            if (isFinishedInMainThread) {
                /**
                 * Notify after tracing finished in the main thread.
                 */
                TracingThreadListenerManager.notifyFinish(this);
            }

            if (isFinishedInMainThread && (!isRunningInAsyncMode || asyncSpanCounter.get() == 0)) {
                TraceSegment finishedSegment = segment.finish(isLimitMechanismWorking());
                /*
                 * Recheck the segment if the segment contains only one span.
                 * Because in the runtime, can't sure this segment is part of distributed trace.
                 *
                 * @see {@link #createSpan(String, long, boolean)}
                 */
                if (!segment.hasRef() && segment.isSingleSpanSegment()) {
                    if (!SAMPLING_SERVICE.trySampling()) {
                        finishedSegment.setIgnore(true);
                    }
                }

                /*
                 * Check that the segment is created after the agent (re-)registered to backend,
                 * otherwise the segment may be created when the agent is still rebooting and should
                 * be ignored
                 */
                if (segment.createTime() < RemoteDownstreamConfig.Agent.INSTANCE_REGISTERED_TIME) {
                    finishedSegment.setIgnore(true);
                }

                TracingContext.ListenerManager.notifyFinish(finishedSegment);

                running = false;
            }
        } finally {
            if (isRunningInAsyncMode) {
                asyncFinishLock.unlock();
            }
        }
    }

    /**
     * The <code>ListenerManager</code> represents an event notify for every registered listener, which are notified
     * when the <code>TracingContext</code> finished, and {@link #segment} is ready for further process.
     */
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
         * Notify the {@link TracingContext.ListenerManager} about the given {@link TraceSegment} have finished. And
         * trigger {@link TracingContext.ListenerManager} to notify all {@link #LISTENERS} 's {@link
         * TracingContextListener#afterFinished(TraceSegment)}
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
     * The <code>ListenerManager</code> represents an event notify for every registered listener, which are notified
     */
    public static class TracingThreadListenerManager {
        private static List<TracingThreadListener> LISTENERS = new LinkedList<>();

        public static synchronized void add(TracingThreadListener listener) {
            LISTENERS.add(listener);
        }

        static void notifyFinish(TracingContext finishedContext) {
            for (TracingThreadListener listener : LISTENERS) {
                listener.afterMainThreadFinish(finishedContext);
            }
        }

        public static synchronized void remove(TracingThreadListener listener) {
            LISTENERS.remove(listener);
        }
    }

    /**
     * @return the top element of 'ActiveSpanStack', and remove it.
     */
    private AbstractSpan pop() {
        return activeSpanStack.removeLast();
    }

    /**
     * Add a new Span at the top of 'ActiveSpanStack'
     *
     * @param span
     */
    private AbstractSpan push(AbstractSpan span) {
        activeSpanStack.addLast(span);
        return span;
    }

    /**
     * @return the top element of 'ActiveSpanStack' only.
     */
    private AbstractSpan peek() {
        if (activeSpanStack.isEmpty()) {
            return null;
        }
        return activeSpanStack.getLast();
    }

    private AbstractSpan first() {
        return activeSpanStack.getFirst();
    }

    private boolean isLimitMechanismWorking() {
        if (spanIdGenerator >= Config.Agent.SPAN_LIMIT_PER_SEGMENT) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastWarningTimestamp > 30 * 1000) {
                logger.warn(new RuntimeException("Shadow tracing context. Thread dump"), "More than {} spans required to create",
                    Config.Agent.SPAN_LIMIT_PER_SEGMENT);
                lastWarningTimestamp = currentTimeMillis;
            }
            return true;
        } else {
            return false;
        }
    }

    public long createTime() {
        return this.createTime;
    }

    public boolean isProfiling() {
        return this.profiling;
    }

}
