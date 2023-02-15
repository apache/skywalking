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
public class MetadataQueryV2 implements GraphQLQueryResolver {

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

    public Set<String> listLayers() throws IOException {
        return getMetadataQueryService().listLayers();
    }

    public List<Service> listServices(final String layer) throws IOException {
        return getMetadataQueryService().listServices(layer, null);
    }

    public Service findService(final String serviceName) throws IOException {
        return getMetadataQueryService().getService(IDManager.ServiceID.buildId(serviceName, true));
    }

    public Service getService(final String serviceId) throws IOException {
        return getMetadataQueryService().getService(serviceId);
    }

    public List<ServiceInstance> listInstances(final Duration duration,
                                               final String serviceId) throws IOException {
        return getMetadataQueryService().listInstances(duration, serviceId);
    }

    public ServiceInstance getInstance(final String instanceId) throws IOException {
        return getMetadataQueryService().getInstance(instanceId);
    }

    public List<Endpoint> findEndpoint(final String keyword, final String serviceId,
                                       final int limit) throws IOException {
        return getMetadataQueryService().findEndpoint(keyword, serviceId, limit);
    }

    public EndpointInfo getEndpointInfo(final String endpointId) throws IOException {
        return getMetadataQueryService().getEndpointInfo(endpointId);
    }

    public List<Process> listProcesses(final Duration duration, final String instanceId) throws IOException {
        return getMetadataQueryService().listProcesses(duration, instanceId);
    }

    public Process getProcess(final String processId) throws IOException {
        return getMetadataQueryService().getProcess(processId);
    }

    public Long estimateProcessScale(String serviceId, List<String> labels) throws IOException {
        return getMetadataQueryService().estimateProcessScale(serviceId, labels);
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
