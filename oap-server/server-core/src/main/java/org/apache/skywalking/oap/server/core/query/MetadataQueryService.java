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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public class MetadataQueryService implements org.apache.skywalking.oap.server.library.module.Service {

    private final ModuleManager moduleManager;
    private IMetadataQueryDAO metadataQueryDAO;
    private ServiceInventoryCache serviceInventoryCache;
    private EndpointInventoryCache endpointInventoryCache;

    public MetadataQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            metadataQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (serviceInventoryCache == null) {
            serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    private EndpointInventoryCache getEndpointInventoryCache() {
        if (endpointInventoryCache == null) {
            endpointInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class);
        }
        return endpointInventoryCache;
    }

    public ClusterBrief getGlobalBrief(final long startTimestamp, final long endTimestamp) throws IOException {
        ClusterBrief clusterBrief = new ClusterBrief();
        clusterBrief.setNumOfService(getMetadataQueryDAO().numOfService(startTimestamp, endTimestamp));
        clusterBrief.setNumOfEndpoint(getMetadataQueryDAO().numOfEndpoint(startTimestamp, endTimestamp));
        clusterBrief.setNumOfDatabase(getMetadataQueryDAO().numOfConjectural(startTimestamp, endTimestamp, NodeType.Database.value()));
        clusterBrief.setNumOfCache(getMetadataQueryDAO().numOfConjectural(startTimestamp, endTimestamp, NodeType.Cache.value()));
        clusterBrief.setNumOfMQ(getMetadataQueryDAO().numOfConjectural(startTimestamp, endTimestamp, NodeType.MQ.value()));
        return clusterBrief;
    }

    public List<Service> getAllServices(final long startTimestamp, final long endTimestamp) throws IOException {
        return getMetadataQueryDAO().getAllServices(startTimestamp, endTimestamp);
    }

    public List<Database> getAllDatabases() throws IOException {
        return getMetadataQueryDAO().getAllDatabases();
    }

    public List<Service> searchServices(final long startTimestamp, final long endTimestamp,
        final String keyword) throws IOException {
        return getMetadataQueryDAO().searchServices(startTimestamp, endTimestamp, keyword);
    }

    public List<ServiceInstance> getServiceInstances(final long startTimestamp, final long endTimestamp,
        final String serviceId) throws IOException {
        return getMetadataQueryDAO().getServiceInstances(startTimestamp, endTimestamp, serviceId);
    }

    public List<Endpoint> searchEndpoint(final String keyword, final String serviceId,
        final int limit) throws IOException {
        return getMetadataQueryDAO().searchEndpoint(keyword, serviceId, limit);
    }

    public Service searchService(final String serviceCode) throws IOException {
        return getMetadataQueryDAO().searchService(serviceCode);
    }

    public EndpointInfo getEndpointInfo(final int endpointId) throws IOException {
        EndpointInventory endpointInventory = getEndpointInventoryCache().get(endpointId);

        EndpointInfo endpointInfo = new EndpointInfo();
        endpointInfo.setId(endpointInventory.getSequence());
        endpointInfo.setName(endpointInventory.getName());
        endpointInfo.setServiceId(endpointInventory.getServiceId());
        endpointInfo.setServiceName(getServiceInventoryCache().get(endpointInventory.getServiceId()).getName());
        return endpointInfo;
    }
}
