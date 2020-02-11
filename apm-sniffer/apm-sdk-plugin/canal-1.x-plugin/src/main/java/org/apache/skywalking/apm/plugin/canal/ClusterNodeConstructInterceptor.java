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

package org.apache.skywalking.apm.plugin.canal;

import com.alibaba.otter.canal.client.impl.ClusterNodeAccessStrategy;
import com.alibaba.otter.canal.common.zookeeper.ZkClientx;
import com.alibaba.otter.canal.common.zookeeper.ZookeeperPathUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ClusterNodeConstructInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {

        String clusterPath = ZookeeperPathUtils.getDestinationClusterRoot(allArguments[0].toString());
        ZkClientx zkClientx = ((ClusterNodeAccessStrategy) objInst).getZkClient();
        ContextManager.getRuntimeContext().put("currentAddress", getCurrentAddress(zkClientx.getChildren(clusterPath)));

    }

    private List<InetSocketAddress> getCurrentAddress(List<String> currentChilds) {
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
        for (String address : currentChilds) {
            String[] strs = StringUtils.split(address, ":");
            if (strs != null && strs.length == 2) {
                addresses.add(new InetSocketAddress(strs[0], Integer.valueOf(strs[1])));
            }
        }

        return addresses;

    }
}

