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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.vavr.Tuple2;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.kubernetes.KubernetesEndpoints;
import org.apache.skywalking.library.kubernetes.KubernetesPods;
import org.apache.skywalking.library.kubernetes.KubernetesServices;
import org.apache.skywalking.library.kubernetes.ObjectID;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toSet;

@Slf4j
public class K8SServiceRegistry {
    protected final EnvoyMetricReceiverConfig config;
    protected final ServiceNameFormatter serviceNameFormatter;

    protected final LoadingCache<K8SServiceRegistry, Set<String>> nodeIPs;
    protected final LoadingCache<String/* ip */, ServiceMetaInfo> ipServiceMetaInfoMap;

    @SneakyThrows
    public K8SServiceRegistry(final EnvoyMetricReceiverConfig config) {
        this.config = config;

        serviceNameFormatter = new ServiceNameFormatter(config.getK8sServiceNameRule());

        final CacheBuilder<Object, Object> cacheBuilder =
            CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(3));

        nodeIPs = cacheBuilder.build(CacheLoader.from(() -> {
            try (final var kubernetesClient = new KubernetesClientBuilder().build()) {
                return kubernetesClient
                    .nodes()
                    .list()
                    .getItems()
                    .stream()
                    .map(Node::getStatus)
                    .map(NodeStatus::getAddresses)
                    .flatMap(it -> it.stream().map(NodeAddress::getAddress)
                        .filter(StringUtil::isNotBlank))
                    .collect(toSet());
            } catch (Exception e) {
                log.error("Failed to list Nodes.", e);
                return Collections.emptySet();
            }
        }));

        ipServiceMetaInfoMap = cacheBuilder.build(new CacheLoader<>() {
            @Override
            public ServiceMetaInfo load(String ip) {
                final Optional<Pod> pod = KubernetesPods.INSTANCE.findByIP(ip);
                if (pod.isEmpty()) {
                    log.debug("No corresponding Pod for IP: {}", ip);
                    return config.serviceMetaInfoFactory().unknown();
                }

                final Optional<ObjectID> serviceID =
                    KubernetesEndpoints.INSTANCE
                        .list()
                        .stream()
                        .filter(endpoints -> endpoints.getMetadata() != null)
                        .filter(endpoints -> endpoints.getSubsets() != null)
                        .map(endpoints -> {
                            final ObjectMeta metadata = endpoints.getMetadata();
                            if (endpoints
                                .getSubsets()
                                .stream()
                                .filter(subset -> subset.getAddresses() != null)
                                .flatMap(subset -> subset.getAddresses().stream())
                                .anyMatch(address -> Objects.equals(ip, address.getIp()))) {
                                return ObjectID
                                    .builder()
                                    .name(metadata.getName())
                                    .namespace(metadata.getNamespace())
                                    .build();
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .findFirst();
                if (serviceID.isEmpty()) {
                    log.debug("No corresponding endpoint for IP: {}", ip);
                    return config.serviceMetaInfoFactory().unknown();
                }

                final Optional<Service> service =
                    KubernetesServices.INSTANCE.findByID(serviceID.get());
                if (service.isEmpty()) {
                    log.debug("No service for namespace and name: {}", serviceID.get());
                    return config.serviceMetaInfoFactory().unknown();
                }
                log.debug(
                    "Composing service meta info from service and pod for IP: {}", ip);
                return composeServiceMetaInfo(service.get(), pod.get());
            }
        });
    }

    protected List<ServiceMetaInfo.KeyValue> transformLabelsToTags(final ObjectMeta podMeta) {
        final Map<String, String> labels = podMeta.getLabels();
        final List<ServiceMetaInfo.KeyValue> tags = new ArrayList<>();
        tags.add(new ServiceMetaInfo.KeyValue("pod", podMeta.getName()));
        tags.add(new ServiceMetaInfo.KeyValue("namespace", podMeta.getNamespace()));
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
        if (isNode(ip)) {
            return config.serviceMetaInfoFactory().unknown();
        }
        return ipServiceMetaInfoMap.get(ip);
    }

    protected ServiceMetaInfo composeServiceMetaInfo(final Service service, final Pod pod) {
        final Map<String, Object> context = ImmutableMap.of("service", service, "pod", pod);
        final ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        final ObjectMeta podMetadata = pod.getMetadata();

        try {
            serviceMetaInfo.setServiceName(serviceNameFormatter.format(context));
        } catch (Exception e) {
            log.error("Failed to evaluate service name.", e);
            final ObjectMeta serviceMetadata = service.getMetadata();
            if (isNull(serviceMetadata)) {
                log.warn("Service metadata is null, {}", service);
                return config.serviceMetaInfoFactory().unknown();
            }
            serviceMetaInfo.setServiceName(serviceMetadata.getName());
        }
        serviceMetaInfo.setServiceInstanceName(
            String.format("%s.%s", podMetadata.getName(), podMetadata.getNamespace()));
        serviceMetaInfo.setTags(transformLabelsToTags(podMetadata));

        return serviceMetaInfo;
    }

    @SneakyThrows
    public boolean isNode(final String ip) {
        return nodeIPs.get(this).contains(ip);
    }
}
