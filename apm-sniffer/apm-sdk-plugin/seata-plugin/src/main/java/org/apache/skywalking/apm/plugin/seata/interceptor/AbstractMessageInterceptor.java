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

import io.seata.core.protocol.AbstractMessage;
import io.seata.core.protocol.transaction.*;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.plugin.seata.enhanced.*;

import java.lang.reflect.Method;

/**
 * @author kezhenxu94
 */
public class AbstractMessageInterceptor implements StaticMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final Class clazz,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] parameterTypes,
                             final MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(final Class clazz,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] parameterTypes,
                              final Object ret) {
        if ("getMergeRequestInstanceByCode".equals(method.getName())) {
            final int typeCode = (Integer) allArguments[0];

            switch (typeCode) {
                case AbstractMessage.TYPE_GLOBAL_BEGIN:
                    return new EnhancedGlobalBeginRequest((GlobalBeginRequest) ret);
                case AbstractMessage.TYPE_GLOBAL_COMMIT:
                    return new EnhancedGlobalCommitRequest((GlobalCommitRequest) ret);
                case AbstractMessage.TYPE_GLOBAL_ROLLBACK:
                    return new EnhancedGlobalRollbackRequest((GlobalRollbackRequest) ret);
                case AbstractMessage.TYPE_GLOBAL_STATUS:
                    return new EnhancedGlobalGetStatusRequest((GlobalStatusRequest) ret);
                case AbstractMessage.TYPE_GLOBAL_LOCK_QUERY:
                    return new EnhancedGlobalLockQueryRequest((GlobalLockQueryRequest) ret);
                case AbstractMessage.TYPE_BRANCH_REGISTER:
                    return new EnhancedBranchRegisterRequest((BranchRegisterRequest) ret);
                case AbstractMessage.TYPE_BRANCH_STATUS_REPORT:
                    return new EnhancedBranchReportRequest((BranchReportRequest) ret);
                default:
                    return ret;
            }
        } else if ("getMsgInstanceByCode".equals(method.getName())) {
            final short typeCode = (Short) allArguments[0];
            switch (typeCode) {
                case AbstractMessage.TYPE_BRANCH_COMMIT:
                    return new EnhancedBranchCommitRequest((BranchCommitRequest) ret);
                case AbstractMessage.TYPE_BRANCH_ROLLBACK:
                    return new EnhancedBranchRollbackRequest((BranchRollbackRequest) ret);
                default:
                    return ret;
            }
        }
        return ret;
    }

    @Override
    public void handleMethodException(final Class clazz,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] parameterTypes,
                                      final Throwable t) {
    }
}
