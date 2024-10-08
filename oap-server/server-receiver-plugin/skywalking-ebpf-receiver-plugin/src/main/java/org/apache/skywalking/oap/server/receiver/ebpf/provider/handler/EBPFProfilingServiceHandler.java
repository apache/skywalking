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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFOffCPUProfiling;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFOnCPUProfiling;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingServiceGrpc;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackMetadata;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingTaskMetadata;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingTaskQuery;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingStackType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.source.EBPFProfilingData;
import org.apache.skywalking.oap.server.core.source.EBPFProcessProfilingSchedule;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.network.trace.component.command.EBPFProfilingTaskCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handle the eBPF Profiling data request from the eBPF Agent side.
 */
@Slf4j
public class EBPFProfilingServiceHandler extends EBPFProfilingServiceGrpc.EBPFProfilingServiceImplBase implements GRPCHandler {
    private static final Gson GSON = new Gson();
    public static final List<EBPFProfilingStackType> COMMON_STACK_TYPE_ORDER = Arrays.asList(
            EBPFProfilingStackType.KERNEL_SPACE, EBPFProfilingStackType.USER_SPACE);
    /**
     * When querying profiling tasks, processes from the last few minutes would be queried.
     */
    public static final int QUERY_TASK_PROCESSES_RANGE_MINUTES = 5;

    private IEBPFProfilingTaskDAO taskDAO;
    private IMetadataQueryDAO metadataQueryDAO;
    private final SourceReceiver sourceReceiver;
    private final CommandService commandService;

    public EBPFProfilingServiceHandler(ModuleManager moduleManager) {
        this.metadataQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetadataQueryDAO.class);
        this.taskDAO = moduleManager.find(StorageModule.NAME).provider().getService(IEBPFProfilingTaskDAO.class);
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
    }

    @Override
    public void queryTasks(EBPFProfilingTaskQuery request, StreamObserver<Commands> responseObserver) {
        String agentId = request.getRoverInstanceId();
        final long latestUpdateTime = request.getLatestUpdateTime();
        try {
            final Calendar now = Calendar.getInstance();
            long endTimeBucket = TimeBucket.getTimeBucket(now.getTimeInMillis(), DownSampling.Minute);
            now.add(Calendar.MINUTE, -QUERY_TASK_PROCESSES_RANGE_MINUTES);
            long startTimeBucket = TimeBucket.getTimeBucket(now.getTimeInMillis(), DownSampling.Minute);
            // find exists process from agent
            final List<Process> processes = metadataQueryDAO.listProcesses(agentId, startTimeBucket, endTimeBucket);
            if (CollectionUtils.isEmpty(processes)) {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }

            // fetch tasks from process id list
            final List<String> serviceIdList = processes.stream().map(Process::getServiceId).distinct().collect(Collectors.toList());
            final List<EBPFProfilingTaskRecord> tasks = taskDAO.queryTasksByServices(serviceIdList, EBPFProfilingTriggerType.FIXED_TIME, 0, latestUpdateTime);

            final Commands.Builder builder = Commands.newBuilder();
            tasks.stream().collect(Collectors.toMap(EBPFProfilingTaskRecord::getLogicalId, Function.identity(), EBPFProfilingTaskRecord::combine))
                .values().stream().flatMap(t -> this.buildProfilingCommands(t, processes).stream())
                .map(EBPFProfilingTaskCommand::serialize).forEach(builder::addCommands);
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
            return;
        } catch (Exception e) {
            log.warn("query ebpf process profiling task failure", e);
        }
        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

    private List<EBPFProfilingTaskCommand> buildProfilingCommands(EBPFProfilingTaskRecord task, List<Process> processes) {
        if (EBPFProfilingTargetType.NETWORK.value() == task.getTargetType()) {
            final List<String> processIdList = processes.stream().filter(p -> Objects.equals(p.getInstanceId(), task.getInstanceId())).map(Process::getId).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(processIdList)) {
                return Collections.emptyList();
            }
            return Collections.singletonList(commandService.newEBPFProfilingTaskCommand(task, processIdList));
        }
        final ArrayList<EBPFProfilingTaskCommand> commands = new ArrayList<>(processes.size());
        for (Process process : processes) {
            // The service id must match between process and task and must could profiling
            if (!Objects.equals(process.getServiceId(), task.getServiceId())
                || !ProfilingSupportStatus.SUPPORT_EBPF_PROFILING.name().equals(process.getProfilingSupportStatus())) {
                continue;
            }

            // If the task doesn't require a label or the process match all labels in task
            List<String> processLabels = Collections.emptyList();
            if (StringUtil.isNotEmpty(task.getProcessLabelsJson())) {
                processLabels = GSON.<List<String>>fromJson(task.getProcessLabelsJson(), ArrayList.class);
            }
            if (CollectionUtils.isEmpty(processLabels)
                    || process.getLabels().containsAll(processLabels)) {
                commands.add(commandService.newEBPFProfilingTaskCommand(task, Collections.singletonList(process.getId())));
            }
        }
        return commands;
    }

    @Override
    public StreamObserver<org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingData> collectProfilingData(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingData>() {
            private volatile boolean isFirst = true;
            private EBPFProfilingTaskMetadata task;
            private String scheduleId;

            @Override
            public void onNext(org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingData ebpfProfilingData) {
                if (isFirst || ebpfProfilingData.hasTask()) {
                    task = ebpfProfilingData.getTask();

                    // update schedule metadata
                    final EBPFProcessProfilingSchedule schedule = new EBPFProcessProfilingSchedule();
                    schedule.setProcessId(task.getProcessId());
                    schedule.setTaskId(task.getTaskId());
                    schedule.setStartTime(task.getProfilingStartTime());
                    schedule.setCurrentTime(task.getCurrentTime());
                    sourceReceiver.receive(schedule);

                    scheduleId = schedule.getEntityId();
                }
                isFirst = false;

                // add profiling data
                final EBPFProfilingData data = new EBPFProfilingData();
                data.setScheduleId(scheduleId);
                data.setTaskId(task.getTaskId());
                data.setUploadTime(task.getCurrentTime());
                switch (ebpfProfilingData.getProfilingCase()) {
                    case ONCPU:
                        try {
                            processOnCPUProfiling(data, ebpfProfilingData.getOnCPU());
                        } catch (IOException e) {
                            log.warn("process ON_CPU profiling data failure", e);
                        }
                        break;
                    case OFFCPU:
                        try {
                            processOffCPUProfiling(data, ebpfProfilingData.getOffCPU());
                        } catch (IOException e) {
                            log.warn("process OFF_CPU profiling data failure", e);
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("the profiling data not set");
                }

                sourceReceiver.receive(data);
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (log.isDebugEnabled()) {
                        log.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                log.error("Error in receiving ebpf profiling data", throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }

    private void processOnCPUProfiling(EBPFProfilingData data, EBPFOnCPUProfiling onCPU) throws IOException {
        Tuple2<String, List<EBPFProfilingStackMetadata>> order = orderMetadataAndSetToData(onCPU.getStacksList(), COMMON_STACK_TYPE_ORDER);
        data.setStackIdList(order._1);
        data.setTargetType(EBPFProfilingTargetType.ON_CPU);
        data.setDataBinary(EBPFOnCPUProfiling.newBuilder()
                .addAllStacks(order._2)
                .setDumpCount(onCPU.getDumpCount())
                .build().toByteArray());
    }

    private void processOffCPUProfiling(EBPFProfilingData data, EBPFOffCPUProfiling offCPUProfiling) throws IOException {
        Tuple2<String, List<EBPFProfilingStackMetadata>> order = orderMetadataAndSetToData(offCPUProfiling.getStacksList(), COMMON_STACK_TYPE_ORDER);
        data.setStackIdList(order._1);
        data.setTargetType(EBPFProfilingTargetType.OFF_CPU);
        data.setDataBinary(EBPFOffCPUProfiling.newBuilder()
                .addAllStacks(order._2)
                .setSwitchCount(offCPUProfiling.getSwitchCount())
                .setDuration(offCPUProfiling.getDuration())
                .build().toByteArray());
    }

    private Tuple2<String, List<EBPFProfilingStackMetadata>> orderMetadataAndSetToData(List<EBPFProfilingStackMetadata> original,
                                           List<EBPFProfilingStackType> order) {
        final HashMap<EBPFProfilingStackType, EBPFProfilingStackMetadata> tmp = new HashMap<>();
        original.forEach(e -> tmp.put(EBPFProfilingStackType.valueOf(e.getStackType()), e));

        final List<Integer> stackIdList = new ArrayList<>();
        final ArrayList<EBPFProfilingStackMetadata> result = new ArrayList<>();
        for (EBPFProfilingStackType orderStack : order) {
            final EBPFProfilingStackMetadata stack = tmp.get(orderStack);
            if (stack != null) {
                result.add(stack);
                stackIdList.add(stack.getStackId());
            }
        }
        return Tuple.of(Joiner.on("_").join(stackIdList), result);
    }
}
