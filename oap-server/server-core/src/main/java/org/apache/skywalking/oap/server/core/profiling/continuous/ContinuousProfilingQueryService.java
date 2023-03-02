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

package org.apache.skywalking.oap.server.core.profiling.continuous;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicyConfiguration;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingTargetType;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingPolicyItem;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingPolicyTarget;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ContinuousProfilingQueryService implements Service {
    private final ModuleManager moduleManager;

    private IContinuousProfilingPolicyDAO policyDAO;

    public IContinuousProfilingPolicyDAO getPolicyDAO() {
        if (policyDAO == null) {
            this.policyDAO = moduleManager.find(StorageModule.NAME)
                .provider().getService(IContinuousProfilingPolicyDAO.class);
        }
        return policyDAO;
    }

    public List<ContinuousProfilingPolicyTarget> queryContinuousProfilingServiceTargets(String serviceId) throws IOException {
        final List<ContinuousProfilingPolicy> policies = getPolicyDAO().queryPolicies(Arrays.asList(serviceId));
        if (CollectionUtils.isEmpty(policies)) {
            return Collections.emptyList();
        }

        final ContinuousProfilingPolicy policy = policies.get(0);
        final ContinuousProfilingPolicyConfiguration configuration =
            ContinuousProfilingPolicyConfiguration.parseFromJSON(policy.getConfigurationJson());

        return configuration.getTargetCheckers().entrySet().stream().map(targetEntry -> {
            final ContinuousProfilingTargetType type = targetEntry.getKey();
            final List<ContinuousProfilingPolicyItem> items = targetEntry.getValue().entrySet().stream().map(checker -> {
                final ContinuousProfilingPolicyItem result = new ContinuousProfilingPolicyItem();
                final ContinuousProfilingPolicyConfiguration.CheckItem item = checker.getValue();
                result.setType(checker.getKey());
                result.setThreshold(item.getThreshold());
                result.setPeriod(item.getPeriod());
                result.setCount(item.getCount());
                result.setUriList(item.getUriList());
                result.setUriRegex(item.getUriRegex());
                return result;
            }).collect(Collectors.toList());

            return ContinuousProfilingPolicyTarget.builder()
                .type(type)
                .checkItems(items)
                .build();
        }).collect(Collectors.toList());
    }
}
