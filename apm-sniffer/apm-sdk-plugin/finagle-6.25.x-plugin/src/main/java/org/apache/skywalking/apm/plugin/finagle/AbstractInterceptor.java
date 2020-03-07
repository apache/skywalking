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

package org.apache.skywalking.apm.plugin.finagle;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * Finagle is an asynchronous rpc framework, every method in its rpc call stack return a future, which means each
 * method may be executed in other thread. The data transfer mechanism across threads or across processes of finagle is
 * achieved by using com.twitter.finagle.context. There are two kinds of context, LocalContext used to transfer data
 * across threads, MarshalledContext used to transfer data across processes.
 *
 * <h3> A simple finagle rpc stack </h3>
 *
 * +------------------------------------------------------------------------------------------------+
 * |       Client                                             Server                                |
 * |                                                                                                |
 * |   initiate rpc          (user thread)                invoke service         (other thread)     |
 * |         |                                                  /|\                                 |
 * |        \|/                                                  |                                  |
 * |  ClientTracingFilter    (user thread)                       |                                  |
 * |         |                                          +------------------+                        |
 * |        \|/                                         |                  |                        |
 * |  +------------------+                              |                  |                        |
 * |  |  other filters   |                              |                  |                        |
 * |  |  in the          |   (other thread)             |                  |                        |
 * |  |  rpc call stack  |                              |  other filters   |                        |
 * |  +----------+-------+                              |  in the          |     (other thread)     |
 * |         |                                          |  rpc call stack  |                        |
 * |        \|/                                         |                  |                        |
 * |  ClientDestTracingFilter (other thread)            |                  |                        |
 * |         |                                          |                  |                        |
 * |        \|/                                         |                  |                        |
 * |  +------------------+                              +------------------+                        |
 * |  |  other filters   |                                        /|\                               |
 * |  |  in the          |    (other thread)                       |                                |
 * |  |  rpc call stack  |                                         |                                |
 * |  +----------+-------+                                ServerTracingFilter    (usually io thread)|
 * |         |                                                    /|\                               |
 * |        \|/                                                    |                                |
 * |  +----------------------------------------------------------------------------------+          |
 * |  |                         Protocol specified transport                             |          |
 * |  |                   such as http, thrift, redis, mysql, etc.                       |          |
 * |  +----------------------------------------------------------------------------------+          |
 * +------------------------------------------------------------------------------------------------+
 *
 * <h3> Plugin Implementation </h3>
 *
 * In client side, We create finagle exitspan in the ClientTracingFilter, then add {@link SWContextCarrier} into the
 * MarshalledContext, however in ClientTracingFilter, we can not know the remote address, So we add exitspan
 * into the LocalContext, when the request reaches ClientDestTracingFilter, we know the remote address, then we can get
 * exitspan from LocalContext and set remote address.
 *
 * In Server side, we just create finagle entryspan in the ServerTracingFilter.
 */
abstract class AbstractInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        if (CompatibilityChecker.isCompatible()) {
            onConstructImpl(objInst, allArguments);
        }
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        if (CompatibilityChecker.isCompatible()) {
            beforeMethodImpl(objInst, method, allArguments, argumentsTypes, result);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (CompatibilityChecker.isCompatible()) {
            return afterMethodImpl(objInst, method, allArguments, argumentsTypes, ret);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        if (CompatibilityChecker.isCompatible()) {
            handleMethodExceptionImpl(objInst, method, allArguments, argumentsTypes, t);
        }
    }

    abstract protected void onConstructImpl(EnhancedInstance objInst, Object[] allArguments);

    abstract protected void beforeMethodImpl(EnhancedInstance objInst, Method method, Object[] allArguments,
                                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable;

    abstract protected Object afterMethodImpl(EnhancedInstance objInst, Method method, Object[] allArguments,
                                              Class<?>[] argumentsTypes, Object ret) throws Throwable;

    abstract protected void handleMethodExceptionImpl(EnhancedInstance objInst, Method method, Object[] allArguments,
                                                      Class<?>[] argumentsTypes, Throwable t);
}
