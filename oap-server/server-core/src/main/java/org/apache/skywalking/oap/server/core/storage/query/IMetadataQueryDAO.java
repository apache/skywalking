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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.DAO;

public interface IMetadataQueryDAO extends DAO {
    /**
     * @param group group name for filtering.
     * @return list of the all available normal services
     */
    List<Service> getAllServices(final String group) throws IOException;

    /**
     * @return list of the all available browser services
     */
    List<Service> getAllBrowserServices() throws IOException;

    /**
     * @return list of all conjecture database services.
     */
    List<Database> getAllDatabases() throws IOException;

    /**
     * @param keyword to filter the normal service
     * @return the list of normal services matching the given keyword
     */
    List<Service> searchServices(final String keyword) throws IOException;

    /**
     * @param serviceCode to literal match
     * @return the service matching the given full name.
     */
    Service searchService(final String serviceCode) throws IOException;

    /**
     * @param keyword   to filter the endpoints
     * @param serviceId the owner of the endpoints
     * @param limit     max match size.
     * @return list of services matching the given conditions.
     */
    List<Endpoint> searchEndpoint(final String keyword, final String serviceId, final int limit) throws IOException;

    /**
     * @param startTimestamp The instance is required to be live after this timestamp
     * @param endTimestamp   The instance is required to be live before this timestamp.
     * @param serviceId      the owner of the instances.
     * @return list of instances matching the given conditions.
     */
    List<ServiceInstance> getServiceInstances(final long startTimestamp, final long endTimestamp,
                                              final String serviceId) throws IOException;
}
