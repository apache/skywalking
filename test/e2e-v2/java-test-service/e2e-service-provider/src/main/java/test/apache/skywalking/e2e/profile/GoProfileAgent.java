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

package test.apache.skywalking.e2e.profile;

import com.google.perftools.profiles.ProfileProto;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.profile.v3.GoProfileData;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskGrpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Go Profile Agent that simulates Go application behavior for profiling.
 * This component sends GoProfileData to OAP via goProfileReport when triggered.
 */
@Component
@Slf4j
public class GoProfileAgent {
    
    @Value("${skywalking.oap.host:localhost}")
    private String oapHost;
    
    @Value("${skywalking.oap.port:11800}")
    private int oapPort;
    
    private ManagedChannel channel;
    private ProfileTaskGrpc.ProfileTaskStub asyncStub;
    
    private volatile String currentTaskId;
    private volatile boolean profilingActive = false;
    
    @PostConstruct
    public void init() {
        log.info("Initializing Go Profile Agent - OAP: {}:{}", oapHost, oapPort);
        channel = ManagedChannelBuilder.forAddress(oapHost, oapPort)
                .usePlaintext()
                .build();
        asyncStub = ProfileTaskGrpc.newStub(channel);
        
        // Start background task to query for profiling tasks
        startTaskQueryLoop();
    }
    
    private void startTaskQueryLoop() {
        Thread taskQueryThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    queryProfileTasks();
                    Thread.sleep(5000); // Query every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.debug("Error querying profile tasks: {}", e.getMessage());
                }
            }
        });
        taskQueryThread.setDaemon(true);
        taskQueryThread.setName("go-profile-task-query");
        taskQueryThread.start();
    }
    
    private void queryProfileTasks() {
        try {
            org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskCommandQuery query = 
                org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskCommandQuery.newBuilder()
                    .setService("e2e-service-provider")
                    .setServiceInstance("provider1")
                    .setLastCommandTime(System.currentTimeMillis())
                    .build();
            
            ProfileTaskGrpc.ProfileTaskBlockingStub blockingStub = ProfileTaskGrpc.newBlockingStub(channel);
            Commands commands = blockingStub.getProfileTaskCommands(query);
            
            // Process commands to start/stop profiling
            if (commands.getCommandsCount() > 0) {
                for (org.apache.skywalking.apm.network.common.v3.Command command : commands.getCommandsList()) {
                    if ("ProfileTaskQuery".equals(command.getCommand())) {
                        // Extract task information from command args
                        String taskId = getArgValue(command, "TaskId");
                        if (taskId != null) {
                            startProfiling(taskId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No profile tasks available: {}", e.getMessage());
        }
    }
    
    private String getArgValue(org.apache.skywalking.apm.network.common.v3.Command command, String key) {
        for (org.apache.skywalking.apm.network.common.v3.KeyStringValuePair arg : command.getArgsList()) {
            if (key.equals(arg.getKey())) {
                return arg.getValue();
            }
        }
        return null;
    }
    
    private void startProfiling(String taskId) {
        log.info("Starting profiling for task: {}", taskId);
        currentTaskId = taskId;
        profilingActive = true;
        startProfilingLoop();
    }
    
    private void startProfilingLoop() {
        Thread profilingThread = new Thread(() -> {
            while (profilingActive && currentTaskId != null) {
                try {
                    triggerProfileCollection();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in profiling loop", e);
                }
            }
        });
        profilingThread.setDaemon(true);
        profilingThread.setName("go-profile-collection");
        profilingThread.start();
    }
    
    private void stopProfiling(String taskId) {
        log.info("Stopping profiling for task: {}", taskId);
        if (taskId.equals(currentTaskId)) {
            profilingActive = false;
            currentTaskId = null;
        }
    }
    
    public void triggerProfileCollection() {
        if (!profilingActive || currentTaskId == null) {
            log.debug("Profiling not active, skipping data collection");
            return;
        }
        
        log.info("Collecting profile data for task: {}", currentTaskId);
        
        try {
            // Generate trace and segment IDs
            String traceId = "trace-" + UUID.randomUUID().toString().substring(0, 8);
            String segmentId = "segment-" + UUID.randomUUID().toString().substring(0, 8);
            
            // Build pprof data
            byte[] pprofBytes = buildPprofData(traceId, segmentId);
            byte[] payload = gzip(pprofBytes);
            
            // Send GoProfileData to OAP
            sendGoProfileData(currentTaskId, payload);
            
            log.info("Profile data sent successfully - traceId: {}, segmentId: {}", traceId, segmentId);
            
        } catch (Exception e) {
            log.error("Error collecting profile data", e);
        }
    }
    
    private void sendGoProfileData(String taskId, byte[] payload) throws Exception {
        StreamObserver<GoProfileData> upstream = asyncStub.goProfileReport(
                new StreamObserver<Commands>() {
                    @Override
                    public void onNext(Commands value) {
                        log.debug("Received response from OAP: {}", value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("Error sending profile data", t);
                    }

                    @Override
                    public void onCompleted() {
                        log.debug("Profile data stream completed");
                    }
                }
        );

        GoProfileData data = GoProfileData.newBuilder()
                .setTaskId(taskId)
                .setPayload(ByteString.copyFrom(payload))
                .setIsLast(true)
                .build();
        
        upstream.onNext(data);
        upstream.onCompleted();
    }
    
    private byte[] buildPprofData(String traceId, String segmentId) throws Exception {
        ProfileProto.Profile.Builder profile = ProfileProto.Profile.newBuilder();
        
        // String table
        profile.addStringTable(""); // Index 0 must be empty
        int kTraceId = profile.getStringTableCount();
        profile.addStringTable("traceID");
        int vTraceId = profile.getStringTableCount();
        profile.addStringTable(traceId);
        int kSegId = profile.getStringTableCount();
        profile.addStringTable("segment_id");
        int vSegId = profile.getStringTableCount();
        profile.addStringTable(segmentId);
        
        // Add some function names to make it more realistic
        int funcNameIdx = profile.getStringTableCount();
        profile.addStringTable("main.profileWork");
        int funcNameIdx2 = profile.getStringTableCount();
        profile.addStringTable("runtime.main");
        
        // Functions
        profile.addFunction(ProfileProto.Function.newBuilder()
                .setId(1)
                .setName(funcNameIdx)
                .setFilename(funcNameIdx)
                .setStartLine(1));
        profile.addFunction(ProfileProto.Function.newBuilder()
                .setId(2)
                .setName(funcNameIdx2)
                .setFilename(funcNameIdx2)
                .setStartLine(1));
        
        // Locations
        profile.addLocation(ProfileProto.Location.newBuilder()
                .setId(1)
                .addLine(ProfileProto.Line.newBuilder().setFunctionId(1).setLine(10)));
        profile.addLocation(ProfileProto.Location.newBuilder()
                .setId(2)
                .addLine(ProfileProto.Line.newBuilder().setFunctionId(2).setLine(5)));
        
        // Labels
        ProfileProto.Label labelTrace = ProfileProto.Label.newBuilder()
                .setKey(kTraceId)
                .setStr(vTraceId)
                .build();
        ProfileProto.Label labelSeg = ProfileProto.Label.newBuilder()
                .setKey(kSegId)
                .setStr(vSegId)
                .build();
        
        // Samples with realistic call stack
        profile.addSample(ProfileProto.Sample.newBuilder()
                .addLocationId(1)
                .addLocationId(2) // Call stack: main.profileWork -> runtime.main
                .addLabel(labelTrace)
                .addLabel(labelSeg)
                .addValue(1000)); // Sample count
        
        return profile.build().toByteArray();
    }
    
    private byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(data);
        }
        return bos.toByteArray();
    }
}
