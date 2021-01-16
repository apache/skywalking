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
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;

/**
 * The <code>IgnoredTracerContext</code> represent a context should be ignored. So it just maintains the stack with an
 * integer depth field.
 * <p>
 * All operations through this will be ignored, and keep the memory and gc cost as low as possible.
 */
public class IgnoredTracerContext implements AbstractTracerContext {
    private static final NoopSpan NOOP_SPAN = new NoopSpan();
    private static final String IGNORE_TRACE = "Ignored_Trace";

    private final CorrelationContext correlationContext;
    private final ExtensionContext extensionContext;

    private int stackDepth;

    public IgnoredTracerContext() {
        this.stackDepth = 0;
        this.correlationContext = new CorrelationContext();
        this.extensionContext = new ExtensionContext();
    }

    @Override
    public void inject(ContextCarrier carrier) {
        this.correlationContext.inject(carrier);
    }

    @Override
    public void extract(ContextCarrier carrier) {
        this.correlationContext.extract(carrier);
    }

    @Override
    public ContextSnapshot capture() {
        return new ContextSnapshot(null, -1, null, null, correlationContext, extensionContext);
    }

    @Override
    public void continued(ContextSnapshot snapshot) {
        this.correlationContext.continued(snapshot);
    }

    @Override
    public String getReadablePrimaryTraceId() {
        return IGNORE_TRACE;
    }

    @Override
    public String getSegmentId() {
        return IGNORE_TRACE;
    }

    @Override
    public int getSpanId() {
        return -1;
    }

    @Override
    public AbstractSpan createEntrySpan(String operationName) {
        stackDepth++;
        return NOOP_SPAN;
    }

    @Override
    public AbstractSpan createLocalSpan(String operationName) {
        stackDepth++;
        return NOOP_SPAN;
    }

    @Override
    public AbstractSpan createExitSpan(String operationName, String remotePeer) {
        stackDepth++;
        return NOOP_SPAN;
    }

    @Override
    public AbstractSpan activeSpan() {
        return NOOP_SPAN;
    }

    @Override
    public boolean stopSpan(AbstractSpan span) {
        stackDepth--;
        if (stackDepth == 0) {
            ListenerManager.notifyFinish(this);
        }
        return stackDepth == 0;
    }

    @Override
    public AbstractTracerContext awaitFinishAsync() {
        return this;
    }

    @Override
    public void asyncStop(AsyncSpan span) {

    }

    @Override
    public CorrelationContext getCorrelationContext() {
        return this.correlationContext;
    }

    public static class ListenerManager {
        private static List<IgnoreTracerContextListener> LISTENERS = new LinkedList<>();

        /**
         * Add the given {@link IgnoreTracerContextListener} to {@link #LISTENERS} list.
         *
         * @param listener the new listener.
         */
        public static synchronized void add(IgnoreTracerContextListener listener) {
            LISTENERS.add(listener);
        }

        /**
         * Notify the {@link IgnoredTracerContext.ListenerManager} about the given {@link IgnoredTracerContext} have
         * finished. And trigger {@link IgnoredTracerContext.ListenerManager} to notify all {@link #LISTENERS} 's {@link
         * IgnoreTracerContextListener#afterFinished(IgnoredTracerContext)}
         */
        static void notifyFinish(IgnoredTracerContext ignoredTracerContext) {
            for (IgnoreTracerContextListener listener : LISTENERS) {
                listener.afterFinished(ignoredTracerContext);
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
