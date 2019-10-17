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


package org.apache.skywalking.apm.plugin.mongodb.v3.interceptor.v36;

import com.mongodb.connection.Cluster;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.mongodb.v3.interceptor.v37.MongoDBV37ClientDelegateInterceptor;
import org.apache.skywalking.apm.plugin.mongodb.v3.support.MongoRemotePeerHelper;

import java.lang.reflect.Method;

/**
 * Same with {@link MongoDBV37ClientDelegateInterceptor}, mark remotePeer of OperationExecutor when it was created.
 *
 * @author scolia
 */
@SuppressWarnings({"deprecation", "Duplicates"})
public class MongoDBV36Interceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    private static final ILog logger = LogManager.getLogger(MongoDBV36Interceptor.class);

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Cluster cluster = (Cluster) allArguments[0];
        String peers = MongoRemotePeerHelper.getRemotePeer(cluster);
        objInst.setSkyWalkingDynamicField(peers);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) {
        if (ret instanceof EnhancedInstance) {
            // pass remotePeer to OperationExecutor, which will be wrapper as EnhancedInstance
            // @see: org.apache.skywalking.apm.plugin.mongodb.v3.interceptor.v36.MongoDBV36OperationExecutorInterceptor
            EnhancedInstance retInstance = (EnhancedInstance) ret;
            String remotePeer = (String) objInst.getSkyWalkingDynamicField();
            if (logger.isDebugEnable()) {
                logger.debug("Mark OperationExecutor remotePeer: {}", remotePeer);
            }
            retInstance.setSkyWalkingDynamicField(remotePeer);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }
}
