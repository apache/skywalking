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
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.util.ThrowableTransformer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.remote.LogReportServiceClient;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;

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
        if (Objects.nonNull(event)) {
            client.produce(transform((AppenderSkeleton) objInst, event));
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

    /**
     * transforms {@link LoggingEvent}  to {@link LogData}
     *
     *
     * @param appender the real {@link AppenderSkeleton appender}
     * @param event {@link LoggingEvent}
     * @return {@link LogData} with filtered trace context in order to reduce the cost on the network
     */
    private LogData transform(final AppenderSkeleton appender, LoggingEvent event) {
        LogData.Builder builder = LogData.newBuilder()
                .setTimestamp(event.getTimeStamp())
                .setService(Config.Agent.SERVICE_NAME)
                .setServiceInstance(Config.Agent.INSTANCE_NAME)
                .setTraceContext(TraceContext.newBuilder()
                        .setTraceId(ContextManager.getGlobalTraceId())
                        .setSpanId(ContextManager.getSpanId())
                        .setTraceSegmentId(ContextManager.getSegmentId())
                        .build())
                .setTags(LogTags.newBuilder()
                        .addData(KeyStringValuePair.newBuilder()
                                .setKey("level").setValue(event.getLevel().toString()).build())
                        .addData(KeyStringValuePair.newBuilder()
                                .setKey("logger").setValue(event.getLoggerName()).build())
                        .addData(KeyStringValuePair.newBuilder()
                                .setKey("thread").setValue(event.getThreadName()).build())
                        .build())
                .setBody(LogDataBody.newBuilder().setType(LogDataBody.ContentCase.TEXT.name())
                                    .setText(TextLog.newBuilder().setText(transformLogText(appender, event)).build()).build());
        return -1 == ContextManager.getSpanId() ? builder.build()
                : builder.setTraceContext(TraceContext.newBuilder()
                        .setTraceId(ContextManager.getGlobalTraceId())
                        .setSpanId(ContextManager.getSpanId())
                        .setTraceSegmentId(ContextManager.getSegmentId())
                        .build()).build();
    }

    private String transformLogText(final AppenderSkeleton appender, final LoggingEvent event) {
        if (appender.getLayout() != null) {
            return appender.getLayout().format(event);
        }
        final String throwableString = Objects.isNull(event.getThrowableInformation()) ? "" :
            ThrowableTransformer.INSTANCE.convert2String(event.getThrowableInformation().getThrowable(), 2048);
        return event.getMessage() + "\n" + throwableString;
    }
}
