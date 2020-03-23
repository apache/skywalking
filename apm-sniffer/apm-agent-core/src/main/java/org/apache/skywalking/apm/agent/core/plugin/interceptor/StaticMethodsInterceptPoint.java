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

package org.apache.skywalking.apm.agent.core.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * One of the three "Intercept Point". "Intercept Point" is a definition about where and how intercept happens. In this
 * "Intercept Point", the definition targets class's static methods, and the interceptor.
 * <p>
 * ref to two others: {@link ConstructorInterceptPoint} and {@link InstanceMethodsInterceptPoint}
 * <p>
 */
public interface StaticMethodsInterceptPoint {
    /**
     * static methods matcher.
     *
     * @return matcher instance.
     */
    ElementMatcher<MethodDescription> getMethodsMatcher();

    /**
     * @return represents a class name, the class instance must instanceof StaticMethodsAroundInterceptor.
     */
    String getMethodsInterceptor();

    boolean isOverrideArgs();
}
