/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.receiver.asyncprofiler.provider.handler.stream;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerCollectType;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerData;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.analyze.JfrAnalyzer;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.source.JFRProfilingData;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.apache.skywalking.oap.server.receiver.asyncprofiler.provider.handler.AsyncProfilerServiceHandler.parseMetaData;
import static org.apache.skywalking.oap.server.receiver.asyncprofiler.provider.handler.AsyncProfilerServiceHandler.recordAsyncProfilerTaskLog;

@Slf4j
public class AsyncProfilerFileCollectObserver implements StreamObserver<AsyncProfilerData> {
    private final IAsyncProfilerTaskQueryDAO taskDAO;
    private final SourceReceiver sourceReceiver;
    private final JfrAnalyzer jfrAnalyzer;
    private final int jfrMaxSize;
    private final StreamObserver<Commands> responseObserver;

    private AsyncProfilerCollectMetaData taskMetaData;
    private Path tempFile;
    private FileOutputStream fileOutputStream;

    public AsyncProfilerFileCollectObserver(IAsyncProfilerTaskQueryDAO taskDAO, JfrAnalyzer jfrAnalyzer,
                                            StreamObserver<Commands> responseObserver, SourceReceiver sourceReceiver,
                                            int jfrMaxSize) {
        this.sourceReceiver = sourceReceiver;
        this.taskDAO = taskDAO;
        this.jfrAnalyzer = jfrAnalyzer;
        this.responseObserver = responseObserver;
        this.jfrMaxSize = jfrMaxSize;
    }

    @Override
    @SneakyThrows
    public void onNext(AsyncProfilerData asyncProfilerData) {
        if (asyncProfilerData.hasMetaData()) {
            taskMetaData = parseMetaData(asyncProfilerData.getMetaData(), taskDAO);
            AsyncProfilerTask task = taskMetaData.getTask();
            if (AsyncProfilerCollectType.PROFILING_SUCCESS.equals(taskMetaData.getType())) {
                if (jfrMaxSize >= taskMetaData.getContentSize()) {
                    tempFile = Files.createTempFile(task.getId() + taskMetaData.getInstanceId() + System.currentTimeMillis(), ".jfr");
                    fileOutputStream = new FileOutputStream(tempFile.toFile());
                } else {
                    responseObserver.onError(new StatusRuntimeException(Status.ABORTED));
                    recordAsyncProfilerTaskLog(task, taskMetaData.getInstanceId(),
                            AsyncProfilerTaskLogOperationType.JFR_UPLOAD_FILE_TOO_LARGE_ERROR);
                }
            } else {
                recordAsyncProfilerTaskLog(task, taskMetaData.getInstanceId(),
                        AsyncProfilerTaskLogOperationType.EXECUTION_TASK_ERROR);
            }
        } else if (asyncProfilerData.hasContent()) {
            fileOutputStream.write(asyncProfilerData.getContent().toByteArray());
        }
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
        log.error("Error in receiving async profiler profiling data", throwable);
    }

    @Override
    @SneakyThrows
    public void onCompleted() {
        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();

        fileOutputStream.close();
        parseAndStorageData(taskMetaData, tempFile.toAbsolutePath().toString());
        tempFile.toFile().delete();
    }

    private void parseAndStorageData(AsyncProfilerCollectMetaData taskMetaData, String fileName) throws IOException {
        long uploadTime = System.currentTimeMillis();
        AsyncProfilerTask task = taskMetaData.getTask();
        if (task == null) {
            log.error("AsyncProfiler taskId:{} not found but receive data", task.getId());
            return;
        }

        recordAsyncProfilerTaskLog(task, taskMetaData.getInstanceId(), AsyncProfilerTaskLogOperationType.EXECUTION_FINISHED);

        List<JFRProfilingData> jfrProfilingData = jfrAnalyzer.parseJfr(fileName);
        for (JFRProfilingData data : jfrProfilingData) {
            data.setTaskId(task.getId());
            data.setInstanceId(taskMetaData.getInstanceId());
            data.setUploadTime(uploadTime);
            sourceReceiver.receive(data);
        }
    }

}
