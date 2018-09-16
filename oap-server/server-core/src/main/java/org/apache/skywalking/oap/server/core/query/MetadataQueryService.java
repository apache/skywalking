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

import java.util.*;
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

    public ClusterBrief getGlobalBrief(final Step step, final long startTB, final long endTB) {
        return new ClusterBrief();
    }

    public List<Service> getAllServices(final Step step, final long startTB, final long endTB) {
        return Collections.emptyList();
    }

    public List<Service> searchServices(final Step step, final long startTB, final long endTB, final String keyword) {
        return Collections.emptyList();
    }

    public List<ServiceInstance> getServiceInstances(final Step step, final long startTB, final long endTB,
        final String id) {
        return Collections.emptyList();
    }

    public List<Endpoint> searchEndpoint(final String keyword, final String serviceId, final int limit) {
        return Collections.emptyList();
    }

    public Service searchService(final Step step, final long startTB, final long endTB, final String serviceCode) {
        return new Service();
    }
}
