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

package org.apache.skywalking.apm.plugin.logger;

import org.apache.logging.log4j.core.Logger;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

public class Log4j2LoggerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ContextConfig.LoggerConfig CONFIG = ContextConfig.getInstance().getLog4j2Config();

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Logger logger = (org.apache.logging.log4j.core.Logger) objInst;
        if (!CONFIG.isValid()) {
            validConfig(logger, CONFIG);
        }
        CONFIG.logIfNecessary(logger.getName(), method.getName(), allArguments);
    }

    private void validConfig(Logger logger, ContextConfig.LoggerConfig config) {
        if (logger == null || config == null) {
            return;
        }
        config.setValid(true);
        switch (config.getLevel().toString()) {
            case "TRACE":
                if (logger.isTraceEnabled()) {
                    config.setLevel(ContextConfig.LogLevel.TRACE);
                    break;
                }
            case "DEBUG":
                if (logger.isDebugEnabled()) {
                    config.setLevel(ContextConfig.LogLevel.DEBUG);
                    break;
                }
            case "INFO":
                if (logger.isInfoEnabled()) {
                    config.setLevel(ContextConfig.LogLevel.INFO);
                    break;
                }
            case "WARN":
                if (logger.isWarnEnabled()) {
                    config.setLevel(ContextConfig.LogLevel.WARN);
                    break;
                }
            case "ERROR":
                if (logger.isErrorEnabled()) {
                    config.setLevel(ContextConfig.LogLevel.ERROR);
                    break;
                }
            case "FATAL":
                if (logger.isFatalEnabled()) {
                    config.setLevel(ContextConfig.LogLevel.FATAL);
                    break;
                }
            default:
                //do nothing
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return null;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }
}
