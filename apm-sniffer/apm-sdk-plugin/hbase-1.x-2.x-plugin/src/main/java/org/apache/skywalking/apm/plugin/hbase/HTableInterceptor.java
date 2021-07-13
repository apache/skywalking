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

package org.apache.skywalking.apm.plugin.hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.ClusterConnection;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.OperationWithAttributes;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class HTableInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final String PREFIX_OPERATION_NAME = "/HTable/";
    private static final String HBASE_DB_TYPE = "hbase";

    @Override
    @SuppressWarnings("unchecked, rawtypes")
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        boolean canTracingServer = false;
        List<OperationWithAttributes> operations = null;
        OperationWithAttributes operation = null;
        if (allArguments != null && allArguments.length > 0) {
            if (allArguments[0] instanceof List) {
                List list = (List) allArguments[0];
                if (!CollectionUtil.isEmpty(list) && list.get(0) instanceof OperationWithAttributes) {
                    operations = list;
                    canTracingServer = true;
                }
            } else if (allArguments[0] instanceof OperationWithAttributes) {
                operation = (OperationWithAttributes) allArguments[0];
                canTracingServer = true;
            }
        }
        AbstractSpan span;
        if (canTracingServer) {
            ContextCarrier contextCarrier = new ContextCarrier();
            span = ContextManager.createExitSpan(PREFIX_OPERATION_NAME + method.getName(),
                    contextCarrier, (String) objInst.getSkyWalkingDynamicField());
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                if (operation != null) {
                    operation.setAttribute(next.getHeadKey(), next.getHeadValue().getBytes(StandardCharsets.UTF_8));
                } else {
                    for (OperationWithAttributes o : operations) {
                        o.setAttribute(next.getHeadKey(), next.getHeadValue().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        } else {
            span = ContextManager.createExitSpan(PREFIX_OPERATION_NAME + method.getName(),
                    (String) objInst.getSkyWalkingDynamicField());
        }
        span.setComponent(ComponentsDefine.HBASE);
        Tags.DB_TYPE.set(span, HBASE_DB_TYPE);
        Tags.DB_INSTANCE.set(span, ((HTable) objInst).getName().getNameAsString());
        SpanLayer.asDB(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) throws Throwable {
        Configuration connection = ((ClusterConnection) allArguments[1]).getConfiguration();
        Field field = connection.getClass().getDeclaredField("overlay");
        field.setAccessible(true);
        Properties properties = (Properties) field.get(connection);
        for (Map.Entry entry : properties.entrySet()) {
            if ("hbase.zookeeper.quorum".equals(entry.getKey())) {
                objInst.setSkyWalkingDynamicField(entry.getValue().toString());
            }
        }
    }
}
