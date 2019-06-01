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

import com.google.common.collect.Iterables;
import io.seata.rm.datasource.ConnectionProxy;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;
import java.util.Set;

import static org.apache.skywalking.apm.plugin.seata.Constants.XID;

/**
 * @author kezhenxu94
 */
public class UndoLogManagerInterceptor implements StaticMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final Class clazz,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] parameterTypes,
                             final MethodInterceptResult result) {
        final String methodName = method.getName();

        String xid = null;

        if ("flushUndoLogs".equals(methodName)) {
            final ConnectionProxy connectionProxy = (ConnectionProxy) allArguments[0];
            xid = connectionProxy.getContext().getXid();
        } else if ("undo".equals(methodName)) {
            xid = (String) allArguments[1];
        } else if ("batchDeleteUndoLog".equals(methodName)) {
            final Set<String> xids = (Set<String>) allArguments[0];
            xid = Iterables.toString(xids);
        }

        final AbstractSpan span = ContextManager.createLocalSpan(
            ComponentsDefine.SEATA.getName() + "/UndoLogManager/" + methodName
        );
        if (xid != null) {
            span.tag(XID, xid);
        }
        span.setComponent(ComponentsDefine.SEATA);
    }

    @Override
    public Object afterMethod(final Class clazz,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] parameterTypes,
                              final Object ret) {
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(final Class clazz,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] parameterTypes,
                                      final Throwable t) {
        final AbstractSpan activeSpan = ContextManager.activeSpan();

        if (activeSpan != null) {
            activeSpan.errorOccurred().log(t);
        }
    }
}
