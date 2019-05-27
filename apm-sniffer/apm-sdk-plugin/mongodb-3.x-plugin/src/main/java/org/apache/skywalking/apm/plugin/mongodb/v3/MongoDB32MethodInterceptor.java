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

import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.connection.Cluster;
import com.mongodb.connection.ServerDescription;
import com.mongodb.operation.ReadOperation;
import com.mongodb.operation.WriteOperation;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;

/**
 * {@link MongoDB32MethodInterceptor} intercept method of {@link com.mongodb.Mongo#execute(ReadOperation, ReadPreference)}
 * or {@link com.mongodb.Mongo#execute(WriteOperation)}, record the mongoDB host, operation name and the key of the
 * operation. Suitable for 3.0 - 3.2 versions.
 *
 * @author SataQiu
 */
public class MongoDB32MethodInterceptor extends MongoDBMethodInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    @Override
    @SuppressWarnings("deprecation")
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Cluster cluster = (Cluster)allArguments[0];
        StringBuilder peers = new StringBuilder();
        for (ServerDescription description : cluster.getDescription().getAll()) {
            ServerAddress address = description.getAddress();
            peers.append(address.getHost() + ":" + address.getPort() + ";");
        }

        objInst.setSkyWalkingDynamicField(peers.subSequence(0, peers.length() - 1).toString());
    }
}
