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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchResponse;
import io.grpc.stub.StreamObserver;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.cluster.ClusterHealthStatus;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class EtcdCoordinator extends ClusterCoordinator {
    private static final Gson GSON = new Gson().newBuilder().create();
    private final ModuleDefineHolder manager;
    private final ClusterModuleEtcdConfig config;
    private volatile Address selfAddress;
    private HealthCheckMetrics healthChecker;

    private final Client client;
    private final String serviceName;
    private final ByteSequence serviceNameBS;

    public EtcdCoordinator(final ModuleDefineHolder manager,
                           final ClusterModuleEtcdConfig config) throws ModuleStartException {
        if (Strings.isNullOrEmpty(config.getServiceName())) {
            throw new ModuleStartException("ServiceName cannot be empty.");
        }
        this.manager = manager;
        this.config = config;
        if (!config.getServiceName().endsWith("/")) {
            serviceName = config.getServiceName() + "/";
        } else {
            serviceName = config.getServiceName();
        }
        this.serviceNameBS = ByteSequence.from(serviceName, Charset.defaultCharset());
        ClientBuilder builder = Client.builder()
                                      .target(config.getEndpoints())
                                      .authority(config.getAuthority());
        if (StringUtil.isNotEmpty(config.getNamespace())) {
            builder.namespace(ByteSequence.from(config.getNamespace(), Charset.defaultCharset()));
        }
        if (config.isAuthentication()) {
            builder.user(ByteSequence.from(config.getUser(), Charset.defaultCharset()))
                   .password(ByteSequence.from(config.getPassword(), Charset.defaultCharset()));
        }
        this.client = builder.build();
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        try {
            final KV kvClient = client.getKVClient();
            final GetResponse response = kvClient.get(
                serviceNameBS,
                GetOption.newBuilder().withPrefix(serviceNameBS).build()
            ).get();

            response.getKvs().forEach(kv -> {
                EtcdEndpoint endpoint = GSON.fromJson(
                    kv.getValue().toString(Charset.defaultCharset()),
                    EtcdEndpoint.class
                );
                Address address = new Address(endpoint.getHost(), endpoint.getPort(), false);
                if (address.equals(selfAddress)) {
                    address.setSelf(true);
                }
                remoteInstances.add(new RemoteInstance(address));
            });

            ClusterHealthStatus healthStatus = OAPNodeChecker.isHealth(remoteInstances);
            if (healthStatus.isHealth()) {
                this.healthChecker.health();
            } else {
                this.healthChecker.unHealth(healthStatus.getReason());
            }
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new RuntimeException(e);
        }
        if (log.isDebugEnabled()) {
            remoteInstances.forEach(instance -> log.debug("Etcd cluster instance: {}", instance));
        }
        return remoteInstances;
    }

    @Override
    public void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {
        if (needUsingInternalAddr()) {
            remoteInstance = new RemoteInstance(
                new Address(config.getInternalComHost(), config.getInternalComPort(), true));
        }

        this.selfAddress = remoteInstance.getAddress();
        final EtcdEndpoint endpoint = new EtcdEndpoint.Builder().serviceName(serviceName)
                                                                .host(selfAddress.getHost())
                                                                .port(selfAddress.getPort())
                                                                .build();
        try {
            final Lease leaseClient = client.getLeaseClient();
            final long leaseID = leaseClient.grant(30L).get().getID();

            ByteSequence instance = ByteSequence.from(GSON.toJson(endpoint), Charset.defaultCharset());
            client.getKVClient()
                  .put(
                      buildKey(serviceName, selfAddress, remoteInstance),
                      instance,
                      PutOption.newBuilder().withLeaseId(leaseID).build()
                  )
                  .get();
            healthChecker.health();

            client.getLeaseClient().keepAlive(leaseID, new StreamObserver<LeaseKeepAliveResponse>() {
                @Override
                public void onNext(final LeaseKeepAliveResponse response) {
                    if (log.isDebugEnabled()) {
                        log.debug("Refresh lease id = {}, ttl = {}", response.getID(), response.getTTL());
                    }
                }

                @Override
                public void onError(final Throwable throwable) {
                    log.error("Failed to keep alive in Etcd coordinator", throwable);
                    healthChecker.unHealth(throwable);
                }

                @Override
                public void onCompleted() {

                }
            });
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceRegisterException(e);
        }
    }

    private static ByteSequence buildKey(String serviceName, Address address, RemoteInstance instance) {
        String key = new StringBuilder(serviceName).append(address.getHost())
                                                   .append("_")
                                                   .append(instance.hashCode())
                                                   .toString();
        return ByteSequence.from(key, Charset.defaultCharset());
    }

    private boolean needUsingInternalAddr() {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }

    private void initHealthChecker() {
        if (healthChecker == null) {
            MetricsCreator metricCreator = manager.find(TelemetryModule.NAME)
                                                  .provider()
                                                  .getService(MetricsCreator.class);
            healthChecker = metricCreator.createHealthCheckerGauge(
                "cluster_etcd", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }

    @Override
    public void start() {
        initHealthChecker();
        this.client.getWatchClient().watch(
            serviceNameBS, WatchOption.newBuilder().withPrefix(serviceNameBS).build(), new EtcdEventListener());
    }

    class EtcdEventListener implements Watch.Listener {
        @Override
        public void onNext(final WatchResponse response) {
            response.getEvents().forEach(event -> {
                switch (event.getEventType()) {
                    case DELETE:
                    case PUT:
                        if (log.isDebugEnabled()) {
                            String key = event.getKeyValue().getKey().toString(Charset.defaultCharset());
                            log.debug("{}: key = {}}", event.getEventType().name(), key);
                        }
                        notifyWatchers(queryRemoteNodes());
                        break;
                    default:
                        break;
                }
            });
        }

        @Override
        public void onError(final Throwable throwable) {
            log.error("Failed to notify RemoteInstances update.", throwable);
            healthChecker.unHealth(throwable);
        }

        @Override
        public void onCompleted() {
        }
    }
}
