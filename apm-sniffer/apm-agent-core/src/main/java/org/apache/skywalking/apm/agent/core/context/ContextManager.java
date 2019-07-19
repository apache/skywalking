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

import org.apache.skywalking.apm.agent.core.boot.*;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.context.trace.*;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.logging.api.*;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * {@link ContextManager} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context. <p> What is 'ChildOf'?
 * https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans
 *
 * <p> Also, {@link ContextManager} delegates to all {@link AbstractTracerContext}'s major methods.
 *
 * @author wusheng
 */
public class ContextManager implements BootService {
    private static final ILog logger = LogManager.getLogger(ContextManager.class);
    private static ThreadLocal<AbstractTracerContext> CONTEXT = new ThreadLocal<AbstractTracerContext>();
    private static ThreadLocal<RuntimeContext> RUNTIME_CONTEXT = new ThreadLocal<RuntimeContext>();
    private static ContextManagerExtendService EXTEND_SERVICE;
    
    final static ContextManager MGR = new ContextManager();

    private static AbstractTracerContext getOrCreate(String operationName, boolean forceSampling) {
        AbstractTracerContext context = CONTEXT.get();
        if (context == null) {
            if (StringUtil.isEmpty(operationName)) {
                if (logger.isDebugEnable()) {
                    logger.debug("No operation name, ignore this trace.");
                }
                context = new IgnoredTracerContext();
            } else {
                if (RemoteDownstreamConfig.Agent.SERVICE_ID != DictionaryUtil.nullValue()
                    && RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID != DictionaryUtil.nullValue()
                ) {
                    if (EXTEND_SERVICE == null) {
                        EXTEND_SERVICE = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);
                    }
                    context = EXTEND_SERVICE.createTraceContext(operationName, forceSampling);
                } else {
                    /**
                     * Can't register to collector, no need to trace anything.
                     */
                    context = new IgnoredTracerContext();
                }
            }
            CONTEXT.set(context);
        }
        return context;
    }

    static AbstractTracerContext get() {
        return CONTEXT.get();
    }

    /**
     * @return the first global trace id if needEnhance. Otherwise, "N/A".
     */
    public static String getGlobalTraceId() {
        AbstractTracerContext segment = CONTEXT.get();
        if (segment == null) {
            return "N/A";
        } else {
            return segment.getReadableGlobalTraceId();
        }
    }

    public static AbstractSpan createEntrySpan(String operationName, ContextCarrier carrier) {
        AbstractSpan span;
        AbstractTracerContext context;
        if (carrier != null && carrier.isValid()) {
            SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
            samplingService.forceSampled();
            context = getOrCreate(operationName, true);
            span = context.createEntrySpan(operationName);
            context.extract(carrier);
        } else {
            context = getOrCreate(operationName, false);
            span = context.createEntrySpan(operationName);
        }
        return span;
    }

    public static AbstractSpan createLocalSpan(String operationName) {
        AbstractTracerContext context = getOrCreate(operationName, false);
        return context.createLocalSpan(operationName);
    }

    public static AbstractSpan createLocalSpan(final String operationName, final AbstractSpan span) {
        final AbstractTracerContext context = getTraceContext(operationName, span);
        return context.createLocalSpan(operationName,span);
    }

    protected static AbstractTracerContext getTraceContext(
        final String operationName, final AbstractSpan span) {
        final AbstractTracerContext context = span == null ? getOrCreate(operationName, false) : span.getContext(MGR);
        return context;
    }
    
    public static AbstractSpan createExitSpan(String operationName, ContextCarrier carrier, String remotePeer) {
        return createExitSpan(operationName,carrier,remotePeer,null);
    }

    public static AbstractSpan createExitSpan(String operationName, ContextCarrier carrier, String remotePeer, final AbstractSpan parentSpan) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        final AbstractTracerContext context = getTraceContext(operationName, parentSpan);
        AbstractSpan span = context.createExitSpan(operationName, remotePeer, parentSpan);
        context.inject(carrier);
        return span;
    }
    
    public static AbstractSpan createExitSpan(final String operationName, final String remotePeer) {
        return createExitSpan(operationName,remotePeer,null);
    }

    public static AbstractSpan createExitSpan(final String operationName, final String remotePeer,final AbstractSpan parentSpan) {
        final AbstractTracerContext context = getTraceContext(operationName, parentSpan);
        final AbstractSpan span = context.createExitSpan(operationName, remotePeer, parentSpan);
        return span;
    }

    public static void inject(ContextCarrier carrier) {
        get().inject(carrier);
    }

    public static void extract(ContextCarrier carrier) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        if (carrier.isValid()) {
            get().extract(carrier);
        }
    }

    public static ContextSnapshot capture() {
        return get().capture();
    }

    public static void continued(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("ContextSnapshot can't be null.");
        }
        if (snapshot.isValid() && !snapshot.isFromCurrent()) {
            get().continued(snapshot);
        }
    }

    @Deprecated
    public static AbstractTracerContext awaitFinishAsync(AbstractSpan span) {
        final AbstractTracerContext context = span.getContext(MGR);
        AbstractSpan activeSpan = context.activeSpan();
        if (span != activeSpan) {
            throw new RuntimeException("Span is not the active in current context.");
        }
        return context.awaitFinishAsync();
    }

    /**
     * If not sure has the active span, use this method, will be cause NPE when has no active span,
     * use ContextManager::isActive method to determine whether there has the active span.
     */
    public static AbstractSpan activeSpan() {
        return get().activeSpan();
    }

    /**
    * Recommend use ContextManager::stopSpan(AbstractSpan span), because in that way, 
    * the TracingContext core could verify this span is the active one, in order to avoid stop unexpected span.
    * If the current span is hard to get or only could get by low-performance way, this stop way is still acceptable.
    */
    public static void stopSpan() {
        final AbstractTracerContext context = get();
        stopSpan(context.activeSpan());
    }

    public static void stopSpan(AbstractSpan span) {
        if (span.stop(MGR)) {
            CONTEXT.remove();
            RUNTIME_CONTEXT.remove();
        }
    }

    @Override
    public void prepare() throws Throwable {

    }

    @Override
    public void boot() {
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override public void shutdown() throws Throwable {

    }

    public static boolean isActive() {
        return get() != null;
    }

    public static RuntimeContext getRuntimeContext() {
        RuntimeContext runtimeContext = RUNTIME_CONTEXT.get();
        if (runtimeContext == null) {
            runtimeContext = new RuntimeContext(RUNTIME_CONTEXT);
            RUNTIME_CONTEXT.set(runtimeContext);
        }

        return runtimeContext;
    }
}
