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

package org.apache.skywalking.apm.plugin.finagle;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getLocalContextHolder;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getContextCarrier;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getSpan;

public class AnnotationInterceptor {

    abstract static class Abstract extends AbstractInterceptor {

        @Override
        public void onConstructImpl(EnhancedInstance enhancedInstance, Object[] objects) {
            onConstruct(enhancedInstance, objects, getSpan());
        }

        protected abstract void onConstruct(EnhancedInstance enhancedInstance, Object[] objects, AbstractSpan span);
    }

    public static class Rpc extends Abstract {

        @Override
        protected void onConstruct(EnhancedInstance enhancedInstance, Object[] objects, AbstractSpan span) {
            if (objects != null && objects.length == 1) {
                String rpc = (String) objects[0];
                if (span == null) {
                    getLocalContextHolder().let(FinagleCtxs.RPC, rpc);
                } else {
                    span.setOperationName(rpc);
                }
                SWContextCarrier swContextCarrier = getContextCarrier();
                if (swContextCarrier != null) {
                    swContextCarrier.setOperationName(rpc);
                }
            }
        }
    }
}
