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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.asyncprofile.v3.AsyncProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.asyncprofile.v3.AsyncProfilerData;
import org.apache.skywalking.apm.network.language.asyncprofile.v3.AsyncProfilerMetaData;
import org.apache.skywalking.apm.network.language.asyncprofile.v3.AsyncProfilerTaskGrpc;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.AsyncProfilerTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.analyze.JfrAnalyzer;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.source.JfrProfilingData;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.network.trace.component.command.AsyncProfilerTaskCommand;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AsyncProfilerServiceHandler extends AsyncProfilerTaskGrpc.AsyncProfilerTaskImplBase implements GRPCHandler {

    private final IAsyncProfilerTaskQueryDAO taskDAO;
    private final SourceReceiver sourceReceiver;
    private final CommandService commandService;
    private final AsyncProfilerTaskCache taskCache;
    private final JfrAnalyzer jfrAnalyzer;

    public AsyncProfilerServiceHandler(ModuleManager moduleManager) {
        this.taskDAO = moduleManager.find(StorageModule.NAME).provider().getService(IAsyncProfilerTaskQueryDAO.class);
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
        this.taskCache = moduleManager.find(CoreModule.NAME).provider().getService(AsyncProfilerTaskCache.class);
        this.jfrAnalyzer = new JfrAnalyzer(moduleManager);
    }

    @Override
    public StreamObserver<AsyncProfilerData> collect(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<AsyncProfilerData>() {
            private AsyncProfilerMetaData taskMetaData;
            private Path tempFile;
            private volatile OutputStream outputStream;

            @Override
            @SneakyThrows
            public void onNext(AsyncProfilerData asyncProfilerData) {
                if (asyncProfilerData.hasMetaData()) {
                    taskMetaData = asyncProfilerData.getMetaData();
                    tempFile = Files.createTempFile(taskMetaData.getTaskId() + taskMetaData.getServiceInstance() + taskMetaData.getUploadTime()
                            , ".jfr").toAbsolutePath();
                    outputStream = Files.newOutputStream(tempFile);
                } else if (asyncProfilerData.hasContent()) {
                    outputStream.write(asyncProfilerData.getContent().toByteArray());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error("close output stream error", e);
                    throw new RuntimeException(e);
                }
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (log.isDebugEnabled()) {
                        log.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                log.error("Error in receiving async profiler profiling data", throwable);
            }

            @Override
            @SneakyThrows
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
                // save data
                try {
                    outputStream.flush();
                } finally {
                    outputStream.close();
                }
                parseAndStorageData(taskMetaData, tempFile.toAbsolutePath().toString());
                Files.delete(tempFile);
            }
        };
    }

    private void parseAndStorageData(AsyncProfilerMetaData taskMetaData, String fileName) throws IOException {
        String taskId = taskMetaData.getTaskId();
        String serviceId = IDManager.ServiceID.buildId(taskMetaData.getService(), true);
        String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, taskMetaData.getServiceInstance());
        AsyncProfilerTask task = taskDAO.getById(taskId);
        if (task == null) {
            log.error("AsyncProfiler taskId:{} not found but receive data", taskId);
            return;
        }

        recordAsyncProfilerTaskLog(task, serviceInstanceId, ProfileTaskLogOperationType.EXECUTION_FINISHED);

        List<JfrProfilingData> jfrProfilingData = jfrAnalyzer.parseJfr(fileName);
        for (JfrProfilingData data : jfrProfilingData) {
            data.setTaskId(taskId);
            data.setInstanceId(serviceInstanceId);
            data.setUploadTime(taskMetaData.getUploadTime());
            sourceReceiver.receive(data);
        }

    }

    @Override
    public void getAsyncProfileTaskCommands(AsyncProfileTaskCommandQuery request, StreamObserver<Commands> responseObserver) {
        String serviceId = IDManager.ServiceID.buildId(request.getService(), true);
        String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, request.getServiceInstance());

        // fetch tasks from cache
        AsyncProfilerTask task = taskCache.getAsyncProfilerTask(serviceId);
//            List<AsyncProfilerTask> taskList = taskDAO.getTaskList(serviceInstanceId, latestUpdateTime, null, 1);
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
        recordAsyncProfilerTaskLog(task, serviceInstanceId, ProfileTaskLogOperationType.NOTIFIED);
        return;
    }

    private void recordAsyncProfilerTaskLog(AsyncProfilerTask task, String instanceId, ProfileTaskLogOperationType operationType) {
        AsyncProfilerTaskLogRecord logRecord = new AsyncProfilerTaskLogRecord();
        logRecord.setTaskId(task.getId());
        logRecord.setInstanceId(instanceId);
        logRecord.setOperationType(operationType.getCode());
        logRecord.setOperationTime(System.currentTimeMillis());
        long timestamp = task.getCreateTime() + TimeUnit.SECONDS.toMillis(task.getDuration());
        logRecord.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        RecordStreamProcessor.getInstance().in(logRecord);
    }
}
