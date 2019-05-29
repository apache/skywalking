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

import io.netty.channel.Channel;
import io.seata.core.protocol.transaction.BranchCommitRequest;
import io.seata.core.protocol.transaction.BranchRollbackRequest;
import io.seata.server.session.GlobalSession;
import io.seata.server.session.SessionHolder;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedBranchCommitRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedBranchRollbackRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedRequest;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

import static org.apache.skywalking.apm.plugin.seata.Constants.BRANCH_ID;
import static org.apache.skywalking.apm.plugin.seata.Constants.RESOURCE_ID;
import static org.apache.skywalking.apm.plugin.seata.Constants.XID;

public class RpcServerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        final Channel channel = (Channel) allArguments[1];
        final Object argument2 = allArguments[2];

        EnhancedRequest enhancedRequest = null;
        String xid = null;
        String branchId = null;
        String resourceId = null;
        String operation = null;

        if (argument2 instanceof BranchCommitRequest) {
            final BranchCommitRequest branchCommitRequest = (BranchCommitRequest) argument2;

            xid = branchCommitRequest.getXid();
            branchId = String.valueOf(branchCommitRequest.getBranchId());
            resourceId = branchCommitRequest.getResourceId();
            enhancedRequest = new EnhancedBranchCommitRequest(branchCommitRequest);
            operation = "BranchCommit";
        } else if (argument2 instanceof BranchRollbackRequest) {
            final BranchRollbackRequest branchRollbackRequest = (BranchRollbackRequest) argument2;

            xid = branchRollbackRequest.getXid();
            branchId = String.valueOf(branchRollbackRequest.getBranchId());
            resourceId = branchRollbackRequest.getResourceId();
            enhancedRequest = new EnhancedBranchRollbackRequest(branchRollbackRequest);
            operation = "BranchRollback";
        }

        if (enhancedRequest != null) {
            final ContextCarrier contextCarrier = new ContextCarrier();
            final InetSocketAddress inetSocketAddress = (InetSocketAddress) channel.remoteAddress();
            final String peerAddress = inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort();

            final AbstractSpan span = ContextManager.createExitSpan(
                operationName(operation),
                contextCarrier,
                peerAddress
            );

            if (xid != null) {
                span.tag(XID, xid);

                final EnhancedInstance globalSession = (EnhancedInstance) SessionHolder.findGlobalSession(xid);
                if (globalSession != null && globalSession.getSkyWalkingDynamicField() != null) {
                    ContextManager.continued(
                        (ContextSnapshot) globalSession.getSkyWalkingDynamicField()
                    );
                }
            }

            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                enhancedRequest.put(next.getHeadKey(), next.getHeadValue());
            }

            allArguments[2] = enhancedRequest;

            if (branchId != null) {
                span.tag(BRANCH_ID, branchId);
            }

            if (resourceId != null) {
                span.tag(RESOURCE_ID, resourceId);
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

    private String operationName(final String operation) {
        return ComponentsDefine.SEATA.getName() + "/TC/" + operation;
    }
}
