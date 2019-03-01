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

package org.apache.skywalking.apm.plugin.customize.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.plugin.customize.conf.CustomizeConfiguration;
import org.apache.skywalking.apm.plugin.customize.constants.CustomizeLanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The instance of customize instrumentation.
 *
 * @author zhaoyuguang
 */

public class CustomizeInstanceInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private Class enhanceClass;

    public CustomizeInstanceInstrumentation(Class enhanceClass) {
        this.enhanceClass = enhanceClass;
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        final Map<CustomizeLanguage, ElementMatcher> configurations = CustomizeConfiguration.INSTANCE.getInterceptPoints(enhanceClass, false);
        if (configurations == null) {
            return new InstanceMethodsInterceptPoint[0];
        } else {
            List<InstanceMethodsInterceptPoint> interceptPoints = new ArrayList<InstanceMethodsInterceptPoint>();
            for (final CustomizeLanguage language : configurations.keySet()) {
                interceptPoints.add(new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return configurations.get(language);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return language.getInterceptor(false).getName();
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                });
            }
            return interceptPoints.toArray(new InstanceMethodsInterceptPoint[interceptPoints.size()]);
        }
    }

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(enhanceClass.getName());
    }
}
