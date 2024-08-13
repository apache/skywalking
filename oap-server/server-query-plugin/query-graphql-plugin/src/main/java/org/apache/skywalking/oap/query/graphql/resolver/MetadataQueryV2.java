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

package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.query.graphql.AsyncQuery;
import org.apache.skywalking.oap.query.graphql.type.TimeInfo;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.EndpointInfo;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Metadata v2 query protocol implementation.
 *
 * @since 9.0.0
 */
public class MetadataQueryV2 extends AsyncQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private MetadataQueryService metadataQueryService;

    public MetadataQueryV2(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private MetadataQueryService getMetadataQueryService() {
        if (metadataQueryService == null) {
            this.metadataQueryService = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(MetadataQueryService.class);
        }
        return metadataQueryService;
    }

    public CompletableFuture<Set<String>> listLayers() {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().listLayers();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<Service>> listServices(final String layer) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().listServices(layer, null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Service> findService(final String serviceName) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().getService(IDManager.ServiceID.buildId(serviceName, true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Service> getService(final String serviceId) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().getService(serviceId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<ServiceInstance>> listInstances(final Duration duration,
                                                                  final String serviceId) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().listInstances(duration, serviceId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<ServiceInstance> getInstance(final String instanceId) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().getInstance(instanceId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<Endpoint>> findEndpoint(final String keyword, final String serviceId,
                                                          final int limit) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().findEndpoint(keyword, serviceId, limit);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<EndpointInfo> getEndpointInfo(final String endpointId) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().getEndpointInfo(endpointId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<Process>> listProcesses(final Duration duration, final String instanceId) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().listProcesses(duration, instanceId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Process> getProcess(final String processId) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().getProcess(processId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Long> estimateProcessScale(String serviceId, List<String> labels) {
        return queryAsync(() -> {
            try {
                return getMetadataQueryService().estimateProcessScale(serviceId, labels);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public TimeInfo getTimeInfo() {
        TimeInfo timeInfo = new TimeInfo();
        SimpleDateFormat timezoneFormat = new SimpleDateFormat("ZZZZZZ");
        Date date = new Date();
        timeInfo.setCurrentTimestamp(date.getTime());
        timeInfo.setTimezone(timezoneFormat.format(date));
        return timeInfo;
    }
}
