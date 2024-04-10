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

package org.apache.skywalking.oap.server.receiver.envoy.als.istio;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
import io.fabric8.istio.api.networking.v1beta1.ServiceEntry;
import io.fabric8.istio.api.networking.v1beta1.WorkloadEntrySpec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.skywalking.library.kubernetes.IstioServiceEntries;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.k8s.ServiceNameFormatter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Slf4j
public class IstioServiceEntryRegistry {
    protected final EnvoyMetricReceiverConfig config;
    protected final ServiceNameFormatter serviceNameFormatter;

    protected final LoadingCache<String/* ip */, ServiceMetaInfo> ipServiceMetaInfoMap;
    protected final Map<String, DnsAddressEndpointGroup> hostnameResolvers = new ConcurrentHashMap<>();

    @SneakyThrows
    public IstioServiceEntryRegistry(final EnvoyMetricReceiverConfig config) {
        this.config = config;

        serviceNameFormatter = new ServiceNameFormatter(config.getIstioServiceNameRule());

        final var ignoredNamespaces = config.getIstioServiceEntryIgnoredNamespaces();

        final var cacheBuilder = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(3));

        ipServiceMetaInfoMap = cacheBuilder.build(new CacheLoader<>() {
            @Override
            public ServiceMetaInfo load(String ip) {
                final var serviceEntry = IstioServiceEntries
                    .INSTANCE
                    .list()
                    .parallelStream()
                    .filter(se -> se.getMetadata() != null)
                    .filter(se -> se.getSpec() != null)
                    .filter(se -> !ignoredNamespaces.contains(se.getMetadata().getNamespace()))
                    .filter(se -> {
                        final var spec = se.getSpec();
                        if (spec.getResolution() == null) {
                            log.debug("Unsupported service entry resolution: {}", spec.getResolution());
                            return false;
                        }
                        switch (spec.getResolution()) {
                            case STATIC:
                                return spec
                                    .getAddresses()
                                    .parallelStream()
                                    .anyMatch(address -> {
                                        if (address.contains("/")) { // CIDR
                                            final var subnet = new SubnetUtils(address);
                                            return subnet.getInfo().isInRange(ip);
                                        }
                                        return Objects.equals(ip, address);
                                    }) ||
                                    spec
                                        .getEndpoints()
                                        .parallelStream()
                                        .map(WorkloadEntrySpec::getAddress)
                                        .anyMatch(address -> Objects.equals(address, ip));
                            case DNS:
                            case DNS_ROUND_ROBIN:
                                return spec
                                    .getHosts()
                                    .parallelStream()
                                    .map(host -> hostnameResolvers.computeIfAbsent(host, it -> {
                                        final var endpointGroup = DnsAddressEndpointGroup.of(it);
                                        endpointGroup.whenReady().join(); // Wait for the first resolution
                                        return endpointGroup;
                                    }))
                                    .anyMatch(dnsAddressEndpointGroup ->
                                        dnsAddressEndpointGroup
                                            .endpoints()
                                            .parallelStream()
                                            .anyMatch(endpoint -> Objects.equals(endpoint.ipAddr(), ip)));
                            default:
                                log.debug("Unsupported service entry resolution: {}", spec.getResolution());
                                return false;
                        }
                    })
                    .findFirst();

                if (serviceEntry.isEmpty()) {
                    log.debug("No corresponding service entry for IP: {}", ip);
                    return config.serviceMetaInfoFactory().unknown();
                }

                log.debug(
                    "Composing service meta info from service entry for IP: {}", ip);
                return composeServiceMetaInfo(serviceEntry.get(), ip);
            }
        });
    }

    protected List<ServiceMetaInfo.KeyValue> transformLabelsToTags(final ObjectMeta serviceEntryMeta) {
        final var labels = serviceEntryMeta.getLabels();
        final var tags = new ArrayList<ServiceMetaInfo.KeyValue>();
        tags.add(new ServiceMetaInfo.KeyValue("namespace", serviceEntryMeta.getNamespace()));
        if (isNull(labels)) {
            return tags;
        }
        return labels.entrySet()
            .stream()
            .map(each -> new ServiceMetaInfo.KeyValue(each.getKey(), each.getValue()))
            .collect(Collectors.toCollection(() -> tags));
    }

    @SneakyThrows
    public ServiceMetaInfo findService(final String ip) {
        return ipServiceMetaInfoMap.get(ip);
    }

    protected ServiceMetaInfo composeServiceMetaInfo(final ServiceEntry serviceEntry, String ip) {
        final var context = ImmutableMap.<String, Object>of("serviceEntry", serviceEntry);
        final var serviceMetaInfo = new ServiceMetaInfo();

        try {
            serviceMetaInfo.setServiceName(serviceNameFormatter.format(context));
        } catch (Exception e) {
            log.error("Failed to evaluate serviceEntry name.", e);
            final var serviceMetadata = serviceEntry.getMetadata();
            if (isNull(serviceMetadata)) {
                log.warn("Service metadata is null, {}", serviceEntry);
                return config.serviceMetaInfoFactory().unknown();
            }
            serviceMetaInfo.setServiceName(serviceMetadata.getName());
        }
        serviceMetaInfo.setTags(transformLabelsToTags(serviceEntry.getMetadata()));
        serviceMetaInfo.setServiceInstanceName(ip);

        return serviceMetaInfo;
    }
}
