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

package org.apache.skywalking.oap.server.core.profiling.continuous.storage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.CONTINUOUS_PROFILING_POLICY;

@Setter
@Getter
@ScopeDeclaration(id = CONTINUOUS_PROFILING_POLICY, name = "ContinuousProfilingPolicy")
@Stream(name = ContinuousProfilingPolicy.INDEX_NAME, scopeId = CONTINUOUS_PROFILING_POLICY,
    builder = ContinuousProfilingPolicy.Builder.class, processor = ManagementStreamProcessor.class)
@EqualsAndHashCode(of = {
    "serviceId"
}, callSuper = false)
public class ContinuousProfilingPolicy extends ManagementData {
    public static final String INDEX_NAME = "continuous_profiling_policy";
    public static final String SERVICE_ID = "service_id";
    public static final String UUID = "uuid";
    public static final String CONFIGURATION_JSON = "configuration_json";

    @Column(name = SERVICE_ID)
    private String serviceId;
    @Column(name = UUID)
    private String uuid;
    @Column(name = CONFIGURATION_JSON, storageOnly = true, length = 5000)
    private String configurationJson;

    @Override
    public StorageID id() {
        return new StorageID().append(SERVICE_ID, serviceId);
    }

    public static class Builder implements StorageBuilder<ContinuousProfilingPolicy> {

        @Override
        public ContinuousProfilingPolicy storage2Entity(Convert2Entity converter) {
            final ContinuousProfilingPolicy policy = new ContinuousProfilingPolicy();
            policy.setServiceId((String) converter.get(SERVICE_ID));
            policy.setUuid((String) converter.get(UUID));
            policy.setConfigurationJson((String) converter.get(CONFIGURATION_JSON));
            return policy;
        }

        @Override
        public void entity2Storage(ContinuousProfilingPolicy entity, Convert2Storage converter) {
            converter.accept(SERVICE_ID, entity.getServiceId());
            converter.accept(UUID, entity.getUuid());
            converter.accept(CONFIGURATION_JSON, entity.getConfigurationJson());
        }
    }
}
