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

package org.apache.skywalking.apm.plugin.service.discovery.kubernetes;

import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.boot.Address;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.discovery.DefaultDiscoveryService;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.ServiceDiscorvery.Kubernetes.PORT_NAME;

@OverrideImplementor(DefaultDiscoveryService.class)
public class KubernetesDiscoveryService extends DefaultDiscoveryService {

    private static final ILog LOG = LogManager.getLogger(KubernetesDiscoveryService.class);

    private NamespacedEndpointsListInformer informer;

    @Override
    public void prepare() throws Throwable {
        String namespace = Config.Collector.ServiceDiscorvery.Kubernetes.NAMESPACE;
        String labelSelector = Config.Collector.ServiceDiscorvery.Kubernetes.LABEL_SELECTOR;
        if (StringUtil.isEmpty(namespace) || StringUtil.isEmpty(labelSelector)) {
            throw new PluginException("namespace and labelSelector is required in kubernetes service discovery plugin");
        }
        informer = new NamespacedEndpointsListInformer();
        informer.boot();
    }

    @Override
    public void shutdown() throws Throwable {
        informer.shutdown();
    }

    @Override
    public List<Address> queryRemoteAddresses() {
        return queryRemoteAddressesFromInformer();
    }

    private List<Address> queryRemoteAddressesFromInformer() {
        Optional<V1EndpointSubset> subset = informer.getV1EndpointSubset();
        return subset.map(this::convertSubset2Addresses).orElse(Collections.emptyList());
    }

    private List<Address> convertSubset2Addresses(final V1EndpointSubset subset) {
        List<V1EndpointAddress> addresses = subset.getAddresses();
        List<V1EndpointPort> ports = subset.getPorts();
        if (Objects.isNull(ports) || Objects.isNull(addresses) || addresses.size() == 0 || ports.size() == 0) {
            LOG.warn("cannot get addresses from the subset of kubernetes endpoints");
            return Collections.emptyList();
        }

        Optional<Integer> port = ports.stream()
                                      .filter(item -> Objects.nonNull(item.getName()))
                                      .filter(item -> item.getName().equalsIgnoreCase(PORT_NAME))
                                      .map(V1EndpointPort::getPort)
                                      .filter(Objects::nonNull)
                                      .findFirst();
        return port.map(integer -> addresses.stream()
                                            .map(item -> new Address(item.getIp(), integer))
                                            .collect(Collectors.toList()))
                   .orElseGet(Collections::emptyList);

    }

}
