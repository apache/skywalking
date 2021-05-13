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

import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.FINAGLE;
import static org.apache.skywalking.apm.plugin.finagle.Constants.PENDING_OP_NAME;
import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getLocalContextHolder;
import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getMarshalledContextHolder;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.SW_SPAN;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getSpan;

public class ClientTracingFilterInterceptor extends AbstractInterceptor {

    @Override
    protected void onConstructImpl(EnhancedInstance objInst, Object[] allArguments) {

    }

    @Override
    public void beforeMethodImpl(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes,
                                 MethodInterceptResult methodInterceptResult) throws Throwable {
        /*
         * At this time, we can't know the operation name and peer address, so we just use placeholders here, the
         * operation name will be filled by {@link AnnotationInterceptor$Rpc} and the peer address will be filled by
         * {@link ClientDestTracingFilterInterceptor} later.
         */
        AbstractSpan finagleSpan = ContextManager.createExitSpan(PENDING_OP_NAME, "");

        finagleSpan.setComponent(FINAGLE);
        SpanLayer.asRPCFramework(finagleSpan);

        getLocalContextHolder().let(SW_SPAN, finagleSpan);
        getMarshalledContextHolder().let(SWContextCarrier$.MODULE$, new SWContextCarrier());
    }

    @Override
    public Object afterMethodImpl(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes, Object ret) throws Throwable {

        final AbstractSpan finagleSpan = getSpan();
        getLocalContextHolder().remove(SW_SPAN);
        getMarshalledContextHolder().remove(SWContextCarrier$.MODULE$);

        /*
         * If the intercepted method throws exception, the ret will be null
         */
        if (ret == null) {
            ContextManager.stopSpan(finagleSpan);
        } else {
            finagleSpan.prepareForAsync();
            ContextManager.stopSpan(finagleSpan);

            ((Future<?>) ret).addEventListener(new FutureEventListener<Object>() {
                @Override
                public void onSuccess(Object value) {
                    finagleSpan.asyncFinish();
                }

                @Override
                public void onFailure(Throwable cause) {
                    finagleSpan.log(cause);
                    finagleSpan.asyncFinish();
                }
            });
        }
        return ret;
    }

    @Override
    public void handleMethodExceptionImpl(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes, Throwable throwable) {
        ContextManager.activeSpan().log(throwable);
    }
}
