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

package org.apache.skywalking.apm.agent.core.plugin.bootstrap.interceptor;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.plugin.bootstrap.IBootstrapLog;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.BootstrapInterRuntimeAssist;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * --------CLASS TEMPLATE---------
 * <p>Author, Wu Sheng </p>
 * <p>Comment, don't change this unless you are 100% sure the agent core mechanism for bootstrap class
 * instrumentation.</p>
 * <p>Date, 24th July 2019</p>
 * -------------------------------
 * <p>
 * This class wouldn't be loaded in real env. This is a class template for dynamic class generation.
 */
public class BootstrapConstructorInter {

    private InstanceConstructorInterceptor interceptor;
    private static IBootstrapLog LOGGER;

    public BootstrapConstructorInter(String interceptorClassName) {
        prepare(interceptorClassName);
    }

    /**
     * Intercept the target constructor.
     *
     * @param obj          target class instance.
     * @param allArguments all constructor arguments
     */
    @RuntimeType
    public void intercept(@This Object obj, @AllArguments Object[] allArguments) {
        try {

            EnhancedInstance targetObject = (EnhancedInstance) obj;

            if (interceptor == null) {
                return;
            }
            interceptor.onConstruct(targetObject, allArguments);
        } catch (Throwable t) {
            LOGGER.error("ConstructorInter failure.", t);
        }
    }

    /**
     * Prepare the context. Link to the agent core in AppClassLoader.
     */
    private void prepare(String interceptorClassName) {
        if (this.interceptor == null) {
            ClassLoader loader = BootstrapInterRuntimeAssist.getAgentClassLoader();

            if (loader != null) {
                IBootstrapLog logger = BootstrapInterRuntimeAssist.getLogger(loader, interceptorClassName);
                if (logger != null) {
                    LOGGER = logger;

                    this.interceptor = BootstrapInterRuntimeAssist.createInterceptor(loader, interceptorClassName, LOGGER);
                }
            } else {
                LOGGER.error("Runtime ClassLoader not found when create {}." + interceptorClassName);
            }
        }
    }
}
