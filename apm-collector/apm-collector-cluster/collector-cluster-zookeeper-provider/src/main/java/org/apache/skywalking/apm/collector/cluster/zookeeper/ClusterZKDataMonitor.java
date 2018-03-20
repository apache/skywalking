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

package org.apache.skywalking.apm.collector.cluster.zookeeper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.apm.collector.client.Client;
import org.apache.skywalking.apm.collector.client.ClientException;
import org.apache.skywalking.apm.collector.client.zookeeper.ZookeeperClient;
import org.apache.skywalking.apm.collector.client.zookeeper.ZookeeperClientException;
import org.apache.skywalking.apm.collector.cluster.ClusterModuleListener;
import org.apache.skywalking.apm.collector.cluster.ClusterNodeExistException;
import org.apache.skywalking.apm.collector.cluster.DataMonitor;
import org.apache.skywalking.apm.collector.cluster.ModuleRegistration;
import org.apache.skywalking.apm.collector.core.CollectorException;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ClusterZKDataMonitor implements DataMonitor, Watcher {

    private final Logger logger = LoggerFactory.getLogger(ClusterZKDataMonitor.class);

    private ZookeeperClient client;

    private Map<String, ClusterModuleListener> listeners;
    private Map<String, ModuleRegistration> registrations;
    private String namespace;

    public ClusterZKDataMonitor() {
        listeners = new LinkedHashMap<>();
        registrations = new LinkedHashMap<>();
    }

    @Override public synchronized void process(WatchedEvent event) {
        logger.info("changed path {}, event type: {}", event.getPath(), event.getType().name());
        if (listeners.containsKey(event.getPath())) {
            List<String> paths;
            try {
                paths = client.getChildren(event.getPath(), true);
                ClusterModuleListener listener = listeners.get(event.getPath());
                Set<String> remoteNodes = new HashSet<>();
                Set<String> notifiedNodes = listener.getAddresses();
                if (CollectionUtils.isNotEmpty(paths)) {
                    for (String serverPath : paths) {
                        Stat stat = new Stat();
                        byte[] data = client.getData(event.getPath() + "/" + serverPath, true, stat);
                        String dataStr = new String(data);
                        String addressValue = serverPath + dataStr;
                        remoteNodes.add(addressValue);
                        if (!notifiedNodes.contains(addressValue)) {
                            logger.info("path children has been created, path: {}, data: {}", event.getPath() + "/" + serverPath, dataStr);
                            listener.addAddress(addressValue);
                            listener.serverJoinNotify(addressValue);
                        }
                    }
                }

                String[] notifiedNodeArray = notifiedNodes.toArray(new String[notifiedNodes.size()]);
                for (int i = notifiedNodeArray.length - 1; i >= 0; i--) {
                    String address = notifiedNodeArray[i];
                    if (remoteNodes.isEmpty() || !remoteNodes.contains(address)) {
                        logger.info("path children has been remove, path and data: {}", event.getPath() + "/" + address);
                        listener.removeAddress(address);
                        listener.serverQuitNotify(address);
                    }
                }
            } catch (ZookeeperClientException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override public void setClient(Client client) {
        this.client = (ZookeeperClient)client;
    }

    public void start() throws CollectorException {
        Iterator<Map.Entry<String, ModuleRegistration>> entryIterator = registrations.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, ModuleRegistration> next = entryIterator.next();
            createPath(next.getKey());

            ModuleRegistration.Value value = next.getValue().buildValue();
            String contextPath = value.getContextPath() == null ? "" : value.getContextPath();

            client.getChildren(next.getKey(), true);
            String serverPath = next.getKey() + "/" + value.getHostPort();

            Stat stat = client.exists(serverPath, false);
            if (stat != null) {
                client.delete(serverPath, stat.getVersion());
            }
            stat = client.exists(serverPath, false);
            if (stat == null) {
                setData(serverPath, contextPath);
            } else {
                client.delete(serverPath, stat.getVersion());
                throw new ClusterNodeExistException("current address: " + value.getHostPort() + " has been registered, check the host and port configuration or wait a moment.");
            }
        }
    }

    @Override public void addListener(ClusterModuleListener listener) {
        String path = getBaseCatalog() + listener.path();
        logger.info("listener path: {}", path);
        listeners.put(path, listener);
    }

    @Override public void register(String path, ModuleRegistration registration) {
        registrations.put(getBaseCatalog() + path, registration);
    }

    @Override public ClusterModuleListener getListener(String path) {
        path = getBaseCatalog() + path;
        return listeners.get(path);
    }

    @Override public void createPath(String path) throws ClientException {
        String[] paths = path.replaceFirst("/", "").split("/");

        StringBuilder pathBuilder = new StringBuilder();
        for (String subPath : paths) {
            pathBuilder.append("/").append(subPath);
            if (client.exists(pathBuilder.toString(), false) == null) {
                client.create(pathBuilder.toString(), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        }
    }

    @Override public void setData(String path, String value) throws ClientException {
        if (client.exists(path, false) == null) {
            client.create(path, value.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } else {
            client.setData(path, value.getBytes(), -1);
        }
    }

    @Override public String getBaseCatalog() {
        if (StringUtil.isEmpty(namespace)) {
            return "/skywalking";
        } else {
            return "/" + namespace + "/skywalking";
        }
    }

    void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
