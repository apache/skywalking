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

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingMonitorType;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicyConfiguration;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingTargetType;
import org.apache.skywalking.oap.server.core.query.input.ContinuousProfilingPolicyItemCreation;
import org.apache.skywalking.oap.server.core.query.input.ContinuousProfilingPolicyCreation;
import org.apache.skywalking.oap.server.core.query.input.ContinuousProfilingPolicyTargetCreation;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingSetResult;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

@RequiredArgsConstructor
public class ContinuousProfilingMutationService implements Service {
    private static final Gson GSON = new Gson();
    private final ModuleManager moduleManager;

    private IContinuousProfilingPolicyDAO policyDAO;

    public IContinuousProfilingPolicyDAO getPolicyDAO() {
        if (policyDAO == null) {
            this.policyDAO = moduleManager.find(StorageModule.NAME)
                .provider().getService(IContinuousProfilingPolicyDAO.class);
        }
        return policyDAO;
    }

    public ContinuousProfilingSetResult setContinuousProfilingPolicy(ContinuousProfilingPolicyCreation request) throws IOException {
        // validate the service id
        if (StringUtil.isEmpty(request.getServiceId())) {
            return buildError("the service id cannot be empty");
        }

        // validate the targets
        if (CollectionUtils.isNotEmpty(request.getTargets())) {
            String validateTarget = validateTargets(request.getTargets());
            if (StringUtil.isNotEmpty(validateTarget)) {
                return buildError(validateTarget);
            }
        }

        // build and save the management data
        final ContinuousProfilingPolicyConfiguration configuration = ContinuousProfilingPolicyConfiguration.buildFromRequest(request);
        final String configurationJSON = GSON.toJson(configuration);
        final ContinuousProfilingPolicy policy = new ContinuousProfilingPolicy();
        policy.setServiceId(request.getServiceId());
        policy.setUuid(Hashing.sha512().hashString(configurationJSON, StandardCharsets.UTF_8).toString());
        policy.setConfigurationJson(configurationJSON);
        getPolicyDAO().savePolicy(policy);

        return buildSaveSuccess();
    }

    private String validateTargets(List<ContinuousProfilingPolicyTargetCreation> targets) {
        final HashSet<ContinuousProfilingTargetType> targetCache = new HashSet<>();
        for (ContinuousProfilingPolicyTargetCreation target : targets) {
            // same target type cannot have multiple
            final ContinuousProfilingTargetType targetType = target.getTargetType();
            if (targetCache.contains(targetType)) {
                return "contains multiple same target type: " + targetType;
            }
            targetCache.add(targetType);

            final HashSet<ContinuousProfilingMonitorType> monitorTypeCache = new HashSet<>();
            for (ContinuousProfilingPolicyItemCreation item : target.getCheckItems()) {
                // save check type cannot have multiple in each target
                if (monitorTypeCache.contains(item.getType())) {
                    return "contains multiple same monitor type " + item.getType() + " in " + targetType;
                }
                monitorTypeCache.add(item.getType());
                // validate each item
                String itemCheck = validatePolicyItem(item);
                if (StringUtil.isNotEmpty(itemCheck)) {
                    return "check " + item.getType() + " in " + targetType + " error: " + itemCheck;
                }
            }
        }
        return null;
    }

    private String validatePolicyItem(ContinuousProfilingPolicyItemCreation item) {
        String timeWindowsValidate = validatePolicyItemWindows(item);
        if (StringUtil.isNotEmpty(timeWindowsValidate)) {
            return timeWindowsValidate;
        }
        try {
            switch (item.getType()) {
                case PROCESS_CPU:
                    final int cpuPercent = Integer.parseInt(item.getThreshold());
                    if (cpuPercent < 0 || cpuPercent > 100) {
                        return "the process CPU percent should in [0-100]";
                    }
                    break;
                case PROCESS_THREAD_COUNT:
                    final int threadCount = Integer.parseInt(item.getThreshold());
                    if (threadCount < 0) {
                        return "the process thread count must bigger than zero";
                    }
                    break;
                case SYSTEM_LOAD:
                    final int systemLoad = Integer.parseInt(item.getThreshold());
                    if (systemLoad < 0) {
                        return "the system load must bigger than zero";
                    }
                    break;
                case HTTP_ERROR_RATE:
                    final int httpErrorRate = Integer.parseInt(item.getThreshold());
                    if (httpErrorRate < 0 || httpErrorRate > 100) {
                        return "the HTTP error rate should in [0-100]";
                    }
                    break;
                case HTTP_AVG_RESPONSE_TIME:
                    final int httpAvgResponseTime = Integer.parseInt(item.getThreshold());
                    if (httpAvgResponseTime < 0) {
                        return "the HTTP average response time must bigger than zero";
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            return "parsing threshold error";
        }
        return null;
    }

    private String validatePolicyItemWindows(ContinuousProfilingPolicyItemCreation item) {
        if (item.getPeriod() <= 0) {
            return "period must bigger than zero";
        }
        if (item.getCount() < 0) {
            return "count must bigger than zero";
        }
        if (item.getCount() > item.getPeriod()) {
            return "count must be small than period";
        }
        return null;
    }

    private ContinuousProfilingSetResult buildError(String message) {
        return ContinuousProfilingSetResult.builder().status(false).errorReason(message).build();
    }

    private ContinuousProfilingSetResult buildSaveSuccess() {
        return ContinuousProfilingSetResult.builder().status(true).build();
    }
}