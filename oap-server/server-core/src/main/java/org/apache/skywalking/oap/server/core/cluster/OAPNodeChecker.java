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
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OAPNodeChecker {
    private static final Set<String> ILLEGAL_NODE_ADDRESS_IN_CLUSTER_MODE = Sets.newHashSet("127.0.0.1", "localhost");

    public static boolean hasUnHealthAddress(Set<String> addressSet) {
        if (CollectionUtils.isEmpty(addressSet)) {
            return false;
        }
        return !Sets.intersection(ILLEGAL_NODE_ADDRESS_IN_CLUSTER_MODE, addressSet).isEmpty();
    }

    public static boolean hasUnHealthAddress(List<RemoteInstance> remoteInstances) {
        Set<String> remoteAddressSet = remoteInstances.stream().map(remoteInstance ->
                remoteInstance.getAddress().getHost()).collect(Collectors.toSet());
        return hasUnHealthAddress(remoteAddressSet);
    }

    public static boolean hasDuplicateSelfAddress(List<RemoteInstance> remoteInstances) {
        List<RemoteInstance> selfInstances = remoteInstances.stream().
                filter(remoteInstance -> remoteInstance.getAddress().isSelf()).collect(Collectors.toList());
        return selfInstances.size() != 1;
    }
}
