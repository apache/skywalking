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

package org.apache.skywalking.apm.plugin.customize.interceptor;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.plugin.customize.conf.CustomizeConfiguration;
import org.apache.skywalking.apm.plugin.customize.conf.MethodConfiguration;
import org.apache.skywalking.apm.plugin.customize.constants.Constants;
import org.apache.skywalking.apm.agent.core.util.CustomizeExpression;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BaseInterceptorMethods {

    void beforeMethod(Method method, Object[] allArguments) {
        Map<String, Object> configuration = CustomizeConfiguration.INSTANCE.getConfiguration(method);
        if (!MethodConfiguration.isCloseBeforeMethod(configuration)) {
            String operationName = MethodConfiguration.getOperationName(configuration);
            Map<String, Object> context = CustomizeExpression.evaluationContext(allArguments);
            if (context == null || context.isEmpty()) {
                ContextManager.createLocalSpan(operationName);
            } else {

                Map<String, String> tags = MethodConfiguration.getTags(configuration);
                Map<String, String> spanTags = new HashMap<String, String>();
                Map<String, String> logs = MethodConfiguration.getLogs(configuration);
                Map<String, String> spanLogs = new HashMap<String, String>();

                List<String> operationNameSuffixes = MethodConfiguration.getOperationNameSuffixes(configuration);
                StringBuilder operationNameSuffix = new StringBuilder();
                if (operationNameSuffixes != null && !operationNameSuffixes.isEmpty()) {
                    for (String expression : operationNameSuffixes) {
                        operationNameSuffix.append(Constants.OPERATION_NAME_SEPARATOR);
                        operationNameSuffix.append(CustomizeExpression.parseExpression(expression, context));
                    }
                }
                if (tags != null && !tags.isEmpty()) {
                    for (Map.Entry<String, String> expression: tags.entrySet()) {
                        spanTags.put(expression.getKey(), CustomizeExpression.parseExpression(expression.getValue(), context));
                    }
                }
                if (logs != null && !logs.isEmpty()) {
                    for (Map.Entry<String, String> entries : logs.entrySet()) {
                        String expression = logs.get(entries.getKey());
                        spanLogs.put(entries.getKey(), CustomizeExpression.parseExpression(expression, context));
                    }
                }
                operationName = operationNameSuffix.insert(0, operationName).toString();

                AbstractSpan span = ContextManager.createLocalSpan(operationName);
                if (!spanTags.isEmpty()) {
                    for (Map.Entry<String, String> tag : spanTags.entrySet()) {
                        span.tag(Tags.ofKey(tag.getKey()), tag.getValue());
                    }
                }
                if (!spanLogs.isEmpty()) {
                    span.log(System.currentTimeMillis(), spanLogs);
                }
            }
        }
    }

    void afterMethod(Method method) {
        if (!MethodConfiguration.isCloseAfterMethod(CustomizeConfiguration.INSTANCE.getConfiguration(method))) {
            ContextManager.stopSpan();
        }
    }

    void handleMethodException(Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }

}
