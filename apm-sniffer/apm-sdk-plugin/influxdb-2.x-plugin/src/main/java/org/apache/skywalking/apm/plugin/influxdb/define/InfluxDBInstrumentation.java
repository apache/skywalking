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

package org.apache.skywalking.apm.plugin.influxdb.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;


/**
 * Enhance InfluxDB InfluxDBFactory
 * Really impl class {@link org.influxdb.impl.InfluxDBImpl}
 *
 * @since  2020/05/22
 */
public class InfluxDBInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.influxdb.impl.InfluxDBImpl";
    private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.influxdb.interceptor.InfluxDBConstructorInterceptor";
    private static final String INFLUXDB_METHOD_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.influxdb.interceptor.InfluxDBMethodInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return ElementMatchers.takesArgument(0, String.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return INTERCEPTOR_CLASS;
                }
            }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
      return new InstanceMethodsInterceptPoint[] {
          new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
              ElementMatcher.Junction<MethodDescription> matcher = none();
              final Set<String> setters = Constants.MATCHER_METHOD_NAME;
              for (String setter : setters) {
                matcher = matcher.or(named(setter));
              }
              return matcher;
            }

            @Override
            public String getMethodsInterceptor() {
              return getInstanceMethodsInterceptor();
            }

            @Override
            public boolean isOverrideArgs() {
              return false;
            }
          }
      };
    }

    protected String getInstanceMethodsInterceptor() {
        return INFLUXDB_METHOD_INTERCEPT_CLASS;
    }
}
