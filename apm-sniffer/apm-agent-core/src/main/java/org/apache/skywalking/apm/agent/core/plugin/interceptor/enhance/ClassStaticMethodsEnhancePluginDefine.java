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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;

/**
 * Plugins, which only need enhance class static methods. Actually, inherit from {@link
 * ClassStaticMethodsEnhancePluginDefine} has no differences with inherit from {@link ClassEnhancePluginDefine}. Just
 * override {@link ClassEnhancePluginDefine#getConstructorsInterceptPoints} and {@link
 * ClassEnhancePluginDefine#getInstanceMethodsInterceptPoints}, and return NULL, which means nothing to enhance.
 */
public abstract class ClassStaticMethodsEnhancePluginDefine extends ClassEnhancePluginDefine {

    /**
     * @return null, means enhance no constructors.
     */
    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    /**
     * @return null, means enhance no instance methods.
     */
    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return null;
    }
}
