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
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.storage.DAO;

/**
 * @author peng-yongsheng
 */
public interface IMetadataQueryDAO extends DAO {

    int numOfService(final long startTimestamp, final long endTimestamp) throws IOException;

    int numOfEndpoint(final long startTimestamp, final long endTimestamp) throws IOException;

    int numOfConjectural(final long startTimestamp, final long endTimestamp, final int srcLayer) throws IOException;

    List<Service> getAllServices(final long startTimestamp, final long endTimestamp) throws IOException;

    List<Service> searchServices(final long startTimestamp, final long endTimestamp,
        final String keyword) throws IOException;

    Service searchService(final String serviceCode) throws IOException;

    List<Endpoint> searchEndpoint(final String keyword, final String serviceId,
        final int limit) throws IOException;

    List<ServiceInstance> getServiceInstances(final long startTimestamp, final long endTimestamp,
        final String serviceId) throws IOException;
}
