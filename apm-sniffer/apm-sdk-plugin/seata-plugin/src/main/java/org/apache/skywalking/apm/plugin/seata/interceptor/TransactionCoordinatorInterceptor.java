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

import io.seata.core.protocol.transaction.GlobalBeginResponse;
import io.seata.server.session.SessionHolder;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedBranchRegisterRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedBranchReportRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalBeginRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalCommitRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalGetStatusRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalRollbackRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalLockQueryRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedRequest;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.XID;

public class TransactionCoordinatorInterceptor implements InstanceMethodsAroundInterceptor {
  @Override
  public void beforeMethod(final EnhancedInstance objInst,
                           final Method method,
                           final Object[] allArguments,
                           final Class<?>[] argumentsTypes,
                           final MethodInterceptResult result) throws Throwable {
    final String methodName = method.getName();
    final Object request = allArguments[0];

    final ContextCarrier contextCarrier = new ContextCarrier();

    EnhancedRequest enhancedRequest = null;
    String xid = null;

    if ("doGlobalBegin".equals(methodName)) {
      enhancedRequest = (EnhancedGlobalBeginRequest) request;
    } else if ("doGlobalCommit".equals(methodName)) {
      final EnhancedGlobalCommitRequest commitRequest = (EnhancedGlobalCommitRequest) request;

      xid = commitRequest.getXid();
      enhancedRequest = commitRequest;
    } else if ("doGlobalRollback".equals(methodName)) {
      final EnhancedGlobalRollbackRequest rollbackRequest = (EnhancedGlobalRollbackRequest) request;

      xid = rollbackRequest.getXid();
      enhancedRequest = rollbackRequest;
    } else if ("doGlobalStatus".equals(methodName)) {
      final EnhancedGlobalGetStatusRequest statusRequest = (EnhancedGlobalGetStatusRequest) request;

      xid = statusRequest.getXid();
      enhancedRequest = statusRequest;
    } else if ("doBranchRegister".equals(methodName)) {
      final EnhancedBranchRegisterRequest registerRequest = (EnhancedBranchRegisterRequest) request;

      xid = registerRequest.getXid();
      enhancedRequest = registerRequest;
    } else if ("doBranchReport".equals(methodName)) {
      final EnhancedBranchReportRequest reportRequest = (EnhancedBranchReportRequest) request;

      xid = reportRequest.getXid();
      enhancedRequest = reportRequest;
    } else if ("doLockCheck".equals(methodName)) {
      final EnhancedGlobalLockQueryRequest lockQueryRequest = (EnhancedGlobalLockQueryRequest) request;

      xid = lockQueryRequest.getXid();
      enhancedRequest = lockQueryRequest;
    }

    if (enhancedRequest != null) {
      CarrierItem next = contextCarrier.items();
      while (next.hasNext()) {
        next = next.next();
        next.setHeadValue(enhancedRequest.get(next.getHeadKey()));
      }
    }

    final AbstractSpan span = ContextManager.createEntrySpan(
        operationName(method),
        contextCarrier
    );

    if (xid != null) {
      span.tag(XID, xid);

      if ("doGlobalCommit".equals(methodName) || "doGlobalRollback".equals(methodName)) {
        final EnhancedInstance globalSession = (EnhancedInstance) SessionHolder.findGlobalSession(xid);
        if (globalSession != null) {
          globalSession.setSkyWalkingDynamicField(ContextManager.capture());
        }
      }
    }
    span.setComponent(ComponentsDefine.SEATA);
    SpanLayer.asDB(span);
  }

  @Override
  public Object afterMethod(final EnhancedInstance objInst,
                            final Method method,
                            final Object[] allArguments,
                            final Class<?>[] argumentsTypes,
                            final Object ret) throws Throwable {
    final String methodName = method.getName();
    final AbstractSpan activeSpan = ContextManager.activeSpan();
    if (activeSpan != null) {
      if ("doGlobalBegin".equals(methodName)) {
        final GlobalBeginResponse beginResponse = (GlobalBeginResponse) allArguments[1];
        final String xid = beginResponse.getXid();
        activeSpan.tag(XID, xid);
      }

      ContextManager.stopSpan();
    }
    return ret;
  }

  @Override
  public void handleMethodException(final EnhancedInstance objInst,
                                    final Method method,
                                    final Object[] allArguments,
                                    final Class<?>[] argumentsTypes,
                                    final Throwable t) {
    final AbstractSpan span = ContextManager.activeSpan();
    if (span != null) {
      span.errorOccurred();
      span.log(t);
    }
  }

  private String operationName(final Method method) {
    final String methodName = method.getName();
    return ComponentsDefine.SEATA.getName()
        + "/TC/"
        + (methodName.startsWith("do") ? methodName.substring(2) : methodName);
  }
}
