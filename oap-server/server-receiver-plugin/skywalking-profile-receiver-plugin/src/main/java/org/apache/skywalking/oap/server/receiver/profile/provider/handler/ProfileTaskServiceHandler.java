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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.profile.v3.GoProfileData;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskFinishReport;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskGrpc;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import com.google.perftools.profiles.ProfileProto;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.ProfileTaskCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
// Analyzer preview imports removed after stabilizing storage path
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofSegmentParser;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofSegmentParser.SegmentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        final String serviceId = IDManager.ServiceID.buildId(request.getService(), true);
        final String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, request.getServiceInstance());
        final List<ProfileTask> profileTaskList = profileTaskCache.getProfileTaskList(serviceId);
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
            recordProfileTaskLog(profileTask, serviceInstanceId, ProfileTaskLogOperationType.NOTIFIED);

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

                // build database data
                final ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();
                record.setTaskId(snapshot.getTaskId());
                record.setSegmentId(snapshot.getTraceSegmentId());
                record.setDumpTime(snapshot.getTime());
                record.setSequence(snapshot.getSequence());
                record.setStackBinary(snapshot.getStack().toByteArray());
                record.setTimeBucket(TimeBucket.getRecordTimeBucket(snapshot.getTime()));
                record.setLanguage(ProfileThreadSnapshotRecord.Language.JAVA); // default language for thread snapshots

                // async storage
                RecordStreamProcessor.getInstance().in(record);
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                LOGGER.error(throwable.getMessage(), throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Commands.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<GoProfileData> goProfileReport(StreamObserver<Commands> responseObserver) {
        return new StreamObserver<GoProfileData>() {
            private ByteArrayOutputStream profileDataBuffer = new ByteArrayOutputStream();
            private String currentTaskId = null;
            
            @Override
            public void onNext(GoProfileData profileData) {
                LOGGER.debug("receive go profile data: taskId='{}', payloadSize={}, isLast={}", 
                           profileData.getTaskId(), 
                           profileData.getPayload().size(), 
                           profileData.getIsLast());

                // Check if taskId is empty - this indicates a problem with Go agent
                if (profileData.getTaskId() == null || profileData.getTaskId().isEmpty()) {
                    LOGGER.error("Go agent sent empty taskId! This indicates a problem with Go agent's profile task management. " +
                                "Please check Go agent's profile task creation and task.TaskID assignment.");
                    return;
                }

                       // Reset state if this is a new task
                if (currentTaskId == null || !currentTaskId.equals(profileData.getTaskId())) {
                    currentTaskId = profileData.getTaskId();
                    profileDataBuffer.reset();
                    LOGGER.debug("Starting new task: {}", currentTaskId);
                }

                       // Collect profile data
                try {
                    profileDataBuffer.write(profileData.getPayload().toByteArray());
                } catch (IOException e) {
                    LOGGER.error("Failed to write Go profile data", e);
                    return;
                }

                       // If this is the last data chunk, parse and store
                if (profileData.getIsLast()) {
                    try {
                        // Parse Go profile data and extract all segment information using library-pprof-parser
                        byte[] rawPprofData = tryDecompressGzip(profileDataBuffer.toByteArray());
                        ProfileProto.Profile profile = ProfileProto.Profile.parseFrom(rawPprofData);
                        List<SegmentInfo> segments = PprofSegmentParser.parseSegments(profile);

                        // Log parsed segments briefly for troubleshooting
                        if (CollectionUtils.isEmpty(segments)) {
                            LOGGER.debug("Parsed Go profile has no segments. taskId={}, hint=check labels segment_id/trace_id", currentTaskId);
                        }

                        // Store ProfileThreadSnapshotRecord for each segment
                        for (SegmentInfo segmentInfo : segments) {
                            storeGoProfileSegment(segmentInfo, currentTaskId, profile);
                        }

                        // Analyzer preview removed to reduce log noise after verification

                        LOGGER.info("Processed Go profile data: taskId={}, segments={}", currentTaskId, segments.size());
                        
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse Go profile data for task: " + currentTaskId, e);
                    } finally {
                               // Reset state
                        profileDataBuffer.reset();
                        currentTaskId = null;
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(throwable.getMessage(), throwable);
                    }
                    return;
                }
                LOGGER.error(throwable.getMessage(), throwable);
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
        final String serviceId = IDManager.ServiceID.buildId(request.getService(), true);
        final String serviceInstanceId = IDManager.ServiceInstanceID.buildId(serviceId, request.getServiceInstance());
        final ProfileTask profileTask = profileTaskCache.getProfileTaskById(request.getTaskId());

        // record finish log
        if (profileTask != null) {
            recordProfileTaskLog(profileTask, serviceInstanceId, ProfileTaskLogOperationType.EXECUTION_FINISHED);
        }

        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

    private void recordProfileTaskLog(ProfileTask task, String instanceId, ProfileTaskLogOperationType operationType) {
        final ProfileTaskLogRecord logRecord = new ProfileTaskLogRecord();
        logRecord.setTaskId(task.getId());
        logRecord.setInstanceId(instanceId);
        logRecord.setOperationType(operationType.getCode());
        logRecord.setOperationTime(System.currentTimeMillis());
        // same with task time bucket, ensure record will ttl same with profile task
        long timestamp = task.getStartTime() + TimeUnit.MINUTES.toMillis(task.getDuration());
        logRecord.setTimeBucket(
            TimeBucket.getRecordTimeBucket(timestamp));
        logRecord.setTimestamp(timestamp);
        RecordStreamProcessor.getInstance().in(logRecord);
    }


    /**
     * If payload is gzip-compressed, decompress it; otherwise return original bytes.
     */
    private byte[] tryDecompressGzip(byte[] bytes) {
        // GZIP magic header 0x1F 0x8B
        if (bytes != null && bytes.length >= 2 && (bytes[0] == (byte) 0x1F) && (bytes[1] == (byte) 0x8B)) {
            try (InputStream gis = new GZIPInputStream(new java.io.ByteArrayInputStream(bytes));
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = gis.read(buffer)) != -1) {
                    bos.write(buffer, 0, read);
                }
                return bos.toByteArray();
            } catch (IOException e) {
                LOGGER.warn("Failed to gunzip Go profile payload, fallback to raw bytes: {}", e.getMessage());
                return bytes;
            }
        }
        return bytes;
    }
    

    /**
     * Store Go profile segment - create a filtered pprof containing only samples for this segment
     */
    private void storeGoProfileSegment(SegmentInfo segmentInfo, String taskId, ProfileProto.Profile originalProfile) {
        try {
            // Create a filtered pprof profile containing only samples for this segment
            ProfileProto.Profile.Builder filteredProfileBuilder = originalProfile.toBuilder();
            filteredProfileBuilder.clearSample();
            
            // Add only samples that belong to this segment
            for (ProfileProto.Sample sample : originalProfile.getSampleList()) {
                String sampleSegmentId = PprofSegmentParser.extractSegmentIdFromLabels(sample.getLabelList(), originalProfile.getStringTableList());
                if (segmentInfo.getSegmentId().equals(sampleSegmentId)) {
                    filteredProfileBuilder.addSample(sample);
                }
            }
            
            ProfileProto.Profile filteredProfile = filteredProfileBuilder.build();
            byte[] filteredPprofData = filteredProfile.toByteArray();
            
            // Create ProfileThreadSnapshotRecord for this segment
            ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();
            record.setTaskId(taskId);
            record.setSegmentId(segmentInfo.getSegmentId()); // Use real segmentId
            long dumpTimeMs = originalProfile.getTimeNanos() > 0 ? originalProfile.getTimeNanos() / 1_000_000L : System.currentTimeMillis();
            record.setDumpTime(dumpTimeMs);
            record.setSequence(0); // Each segment has only one record
            record.setLanguage(ProfileThreadSnapshotRecord.Language.GO); // mark as Go profile data
            
            // Store filtered pprof data containing only this segment's samples
            record.setStackBinary(filteredPprofData);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(dumpTimeMs));
            
            LOGGER.info("About to store Go profile snapshot: taskId={}, segmentId={}, dumpTime={}, timeBucket={}, sequence={}, language={}, filteredDataSize={}, samples={}",
                record.getTaskId(), record.getSegmentId(), record.getDumpTime(), record.getTimeBucket(), 
                record.getSequence(), record.getLanguage(), filteredPprofData.length, filteredProfile.getSampleCount());
            
            // Store to database
            RecordStreamProcessor.getInstance().in(record);
            LOGGER.info("Stored Go profile snapshot: taskId={}, segmentId={}, dumpTime={}, sequence={}, language={}",
                record.getTaskId(), record.getSegmentId(), record.getDumpTime(), record.getSequence(), record.getLanguage());
                
        } catch (Exception e) {
            LOGGER.error("Failed to store Go profile segment: segmentId={}, taskId={}", segmentInfo.getSegmentId(), taskId, e);
        }
    }

}
