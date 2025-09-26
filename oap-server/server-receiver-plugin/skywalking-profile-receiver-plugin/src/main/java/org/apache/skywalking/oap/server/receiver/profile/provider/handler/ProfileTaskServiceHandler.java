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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
                record.setGo(false); // Mark as Java or python profile data

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
                               // Parse Go profile data and extract all segment information
                        List<GoProfileSegmentInfo> segments = parseGoProfileData(profileDataBuffer.toByteArray());

                        // Log parsed segments briefly for troubleshooting
                        if (CollectionUtils.isEmpty(segments)) {
                            LOGGER.debug("Parsed Go profile has no segments. taskId={}, hint=check labels segment_id/trace_id", currentTaskId);
                        }

                        // Store ProfileThreadSnapshotRecord for each segment
                        byte[] rawPprofData = tryDecompressGzip(profileDataBuffer.toByteArray());
                        ProfileProto.Profile profile = ProfileProto.Profile.parseFrom(rawPprofData);
                        for (GoProfileSegmentInfo segmentInfo : segments) {
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
     * Parse Go profile data and extract all segment information
     */
    private List<GoProfileSegmentInfo> parseGoProfileData(byte[] profileData) {
        List<GoProfileSegmentInfo> segments = new ArrayList<>();
        
        try {
            // Parse pprof format profile data (payload may be gzip-compressed per pprof spec)
            byte[] parsedBytes = tryDecompressGzip(profileData);
            ProfileProto.Profile profile = ProfileProto.Profile.parseFrom(parsedBytes);
            
            // Group samples by segmentId
            Map<String, List<ProfileProto.Sample>> segmentSamples = new HashMap<>();
            
            for (ProfileProto.Sample sample : profile.getSampleList()) {
                String segmentId = extractSegmentIdFromLabels(sample.getLabelList(), profile.getStringTableList());
                if (segmentId != null) {
                    segmentSamples.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(sample);
                } else {
                    // Log filtered out samples (for debugging)
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Filtered out sample without segmentId, labels: {}", 
                                   sample.getLabelList().stream()
                                       .map(label -> getStringFromTable(label.getKey(), profile.getStringTableList()) + "=" + 
                                                    getStringFromTable(label.getStr(), profile.getStringTableList()))
                                       .collect(java.util.stream.Collectors.toList()));
                    }
                }
            }
            
            // Create GoProfileSegmentInfo for each segment
            for (Map.Entry<String, List<ProfileProto.Sample>> entry : segmentSamples.entrySet()) {
                String segmentId = entry.getKey();
                List<ProfileProto.Sample> samples = entry.getValue();
                
                GoProfileSegmentInfo segmentInfo = new GoProfileSegmentInfo();
                segmentInfo.setSegmentId(segmentId);
                
                // Extract basic information.
                ProfileProto.Sample firstSample = samples.get(0);
                segmentInfo.setTraceId(extractTraceIdFromLabels(firstSample.getLabelList(), profile.getStringTableList()));
                segmentInfo.setSpanId(extractSpanIdFromLabels(firstSample.getLabelList(), profile.getStringTableList()));
                segmentInfo.setServiceInstanceId(extractServiceInstanceIdFromLabels(firstSample.getLabelList(), profile.getStringTableList()));
                
                // Merge call stacks from all samples
                List<String> combinedStack = extractCombinedStackFromSamples(samples, profile);
                segmentInfo.setStack(combinedStack);
                
                // Calculate total sample count
                long totalCount = samples.stream()
                    .mapToLong(sample -> sample.getValueCount() > 0 ? sample.getValue(0) : 1)
                    .sum();
                segmentInfo.setCount(totalCount);
                
                segments.add(segmentInfo);
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Parsed Go profile segment: {}, samples: {}, stack depth: {}", 
                               segmentId, samples.size(), combinedStack.size());
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to parse Go profile data", e);
        }
        
        return segments;
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
     * Extract segmentId from labels
     */
    private String extractSegmentIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
        for (ProfileProto.Label label : labels) {
            String key = getStringFromTable(label.getKey(), stringTable);
            if (key != null && (key.equals("segment_id") || key.equals("trace_segment_id") || 
                               key.equals("segmentId") || key.equals("traceSegmentId") ||
                               key.equals("traceSegmentID"))) {
                return getStringFromTable(label.getStr(), stringTable);
            }
        }
        return null;
    }
    
    /**
     * Extract traceId from labels
     */
    private String extractTraceIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
        for (ProfileProto.Label label : labels) {
            String key = getStringFromTable(label.getKey(), stringTable);
            if (key != null && (key.equals("trace_id") || key.equals("traceId") || key.equals("traceID"))) {
                return getStringFromTable(label.getStr(), stringTable);
            }
        }
        return "go_trace_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Extract spanId from labels
     */
    private String extractSpanIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
        for (ProfileProto.Label label : labels) {
            String key = getStringFromTable(label.getKey(), stringTable);
            if (key != null && (key.equals("span_id") || key.equals("spanId") || key.equals("spanID"))) {
                return getStringFromTable(label.getStr(), stringTable);
            }
        }
        return null; // spanId is optional
    }
    
    /**
     * Extract serviceInstanceId from labels
     */
    private String extractServiceInstanceIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
        for (ProfileProto.Label label : labels) {
            String key = getStringFromTable(label.getKey(), stringTable);
            if (key != null && (key.equals("service_instance_id") || key.equals("serviceInstanceId") || 
                               key.equals("instance_id") || key.equals("instanceId"))) {
                return getStringFromTable(label.getStr(), stringTable);
            }
        }
        return "go_instance_1";
    }
    
    /**
     * Extract start time from labels
     */
    private long extractStartTimeFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
        for (ProfileProto.Label label : labels) {
            final String key = getStringFromTable(label.getKey(), stringTable);
            if (key == null) {
                continue;
            }
            if (!"startTime".equalsIgnoreCase(key)) {
                continue;
            }
            long ts = parseTimestampLabelValue(label, stringTable);
            if (ts > 0) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Parsed startTime from pprof labels: {} => {}", key, ts);
                }
                return ts;
            }
        }
        // Not found in labels
        return 0L;
    }
    
    /**
     * Extract end time from labels
     */
    private long extractEndTimeFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
        for (ProfileProto.Label label : labels) {
            final String key = getStringFromTable(label.getKey(), stringTable);
            if (key == null) {
                continue;
            }
            if (!"endTime".equalsIgnoreCase(key)) {
                continue;
            }
            long ts = parseTimestampLabelValue(label, stringTable);
            if (ts > 0) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Parsed endTime from pprof labels: {} => {}", key, ts);
                }
                return ts;
            }
        }
        // Not found in labels
        return 0L;
    }

    /**
     * Parse timestamp value from a pprof Label. Value could be put in num or str.
     * Accept seconds (10 digits) and milliseconds (13 digits). Convert seconds to millis.
     */
    private long parseTimestampLabelValue(ProfileProto.Label label, List<String> stringTable) {
        long value = label.getNum();
        if (value <= 0) {
            try {
                final String str = getStringFromTable(label.getStr(), stringTable);
                if (str != null) {
                    value = Long.parseLong(str.trim());
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        if (value > 0 && value < 1_000_000_000_000L) { // looks like seconds
            return value * 1000L;
        }
        return value;
    }
    
    /**
     * Extract merged call stack from samples
     */
    private List<String> extractCombinedStackFromSamples(List<ProfileProto.Sample> samples, ProfileProto.Profile profile) {
        Set<String> uniqueStack = new LinkedHashSet<>();
        
        for (ProfileProto.Sample sample : samples) {
            List<String> stack = extractStackFromSample(sample, profile);
            uniqueStack.addAll(stack);
        }
        
        return new ArrayList<>(uniqueStack);
    }
    
    /**
     * Extract call stack from a single sample
     */
    private List<String> extractStackFromSample(ProfileProto.Sample sample, ProfileProto.Profile profile) {
        List<String> stack = new ArrayList<>();
        
        // Traverse location_id from leaf to root
        for (int i = sample.getLocationIdCount() - 1; i >= 0; i--) {
            long locationId = sample.getLocationId(i);
            
            // Find corresponding Location
            for (ProfileProto.Location location : profile.getLocationList()) {
                if (location.getId() == locationId) {
                    // Get function name
                    String functionName = extractFunctionNameFromLocation(location, profile);
                    if (functionName != null && !functionName.isEmpty()) {
                        stack.add(functionName);
                    }
                    break;
                }
            }
        }
        
        return stack;
    }
    
    /**
     * Extract function name from Location
     */
    private String extractFunctionNameFromLocation(ProfileProto.Location location, ProfileProto.Profile profile) {
        if (location.getLineCount() > 0) {
            ProfileProto.Line line = location.getLine(0);
            long functionId = line.getFunctionId();
            
            // Find corresponding Function
            for (ProfileProto.Function function : profile.getFunctionList()) {
                if (function.getId() == functionId) {
                    return getStringFromTable(function.getName(), profile.getStringTableList());
                }
            }
        }
        return "unknown_function";
    }
    
    /**
     * Get string from string table
     */
    private String getStringFromTable(long index, List<String> stringTable) {
        if (index >= 0 && index < stringTable.size()) {
            return stringTable.get((int) index);
        }
        return null;
    }

    /**
     * Store Go profile segment - create a filtered pprof containing only samples for this segment
     */
    private void storeGoProfileSegment(GoProfileSegmentInfo segmentInfo, String taskId, ProfileProto.Profile originalProfile) {
        try {
            // Create a filtered pprof profile containing only samples for this segment
            ProfileProto.Profile.Builder filteredProfileBuilder = originalProfile.toBuilder();
            filteredProfileBuilder.clearSample();
            
            // Add only samples that belong to this segment
            for (ProfileProto.Sample sample : originalProfile.getSampleList()) {
                String sampleSegmentId = extractSegmentIdFromLabels(sample.getLabelList(), originalProfile.getStringTableList());
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
            record.setGo(true); // Mark as Go profile data
            
            // Store filtered pprof data containing only this segment's samples
            record.setStackBinary(filteredPprofData);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(dumpTimeMs));
            
            LOGGER.info("About to store Go profile snapshot: taskId={}, segmentId={}, dumpTime={}, timeBucket={}, sequence={}, isGo={}, filteredDataSize={}, samples={}",
                record.getTaskId(), record.getSegmentId(), record.getDumpTime(), record.getTimeBucket(), 
                record.getSequence(), record.isGo(), filteredPprofData.length, filteredProfile.getSampleCount());
            
            // Store to database
            RecordStreamProcessor.getInstance().in(record);
            LOGGER.info("Stored Go profile snapshot: taskId={}, segmentId={}, dumpTime={}, sequence={}, isGo={}",
                record.getTaskId(), record.getSegmentId(), record.getDumpTime(), record.getSequence(), record.isGo());
                
        } catch (Exception e) {
            LOGGER.error("Failed to store Go profile segment: segmentId={}, taskId={}", segmentInfo.getSegmentId(), taskId, e);
        }
    }

    /**
     * Go profile segment information
     */
    private static class GoProfileSegmentInfo {
        private String segmentId;
        private String traceId;
        private String spanId;
        private String serviceInstanceId;
        private long startTime;
        private long endTime;
        private List<String> stack;
        private long count;

        // Getters and Setters
        public String getSegmentId() { 
            return segmentId; 
        }

        public void setSegmentId(String segmentId) { 
            this.segmentId = segmentId; 
        }

        public String getTraceId() { 
            return traceId; 
        }

        public void setTraceId(String traceId) { 
            this.traceId = traceId; 
        }

        public String getSpanId() { 
            return spanId; 
        }

        public void setSpanId(String spanId) { 
            this.spanId = spanId; 
        }

        public String getServiceInstanceId() { 
            return serviceInstanceId; 
        }

        public void setServiceInstanceId(String serviceInstanceId) { 
            this.serviceInstanceId = serviceInstanceId; 
        }

        public long getStartTime() { 
            return startTime; 
        }

        public void setStartTime(long startTime) { 
            this.startTime = startTime; 
        }

        public long getEndTime() { 
            return endTime; 
        }

        public void setEndTime(long endTime) { 
            this.endTime = endTime; 
        }

        public List<String> getStack() { 
            return stack; 
        }

        public void setStack(List<String> stack) { 
            this.stack = stack; 
        }

        public long getCount() { 
            return count; 
        }

        public void setCount(long count) { 
            this.count = count; 
        }
    }

}
