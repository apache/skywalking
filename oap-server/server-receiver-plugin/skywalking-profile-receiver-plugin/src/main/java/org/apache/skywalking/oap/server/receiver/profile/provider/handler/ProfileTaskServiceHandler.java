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

package org.apache.skywalking.oap.server.receiver.profile.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskFinishReport;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskGrpc;
import org.apache.skywalking.apm.network.language.profile.ThreadSnapshot;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author MrPro
 */
public class ProfileTaskServiceHandler extends ProfileTaskGrpc.ProfileTaskImplBase implements GRPCHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileTaskServiceHandler.class);

    private ProfileTaskCache profileTaskCache;
    private final CommandService commandService;

    public ProfileTaskServiceHandler(ModuleManager moduleManager) {
        this.profileTaskCache = moduleManager.find(CoreModule.NAME).provider().getService(ProfileTaskCache.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
    }

    @Override
    public void getProfileTaskCommands(ProfileTaskCommandQuery request, StreamObserver<Commands> responseObserver) {
        // query profile task list by service id
        final List<ProfileTask> profileTaskList = profileTaskCache.getProfileTaskList(request.getServiceId());
        if (CollectionUtils.isEmpty(profileTaskList)) {
            responseObserver.onNext(Commands.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        // build command list
        final Commands.Builder commandsBuilder = Commands.newBuilder();
        final long lastCommandTime = request.getLastCommandTime();

        for (ProfileTask profileTask : profileTaskList) {
            // if command create time less than last command time, means sniffer already have task
            if (profileTask.getCreateTime() <= lastCommandTime) {
                continue;
            }

            // record profile task log
            recordProfileTaskLog(profileTask, request.getInstanceId(), ProfileTaskLogOperationType.NOTIFIED);

            // add command
            commandsBuilder.addCommands(commandService.newProfileTaskCommand(profileTask).serialize().build());
        }

        responseObserver.onNext(commandsBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ThreadSnapshot> collectSnapshot(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<ThreadSnapshot>() {
            @Override
            public void onNext(ThreadSnapshot snapshot) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("receive profile segment snapshot");
                }

                // parse segment id
                UniqueId uniqueId = snapshot.getTraceSegmentId();
                StringBuilder segmentIdBuilder = new StringBuilder();
                for (int i = 0; i < uniqueId.getIdPartsList().size(); i++) {
                    if (i == 0) {
                        segmentIdBuilder.append(uniqueId.getIdPartsList().get(i));
                    } else {
                        segmentIdBuilder.append(".").append(uniqueId.getIdPartsList().get(i));
                    }
                }

                // build database data
                final ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();
                record.setTaskId(snapshot.getTaskId());
                record.setSegmentId(segmentIdBuilder.toString());
                record.setDumpTime(snapshot.getTime());
                record.setSequence(snapshot.getSequence());
                record.setStackBinary(snapshot.getStack().toByteArray());
                record.setTimeBucket(TimeBucket.getRecordTimeBucket(snapshot.getTime()));

                // async storage
                RecordStreamProcessor.getInstance().in(record);
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.error(throwable.getMessage(), throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void reportTaskFinish(ProfileTaskFinishReport request, StreamObserver<Commands> responseObserver) {
        // query task from cache, set log time bucket need it
        final ProfileTask profileTask = profileTaskCache.getProfileTaskById(request.getTaskId());

        // record finish log
        if (profileTask != null) {
            recordProfileTaskLog(profileTask, request.getInstanceId(), ProfileTaskLogOperationType.EXECUTION_FINISHED);
        }

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

    private void recordProfileTaskLog(ProfileTask task, int instanceId, ProfileTaskLogOperationType operationType) {
        final ProfileTaskLogRecord logRecord = new ProfileTaskLogRecord();
        logRecord.setTaskId(task.getId());
        logRecord.setInstanceId(instanceId);
        logRecord.setOperationType(operationType.getCode());
        logRecord.setOperationTime(System.currentTimeMillis());
        // same with task time bucket, ensure record will ttl same with profile task
        logRecord.setTimeBucket(TimeBucket.getRecordTimeBucket(task.getStartTime() + TimeUnit.MINUTES.toMillis(task.getDuration())));

        RecordStreamProcessor.getInstance().in(logRecord);
    }

}
