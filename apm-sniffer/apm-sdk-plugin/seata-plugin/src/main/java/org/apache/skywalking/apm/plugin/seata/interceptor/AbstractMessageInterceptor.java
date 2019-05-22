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

import io.seata.core.protocol.transaction.GlobalBeginRequest;
import io.seata.core.protocol.transaction.GlobalCommitRequest;
import io.seata.core.protocol.transaction.GlobalRollbackRequest;
import io.seata.core.protocol.transaction.GlobalStatusRequest;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalBeginRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalCommitRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalGetStatusRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalRollbackRequest;

import java.lang.reflect.Method;

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
    if (ret instanceof GlobalBeginRequest) {
      return new EnhancedGlobalBeginRequest((GlobalBeginRequest) ret);
    }
    if (ret instanceof GlobalCommitRequest) {
      return new EnhancedGlobalCommitRequest((GlobalCommitRequest) ret);
    }
    if (ret instanceof GlobalRollbackRequest) {
      return new EnhancedGlobalRollbackRequest((GlobalRollbackRequest) ret);
    }
    if (ret instanceof GlobalStatusRequest) {
      return new EnhancedGlobalGetStatusRequest((GlobalStatusRequest) ret);
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
