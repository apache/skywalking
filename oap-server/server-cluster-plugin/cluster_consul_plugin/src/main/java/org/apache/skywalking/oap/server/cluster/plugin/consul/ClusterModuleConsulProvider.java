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

package org.apache.skywalking.oap.server.cluster.plugin.consul;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import java.util.*;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.util.*;
import org.slf4j.*;

/**
 * Use consul to manage all service instances in SkyWalking cluster.
 *
 * @author peng-yongsheng
 */
public class ClusterModuleConsulProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClusterModuleConsulProvider.class);

    private final ClusterModuleConsulConfig config;
    private Consul client;

    public ClusterModuleConsulProvider() {
        super();
        this.config = new ClusterModuleConsulConfig();
    }

    @Override public String name() {
        return "consul";
    }

    @Override public Class module() {
        return ClusterModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        try {
            List<Address> addressList = ConnectUtils.parse(config.getHostPort());

            List<HostAndPort> hostAndPorts = new ArrayList<>();
            for (Address address : addressList) {
                hostAndPorts.add(HostAndPort.fromParts(address.getHost(), address.getPort()));
            }

            if (hostAndPorts.size() > 1) {
                client = Consul.builder().withMultipleHostAndPort(hostAndPorts, 5000).build();
            } else {
                client = Consul.builder().withHostAndPort(hostAndPorts.get(0)).build();
            }
        } catch (ConnectStringParseException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        ConsulCoordinator coordinator = new ConsulCoordinator(client, config.getServiceName());
        this.registerServiceImplementation(ClusterRegister.class, coordinator);
        this.registerServiceImplementation(ClusterNodesQuery.class, coordinator);
    }

    @Override public void start() {
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
