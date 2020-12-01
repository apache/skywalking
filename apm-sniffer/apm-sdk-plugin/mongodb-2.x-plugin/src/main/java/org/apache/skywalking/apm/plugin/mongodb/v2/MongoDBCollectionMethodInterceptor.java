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

package org.apache.skywalking.apm.plugin.mongodb.v2;

import com.mongodb.AggregationOutput;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.ServerAddress;
import com.mongodb.WriteResult;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class MongoDBCollectionMethodInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final String DB_TYPE = "MongoDB";

    private static final String MONGO_DB_OP_PREFIX = "MongoDB/";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

        String remotePeer = (String) objInst.getSkyWalkingDynamicField();
        String operation = method.getName();
        AbstractSpan span = ContextManager.createExitSpan(MONGO_DB_OP_PREFIX + operation, new ContextCarrier(), remotePeer);
        span.setComponent(ComponentsDefine.MONGO_DRIVER);
        Tags.DB_TYPE.set(span, DB_TYPE);
        SpanLayer.asDB(span);

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        CommandResult cresult = null;
        if (ret instanceof WriteResult) {
            WriteResult wresult = (WriteResult) ret;
            cresult = wresult.getCachedLastError();
        } else if (ret instanceof AggregationOutput) {
            AggregationOutput aresult = (AggregationOutput) ret;
            cresult = aresult.getCommandResult();
        }
        if (null != cresult && !cresult.ok()) {
            activeSpan.log(cresult.getException());
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.log(t);
    }

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        DB db = (DB) allArguments[0];
        List<ServerAddress> servers = db.getMongo().getAllAddress();
        StringBuilder peers = new StringBuilder();
        for (ServerAddress address : servers) {
            peers.append(address.getHost()).append(":").append(address.getPort()).append(";");
        }

        objInst.setSkyWalkingDynamicField(peers.subSequence(0, peers.length() - 1).toString());
    }

}
