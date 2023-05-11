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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.metadata.Property;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class BanyanDBContinuousProfilingPolicyDAO extends AbstractBanyanDBDAO implements IContinuousProfilingPolicyDAO {
    private static final String GROUP = "sw";

    public BanyanDBContinuousProfilingPolicyDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public void savePolicy(ContinuousProfilingPolicy policy) throws IOException {
        try {
            this.getClient().define(applyAll(policy));
        } catch (IOException e) {
            log.error("fail to save policy", e);
        }
    }

    public Property applyAll(ContinuousProfilingPolicy policy) {
        return Property.create(GROUP, ContinuousProfilingPolicy.INDEX_NAME, policy.id().build())
            .addTag(TagAndValue.newStringTag(ContinuousProfilingPolicy.UUID, policy.getUuid()))
            .addTag(TagAndValue.newStringTag(ContinuousProfilingPolicy.CONFIGURATION_JSON, policy.getConfigurationJson()))
            .build();
    }

    @Override
    public List<ContinuousProfilingPolicy> queryPolicies(List<String> serviceIdList) throws IOException {
        return serviceIdList.stream().map(s -> {
            try {
                return getClient().queryProperty(GROUP, ContinuousProfilingPolicy.INDEX_NAME, s);
            } catch (IOException e) {
                log.warn("query policy error", e);
                return null;
            }
        }).filter(Objects::nonNull).map(properties -> {
            final ContinuousProfilingPolicy policy = new ContinuousProfilingPolicy();
            policy.setServiceId(properties.id());
            for (TagAndValue<?> tag : properties.tags()) {
                if (tag.getTagName().equals(ContinuousProfilingPolicy.CONFIGURATION_JSON)) {
                    policy.setConfigurationJson((String) tag.getValue());
                } else if (tag.getTagName().equals(ContinuousProfilingPolicy.UUID)) {
                    policy.setUuid((String) tag.getValue());
                }
            }
            return policy;
        }).collect(Collectors.toList());
    }

}