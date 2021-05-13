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
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import static org.apache.skywalking.apm.plugin.finagle.ContextCarrierHelper.tryInjectContext;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getSpan;

/**
 * Finagle use Annotation to represent data that tracing system interested, usually these annotations are created by
 * filters after ClientTracingFilter in the rpc call stack. We can intercept annotations that we interested.
 */
public class AnnotationInterceptor {

    abstract static class Abstract implements InstanceConstructorInterceptor {

        @Override
        public void onConstruct(EnhancedInstance enhancedInstance, Object[] objects) {
            onConstruct(enhancedInstance, objects, getSpan());
        }

        protected abstract void onConstruct(EnhancedInstance enhancedInstance, Object[] objects, AbstractSpan span);
    }

    /**
     * When we create exitspan in ClientTracingFilter, we can't know the operation name, however the Rpc annotation
     * contains the operation name we need, so we intercept the constructor of this Annotation and set operation name
     * to exitspan.
     */
    public static class Rpc extends Abstract {

        @Override
        protected void onConstruct(EnhancedInstance enhancedInstance, Object[] objects, AbstractSpan span) {
            if (objects != null && objects.length == 1) {
                String rpc = (String) objects[0];
                if (span != null && span.isExit()) {
                    /*
                     * The Rpc Annotation is created both in client side and server side, in server side, this
                     * annotation is created only in finagle versions below 17.12.0.
                     *
                     * If the span is not null, which means we are in the client side, we just set op name to the
                     * exitspan.
                     *
                     * If the span is null, which means we are in the server side with finagle version below 17.12.0.
                     * In server side, we don't need this annotation, because we can get op name from Contexts.broadcast
                     * which comes from client.
                     */
                    span.setOperationName(rpc);
                    tryInjectContext(span);
                }
            }
        }
    }
}
