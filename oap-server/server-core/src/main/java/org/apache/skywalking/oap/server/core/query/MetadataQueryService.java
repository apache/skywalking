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
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.EndpointInfo;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class MetadataQueryService implements org.apache.skywalking.oap.server.library.module.Service {

    private final ModuleManager moduleManager;
    private IMetadataQueryDAO metadataQueryDAO;

    public MetadataQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            metadataQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    public List<Service> listServices(final String layer, final String group) throws IOException {
        return getMetadataQueryDAO().listServices(layer, group).stream()
                                    .peek(service -> {
                                        if (service.getGroup() == null) {
                                            service.setGroup(Const.EMPTY_STRING);
                                        }
                                    })
                                    .distinct()
                                    .collect(Collectors.toList());
    }

    public Service getService(final String serviceId) throws IOException {
        Service service = getMetadataQueryDAO().findService(serviceId);
        if (service.getGroup() == null) {
            service.setGroup(Const.EMPTY_STRING);
        }
        return service;
    }

    public ServiceInstance getInstance(final String instanceId) throws IOException {
        return getMetadataQueryDAO().getInstance(instanceId);
    }

//    public List<Service> getAllBrowserServices() throws IOException {
//        return getMetadataQueryDAO().getAllBrowserServices().stream().distinct().collect(Collectors.toList());
//    }
//
//    public List<Database> getAllDatabases() throws IOException {
//        return getMetadataQueryDAO().getAllDatabases().stream().distinct().collect(Collectors.toList());
//    }
//
//    public List<Service> searchServices(final long startTimestamp, final long endTimestamp,
//                                        final String keyword) throws IOException {
//        return getMetadataQueryDAO().searchServices(NodeType.Normal, keyword)
//                                    .stream()
//                                    .distinct()
//                                    .collect(Collectors.toList());
//    }
//
//    public List<Service> searchBrowserServices(final long startTimestamp, final long endTimestamp,
//                                               final String keyword) throws IOException {
//        return getMetadataQueryDAO().searchServices(NodeType.Browser, keyword)
//                                    .stream()
//                                    .distinct()
//                                    .collect(Collectors.toList());
//    }

    public List<ServiceInstance> listInstances(final long startTimestamp, final long endTimestamp,
                                                     final String serviceId) throws IOException {
        return getMetadataQueryDAO().listInstances(startTimestamp, endTimestamp, serviceId)
                                    .stream().distinct().collect(Collectors.toList());
    }

    public List<Endpoint> findEndpoint(final String keyword, final String serviceId,
                                       final int limit) throws IOException {
        return getMetadataQueryDAO().findEndpoint(keyword, serviceId, limit)
                                    .stream().distinct().collect(Collectors.toList());
    }

//    public Service searchService(final String serviceCode) throws IOException {
//        return getMetadataQueryDAO().searchService(NodeType.Normal, serviceCode);
//    }
//
//    public Service searchBrowserService(final String serviceCode) throws IOException {
//        return getMetadataQueryDAO().searchService(NodeType.Browser, serviceCode);
//    }

    public EndpointInfo getEndpointInfo(final String endpointId) throws IOException {
        final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
            endpointId);
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
            endpointIDDefinition.getServiceId());

        EndpointInfo endpointInfo = new EndpointInfo();
        endpointInfo.setId(endpointId);
        endpointInfo.setName(endpointIDDefinition.getEndpointName());
        endpointInfo.setServiceId(endpointIDDefinition.getServiceId());
        endpointInfo.setServiceName(serviceIDDefinition.getName());
        return endpointInfo;
    }
}
