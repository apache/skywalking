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

package org.apache.skywalking.oap.server.receiver.pprof.provider.handler;

import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.pprof.v10.PprofCollectionResponse;
import org.apache.skywalking.apm.network.pprof.v10.PprofData;
import org.apache.skywalking.apm.network.pprof.v10.PprofMetaData;
import org.apache.skywalking.apm.network.pprof.v10.PprofTaskCommandQuery;
import org.apache.skywalking.apm.network.pprof.v10.PprofTaskGrpc;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.PprofTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.network.trace.component.command.PprofTaskCommand;
import org.apache.skywalking.oap.server.receiver.pprof.provider.handler.stream.PprofByteBufCollectionObserver;
import org.apache.skywalking.oap.server.receiver.pprof.provider.handler.stream.PprofCollectionMetaData;
import org.apache.skywalking.oap.server.receiver.pprof.provider.handler.stream.PprofFileCollectionObserver;

@Slf4j
public class PprofServiceHandler extends PprofTaskGrpc.PprofTaskImplBase implements GRPCHandler {

    private final IPprofTaskQueryDAO taskDAO;
    private final SourceReceiver sourceReceiver;
    private final CommandService commandService;
    private final PprofTaskCache taskCache;
    private final int pprofMaxSize;
    private final boolean memoryParserEnabled;

    public PprofServiceHandler(ModuleManager moduleManager, int pprofMaxSize, boolean memoryParserEnabled) {
        this.taskDAO = moduleManager.find(StorageModule.NAME).provider().getService(IPprofTaskQueryDAO.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.taskCache = moduleManager.find(CoreModule.NAME).provider().getService(PprofTaskCache.class);
        this.pprofMaxSize = pprofMaxSize;
        this.memoryParserEnabled = memoryParserEnabled;
    }

    @Override
    public StreamObserver<PprofData> collect(StreamObserver<PprofCollectionResponse> responseObserver) {
        return memoryParserEnabled ? new PprofByteBufCollectionObserver(
            taskDAO, responseObserver, sourceReceiver, pprofMaxSize) : new PprofFileCollectionObserver(
            taskDAO, responseObserver, sourceReceiver, pprofMaxSize);
    }

    @Override
    public void getPprofTaskCommands(PprofTaskCommandQuery request, StreamObserver<Commands> responseObserver) {
        String serviceId = IDManager.ServiceID.buildId(request.getService(), true);
        String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, request.getServiceInstance());
        List<PprofTask> taskList = taskCache.getPprofTaskList(serviceId);
        // if task is null or createTime is less than lastCommandTime, return empty commands
        if (CollectionUtils.isEmpty(taskList)) {
            responseObserver.onNext(Commands.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        final long lastCommandTime = request.getLastCommandTime();
        long minCreateTime = Long.MAX_VALUE;
        PprofTask taskResult = null;
        for (PprofTask task : taskList) {
            if (task.getCreateTime() > lastCommandTime) {
                if (task.getCreateTime() < minCreateTime) {
                    minCreateTime = task.getCreateTime();
                    taskResult = task;
                }
            }
        }

        PprofTaskCommand pprofTaskCommand = commandService.newPprofTaskCommand(taskResult);
        Commands commands = Commands.newBuilder().addCommands(pprofTaskCommand.serialize()).build();
        responseObserver.onNext(commands);
        responseObserver.onCompleted();
        recordPprofTaskLog(taskResult, serviceInstanceId, PprofTaskLogOperationType.NOTIFIED);
    }

    public static void recordPprofTaskLog(PprofTask task, String instanceId, PprofTaskLogOperationType operationType) {
        PprofTaskLogRecord logRecord = new PprofTaskLogRecord();
        logRecord.setTaskId(task.getId());
        logRecord.setInstanceId(instanceId);
        logRecord.setOperationType(operationType.getCode());
        logRecord.setOperationTime(System.currentTimeMillis());
        long timestamp = task.getCreateTime() + TimeUnit.SECONDS.toMillis(task.getDuration());
        logRecord.setTimestamp(timestamp);
        logRecord.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        RecordStreamProcessor.getInstance().in(logRecord);
    }

    public static PprofCollectionMetaData parseMetaData(PprofMetaData metaData,
                                                        IPprofTaskQueryDAO taskDAO) throws IOException {
        String taskId = metaData.getTaskId();
        PprofTask task = taskDAO.getById(taskId);
        String serviceId = IDManager.ServiceID.buildId(metaData.getService(), true);
        String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, metaData.getServiceInstance());

        return PprofCollectionMetaData.builder()
                                      .task(task)
                                      .serviceId(serviceId)
                                      .instanceId(serviceInstanceId)
                                      .type(metaData.getType())
                                      .contentSize(metaData.getContentSize())
                                      .uploadTime(System.currentTimeMillis())
                                      .build();
    }
}