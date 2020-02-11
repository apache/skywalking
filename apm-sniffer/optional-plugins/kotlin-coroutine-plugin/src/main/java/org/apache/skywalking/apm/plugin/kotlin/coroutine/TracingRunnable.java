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

package org.apache.skywalking.apm.plugin.kotlin.coroutine;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link Runnable} wrapper with trace context snapshot, it will create span with context snapshot around {@link
 * Runnable} runs.
 * <p>
 * A class implementation will be cheaper cost than lambda with captured variables implementation.
 */
class TracingRunnable implements Runnable {
    private static final String COROUTINE = "/Kotlin/Coroutine";

    private ContextSnapshot snapshot;
    private Runnable delegate;

    private TracingRunnable(ContextSnapshot snapshot, Runnable delegate) {
        this.snapshot = snapshot;
        this.delegate = delegate;
    }

    /**
     * Wrap {@link Runnable} by {@link TracingRunnable} if active trace context existed.
     *
     * @param delegate {@link Runnable} to wrap.
     * @return Wrapped {@link TracingRunnable} or original {@link Runnable} if trace context not existed.
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
        if (ContextManager.isActive() && snapshot.isFromCurrent()) {
            // Thread not switched, skip restore snapshot.
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
