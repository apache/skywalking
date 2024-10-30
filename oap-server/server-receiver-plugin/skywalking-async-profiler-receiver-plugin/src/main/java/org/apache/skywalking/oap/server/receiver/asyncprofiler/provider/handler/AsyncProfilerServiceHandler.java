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

package org.apache.skywalking.oap.server.receiver.asyncprofiler.provider.handler;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerCollectionResponse;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerData;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerMetaData;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerTaskCommandQuery;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerTaskGrpc;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.AsyncProfilerTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.analyze.JfrAnalyzer;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.network.trace.component.command.AsyncProfilerTaskCommand;
import org.apache.skywalking.oap.server.receiver.asyncprofiler.provider.handler.stream.AsyncProfilerByteBufCollectionObserver;
import org.apache.skywalking.oap.server.receiver.asyncprofiler.provider.handler.stream.AsyncProfilerCollectionMetaData;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AsyncProfilerServiceHandler extends AsyncProfilerTaskGrpc.AsyncProfilerTaskImplBase implements GRPCHandler {

    private final IAsyncProfilerTaskQueryDAO taskDAO;
    private final SourceReceiver sourceReceiver;
    private final CommandService commandService;
    private final AsyncProfilerTaskCache taskCache;
    private final JfrAnalyzer jfrAnalyzer;
    private final int jfrMaxSize;

    public AsyncProfilerServiceHandler(ModuleManager moduleManager, int jfrMaxSize) {
        this.taskDAO = moduleManager.find(StorageModule.NAME).provider().getService(IAsyncProfilerTaskQueryDAO.class);
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
        this.taskCache = moduleManager.find(CoreModule.NAME).provider().getService(AsyncProfilerTaskCache.class);
        this.jfrAnalyzer = new JfrAnalyzer(moduleManager);
        this.jfrMaxSize = jfrMaxSize;
    }

    @Override
    public StreamObserver<AsyncProfilerData> collect(StreamObserver<AsyncProfilerCollectionResponse> responseObserver) {
        return new AsyncProfilerByteBufCollectionObserver(taskDAO, jfrAnalyzer, responseObserver, sourceReceiver, jfrMaxSize);
    }

    @Override
    public void getAsyncProfilerTaskCommands(AsyncProfilerTaskCommandQuery request, StreamObserver<Commands> responseObserver) {
        String serviceId = IDManager.ServiceID.buildId(request.getService(), true);
        String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, request.getServiceInstance());

        // fetch tasks from cache
        AsyncProfilerTask task = taskCache.getAsyncProfilerTask(serviceId);
        if (Objects.isNull(task) || task.getCreateTime() <= request.getLastCommandTime() ||
                (!CollectionUtils.isEmpty(task.getServiceInstanceIds()) && !task.getServiceInstanceIds().contains(serviceInstanceId))) {
            responseObserver.onNext(Commands.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        AsyncProfilerTaskCommand asyncProfilerTaskCommand = commandService.newAsyncProfileTaskCommand(task);
        Commands commands = Commands.newBuilder().addCommands(asyncProfilerTaskCommand.serialize()).build();
        responseObserver.onNext(commands);
        responseObserver.onCompleted();
        recordAsyncProfilerTaskLog(task, serviceInstanceId, AsyncProfilerTaskLogOperationType.NOTIFIED);
        return;
    }

    public static void recordAsyncProfilerTaskLog(AsyncProfilerTask task, String instanceId, AsyncProfilerTaskLogOperationType operationType) {
        AsyncProfilerTaskLogRecord logRecord = new AsyncProfilerTaskLogRecord();
        logRecord.setTaskId(task.getId());
        logRecord.setInstanceId(instanceId);
        logRecord.setOperationType(operationType.getCode());
        logRecord.setOperationTime(System.currentTimeMillis());
        long timestamp = task.getCreateTime() + TimeUnit.SECONDS.toMillis(task.getDuration());
        logRecord.setTimestamp(timestamp);
        logRecord.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        RecordStreamProcessor.getInstance().in(logRecord);
    }

    public static AsyncProfilerCollectionMetaData parseMetaData(AsyncProfilerMetaData metaData, IAsyncProfilerTaskQueryDAO taskDAO) throws IOException {
        String taskId = metaData.getTaskId();
        AsyncProfilerTask task = taskDAO.getById(taskId);
        String serviceId = IDManager.ServiceID.buildId(metaData.getService(), true);
        String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, metaData.getServiceInstance());
        return AsyncProfilerCollectionMetaData.builder()
                .task(task)
                .serviceId(serviceId)
                .instanceId(serviceInstanceId)
                .type(metaData.getType())
                .contentSize(metaData.getContentSize())
                .build();
    }
}
