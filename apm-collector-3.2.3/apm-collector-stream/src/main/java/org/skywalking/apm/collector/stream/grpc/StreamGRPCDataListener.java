/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.grpc;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.client.grpc.GRPCClient;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.RemoteWorkerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StreamGRPCDataListener extends ClusterDataListener {

    private final Logger logger = LoggerFactory.getLogger(StreamGRPCDataListener.class);

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + StreamModuleGroupDefine.GROUP_NAME + "." + StreamGRPCModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }

    private Map<String, GRPCClient> clients = new HashMap<>();
    private Map<String, List<RemoteWorkerRef>> remoteWorkerRefMap = new HashMap<>();

    @Override public void serverJoinNotify(String serverAddress) {
        String selfAddress = StreamGRPCConfig.HOST + ":" + StreamGRPCConfig.PORT;
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        if (!clients.containsKey(serverAddress)) {
            logger.info("new address: {}, create this address remote worker reference", serverAddress);
            String[] hostPort = serverAddress.split(":");
            GRPCClient client = new GRPCClient(hostPort[0], Integer.valueOf(hostPort[1]));
            try {
                client.initialize();
            } catch (ClientException e) {
                e.printStackTrace();
            }
            clients.put(serverAddress, client);

            if (selfAddress.equals(serverAddress)) {
                context.getClusterWorkerContext().getProviders().forEach(provider -> {
                    logger.info("create remote worker self reference, role: {}", provider.role().roleName());
                    provider.create();
                });
            } else {
                context.getClusterWorkerContext().getProviders().forEach(provider -> {
                    logger.info("create remote worker reference, role: {}", provider.role().roleName());
                    RemoteWorkerRef remoteWorkerRef = provider.create(client);
                    if (!remoteWorkerRefMap.containsKey(serverAddress)) {
                        remoteWorkerRefMap.put(serverAddress, new LinkedList<>());
                    }
                    remoteWorkerRefMap.get(serverAddress).add(remoteWorkerRef);
                });
            }
        } else {
            logger.info("address: {} had remote worker reference, ignore", serverAddress);
        }
    }

    @Override public void serverQuitNotify(String serverAddress) {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        if (clients.containsKey(serverAddress)) {
            clients.get(serverAddress).shutdown();
            clients.remove(serverAddress);
        }
        if (remoteWorkerRefMap.containsKey(serverAddress)) {
            for (RemoteWorkerRef remoteWorkerRef : remoteWorkerRefMap.get(serverAddress)) {
                context.getClusterWorkerContext().remove(remoteWorkerRef);
            }
        }
    }
}
