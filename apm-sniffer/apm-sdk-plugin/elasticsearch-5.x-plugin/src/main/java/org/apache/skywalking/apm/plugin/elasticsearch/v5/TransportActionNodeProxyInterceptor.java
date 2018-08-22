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

package org.apache.skywalking.apm.plugin.elasticsearch.v5;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.elasticsearch.cluster.node.DiscoveryNode;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.agent.core.conf.Config.Plugin.Elasticsearch.TRACE_DSL;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.*;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Util.wrapperNullStringValue;

/**
 * @author oatiz.
 */
public class TransportActionNodeProxyInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        ElasticSearchEnhanceInfo enhanceInfo = (ElasticSearchEnhanceInfo) ContextManager.getRuntimeContext().get(ES_ENHANCE_INFO);

        String opType = allArguments[1].getClass().getSimpleName();
        String operationName = ELASTICSEARCH_DB_OP_PREFIX + opType;
        AbstractSpan span = ContextManager.createExitSpan(operationName, enhanceInfo.getTransportAddress());
        span.setComponent(ComponentsDefine.TRANSPORT_CLIENT);
        Tags.DB_TYPE.set(span, DB_TYPE);
        Tags.DB_INSTANCE.set(span, enhanceInfo.getClusterName());
        if (TRACE_DSL) {
            Tags.DB_STATEMENT.set(span, enhanceInfo.getSource());
        }
        span.tag(ES_NODE, ((DiscoveryNode) allArguments[0]).getAddress().toString());
        span.tag(ES_INDEX, wrapperNullStringValue(enhanceInfo.getIndices()));
        span.tag(ES_TYPE, wrapperNullStringValue(enhanceInfo.getTypes()));
        SpanLayer.asDB(span);
        ContextManager.getRuntimeContext().remove(ES_ENHANCE_INFO);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }

}
