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

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingProcessFinderType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingTaskFixedTimeCreationRequest;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.EBPFProfilingProcessFinder;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class EBPFProfilingMutationService implements Service {
    public static final int FIXED_TIME_MIN_DURATION = (int) TimeUnit.SECONDS.toSeconds(60);

    private final ModuleManager moduleManager;
    private IEBPFProfilingTaskDAO processProfilingTaskDAO;
    private IMetadataQueryDAO metadataQueryDAO;

    private IEBPFProfilingTaskDAO getProcessProfilingTaskDAO() {
        if (processProfilingTaskDAO == null) {
            this.processProfilingTaskDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IEBPFProfilingTaskDAO.class);
        }
        return processProfilingTaskDAO;
    }

    private IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            this.metadataQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
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
        task.setProcessFindType(request.getProcessFinder().getFinderType().value());
        if (request.getProcessFinder().getFinderType() == EBPFProfilingProcessFinderType.PROCESS_ID) {
            final Process process = getMetadataQueryDAO().getProcess(request.getProcessFinder().getProcessId());
            if (process == null) {
                return buildError("could not found process");
            }
            task.setServiceId(process.getServiceId());
            task.setInstanceId(process.getInstanceId());
            task.setProcessId(process.getId());
            task.setProcessName(process.getName());
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
        String err = "";

        // validate process finder
        if (request.getProcessFinder() == null) {
            return "The process finder could not be null";
        }
        if (request.getProcessFinder().getFinderType() == null) {
            return "The process find type could not be null";
        }
        switch (request.getProcessFinder().getFinderType()) {
            case PROCESS_ID:
                err = requiredNotEmpty(err, "process id", request.getProcessFinder().getProcessId());
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
                EBPFProfilingProcessFinder.builder()
                        .finderType(request.getProcessFinder().getFinderType())
                        .processIdList(Arrays.asList(request.getProcessFinder().getProcessId()))
                        .build(), request.getTargetType(), calculateStartTime(request), 0);
        if (CollectionUtils.isNotEmpty(tasks)) {
            return "already have profiling task at this time";
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