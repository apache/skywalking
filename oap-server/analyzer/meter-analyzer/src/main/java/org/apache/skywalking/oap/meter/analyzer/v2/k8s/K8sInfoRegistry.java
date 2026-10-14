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

package org.apache.skywalking.oap.meter.analyzer.v2.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.apache.skywalking.library.kubernetes.KubernetesPods;
import org.apache.skywalking.library.kubernetes.KubernetesReadFailedException;
import org.apache.skywalking.library.kubernetes.KubernetesServices;
import org.apache.skywalking.library.kubernetes.LookupCache;
import org.apache.skywalking.library.kubernetes.ObjectID;
import org.apache.skywalking.oap.server.core.config.K8sMetadataReadSettings;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Resolves pods and services for MAL and the eBPF access log. No lookup waits: a key not read yet is found by a later
 * lookup, as it is read in the background. A MAL step waits instead, before its lookups, with
 * {@link #readServicesOfPods} or {@link #readPodAndServiceByIP}: they queue the reads of all its keys not read yet,
 * then wait for them up to {@link K8sMetadataReadSettings#getMalReadTimeoutMillis()} in all, so the step waits that
 * long at most however many keys it has, and whatever happens to their reads meanwhile. The lookups MAL makes queue
 * their reads in the lane of the reads waited for; those ending in {@code WithoutWait}, for the eBPF access log, in the
 * other lane.
 */
public class K8sInfoRegistry {
    private final static K8sInfoRegistry INSTANCE = new K8sInfoRegistry();
    private final LookupCache<ObjectID /* Pod */, ObjectID /* Service */> podServiceMap;
    private final LookupCache<String/* podIP */, ObjectID /* Pod */> ipPodMap;
    private final LookupCache<String/* serviceIP */, ObjectID /* Service */> ipServiceMap;

    private K8sInfoRegistry() {
        ipPodMap = newCache(ip -> KubernetesPods.INSTANCE
            .findByIP(ip)
            .map(it -> ObjectID
                .builder()
                .name(it.getMetadata().getName())
                .namespace(it.getMetadata().getNamespace())
                .build())
            .orElse(null));
        ipServiceMap = newCache(ip -> findServiceByIP(KubernetesServices.INSTANCE.list(), ip)
            .or(() -> findServiceByIP(
                KubernetesServices.INSTANCE.list(K8sMetadataReadSettings.getNotFoundRetryPeriod()), ip))
            .orElse(null));
        podServiceMap = newCache(this::readServiceOfPod);
    }

    private static <K> LookupCache<K, ObjectID> newCache(final Function<K, ObjectID> reader) {
        return new LookupCache<>(
            reader, K8sMetadataReadSettings.getRefreshPeriod(), K8sMetadataReadSettings.getNotFoundRetryPeriod(),
            K8sMetadataReadSettings.getMalCacheTime(),
            K8sMetadataReadSettings::getMalReadQueueSize, K8sMetadataReadSettings::getAlsReadQueueSize
        );
    }

    /**
     * Runs in the background. A pod matching no service in the services read may be of a service newer than them, so
     * they are read again when they are older than the not-found retry period.
     *
     * @return the service selecting the pod, or null when the pod or the service is not found
     * @throws KubernetesReadFailedException when a read failed
     */
    private ObjectID readServiceOfPod(final ObjectID podObjectID) {
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
            return null;
        }

        return findServiceOfPod(KubernetesServices.INSTANCE.list(), pod.get())
            .or(() -> findServiceOfPod(
                KubernetesServices.INSTANCE.list(K8sMetadataReadSettings.getNotFoundRetryPeriod()), pod.get()))
            .map(service -> ObjectID
                .builder()
                .name(service.getMetadata().getName())
                .namespace(service.getMetadata().getNamespace())
                .build())
            .orElse(null);
    }

    private Optional<Service> findServiceOfPod(final List<Service> services, final Pod pod) {
        return services
            .stream()
            .filter(it -> it.getMetadata() != null)
            .filter(it -> Objects.equals(it.getMetadata().getNamespace(), pod.getMetadata().getNamespace()))
            .filter(it -> it.getSpec() != null)
            .filter(it -> requireNonNull(it.getSpec()).getSelector() != null)
            .filter(it -> !it.getSpec().getSelector().isEmpty())
            .filter(it -> {
                final Map<String, String> labels = pod.getMetadata().getLabels();
                final Map<String, String> selector = it.getSpec().getSelector();
                return hasIntersection(selector.entrySet(), labels.entrySet());
            })
            .findFirst();
    }

    private static Optional<ObjectID> findServiceByIP(final List<Service> services, final String ip) {
        return services
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
            .findFirst();
    }

    public static K8sInfoRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Reads the services of the pods not read yet, for the lookups of them that follow: queues all their reads, then
     * waits for them up to {@link K8sMetadataReadSettings#getMalReadTimeoutMillis()} in all, cached pods not at all.
     * The steps over the same pods one after another, such as the {@code retagByK8sMeta} of one batch of data, wait for
     * a pod that long at most in all. The reads not done by then go on in the background.
     */
    public void readServicesOfPods(Collection<ObjectID> pods) {
        final long timeout = K8sMetadataReadSettings.getMalReadTimeoutMillis();
        pods.forEach(pod -> podServiceMap.queueForWaiting(pod, timeout));
        final long deadline = deadline(timeout);
        for (final ObjectID pod : pods) {
            podServiceMap.awaitRead(pod, timeout, remainingMillis(deadline));
        }
    }

    /**
     * Reads the pod and the service using {@code ip}, if not read yet, for the lookups of them that follow: queues both
     * reads, then waits for them up to {@link K8sMetadataReadSettings#getMalReadTimeoutMillis()} in all.
     */
    public void readPodAndServiceByIP(String ip) {
        final long timeout = K8sMetadataReadSettings.getMalReadTimeoutMillis();
        ipPodMap.queueForWaiting(ip, timeout);
        ipServiceMap.queueForWaiting(ip, timeout);
        final long deadline = deadline(timeout);
        ipPodMap.awaitRead(ip, timeout, remainingMillis(deadline));
        ipServiceMap.awaitRead(ip, timeout, remainingMillis(deadline));
    }

    private static long deadline(final long timeoutMillis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    private static long remainingMillis(final long deadline) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
    }

    public String findServiceName(String namespace, String podName) {
        return findService(namespace, podName).toString();
    }

    public ObjectID findService(String namespace, String podName) {
        return podServiceMap.find(podObjectID(namespace, podName), 0).orElse(ObjectID.EMPTY);
    }

    public ObjectID findPodByIP(String ip) {
        return ipPodMap.find(ip, 0).orElse(ObjectID.EMPTY);
    }

    public ObjectID findServiceByIP(String ip) {
        return ipServiceMap.find(ip, 0).orElse(ObjectID.EMPTY);
    }

    public ObjectID findServiceWithoutWait(String namespace, String podName) {
        return podServiceMap.find(podObjectID(namespace, podName)).orElse(ObjectID.EMPTY);
    }

    public ObjectID findPodByIPWithoutWait(String ip) {
        return ipPodMap.find(ip).orElse(ObjectID.EMPTY);
    }

    public ObjectID findServiceByIPWithoutWait(String ip) {
        return ipServiceMap.find(ip).orElse(ObjectID.EMPTY);
    }

    private static ObjectID podObjectID(final String namespace, final String podName) {
        return ObjectID
            .builder()
            .name(podName)
            .namespace(namespace)
            .build();
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
