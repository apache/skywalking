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

import javax.annotation.Nullable;

/**
 * <h3> Finagle context usage </h3>
 *
 * The only way we can transfer data through finagle context as below:
 * <pre>{@code
 *    Context context =  Contexts.local(); // Contexts.broadcast()
 *    context.let(key, value, (Function0) fn);
 * }</pre>
 *
 * which means bind value to key in the scope of fn, even if fn may be executed in any threads. But we can't use this
 * method directly, for example, when we intercept ClientTracingFilter, what we need is add {@link SWContextCarrier}
 * into the MarshalledContext in beforeMethod, and remove it in afterMethod, as below:
 * <pre>{@code
 * abstract ClientTracingFilterInterceptor extends InstanceMethodsAroundInterceptor {
 *     void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes
 *             MethodInterceptResult result) throws Throwable {
 *         Context context =  Contexts.broadcast();
 *         SWContextCarrier swContextCarrier = new SWContextCarrier();
 *         context.let(key, swContextCarrier);
 *     }
 *
 *     Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes
 *             Object ret) throws Throwable {
 *         Context context =  Contexts.broadcast();
 *         context.remove(key);
 *     }
 * }}</pre>
 *
 * So we use ContextHolder to achieve this, the {@link #let(Object, Object)} and {@link #remove(Object)} methos split
 * the function of <pre>let(key, value, (Function0) fn)</pre> into two methods.
 */
abstract class ContextHolder {

    abstract void let(Object key, Object value);

    @Nullable
    abstract <T> T remove(Object key);
}
