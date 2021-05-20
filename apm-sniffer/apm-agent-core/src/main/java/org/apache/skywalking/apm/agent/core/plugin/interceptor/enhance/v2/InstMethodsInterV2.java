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
 */

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;

/**
 * The actual byte-buddy's interceptor to intercept class instance methods. In this class, it provide a bridge between
 * byte-buddy and sky-walking plugin.
 */
public class InstMethodsInterV2 {
    private static final ILog LOGGER = LogManager.getLogger(InstMethodsInterV2.class);

    private InstanceMethodsAroundInterceptorV2 interceptor;

    public InstMethodsInterV2(String instanceMethodsAroundInterceptorClassName, ClassLoader classLoader) {
        try {
            interceptor = InterceptorInstanceLoader.load(instanceMethodsAroundInterceptorClassName, classLoader);
        } catch (Throwable t) {
            throw new PluginException("Can't create InstanceMethodsAroundInterceptor.", t);
        }
    }

    @RuntimeType
    public Object intercept(@This Object obj, @AllArguments Object[] allArguments, @SuperCall Callable<?> zuper,
                            @Origin Method method) throws Throwable {
        EnhancedInstance targetObject = (EnhancedInstance) obj;

        MethodInvocationContext context = new MethodInvocationContext();
        try {
            interceptor.beforeMethod(targetObject, method, allArguments, method.getParameterTypes(), context);
        } catch (Throwable t) {
            LOGGER.error(t, "class[{}] before method[{}] intercept failure", obj.getClass(), method.getName());
        }

        Object ret = null;
        try {
            if (!context.isContinue()) {
                ret = context._ret();
            } else {
                ret = zuper.call();
            }
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(targetObject, method, allArguments, method.getParameterTypes(), t, context);
            } catch (Throwable t2) {
                LOGGER.error(t2, "class[{}] handle method[{}] exception failure", obj.getClass(), method.getName());
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(targetObject, method, allArguments, method.getParameterTypes(), ret, context);
            } catch (Throwable t) {
                LOGGER.error(t, "class[{}] after method[{}] intercept failure", obj.getClass(), method.getName());
            }
        }
        return ret;
    }
}
