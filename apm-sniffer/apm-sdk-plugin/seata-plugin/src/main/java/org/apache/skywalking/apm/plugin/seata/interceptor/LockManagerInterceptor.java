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

package org.apache.skywalking.apm.plugin.seata.interceptor;

import io.seata.server.session.BranchSession;
import org.apache.commons.lang.StringUtils;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.XID;
import static org.apache.skywalking.apm.plugin.seata.define.LockManagerInstrumentation.*;

/**
 * @author kezhenxu94
 */
public class LockManagerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(
        final EnhancedInstance objInst,
        final Method method,
        final Object[] allArguments,
        final Class<?>[] argumentsTypes,
        final MethodInterceptResult result) throws Throwable {
        final String methodName = method.getName();
        final String operation = StringUtils.capitalize(methodName);
        final AbstractSpan span = ContextManager.createLocalSpan(operation);

        if (ACQUIRE_LOCK.equals(methodName) || RELEASE_LOCK.equals(methodName)) {
            final BranchSession branchSession = (BranchSession) allArguments[0];
            span.tag(XID, branchSession.getXid());
        } else if (IS_LOCKABLE.equals(methodName)) {
            final String xid = (String) allArguments[0];
            span.tag(XID, xid);
        }
    }

    @Override
    public Object afterMethod(
        final EnhancedInstance objInst,
        final Method method,
        final Object[] allArguments,
        final Class<?>[] argumentsTypes,
        final Object ret) throws Throwable {

        ContextManager.stopSpan();

        return ret;
    }

    @Override
    public void handleMethodException(
        final EnhancedInstance objInst,
        final Method method,
        final Object[] allArguments,
        final Class<?>[] argumentsTypes,
        final Throwable t) {

        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
