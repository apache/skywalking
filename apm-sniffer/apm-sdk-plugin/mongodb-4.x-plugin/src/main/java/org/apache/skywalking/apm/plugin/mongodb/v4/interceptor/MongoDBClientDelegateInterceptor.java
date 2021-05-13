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

package org.apache.skywalking.apm.plugin.mongodb.v4.interceptor;

import com.mongodb.internal.connection.Cluster;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.mongodb.v4.support.MongoRemotePeerHelper;

import java.lang.reflect.Method;

public class MongoDBClientDelegateInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(MongoDBClientDelegateInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Cluster cluster = (Cluster) allArguments[0];
        String remotePeer = MongoRemotePeerHelper.getRemotePeer(cluster);
        objInst.setSkyWalkingDynamicField(remotePeer);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) {
        // do nothing
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) {
        if (ret instanceof EnhancedInstance) {
            // pass remotePeer to OperationExecutor, which has be enhanced as EnhancedInstance
            // See: org.apache.skywalking.apm.plugin.mongodb.v3.define.v37.MongoDBOperationExecutorInstrumentation
            EnhancedInstance retInstance = (EnhancedInstance) ret;
            String remotePeer = (String) objInst.getSkyWalkingDynamicField();
            if (LOGGER.isDebugEnable()) {
                LOGGER.debug("Mark OperationExecutor remotePeer: {}", remotePeer);
            }
            retInstance.setSkyWalkingDynamicField(remotePeer);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        // do nothing
    }
}
