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

import java.lang.reflect.Method;

/**
 * This is a method return value manipulator. When a interceptor's method, such as {@link
 * InstanceMethodsAroundInterceptor#beforeMethod(EnhancedInstance, Method, Object[], Class[], MethodInterceptResult)}
 * (org.apache.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext, has this as a method argument,
 * the interceptor can manipulate the method's return value. <p> The new value set to this object, by {@link
 * MethodInterceptResult#defineReturnValue(Object)}, will override the origin return value.
 */
public class MethodInterceptResult {
    private boolean isContinue = true;

    private Object ret = null;

    /**
     * define the new return value.
     *
     * @param ret new return value.
     */
    public void defineReturnValue(Object ret) {
        this.isContinue = false;
        this.ret = ret;
    }

    /**
     * @return true, will trigger method interceptor({@link InstMethodsInter} and {@link StaticMethodsInter}) to invoke
     * the origin method. Otherwise, not.
     */
    public boolean isContinue() {
        return isContinue;
    }

    /**
     * @return the new return value.
     */
    public Object _ret() {
        return ret;
    }
}
