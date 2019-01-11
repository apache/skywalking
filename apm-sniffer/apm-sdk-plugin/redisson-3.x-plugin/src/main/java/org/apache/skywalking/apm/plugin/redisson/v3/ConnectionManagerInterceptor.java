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

package org.apache.skywalking.apm.plugin.redisson.v3;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.redisson.config.*;
import org.redisson.connection.ConnectionManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collection;

/**
 * @author zhaoyuguang
 */
public class ConnectionManagerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog logger = LogManager.getLogger(ConnectionManagerInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        try {
            ConnectionManager connectionManager = (ConnectionManager) objInst;
            Config config = connectionManager.getCfg();

            SentinelServersConfig sentinelServersConfig = (SentinelServersConfig) getServersConfig(config, "sentinelServersConfig");
            MasterSlaveServersConfig masterSlaveServersConfig = (MasterSlaveServersConfig) getServersConfig(config, "masterSlaveServersConfig");
            ClusterServersConfig clusterServersConfig = (ClusterServersConfig) getServersConfig(config, "clusterServersConfig");
            ReplicatedServersConfig replicatedServersConfig = (ReplicatedServersConfig) getServersConfig(config, "replicatedServersConfig");

            StringBuilder peer = new StringBuilder();
            EnhancedInstance retInst = (EnhancedInstance) ret;

            if (sentinelServersConfig != null) {
                appendAddresses(peer, sentinelServersConfig.getSentinelAddresses());
                retInst.setSkyWalkingDynamicField(peer.toString());
                return ret;
            }
            if (masterSlaveServersConfig != null) {
                URI masterAddress = masterSlaveServersConfig.getMasterAddress();
                peer.append(masterAddress.getHost()).append(":").append(masterAddress.getPort());
                appendAddresses(peer, masterSlaveServersConfig.getSlaveAddresses());
                retInst.setSkyWalkingDynamicField(peer.toString());
                return ret;
            }
            if (clusterServersConfig != null) {
                appendAddresses(peer, clusterServersConfig.getNodeAddresses());
                retInst.setSkyWalkingDynamicField(peer.toString());
                return ret;
            }
            if (replicatedServersConfig != null) {
                appendAddresses(peer, replicatedServersConfig.getNodeAddresses());
                retInst.setSkyWalkingDynamicField(peer.toString());
                return ret;
            }
        } catch (Exception e) {
            logger.warn("redisClient set peer error: ", e);
        }
        return ret;
    }

    private Object getServersConfig(Config config, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = config.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(config);
    }

    private void appendAddresses(StringBuilder peer, Collection<URI> nodeAddresses) {
        if (nodeAddresses != null && !nodeAddresses.isEmpty()) {
            for (URI uri : nodeAddresses) {
                peer.append(uri.getHost()).append(":").append(uri.getPort()).append(";");
            }
        }
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }
}
