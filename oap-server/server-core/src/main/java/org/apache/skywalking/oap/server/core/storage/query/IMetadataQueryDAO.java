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

import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.DAO;

public interface IMetadataQueryDAO extends DAO {

    /**
     * List all existing services.
     */
    List<Service> listServices() throws IOException;

    /**
     * @param duration   The instance is required to be live in this duration, could be null.
     * @param serviceId      the owner of the instances.
     * @return list of instances matching the given conditions.
     */
    List<ServiceInstance> listInstances(@Nullable final Duration duration,
                                        final String serviceId) throws IOException;

    ServiceInstance getInstance(final String instanceId) throws IOException;

    /**
     * @param instanceIds instance id list
     */
    List<ServiceInstance> getInstances(final List<String> instanceIds) throws IOException;

    /**
     * @param keyword   to filter the endpoints
     * @param serviceId the owner of the endpoints
     * @param limit     max match size.
     * @return list of endpoint matching the given conditions.
     */
    List<Endpoint> findEndpoint(final String keyword, final String serviceId, final int limit) throws IOException;

    /**
     * @param serviceId the service id of the process.
     * @param supportStatus the profiling status of the process.
     * @param lastPingStartTimeBucket the start time bucket of last ping.
     * @param lastPingEndTimeBucket the end time bucket of last ping.
     */
    List<Process> listProcesses(final String serviceId, final ProfilingSupportStatus supportStatus,
                                final long lastPingStartTimeBucket, final long lastPingEndTimeBucket) throws IOException;

    /**
     * @param serviceInstanceId the instance id of the process.
     * @param duration the start and end time bucket of last ping.
     */
    List<Process> listProcesses(final String serviceInstanceId, final Duration duration, boolean includeVirtual) throws IOException;

    /**
     * @param agentId the agent id of the process.
     */
    List<Process> listProcesses(final String agentId) throws IOException;

    /**
     * @param serviceId the service id of the process
     * @param profilingSupportStatus the profiling status of the process.
     * @param lastPingStartTimeBucket the start time bucket of last ping.
     * @param lastPingEndTimeBucket the end time bucket of last ping.
     */
    long getProcessCount(final String serviceId,
                         final ProfilingSupportStatus profilingSupportStatus, final long lastPingStartTimeBucket,
                         final long lastPingEndTimeBucket) throws IOException;

    /**
     * @param instanceId the service instance id of the process
     */
    long getProcessCount(final String instanceId) throws IOException;

    /**
     * @param processId the id of the process.
     */
    Process getProcess(final String processId) throws IOException;
}
