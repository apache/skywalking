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
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public class MetadataQueryService implements org.apache.skywalking.oap.server.library.module.Service {

    private final ModuleManager moduleManager;
    private IMetadataQueryDAO metadataQueryDAO;

    public MetadataQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            metadataQueryDAO = moduleManager.find(StorageModule.NAME).getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    public ClusterBrief getGlobalBrief(final long startTimestamp, final long endTimestamp) throws IOException {
        ClusterBrief clusterBrief = new ClusterBrief();
        clusterBrief.setNumOfService(getMetadataQueryDAO().numOfService(startTimestamp, endTimestamp));
        clusterBrief.setNumOfEndpoint(getMetadataQueryDAO().numOfEndpoint(startTimestamp, endTimestamp));
        clusterBrief.setNumOfDatabase(getMetadataQueryDAO().numOfConjectural(startTimestamp, endTimestamp, SpanLayer.Database_VALUE));
        clusterBrief.setNumOfCache(getMetadataQueryDAO().numOfConjectural(startTimestamp, endTimestamp, SpanLayer.Cache_VALUE));
        clusterBrief.setNumOfMQ(getMetadataQueryDAO().numOfConjectural(startTimestamp, endTimestamp, SpanLayer.MQ_VALUE));
        return clusterBrief;
    }

    public List<Service> getAllServices(final long startTimestamp, final long endTimestamp) throws IOException {
        return getMetadataQueryDAO().getAllServices(startTimestamp, endTimestamp);
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
}
