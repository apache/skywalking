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

package org.apache.skywalking.oap.meter.analyzer.k8s;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import lombok.SneakyThrows;
import org.apache.skywalking.library.kubernetes.KubernetesPods;
import org.apache.skywalking.library.kubernetes.KubernetesServices;
import org.apache.skywalking.library.kubernetes.ObjectID;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class K8sInfoRegistry {
    private final static K8sInfoRegistry INSTANCE = new K8sInfoRegistry();
    private final LoadingCache<ObjectID /* Pod */, ObjectID /* Service */> podServiceMap;
    private final LoadingCache<String/* podIP */, ObjectID /* Pod */> ipPodMap;
    private final LoadingCache<String/* serviceIP */, ObjectID /* Service */> ipServiceMap;

    private K8sInfoRegistry() {
        ipPodMap = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(3))
            .build(CacheLoader.from(ip -> KubernetesPods.INSTANCE
                .findByIP(ip)
                .map(it -> ObjectID
                    .builder()
                    .name(it.getMetadata().getName())
                    .namespace(it.getMetadata().getNamespace())
                    .build())
                .orElse(ObjectID.EMPTY)));

        ipServiceMap = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(3))
            .build(CacheLoader.from(ip -> KubernetesServices.INSTANCE
                .list()
                .stream()
                .filter(it -> it.getSpec() != null)
                .filter(it -> it.getStatus() != null)
                .filter(it -> it.getMetadata() != null)
                .filter(it -> (it.getSpec().getClusterIPs() != null &&
                    it.getSpec().getClusterIPs().stream()
                        .anyMatch(clusterIP -> Objects.equals(clusterIP, ip)))
                    || (it.getStatus().getLoadBalancer() != null &&
                        it.getStatus().getLoadBalancer().getIngress() != null &&
                        it.getStatus().getLoadBalancer().getIngress().stream()
                            .anyMatch(ingress -> Objects.equals(ingress.getIp(), ip))))
                .map(it -> ObjectID
                    .builder()
                    .name(it.getMetadata().getName())
                    .namespace(it.getMetadata().getNamespace())
                    .build())
                .findFirst()
                .orElse(ObjectID.EMPTY)));

        podServiceMap = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(3))
            .build(CacheLoader.from(podObjectID -> {
                final Optional<Pod> pod = KubernetesPods.INSTANCE
                    .findByObjectID(
                        ObjectID
                            .builder()
                            .name(podObjectID.name())
                            .namespace(podObjectID.namespace())
                            .build());

                if (!pod.isPresent()
                    || pod.get().getMetadata() == null
                    || pod.get().getMetadata().getLabels() == null) {
                    return ObjectID.EMPTY;
                }

                final Optional<Service> service = KubernetesServices.INSTANCE
                    .list()
                    .stream()
                    .filter(it -> it.getMetadata() != null)
                    .filter(it -> Objects.equals(it.getMetadata().getNamespace(), pod.get().getMetadata().getNamespace()))
                    .filter(it -> it.getSpec() != null)
                    .filter(it -> requireNonNull(it.getSpec()).getSelector() != null)
                    .filter(it -> {
                        final Map<String, String> labels = pod.get().getMetadata().getLabels();
                        final Map<String, String> selector = it.getSpec().getSelector();
                        return hasIntersection(selector.entrySet(), labels.entrySet());
                    })
                    .findFirst();
                if (!service.isPresent()) {
                    return ObjectID.EMPTY;
                }
                return ObjectID
                    .builder()
                    .name(service.get().getMetadata().getName())
                    .namespace(service.get().getMetadata().getNamespace())
                    .build();
            }));
    }

    public static K8sInfoRegistry getInstance() {
        return INSTANCE;
    }

    @SneakyThrows
    public String findServiceName(String namespace, String podName) {
        return this.podServiceMap.get(
            ObjectID
                .builder()
                .name(podName)
                .namespace(namespace)
                .build())
            .toString();
    }

    @SneakyThrows
    public String findPodByIP(String ip) {
        return this.ipPodMap.get(ip).toString();
    }

    @SneakyThrows
    public String findServiceByIP(String ip) {
        return this.ipServiceMap.get(ip).toString();
    }

    private boolean hasIntersection(Collection<?> o, Collection<?> c) {
        Objects.requireNonNull(o);
        Objects.requireNonNull(c);
        for (final Object value : o) {
            if (!c.contains(value)) {
                return false;
            }
        }
        return true;
    }
}
