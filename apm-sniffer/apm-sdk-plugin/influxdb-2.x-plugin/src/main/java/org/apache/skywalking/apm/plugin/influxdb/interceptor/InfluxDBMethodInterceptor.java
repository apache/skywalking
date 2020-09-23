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

package org.apache.skywalking.apm.plugin.influxdb.interceptor;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.influxdb.InfluxDBPluginConfig;
import org.apache.skywalking.apm.plugin.influxdb.define.Constants;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

import static org.apache.skywalking.apm.plugin.influxdb.define.Constants.DB_TYPE;

public class InfluxDBMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        String methodName = method.getName();
        String peer = String.valueOf(objInst.getSkyWalkingDynamicField());
        AbstractSpan span = ContextManager.createExitSpan("InfluxDB/" + methodName, peer);
        span.setComponent(ComponentsDefine.INFLUXDB_JAVA);
        SpanLayer.asDB(span);
        Tags.DB_TYPE.set(span, DB_TYPE);

        if (allArguments.length <= 0 || !InfluxDBPluginConfig.Plugin.InfluxDB.TRACE_INFLUXQL) {
            return;
        }

        if (allArguments[0] instanceof Query) {
            Query query = (Query) allArguments[0];
            Tags.DB_INSTANCE.set(span, query.getDatabase());
            Tags.DB_STATEMENT.set(span, query.getCommand());
            return;
        }

        if (Constants.WRITE_METHOD.equals(methodName)) {
            if (allArguments[0] instanceof BatchPoints) {
                BatchPoints batchPoints = (BatchPoints) allArguments[0];
                Tags.DB_INSTANCE.set(span, batchPoints.getDatabase());
                Tags.DB_STATEMENT.set(span, batchPoints.lineProtocol());
                return;
            }
            if (allArguments.length == 5) {
                if (allArguments[0] instanceof String) {
                    Tags.DB_INSTANCE.set(span, (String) allArguments[0]);
                }
                if (allArguments[4] instanceof String) {
                    Tags.DB_STATEMENT.set(span, (String) allArguments[4]);
                }
                return;
            }
            if (allArguments.length == 3 && allArguments[2] instanceof Point) {
                Tags.DB_INSTANCE.set(span, (String) allArguments[0]);
                Tags.DB_STATEMENT.set(span, ((Point) allArguments[2]).lineProtocol());
            }
        }

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
        ContextManager.activeSpan().log(t);
    }
}
