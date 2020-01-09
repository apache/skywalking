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
import org.apache.skywalking.apm.network.language.profile.ProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.profile.ProfileTaskGrpc;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author MrPro
 */
public class ProfileTaskServiceHandler extends ProfileTaskGrpc.ProfileTaskImplBase implements GRPCHandler {

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
            recordProfileTaskLog(profileTask, request);

            // add command
            commandsBuilder.addCommands(commandService.newProfileTaskCommand(profileTask).serialize().build());
        }

        responseObserver.onNext(commandsBuilder.build());
        responseObserver.onCompleted();
    }

    private void recordProfileTaskLog(ProfileTask task, ProfileTaskCommandQuery query) {
        final ProfileTaskLogRecord logRecord = new ProfileTaskLogRecord();
        logRecord.setTaskId(task.getId());
        logRecord.setInstanceId(query.getInstanceId());
        logRecord.setOperationType(ProfileTaskLogOperationType.NOTIFIED.getCode());
        logRecord.setOperationTime(System.currentTimeMillis());
        // same with task time bucket, ensure record will ttl same with profile task
        logRecord.setTimeBucket(TimeBucket.getRecordTimeBucket(task.getStartTime() + TimeUnit.MINUTES.toMillis(task.getDuration())));

        RecordStreamProcessor.getInstance().in(logRecord);
    }

}
