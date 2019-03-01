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

package org.apache.skywalking.apm.plugin.customize.interceptor.spel;

import com.google.gson.Gson;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.plugin.customize.conf.CustomizeConfiguration;
import org.apache.skywalking.apm.plugin.customize.conf.SpELMethodConfiguration;
import org.apache.skywalking.apm.plugin.customize.constants.Constant;
import org.apache.skywalking.apm.plugin.customize.interceptor.BaseInterceptorMethods;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhaoyuguang
 */

class BaseSpELInterceptorMethods extends BaseInterceptorMethods {
    void beforeMethod(Method method) {
        SpELMethodConfiguration configuration = (SpELMethodConfiguration) CustomizeConfiguration.INSTANCE.getConfiguration(method);
        if (configuration != null && !configuration.isCloseBeforeMethod()) {
            ExpressionParser parser = new SpelExpressionParser();
            EvaluationContext context = new StandardEvaluationContext();
            String[] parameterNames = new LocalVariableTableParameterNameDiscoverer().getParameterNames(method);
            for (int i = 0; i < method.getParameterTypes().length; i++) {
                context.setVariable(parameterNames[i], method.getParameterTypes()[i]);
            }

            Map<String, String> tags = configuration.getTags();
            Map<String, String> spanTags = new HashMap<String, String>();
            Map<String, String> logs = configuration.getLogs();
            Map<String, String> spanLogs = new HashMap<String, String>();
            List<String> operationNameSuffixes = configuration.getOperationNameSuffixes();
            StringBuilder operationNameSuffix = new StringBuilder();

            if (operationNameSuffixes != null && !operationNameSuffixes.isEmpty()) {
                for (String spEL : operationNameSuffixes) {
                    operationNameSuffix.append(Constant.OPERATION_NAME_SEPARATOR);
                    operationNameSuffix.append(new Gson().toJson(parser.parseExpression(spEL).getValue(context)));
                }
                operationNameSuffix.deleteCharAt(operationNameSuffix.length() - 1);
            }
            if (tags != null && !tags.isEmpty()) {
                for (String key : tags.keySet()) {
                    String spEL = tags.get(key);
                    spanTags.put(key, new Gson().toJson(parser.parseExpression(spEL).getValue(context)));
                }
            }
            if (logs != null && !logs.isEmpty()) {
                for (String key : logs.keySet()) {
                    String spEL = logs.get(key);
                    spanLogs.put(key, new Gson().toJson(parser.parseExpression(spEL).getValue(context)));
                }
            }
            String operationName = operationNameSuffix.insert(0, configuration.getOperationName()).toString();

            AbstractSpan span = ContextManager.createLocalSpan(operationName);
            if (!spanTags.isEmpty()) {
                for (Map.Entry<String, String> tag : spanTags.entrySet()) {
                    span.tag(tag.getKey(), tag.getValue());
                }
            }
            if (!spanLogs.isEmpty()) {
                span.log(System.currentTimeMillis(), logs);
            }
        }
    }
}
