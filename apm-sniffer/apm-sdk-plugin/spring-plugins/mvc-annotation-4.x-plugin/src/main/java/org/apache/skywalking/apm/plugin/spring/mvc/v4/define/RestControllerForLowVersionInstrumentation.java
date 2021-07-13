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

package org.apache.skywalking.apm.plugin.spring.mvc.v4.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class RestControllerForLowVersionInstrumentation extends AbstractControllerInstrumentation {
    public static final String WITNESS_CLASSES_LOW_VERSION = "org.springframework.web.method.HandlerMethodSelector";

    public static final String ENHANCE_ANNOTATION = "org.springframework.web.bind.annotation.RestController";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        ConstructorInterceptPoint constructorInterceptPoint = new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return any();
            }

            @Override
            public String getConstructorInterceptor() {
                return "org.apache.skywalking.apm.plugin.spring.mvc.v4.ControllerForLowVersionConstructorInterceptor";
            }
        };
        return new ConstructorInterceptPoint[] {constructorInterceptPoint};
    }

    @Override
    protected String[] witnessClasses() {
        return new String[] {
            WITHNESS_CLASSES,
            "org.springframework.cache.interceptor.DefaultKeyGenerator",
            WITNESS_CLASSES_LOW_VERSION
        };
    }

    @Override
    protected String[] getEnhanceAnnotations() {
        return new String[] {ENHANCE_ANNOTATION};
    }
}
