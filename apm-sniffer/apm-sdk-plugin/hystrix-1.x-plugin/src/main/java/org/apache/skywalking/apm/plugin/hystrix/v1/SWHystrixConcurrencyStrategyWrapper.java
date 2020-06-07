/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package org.apache.skywalking.apm.plugin.hystrix.v1;

import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import java.util.concurrent.Callable;

import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import org.apache.skywalking.apm.agent.core.conf.RuntimeContextConfiguration;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.RuntimeContextSnapshot;

public class SWHystrixConcurrencyStrategyWrapper extends HystrixConcurrencyStrategy {

    private final HystrixConcurrencyStrategy delegate;

    public SWHystrixConcurrencyStrategyWrapper(HystrixConcurrencyStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        Callable<T> delegateCallable = delegate != null ? delegate.wrapCallable(callable) : super.wrapCallable(callable);
        return new WrappedCallable<T>(ContextManager.getRuntimeContext().capture(), delegateCallable);
    }

    @Override
    public <T> HystrixRequestVariable<T> getRequestVariable(HystrixRequestVariableLifecycle<T> rv) {
        return new SWHystrixLifecycleForwardingRequestVariable(rv);
    }

    static class WrappedCallable<T> implements Callable<T> {

        private final RuntimeContextSnapshot contextSnapshot;
        private final Callable<T> target;

        WrappedCallable(RuntimeContextSnapshot contextSnapshot, Callable<T> target) {
            this.contextSnapshot = contextSnapshot;
            this.target = target;
        }

        @Override
        public T call() throws Exception {
            try {
                ContextManager.getRuntimeContext().accept(contextSnapshot);
                return target.call();
            } finally {
                for (String key : RuntimeContextConfiguration.NEED_PROPAGATE_CONTEXT_KEY) {
                    ContextManager.getRuntimeContext().remove(key);
                }
            }
        }
    }

}
