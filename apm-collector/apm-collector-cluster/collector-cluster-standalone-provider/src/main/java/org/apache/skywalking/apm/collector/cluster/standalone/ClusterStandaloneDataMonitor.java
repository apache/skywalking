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

package org.apache.skywalking.apm.collector.cluster.standalone;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.Client;
import org.apache.skywalking.apm.collector.client.ClientException;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.cluster.ClusterModuleListener;
import org.apache.skywalking.apm.collector.cluster.DataMonitor;
import org.apache.skywalking.apm.collector.cluster.ModuleRegistration;
import org.apache.skywalking.apm.collector.core.CollectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ClusterStandaloneDataMonitor implements DataMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ClusterStandaloneDataMonitor.class);

    private H2Client client;

    private Map<String, ClusterModuleListener> listeners;
    private Map<String, ModuleRegistration> registrations;

    ClusterStandaloneDataMonitor() {
        listeners = new LinkedHashMap<>();
        registrations = new LinkedHashMap<>();
    }

    @Override public void setClient(Client client) {
        this.client = (H2Client)client;
    }

    @Override
    public void addListener(ClusterModuleListener listener) {
        String path = getBaseCatalog() + listener.path();
        logger.info("listener path: {}", path);
        listeners.put(path, listener);
    }

    @Override public ClusterModuleListener getListener(String path) {
        path = getBaseCatalog() + path;
        return listeners.get(path);
    }

    @Override public void register(String path, ModuleRegistration registration) {
        registrations.put(getBaseCatalog() + path, registration);
    }

    @Override public void createPath(String path) throws ClientException {

    }

    @Override public void setData(String path, String value) throws ClientException {
        if (listeners.containsKey(path)) {
            listeners.get(path).addAddress(value);
            listeners.get(path).serverJoinNotify(value);
        }
    }

    @Override public String getBaseCatalog() {
        return "/skywalking";
    }

    public void start() throws CollectorException {
        Iterator<Map.Entry<String, ModuleRegistration>> entryIterator = registrations.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, ModuleRegistration> next = entryIterator.next();
            ModuleRegistration.Value value = next.getValue().buildValue();
            String contextPath = value.getContextPath() == null ? "" : value.getContextPath();
            setData(next.getKey(), value.getHostPort() + contextPath);
        }
    }
}
