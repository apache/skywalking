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
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFOnCPUProfiling;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingServiceGrpc;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackMetadata;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingTaskMetadata;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingTaskQuery;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingStackType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.source.EBPFProfilingData;
import org.apache.skywalking.oap.server.core.source.EBPFProcessProfilingSchedule;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.EBPFProfilingProcessFinder;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handle the eBPF Profiling data request from the eBPF Agent side.
 */
@Slf4j
public class EBPFProfilingServiceHandler extends EBPFProfilingServiceGrpc.EBPFProfilingServiceImplBase implements GRPCHandler {
    public static final List<EBPFProfilingStackType> COMMON_STACK_TYPE_ORDER = Arrays.asList(
            EBPFProfilingStackType.KERNEL_SPACE, EBPFProfilingStackType.USER_SPACE);

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
            // find exists process from agent
            final List<Process> processes = metadataQueryDAO.listProcesses(null, null, agentId);
            if (CollectionUtils.isEmpty(processes)) {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }

            // fetch tasks from process id list
            final EBPFProfilingProcessFinder finder = EBPFProfilingProcessFinder.builder()
                    .processIdList(processes.stream().map(Process::getId).collect(Collectors.toList())).build();
            final List<EBPFProfilingTask> tasks = taskDAO.queryTasks(finder, null, 0, latestUpdateTime);
            final Commands.Builder builder = Commands.newBuilder();
            tasks.stream().map(t -> commandService.newEBPFProfilingTaskCommand(t).serialize()).forEach(builder::addCommands);
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
            return;
        } catch (Exception e) {
            log.warn("query ebpf process profiling task failure", e);
        }
        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
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
                    default:
                        throw new IllegalArgumentException("the profiling data not set");
                }

                sourceReceiver.receive(data);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error in receiving ebpf profiling data", throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }

    private void processOnCPUProfiling(EBPFProfilingData data, EBPFOnCPUProfiling onCPU) throws IOException {
        orderMetadataAndSetToData(data, onCPU.getStacksList(), COMMON_STACK_TYPE_ORDER);
        data.setDumpCount(onCPU.getDumpCount());
    }

    private void orderMetadataAndSetToData(EBPFProfilingData data, List<EBPFProfilingStackMetadata> original,
                                           List<EBPFProfilingStackType> order) throws IOException {
        final HashMap<EBPFProfilingStackType, EBPFProfilingStackMetadata> tmp = new HashMap<>();
        original.forEach(e -> tmp.put(EBPFProfilingStackType.valueOf(e.getStackType()), e));

        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final List<Integer> stackIdList = new ArrayList<>();
        for (EBPFProfilingStackType orderStack : order) {
            final EBPFProfilingStackMetadata stack = tmp.get(orderStack);
            if (stack != null) {
                stack.writeDelimitedTo(result);
                stackIdList.add(stack.getStackId());
            }
        }
        data.setStacksBinary(result.toByteArray());
        data.setStackIdList(Joiner.on("_").join(stackIdList));
    }
}
