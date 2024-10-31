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
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @since 9.0.0 This query is replaced by {@link MetadataQueryV2}
 */
@Deprecated
public class MetadataQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private MetadataQueryService metadataQueryService;

    public MetadataQuery(ModuleManager moduleManager) {
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

    public List<Service> getAllServices(final Duration duration,
                                        final String group) throws IOException {
        return getMetadataQueryService().listServices(null, group);
    }

    public List<Service> getAllBrowserServices(final Duration duration) throws IOException {
        return getMetadataQueryService().listServices(Layer.BROWSER.name(), null);
    }

    public List<Service> searchServices(final Duration duration,
                                        final String keyword) throws IOException {
        List<Service> services = getMetadataQueryService().listServices(null, null);
        return services.stream().filter(service -> service.getName().contains(keyword)).collect(Collectors.toList());
    }

    public Service searchService(final String serviceCode) throws IOException {
        return getMetadataQueryService().getService(IDManager.ServiceID.buildId(serviceCode, true));
    }

    public List<Service> searchBrowserServices(final Duration duration,
                                               final String keyword) throws IOException {
        List<Service> services = getMetadataQueryService().listServices(Layer.BROWSER.name(), null);
        return services.stream().filter(service -> service.getName().contains(keyword)).collect(Collectors.toList());
    }

    public Service searchBrowserService(final String serviceCode) throws IOException {
        return getMetadataQueryService().getService(IDManager.ServiceID.buildId(serviceCode, true));
    }

    public List<ServiceInstance> getServiceInstances(final Duration duration,
                                                     final String serviceId) throws IOException {
        return getMetadataQueryService().listInstances(duration, serviceId);
    }

    public List<Endpoint> searchEndpoint(final String keyword, final String serviceId,
                                         final int limit) throws IOException {
        return getMetadataQueryService().findEndpoint(keyword, serviceId, limit, null);
    }

    public List<Database> getAllDatabases(final Duration duration) throws IOException {
        final List<Service> serviceList = getMetadataQueryService().listServices(Layer.VIRTUAL_DATABASE.name(), null);
        return serviceList.stream().map(service -> {
            Database database = new Database();
            database.setId(service.getId());
            database.setName(service.getName());
            return database;
        }).distinct().collect(Collectors.toList());
    }
}
