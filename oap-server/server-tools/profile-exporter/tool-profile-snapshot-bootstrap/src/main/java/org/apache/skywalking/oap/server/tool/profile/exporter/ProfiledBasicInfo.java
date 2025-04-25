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

package org.apache.skywalking.oap.server.tool.profile.exporter;

import lombok.Data;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public class ProfiledBasicInfo {

    public static final int SEQUENCE_RANGE_BATCH_SIZE = 500;

    private ExporterConfig config;

    // profiled segment
    private List<ProfiledSegment> segments;

    // spans
    private List<Span> profiledSegmentSpans;

    /**
     * reading data from storage and build data
     */
    public static ProfiledBasicInfo build(ExporterConfig config, ModuleManager manager) throws IOException {
        ProfiledBasicInfo data = new ProfiledBasicInfo();
        data.setConfig(config);

        ProfileTaskQueryService taskQueryService = manager.find(CoreModule.NAME).provider().getService(ProfileTaskQueryService.class);
        TraceQueryService traceQueryService = manager.find(CoreModule.NAME).provider().getService(TraceQueryService.class);
        IProfileThreadSnapshotQueryDAO threadSnapshotQueryDAO = manager.find(StorageModule.NAME).provider().getService(IProfileThreadSnapshotQueryDAO.class);

        // query and found profiled segment
        List<SegmentRecord> taskTraces = taskQueryService.getTaskSegments(config.getTaskId());
        List<SegmentRecord> segments = taskTraces.stream().filter(t -> Objects.equals(t.getTraceId(), config.getTraceId())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(segments)) {
            throw new IllegalArgumentException("Cannot fount profiled segment in current task: " + config.getTaskId()
                    + ", segment id: " + config.getTraceId() + ", current task total profiled trace count is " + taskTraces.size());
        }

        // setting segment basic info
        data.setSegments(new ArrayList<>());
        data.setProfiledSegmentSpans(new ArrayList<>());
        for (SegmentRecord segment : segments) {
            final ProfiledSegment profiledSegment = new ProfiledSegment();

            String segmentId = segment.getSegmentId();
            long startTime = segment.getStartTime();
            long endTime = startTime + segment.getLatency();
            profiledSegment.setSegmentId(segmentId);
            profiledSegment.setSegmentStartTime(startTime);
            profiledSegment.setSegmentEndTime(endTime);
            profiledSegment.setDuration(segment.getLatency());

            // query spans
            Trace trace = traceQueryService.queryTrace(config.getTraceId(), null);
            List<Span> profiledSegmentSpans = trace.getSpans().stream().filter(s -> Objects.equals(s.getSegmentId(), segmentId)).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(profiledSegmentSpans)) {
                throw new IllegalArgumentException("Current segment cannot found any span");
            }
            data.getProfiledSegmentSpans().addAll(profiledSegmentSpans);

            // query snapshots sequences
            int minSequence = threadSnapshotQueryDAO.queryMinSequence(segmentId, startTime, endTime);
            int maxSequence = threadSnapshotQueryDAO.queryMaxSequence(segmentId, startTime, endTime);
            profiledSegment.setMinSequence(minSequence);
            profiledSegment.setMaxSequence(maxSequence);

            data.getSegments().add(profiledSegment);
        }

        return data;
    }

    /**
     * serialize data to file
     */
    public File writeFile() throws IOException {
        String serialData = new Yaml().dump(this);
        File file = new File(config.getAnalyzeResultDist() + File.separator + "basic.yml");
        FileUtils.write(file, serialData, "UTF-8");
        return file;
    }

    /**
     * deserialize data from file
     */
    public static ProfiledBasicInfo parseFormFile(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            return new Yaml().loadAs(fileInputStream, ProfiledBasicInfo.class);
        }
    }

    /**
     * build current profiles segment snapshot search sequence ranges
     */
    public List<SequenceRange> buildSequenceRanges() {
        ArrayList<SequenceRange> ranges = new ArrayList<>();
        for (ProfiledSegment segment : this.segments) {
            int minSequence = segment.minSequence;
            do {
                int batchMax = Math.min(minSequence + SEQUENCE_RANGE_BATCH_SIZE, segment.maxSequence);
                ranges.add(new SequenceRange(segment.getSegmentId(), minSequence, batchMax));
                minSequence = batchMax;
            }
            while (minSequence < segment.maxSequence);
        }

        return ranges;
    }

    @Getter
    public static class SequenceRange {
        private String segmentId;
        private int min;
        private int max;

        public SequenceRange(String segmentId, int min, int max) {
            this.segmentId = segmentId;
            this.min = min;
            this.max = max;
        }
    }

    @Data
    public static class ProfiledSegment {
        private String segmentId;
        private long segmentStartTime;
        private long segmentEndTime;
        private int duration;

        // snapshot sequence
        private int minSequence;
        private int maxSequence;
    }

}
