/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.grpc.v1;

import io.grpc.Metadata;
import io.grpc.Status;
import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;

import static org.skywalking.apm.plugin.grpc.v1.define.Constants.ON_NEXT_COUNT_TAG_KEY;
import static org.skywalking.apm.plugin.grpc.v1.define.Constants.STREAM_CALL_OPERATION_NAME_SUFFIX;

/**
 * {@link StreamClientOnCloseInterceptor} stop the active span when the call end.
 *
 * @author zhangxin
 */
public class StreamClientOnCloseInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        Status status = (Status)allArguments[0];
        if (status.getCode() == Status.Code.UNAVAILABLE) {
            GRPCDynamicFields cachedObjects = (GRPCDynamicFields)objInst.getSkyWalkingDynamicField();
            AbstractSpan span = ContextManager.createLocalSpan(cachedObjects.getRequestMethodName() + STREAM_CALL_OPERATION_NAME_SUFFIX);
            span.setComponent(ComponentsDefine.GRPC);
            span.setLayer(SpanLayer.RPC_FRAMEWORK);
            ContextManager.continued(cachedObjects.getSnapshot());
        }

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.tag(ON_NEXT_COUNT_TAG_KEY, String.valueOf(((GRPCDynamicFields)objInst.getSkyWalkingDynamicField()).getOnNextCount()));

        Status status = (Status)allArguments[0];
        if (status != Status.OK) {
            activeSpan.errorOccurred().log(status.asRuntimeException((Metadata)allArguments[1]));
            Tags.STATUS_CODE.set(activeSpan, status.getCode().toString());
        }

        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
