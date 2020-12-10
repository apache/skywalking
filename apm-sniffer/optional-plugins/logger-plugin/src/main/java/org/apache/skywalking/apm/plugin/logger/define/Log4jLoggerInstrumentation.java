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

package org.apache.skywalking.apm.plugin.logger.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.plugin.logger.ContextConfig;
import org.apache.skywalking.apm.plugin.logger.ContextConfig.LogLevel;

import java.util.Arrays;
import java.util.function.Function;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class Log4jLoggerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    private static final String ENHANCE_CLASS = "org.apache.log4j.Category";
    private static final ContextConfig.LoggerConfig CONFIG = ContextConfig.getInstance().getLog4jConfig();

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        if (CONFIG == null) {
            return null;
        }
        return Arrays.stream(LogLevel.values())
                .filter(it -> it.getPriority() >= CONFIG.getLevel().getPriority())
                .map((Function<LogLevel, InstanceMethodsInterceptPoint>) level -> new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(level.name().toLowerCase());
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return String.format("org.apache.skywalking.apm.plugin.logger.%sLog4jLoggerInterceptor",
                                level.name().charAt(0) + level.name().substring(1).toLowerCase());
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }).toArray(InstanceMethodsInterceptPoint[]::new);
    }
}
