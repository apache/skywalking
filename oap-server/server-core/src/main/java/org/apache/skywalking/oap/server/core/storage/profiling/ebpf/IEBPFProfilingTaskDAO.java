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

package org.apache.skywalking.oap.server.core.storage.profiling.ebpf;

import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.storage.DAO;

import java.io.IOException;
import java.util.List;

/**
 * EBPF Profiling task query
 */
public interface IEBPFProfilingTaskDAO extends DAO {

    /**
     * Query profiling task through service id list
     * @param serviceIdList cannot be empty
     */
    List<EBPFProfilingTaskRecord> queryTasksByServices(List<String> serviceIdList, EBPFProfilingTriggerType triggerType,
                                                 long taskStartTime, long latestUpdateTime) throws IOException;

    /**
     * Query profiling task through target types
     * @param targetTypes cannot be empty
     */
    List<EBPFProfilingTaskRecord> queryTasksByTargets(String serviceId, String serviceInstanceId,
                                                List<EBPFProfilingTargetType> targetTypes,
                                                EBPFProfilingTriggerType triggerType,
                                                long taskStartTime, long latestUpdateTime) throws IOException;

    /**
     * Query profiling task by logical ID
     * @param id {@link EBPFProfilingTaskRecord#getLogicalId()}
     */
    List<EBPFProfilingTaskRecord> queryByLogicalId(String id) throws IOException;
}