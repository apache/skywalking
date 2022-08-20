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

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.library.kubernetes.KubernetesPodListener;
import org.apache.skywalking.library.kubernetes.KubernetesPodWatcher;
import org.apache.skywalking.library.kubernetes.KubernetesServiceListener;
import org.apache.skywalking.library.kubernetes.KubernetesServiceWatcher;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import com.google.common.base.Strings;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1LoadBalancerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import lombok.SneakyThrows;

public class K8sInfoRegistry
    implements KubernetesServiceListener, KubernetesPodListener {

    private final static K8sInfoRegistry INSTANCE = new K8sInfoRegistry();
    private final Map<String/* podName.namespace */, V1Pod> namePodMap = new ConcurrentHashMap<>();
    protected final Map<String/* serviceName.namespace  */, V1Service> nameServiceMap = new ConcurrentHashMap<>();
    private final Map<String/* podName.namespace */, String /* serviceName.namespace */> podServiceMap = new ConcurrentHashMap<>();
    private final Map<String/* podIP */, String /* podName.namespace */> ipPodMap = new ConcurrentHashMap<>();
    private final Map<String/* serviceIP */, String /* serviceName.namespace */> ipServiceMap = new ConcurrentHashMap<>();
    private static final String SEPARATOR = ".";

    public static K8sInfoRegistry getInstance() {
        return INSTANCE;
    }

    @SneakyThrows
    public void start() {
        KubernetesPodWatcher.INSTANCE.addListener(this).start();
        KubernetesServiceWatcher.INSTANCE.addListener(this).start();
    }

    @Override
    public void onServiceAdded(final V1Service service) {
        ofNullable(service.getMetadata()).ifPresent(
            metadata -> nameServiceMap.put(metadata.getName() + SEPARATOR + metadata.getNamespace(), service)
        );
        recompose();
    }

    @Override
    public void onServiceDeleted(final V1Service service) {
        ofNullable(service.getMetadata()).ifPresent(
            metadata -> nameServiceMap.remove(metadata.getName() + SEPARATOR + metadata.getNamespace())
        );
        ofNullable(service.getStatus()).map(V1ServiceStatus::getLoadBalancer).filter(Objects::nonNull)
            .map(V1LoadBalancerStatus::getIngress).filter(CollectionUtils::isNotEmpty)
            .ifPresent(l -> l.stream().filter(i -> StringUtil.isNotEmpty(i.getIp()))
                    .forEach(i -> ipServiceMap.remove(i.getIp())));
        ofNullable(service.getSpec()).map(V1ServiceSpec::getClusterIPs).filter(CollectionUtils::isNotEmpty)
            .ifPresent(l -> l.stream().filter(StringUtil::isNotEmpty).forEach(ipServiceMap::remove));
        recompose();
    }

    @Override
    public void onServiceUpdated(V1Service oldService, V1Service newService) {
        onServiceAdded(newService);
    }

    @Override
    public void onPodAdded(final V1Pod pod) {
        ofNullable(pod.getMetadata()).ifPresent(
            metadata -> namePodMap.put(metadata.getName() + SEPARATOR + metadata.getNamespace(), pod));

        recompose();
    }

    @Override
    public void onPodDeleted(final V1Pod pod) {
        ofNullable(pod.getMetadata()).ifPresent(
            metadata -> namePodMap.remove(metadata.getName() + SEPARATOR + metadata.getNamespace()));

        ofNullable(pod.getMetadata()).ifPresent(
            metadata -> podServiceMap.remove(metadata.getName() + SEPARATOR + metadata.getNamespace()));

        ofNullable(pod.getStatus()).filter(s -> StringUtil.isNotEmpty(s.getPodIP())).ifPresent(
            status -> ipPodMap.remove(status.getPodIP()));
    }

    @Override
    public void onPodUpdated(V1Pod oldPod, V1Pod newPod) {
        onPodAdded(newPod);
    }

    private void recompose() {
        namePodMap.forEach((podName, pod) -> {
            if (!isNull(pod.getMetadata())) {
                ofNullable(pod.getStatus()).filter(s -> StringUtil.isNotEmpty(s.getPodIP())).ifPresent(
                        status -> ipPodMap.put(status.getPodIP(), podName));
            }

            nameServiceMap.forEach((serviceName, service) -> {
                if (isNull(pod.getMetadata()) || isNull(service.getMetadata()) || isNull(service.getSpec())) {
                    return;
                }

                Map<String, String> selector = service.getSpec().getSelector();
                Map<String, String> labels = pod.getMetadata().getLabels();

                if (isNull(labels) || isNull(selector)) {
                    return;
                }

                String podNamespace = pod.getMetadata().getNamespace();
                String serviceNamespace = service.getMetadata().getNamespace();

                if (Strings.isNullOrEmpty(podNamespace) || Strings.isNullOrEmpty(
                    serviceNamespace) || !podNamespace.equals(serviceNamespace)) {
                    return;
                }

                if (hasIntersection(selector.entrySet(), labels.entrySet())) {
                    podServiceMap.put(podName, serviceName);
                }
            });
        });
        nameServiceMap.forEach((serviceName, service) -> {
            if (!isNull(service.getSpec()) && CollectionUtils.isNotEmpty(service.getSpec().getClusterIPs())) {
                for (String clusterIP : service.getSpec().getClusterIPs()) {
                    ipServiceMap.put(clusterIP, serviceName);
                }
            }
            if (!isNull(service.getStatus()) && !isNull(service.getStatus().getLoadBalancer())
                && CollectionUtils.isNotEmpty(service.getStatus().getLoadBalancer().getIngress())) {
                for (V1LoadBalancerIngress loadBalancerIngress : service.getStatus().getLoadBalancer().getIngress()) {
                    if (StringUtil.isNotEmpty(loadBalancerIngress.getIp())) {
                        ipServiceMap.put(loadBalancerIngress.getIp(), serviceName);
                    }
                }
            }
        });
    }

    public String findServiceName(String namespace, String podName) {
        return this.podServiceMap.get(podName + SEPARATOR + namespace);
    }

    public String findPodByIP(String ip) {
        return this.ipPodMap.get(ip);
    }

    public String findServiceByIP(String ip) {
        return this.ipServiceMap.get(ip);
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
