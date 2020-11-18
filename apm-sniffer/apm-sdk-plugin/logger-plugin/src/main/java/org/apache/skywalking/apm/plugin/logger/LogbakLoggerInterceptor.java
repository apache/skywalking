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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.logger.logbak.ContextConfig;
import org.apache.skywalking.apm.plugin.logger.logbak.LoggerConfig;
import org.apache.skywalking.apm.plugin.logger.util.LoggerContextUtil;

import java.lang.reflect.Method;
import java.util.List;

public class LogbakLoggerInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
//        //test
//        long timeStamp = System.currentTimeMillis();
//        Level level = getLevel(method.getName());
//        String theadName = Thread.currentThread().getName();

        if (isRecorder(getLevel(method.getName()), allArguments)) {
            //TODO
            //recorde log and save it to span log

        } else {
            return;
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return null;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }

    private boolean isRecorder(Level methodLevel, Object[] allArguments) {
        LoggerConfig logbakConig = ContextConfig.getInstance().getLogbakConfig();
        Level recorderLevel = logbakConig.getLevel();
        LoggerContext loggerContext = LoggerContextUtil.LOCAL_LOGGER_CONTEXT.get();
        // judge level
        if (!levelBig(methodLevel, recorderLevel)) {
            return false;
        }
        // judge package
        List<String> packages = logbakConig.getPackages();
//        if (!"*".equals(packages.get(0))) {
            List packageName = loggerContext.getFrameworkPackages();
            
            if(true){
                int test = 1;
            }
//        }

        return true;
    }

    private boolean levelBig(Level methodLevel, Level recorderLevel) {
        if (methodLevel.toInt() >= recorderLevel.toInt()) {
            return true;
        }
        return false;
    }

    /**
     * covert to log level acording to the name of method
     *
     * @param methodName the name of method
     * @return level
     */
    private Level getLevel(String methodName) {
        if (methodName == null || "".equals(methodName)) {
            return null;
        }
        switch (methodName) {
            case "trace":
                return Level.TRACE;
            case "debug":
                return Level.DEBUG;
            case "error":
                return Level.ERROR;
            default:
                return null;
        }
    }
}
