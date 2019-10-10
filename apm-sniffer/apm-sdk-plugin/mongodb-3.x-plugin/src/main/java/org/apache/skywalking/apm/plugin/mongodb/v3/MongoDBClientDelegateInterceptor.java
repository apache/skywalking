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


package org.apache.skywalking.apm.plugin.mongodb.v3;

import com.mongodb.client.internal.MongoClientDelegate;
import com.mongodb.client.internal.OperationExecutor;
import com.mongodb.connection.Cluster;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.plugin.mongodb.v3.support.MongoRemotePeerHelper;
import org.apache.skywalking.apm.plugin.mongodb.v3.support.OperationExecutorContext;

/**
 * @author scolia
 */
public class MongoDBClientDelegateInterceptor implements InstanceConstructorInterceptor {

    @SuppressWarnings("deprecation")
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Cluster cluster = (Cluster) allArguments[0];
        String remotePeer = MongoRemotePeerHelper.getRemotePeer(cluster);
        // register OperationExecutor-remotePeer mapping
        MongoClientDelegate clientDelegate = (MongoClientDelegate) objInst;
        OperationExecutor executor = clientDelegate.getOperationExecutor();
        OperationExecutorContext context = OperationExecutorContext.getInstance();
        context.putRemotePeerMapping(executor, remotePeer);
    }
}
