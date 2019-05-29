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

import io.seata.core.protocol.transaction.AbstractTransactionRequest;
import io.seata.core.protocol.transaction.GlobalBeginRequest;
import io.seata.core.protocol.transaction.GlobalBeginResponse;
import io.seata.core.protocol.transaction.GlobalCommitRequest;
import io.seata.core.protocol.transaction.GlobalRollbackRequest;
import io.seata.core.protocol.transaction.GlobalStatusRequest;
import io.seata.core.rpc.netty.TmRpcClient;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalBeginRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalCommitRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalGetStatusRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedGlobalRollbackRequest;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedRequest;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.XID;

// TODO: replace instanceof's
public class TransactionManagerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        final AbstractTransactionRequest message = (AbstractTransactionRequest) allArguments[0];
        final ContextCarrier contextCarrier = new ContextCarrier();

        String xid = null;
        String methodName = null;
        EnhancedRequest enhancedRequest = null;

        if (message instanceof GlobalBeginRequest) {
            methodName = "begin";

            enhancedRequest = new EnhancedGlobalBeginRequest((GlobalBeginRequest) message);
        } else if (message instanceof GlobalCommitRequest) {
            final GlobalCommitRequest request = (GlobalCommitRequest) message;

            methodName = "commit";
            xid = request.getXid();

            enhancedRequest = new EnhancedGlobalCommitRequest(request);
        } else if (message instanceof GlobalRollbackRequest) {
            final GlobalRollbackRequest request = (GlobalRollbackRequest) message;

            methodName = "rollback";
            xid = request.getXid();

            enhancedRequest = new EnhancedGlobalRollbackRequest(request);
        } else if (message instanceof GlobalStatusRequest) {
            final GlobalStatusRequest request = (GlobalStatusRequest) message;

            methodName = "getStatus";
            xid = request.getXid();

            enhancedRequest = new EnhancedGlobalGetStatusRequest(request);
        }

        if (methodName != null) {
            final Object client = TmRpcClient.getInstance();
            final EnhancedInstance tmRpcClient = (EnhancedInstance) client;
            final String peerAddress = (String) tmRpcClient.getSkyWalkingDynamicField();

            final AbstractSpan span = ContextManager.createExitSpan(
                ComponentsDefine.SEATA.getName() + "/TM/" + methodName,
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
        final AbstractSpan activeSpan = ContextManager.activeSpan();
        if (activeSpan != null) {
            if (ret instanceof GlobalBeginResponse) {
                final GlobalBeginResponse response = (GlobalBeginResponse) ret;
                activeSpan.tag(XID, response.getXid());
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
        AbstractSpan span = ContextManager.activeSpan();
        if (span != null) {
            span.errorOccurred();
            span.log(t);
        }
    }
}
