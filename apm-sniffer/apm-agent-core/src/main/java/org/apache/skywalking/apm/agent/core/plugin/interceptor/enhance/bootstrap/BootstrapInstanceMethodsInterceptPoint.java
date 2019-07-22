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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.bootstrap;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Very similar to InstanceMethodsInterceptPoint, but due to do JVM instrumentation, a ByteBuddy interceptor required.
 *
 * @author wusheng
 */
public interface BootstrapInstanceMethodsInterceptPoint {
    /**
     * class instance methods matcher.
     *
     * @return methods matcher
     */
    ElementMatcher<MethodDescription> getMethodsMatcher();

    /**
     * @return class will use as interceptor, this Class need to follow ByteBuddy static @RuntimeType interceptor usage.
     */
    Class getMethodsInterceptor();

    boolean isOverrideArgs();
}
