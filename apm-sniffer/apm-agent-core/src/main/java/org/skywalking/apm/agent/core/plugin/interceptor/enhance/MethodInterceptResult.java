/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

/**
 * This is a method return value manipulator. When a interceptor's method, such as {@link
 * InstanceMethodsAroundInterceptor#beforeMethod(org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext,
 * InstanceMethodInvokeContext, MethodInterceptResult)}, has this as a method argument, the interceptor can manipulate
 * the method's return value. <p> The new value set to this object, by {@link MethodInterceptResult#defineReturnValue(Object)},
 * will override the origin return value.
 *
 * @author wusheng
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
    Object _ret() {
        return ret;
    }
}
