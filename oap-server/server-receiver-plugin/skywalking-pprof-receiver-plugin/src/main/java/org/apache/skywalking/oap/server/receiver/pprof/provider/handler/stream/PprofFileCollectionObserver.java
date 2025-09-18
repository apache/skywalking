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

package org.apache.skywalking.oap.server.receiver.pprof.provider.handler.stream;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import lombok.SneakyThrows;
import org.apache.skywalking.apm.network.pprof.v10.PprofData;
import org.apache.skywalking.apm.network.pprof.v10.PprofCollectionResponse;
import org.apache.skywalking.apm.network.pprof.v10.PprofProfilingStatus;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.core.source.PprofProfilingData;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofParser;
import org.apache.skywalking.oap.server.library.pprof.type.FrameTree;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import static org.apache.skywalking.oap.server.receiver.pprof.provider.handler.PprofServiceHandler.recordPprofTaskLog;
import static org.apache.skywalking.oap.server.receiver.pprof.provider.handler.PprofServiceHandler.parseMetaData;

@Slf4j
public class PprofFileCollectionObserver implements StreamObserver<PprofData> {
    private final IPprofTaskQueryDAO taskDAO;
    private final StreamObserver<PprofCollectionResponse> responseObserver;
    private final SourceReceiver sourceReceiver;
    private final int pprofMaxSize;
    private PprofCollectionMetaData taskMetaData;
    private Path tempFile;
    private FileOutputStream fileOutputStream;

    public PprofFileCollectionObserver(IPprofTaskQueryDAO taskDAO, 
                                      StreamObserver<PprofCollectionResponse> responseObserver, 
                                      SourceReceiver sourceReceiver, int pprofMaxSize) {
        this.taskDAO = taskDAO;
        this.responseObserver = responseObserver;
        this.sourceReceiver = sourceReceiver;
        this.pprofMaxSize = pprofMaxSize;
    }

    @SneakyThrows
    @Override
    public void onNext(PprofData pprofData) {
        if (Objects.isNull(taskMetaData) && pprofData.hasMetadata()) {
            taskMetaData = parseMetaData(pprofData.getMetadata(), taskDAO);
            
            if (PprofProfilingStatus.PPROF_PROFILING_SUCCESS.equals(taskMetaData.getType())) {
                int size = taskMetaData.getContentSize();
                if (pprofMaxSize >= size) {
                    // Create temporary file for pprof data
                    tempFile = Files.createTempFile(taskMetaData.getTask().getId() + taskMetaData.getInstanceId() + System.currentTimeMillis(), ".pprof");
                    fileOutputStream = new FileOutputStream(tempFile.toFile());
                    
                    // Send success response to allow client to continue uploading
                    responseObserver.onNext(PprofCollectionResponse.newBuilder()
                            .setStatus(PprofProfilingStatus.PPROF_PROFILING_SUCCESS)
                            .build());
                } else {
                    responseObserver.onNext(PprofCollectionResponse.newBuilder()
                            .setStatus(PprofProfilingStatus.PPROF_TERMINATED_BY_OVERSIZE)
                            .build());
                    recordPprofTaskLog(taskMetaData.getTask(), taskMetaData.getInstanceId(), PprofTaskLogOperationType.PPROF_UPLOAD_FILE_TOO_LARGE_ERROR);
                }
            } else {
                responseObserver.onNext(PprofCollectionResponse.newBuilder()
                        .setStatus(PprofProfilingStatus.PPROF_EXECUTION_TASK_ERROR)
                        .build());
                recordPprofTaskLog(taskMetaData.getTask(), taskMetaData.getInstanceId(), PprofTaskLogOperationType.EXECUTION_TASK_ERROR);
            }
        } else if (pprofData.hasContent()) {
            if (fileOutputStream != null) {
                fileOutputStream.write(pprofData.getContent().toByteArray());
                
                if (log.isDebugEnabled()) {
                    log.debug("Received {} bytes of pprof data", pprofData.getContent().size());
                }
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Status status = Status.fromThrowable(throwable);
        if (Status.CANCELLED.getCode() == status.getCode()) {
            if (log.isDebugEnabled()) {
                log.debug("Pprof data collection cancelled: {}", throwable.getMessage());
            }
        } else {
            log.error("Error in receiving pprof profiling data", throwable);
        }
        
        // Clean up resources
        closeFileStream();
    }

    @Override
    @SneakyThrows
    public void onCompleted() {
        responseObserver.onCompleted();
        
        if (Objects.nonNull(tempFile)) {
            closeFileStream();
            parseAndStorageData(taskMetaData, tempFile.toAbsolutePath().toString());
        }
    }

    private void closeFileStream() {
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
                fileOutputStream = null;
            } catch (IOException e) {
                log.error("Failed to close file output stream", e);
            }
        }
    }

    @SneakyThrows
    private void parseAndStorageData(PprofCollectionMetaData taskMetaData, String fileName) {
        PprofTask task = taskMetaData.getTask();
        if (task == null) {
            log.error("Pprof instanceId:{} has not been assigned a task but still uploaded data", taskMetaData.getInstanceId());
            return;
        }
        recordPprofTaskLog(task, taskMetaData.getInstanceId(), PprofTaskLogOperationType.EXECUTION_FINISHED);
        parsePprofAndStorage(taskMetaData, fileName);
    }

    public void parsePprofAndStorage(PprofCollectionMetaData taskMetaData, 
                                     String fileName) throws IOException {
    log.info("Parsing pprof file for service: {}, instance: {}", 
    taskMetaData.getServiceId(), taskMetaData.getInstanceId());
    PprofTask task = taskMetaData.getTask();
    FrameTree tree = PprofParser.dumpTree(fileName);
    PprofProfilingData data = new PprofProfilingData();
    data.setEventType(PprofEventType.valueOfString(task.getEvents().name()));
    data.setFrameTree(tree);
    data.setTaskId(task.getId());
    data.setInstanceId(taskMetaData.getInstanceId());
    data.setUploadTime(taskMetaData.getUploadTime());
    log.info("data eventType: {}", data.getEventType());
    log.info("data frameTree: {}", tree);
    log.info("data taskId: {}", task.getId());
    log.info("data instanceId: {}", taskMetaData.getInstanceId());
    log.info("data uploadTime: {}", taskMetaData.getUploadTime());
    sourceReceiver.receive(data);
    }
}
