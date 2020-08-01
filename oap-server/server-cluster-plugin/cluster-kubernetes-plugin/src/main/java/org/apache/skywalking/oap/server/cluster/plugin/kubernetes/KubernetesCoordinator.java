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

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * Read collector pod info from api-server of kubernetes, then using all containerIp list to construct the list of
 * {@link RemoteInstance}.
 */
@Slf4j
public class KubernetesCoordinator implements ClusterRegister, ClusterNodesQuery {

    private final ModuleDefineHolder manager;

    private volatile int port = -1;

    private final String uid;

    public KubernetesCoordinator(final ModuleDefineHolder manager,
                                 final ClusterModuleKubernetesConfig config) {
        this.uid = new UidEnvSupplier(config.getUidEnvName()).get();
        this.manager = manager;
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {

        List<V1Pod> pods = NamespacedPodListInformer.INFORMER.listPods().orElseGet(this::selfPod);

        if (log.isDebugEnabled()) {
            List<String> uidList = pods
                .stream()
                .map(item -> item.getMetadata().getUid())
                .collect(Collectors.toList());
            log.debug("[kubernetes cluster pods uid list]:{}", uidList.toString());
        }

        if (port == -1) {
            port = manager.find(CoreModule.NAME).provider().getService(ConfigService.class).getGRPCPort();
        }

        return pods.stream()
                   .map(pod -> new RemoteInstance(
                       new Address(pod.getStatus().getPodIP(), port, pod.getMetadata().getUid().equals(uid))))
                   .collect(Collectors.toList());

    }

    @Override
    public void registerRemote(final RemoteInstance remoteInstance) throws ServiceRegisterException {
        this.port = remoteInstance.getAddress().getPort();
    }

    private List<V1Pod> selfPod() {

        V1Pod v1Pod = new V1Pod();
        v1Pod.setMetadata(new V1ObjectMeta());
        v1Pod.setStatus(new V1PodStatus());
        v1Pod.getMetadata().setUid(uid);
        v1Pod.getStatus().setPodIP("127.0.0.1");
        return Collections.singletonList(v1Pod);

    }
}
