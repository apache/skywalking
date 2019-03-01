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
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.plugin.customize.conf.CustomizeConfiguration;
import org.apache.skywalking.apm.plugin.customize.constants.CustomizeLanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The static of customize instrumentation.
 *
 * @author zhaoyuguang
 */

public class CustomizeStaticInstrumentation extends ClassStaticMethodsEnhancePluginDefine {
    private Class enhanceClass;


    public CustomizeStaticInstrumentation(Class enhanceClass) {
        this.enhanceClass = enhanceClass;
    }

    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        final Map<CustomizeLanguage, ElementMatcher> configurations = CustomizeConfiguration.INSTANCE.getInterceptPoints(enhanceClass, true);
        if (configurations == null) {
            return new StaticMethodsInterceptPoint[0];
        } else {
            List<StaticMethodsInterceptPoint> interceptPoints = new ArrayList<StaticMethodsInterceptPoint>();
            for (final CustomizeLanguage language : configurations.keySet()) {
                interceptPoints.add(new StaticMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return configurations.get(language);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return language.getInterceptor(true).getName();
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                });
            }
            return interceptPoints.toArray(new StaticMethodsInterceptPoint[interceptPoints.size()]);
        }
    }

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(enhanceClass.getName());
    }
}
