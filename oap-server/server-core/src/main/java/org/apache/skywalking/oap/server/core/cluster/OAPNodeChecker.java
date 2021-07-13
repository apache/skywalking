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

package org.apache.skywalking.oap.server.core.cluster;

import com.google.common.collect.Sets;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OAPNodeChecker {
    private static final Set<String> ILLEGAL_NODE_ADDRESS_IN_CLUSTER_MODE = Sets.newHashSet("127.0.0.1", "localhost");

    @Setter
    private static CoreModuleConfig.Role ROLE = CoreModuleConfig.Role.Mixed;

    public static boolean hasIllegalNodeAddress(List<RemoteInstance> remoteInstances) {
        if (CollectionUtils.isEmpty(remoteInstances)) {
            return false;
        }
        Set<String> remoteAddressSet = remoteInstances.stream().map(remoteInstance ->
                remoteInstance.getAddress().getHost()).collect(Collectors.toSet());
        return !Sets.intersection(ILLEGAL_NODE_ADDRESS_IN_CLUSTER_MODE, remoteAddressSet).isEmpty();
    }

    /**
     * Check the remote instance healthiness, set health to false for bellow conditions:
     * 1.can't get the instance list
     * 2.can't get itself
     * 3.check for illegal node in cluster mode such as 127.0.0.1, localhost
     *
     * @param remoteInstances all the remote instances from cluster
     * @return true health false unHealth
     */
    public static ClusterHealthStatus isHealth(List<RemoteInstance> remoteInstances) {
        if (CollectionUtils.isEmpty(remoteInstances)) {
            return ClusterHealthStatus.unHealth("can't get the instance list");
        }
        if (!CoreModuleConfig.Role.Receiver.equals(ROLE)) {
            List<RemoteInstance> selfInstances = remoteInstances.stream().
                    filter(remoteInstance -> remoteInstance.getAddress().isSelf()).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(selfInstances)) {
                return ClusterHealthStatus.unHealth("can't get itself");
            }
        }
        if (remoteInstances.size() > 1 && hasIllegalNodeAddress(remoteInstances)) {
            return ClusterHealthStatus.unHealth("find illegal node in cluster mode such as 127.0.0.1, localhost");
        }
        return ClusterHealthStatus.HEALTH;
    }
}