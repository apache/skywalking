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

import io.seata.core.protocol.transaction.BranchRegisterRequest;
import io.seata.core.protocol.transaction.BranchReportRequest;
import io.seata.core.protocol.transaction.GlobalLockQueryRequest;
import io.seata.core.rpc.netty.RmRpcClient;
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
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalLockQueryRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedRequest;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.XID;

public class RmRpcClientInterceptor implements InstanceMethodsAroundInterceptor {
  @Override
  public void beforeMethod(final EnhancedInstance objInst,
                           final Method method,
                           final Object[] allArguments,
                           final Class<?>[] argumentsTypes,
                           final MethodInterceptResult result) throws Throwable {
    final Object argument0 = allArguments[0];

    String xid = null;
    String methodName = null;
    EnhancedRequest enhancedRequest = null;

    if (argument0 instanceof GlobalLockQueryRequest) {
      final EnhancedGlobalLockQueryRequest globalLockQueryRequest = new EnhancedGlobalLockQueryRequest((GlobalLockQueryRequest) argument0);
      xid = globalLockQueryRequest.getXid();

      enhancedRequest = globalLockQueryRequest;
      methodName = "GlobalLockQuery";
    } else if (argument0 instanceof BranchRegisterRequest) {
      final EnhancedBranchRegisterRequest branchRegisterRequest = new EnhancedBranchRegisterRequest((BranchRegisterRequest) argument0);
      xid = branchRegisterRequest.getXid();

      enhancedRequest = branchRegisterRequest;
      methodName = "BranchRegister";
    } else if (argument0 instanceof BranchReportRequest) {
      final EnhancedBranchReportRequest branchReportRequest = new EnhancedBranchReportRequest((BranchReportRequest) argument0);
      xid = branchReportRequest.getXid();

      enhancedRequest = branchReportRequest;
      methodName = "BranchReport";
    }

    final ContextCarrier contextCarrier = new ContextCarrier();
    if (methodName != null) {
      final Object client = RmRpcClient.getInstance();
      final EnhancedInstance rmRpcClient = (EnhancedInstance) client;
      final String peerAddress = (String) rmRpcClient.getSkyWalkingDynamicField();

      final AbstractSpan span = ContextManager.createExitSpan(
          ComponentsDefine.SEATA.getName() + "/RM/" + methodName,
          contextCarrier,
          peerAddress
      );

      CarrierItem next = contextCarrier.items();
      while (next.hasNext()) {
        next = next.next();
        enhancedRequest.put(next.getHeadKey(), next.getHeadValue());
      }

      allArguments[0] = enhancedRequest;

      if (xid != null) {
        span.tag(XID, xid);
      }

      span.setComponent(ComponentsDefine.SEATA);
      SpanLayer.asDB(span);
    }
  }

  @Override
  public Object afterMethod(final EnhancedInstance objInst,
                            final Method method,
                            final Object[] allArguments,
                            final Class<?>[] argumentsTypes,
                            final Object ret) throws Throwable {
    if (ContextManager.isActive()) {
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
    final AbstractSpan activeSpan = ContextManager.activeSpan();
    if (activeSpan != null) {
      activeSpan.errorOccurred();
      activeSpan.log(t);
    }
  }
}
