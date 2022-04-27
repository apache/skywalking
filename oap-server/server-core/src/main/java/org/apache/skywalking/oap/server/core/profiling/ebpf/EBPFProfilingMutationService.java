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

package org.apache.skywalking.oap.server.core.profiling.ebpf;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingTaskFixedTimeCreationRequest;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskCreationResult;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class EBPFProfilingMutationService implements Service {
    private static final Gson GSON = new Gson();
    public static final int FIXED_TIME_MIN_DURATION = (int) TimeUnit.SECONDS.toSeconds(60);

    private final ModuleManager moduleManager;
    private IEBPFProfilingTaskDAO processProfilingTaskDAO;
    private IServiceLabelDAO serviceLabelDAO;

    private IEBPFProfilingTaskDAO getProcessProfilingTaskDAO() {
        if (processProfilingTaskDAO == null) {
            this.processProfilingTaskDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IEBPFProfilingTaskDAO.class);
        }
        return processProfilingTaskDAO;
    }

    public IServiceLabelDAO getServiceLabelDAO() {
        if (serviceLabelDAO == null) {
            this.serviceLabelDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IServiceLabelDAO.class);
        }
        return serviceLabelDAO;
    }

    /**
     * Create eBPF Profiling task with {@link EBPFProfilingTriggerType#FIXED_TIME}
     */
    public EBPFProfilingTaskCreationResult createTask(EBPFProfilingTaskFixedTimeCreationRequest request) throws IOException {
        final long current = System.currentTimeMillis();
        if (request.getStartTime() <= 0) {
            request.setStartTime(current);
        }

        // check request
        final String error = checkCreateRequest(request);
        if (StringUtil.isNotEmpty(error)) {
            return buildError(error);
        }

        // create task
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(request.getServiceId());
        if (CollectionUtils.isNotEmpty(request.getProcessLabels())) {
            task.setProcessLabelsJson(GSON.toJson(request.getProcessLabels()));
        } else {
            task.setProcessLabelsJson(Const.EMPTY_STRING);
        }
        task.setStartTime(request.getStartTime());
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(request.getDuration());
        task.setTargetType(request.getTargetType().value());
        task.setCreateTime(current);
        task.setLastUpdateTime(current);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(current));
        NoneStreamProcessor.getInstance().in(task);

        return EBPFProfilingTaskCreationResult.builder().status(true).id(task.id()).build();
    }

    private EBPFProfilingTaskCreationResult buildError(String msg) {
        return EBPFProfilingTaskCreationResult.builder().status(false).errorReason(msg).build();
    }

    private String checkCreateRequest(EBPFProfilingTaskFixedTimeCreationRequest request) throws IOException {
        String err = null;

        err = requiredNotEmpty(err, "service", request.getServiceId());

        // the request label must be legal
        if (err == null && CollectionUtils.isNotEmpty(request.getProcessLabels())) {
            final List<String> existingLabels = getServiceLabelDAO().queryAllLabels(request.getServiceId());
            List<String> notExistLabels = new ArrayList<>(existingLabels.size());
            for (String processLabel : request.getProcessLabels()) {
                if (!existingLabels.contains(processLabel)) {
                    notExistLabels.add(processLabel);
                }
            }
            if (notExistLabels.size() > 0) {
                err = String.format("The service doesn't have processes with label(s) %s.", Joiner.on(", ").join(notExistLabels));
            } else {
                final String labelJson = GSON.toJson(request.getProcessLabels());
                if (labelJson.length() > EBPFProfilingTaskRecord.PROCESS_LABELS_JSON_MAX_LENGTH) {
                    err = String.format("The labels length is bigger than %d, please reduce the labels count",
                            EBPFProfilingTaskRecord.PROCESS_LABELS_JSON_MAX_LENGTH);
                }
            }
        }
        if (err != null) {
            return err;
        }

        // validate target type
        err = validateTargetType(request);
        if (err != null) {
            return err;
        }

        err = validateTriggerType(request);
        if (err != null) {
            return err;
        }

        // query exist processes
        final List<EBPFProfilingTask> tasks = getProcessProfilingTaskDAO().queryTasks(
                Arrays.asList(request.getServiceId()), request.getTargetType(), request.getStartTime(), 0);
        if (CollectionUtils.isNotEmpty(tasks)) {
            final EBPFProfilingTask mostRecentTask = tasks.stream()
                    .min(Comparator.comparingLong(EBPFProfilingTask::getTaskStartTime)).get();
            if (mostRecentTask.getTaskStartTime() < calculateStartTime(request)) {
                return "Task's time range overlaps with other tasks";
            }
        }
        return null;
    }

    private long calculateStartTime(EBPFProfilingTaskFixedTimeCreationRequest request) {
        return request.getStartTime() - TimeUnit.SECONDS.toMillis(request.getDuration());
    }

    private String validateTriggerType(EBPFProfilingTaskFixedTimeCreationRequest request) {
        if (request.getDuration() < FIXED_TIME_MIN_DURATION) {
            return "the fixed time duration must be greater than or equals " + FIXED_TIME_MIN_DURATION + "s";
        }
        return null;
    }

    private String requiredNotEmpty(String error, String type, String data) {
        if (StringUtil.isNotEmpty(error)) {
            return error;
        }
        return StringUtil.isNotEmpty(data) ? null : String.format("%s could not be empty", type);
    }

    private String validateTargetType(EBPFProfilingTaskFixedTimeCreationRequest request) {
        if (request.getTargetType() == null) {
            return "the profiling target could not be null";
        }
        return null;
    }
}