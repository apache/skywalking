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

package org.apache.skywalking.apm.toolkit.activation.log.log4j.v1.x.log;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.log.LogReportServiceClient;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.apm.network.logging.v3.YAMLLog;
import org.slf4j.event.LoggingEvent;

public class GRPCLogAppenderInterceptor implements InstanceMethodsAroundInterceptor {

    private LogReportServiceClient client;

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        if (Objects.isNull(client)) {
            client = ServiceManager.INSTANCE.findService(LogReportServiceClient.class);
            if (Objects.isNull(client)) {
                return;
            }
        }
        LoggingEvent event = (LoggingEvent) allArguments[0];
        if (event != null) {
            LogData logData = transform(event);
            if (Objects.nonNull(logData)) {
                client.produce(logData);
            }
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {

    }

    private LogData transform(LoggingEvent event) {
        if (Objects.isNull(event)) {
            return null;
        }
        LogData.Builder logBuilder = LogData.newBuilder()
                .setTimestamp(event.getTimeStamp())
                .setService(Config.Agent.SERVICE_NAME)
                .setServiceInstance(Config.Agent.INSTANCE_NAME)
                .setTraceContext(TraceContext.newBuilder()
                        .setTraceId(ContextManager.getGlobalTraceId()).build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey("level").setValue(event.getLevel().toString()).build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey("logger").setValue(event.getLoggerName()).build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey("thread").setValue(event.getThreadName()).build());

        LogDataBody.Builder logDataBodyBuilder = LogDataBody.newBuilder();
        LogDataBody.ContentCase contentType = Optional.ofNullable(LogDataBody.ContentCase.forNumber(
                Config.GRPCLog.LOG_BODY_TYPE)).orElse(LogDataBody.ContentCase.TEXT);
        switch (contentType) {
            case JSON:
                logDataBodyBuilder.setType(LogDataBody.ContentCase.JSON.name())
                        .setJson(JSONLog.newBuilder().setJson(event.getMessage()).build()).build();
                break;
            case YAML:
                logDataBodyBuilder.setType(LogDataBody.ContentCase.YAML.name())
                        .setYaml(YAMLLog.newBuilder().setYaml(event.getMessage()).build()).build();
                break;
            case TEXT:
            default:
                logDataBodyBuilder.setType(LogDataBody.ContentCase.TEXT.name())
                        .setText(TextLog.newBuilder().setText(event.getMessage()).build()).build();
        }
        logBuilder.setBody(logDataBodyBuilder);

        return logBuilder.build();
    }
}
