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

package org.apache.skywalking.oap.server.library.pprof.parser;

import com.google.perftools.profiles.ProfileProto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parse pprof profile data and extract segment information
 */
public class PprofSegmentParser {

    /**
     * Segment information extracted from pprof labels
     */
    public static class SegmentInfo {
        private String segmentId;
        private String traceId;
        private String spanId;
        private String serviceInstanceId;
        private List<String> stack;
        private long count;

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

    /**
     * Parse pprof profile and extract all segment information
     */
    public static List<SegmentInfo> parseSegments(ProfileProto.Profile profile) {
        List<String> stringTable = profile.getStringTableList();

        // Group samples by segmentId
        Map<String, List<ProfileProto.Sample>> segmentSamples = new HashMap<>();

        for (ProfileProto.Sample sample : profile.getSampleList()) {
            String segmentId = extractSegmentIdFromLabels(sample.getLabelList(), stringTable);
            if (segmentId != null) {
                segmentSamples.computeIfAbsent(segmentId, k -> new ArrayList<>()).add(sample);
            }
        }

        // Create SegmentInfo for each segment
        List<SegmentInfo> result = new ArrayList<>(segmentSamples.size());
        for (Map.Entry<String, List<ProfileProto.Sample>> entry : segmentSamples.entrySet()) {
            String segmentId = entry.getKey();
            List<ProfileProto.Sample> samples = entry.getValue();

            SegmentInfo segmentInfo = new SegmentInfo();
            segmentInfo.setSegmentId(segmentId);

            // Extract basic information from first sample
            ProfileProto.Sample firstSample = samples.get(0);
            segmentInfo.setTraceId(extractTraceIdFromLabels(firstSample.getLabelList(), stringTable));
            segmentInfo.setSpanId(extractSpanIdFromLabels(firstSample.getLabelList(), stringTable));
            segmentInfo.setServiceInstanceId(extractServiceInstanceIdFromLabels(firstSample.getLabelList(), stringTable));

            // Merge call stacks from all samples
            List<String> combinedStack = extractCombinedStackFromSamples(samples, profile);
            segmentInfo.setStack(combinedStack);

            // Calculate total sample count
            long totalCount = samples.stream()
                .mapToLong(sample -> sample.getValueCount() > 0 ? sample.getValue(0) : 1)
                .sum();
            segmentInfo.setCount(totalCount);

            result.add(segmentInfo);
        }

        return result;
    }

    /**
     * Extract segmentId from labels
     */
    public static String extractSegmentIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
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
    private static String extractTraceIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
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
    private static String extractSpanIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
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
    private static String extractServiceInstanceIdFromLabels(List<ProfileProto.Label> labels, List<String> stringTable) {
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
     * Extract merged call stack from samples
     */
    private static List<String> extractCombinedStackFromSamples(List<ProfileProto.Sample> samples, ProfileProto.Profile profile) {
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
    public static List<String> extractStackFromSample(ProfileProto.Sample sample, ProfileProto.Profile profile) {
        List<String> stack = new ArrayList<>();

        // Traverse location_id from leaf to root
        for (int i = sample.getLocationIdCount() - 1; i >= 0; i--) {
            long locationId = sample.getLocationId(i);

            // Delegate signature resolution to PprofParser to avoid duplication
            String signature = PprofParser.resolveSignature(locationId, profile);
            if (signature != null && !signature.isEmpty()) {
                stack.add(signature);
            }
        }

        return stack;
    }

    /**
     * Get string from string table
     */
    public static String getStringFromTable(long index, List<String> stringTable) {
        if (index >= 0 && index < stringTable.size()) {
            return stringTable.get((int) index);
        }
        return null;
    }

    /**
     * Extract label value from sample labels
     */
    public static String extractLabel(ProfileProto.Sample sample, List<String> stringTable, String... keys) {
        for (ProfileProto.Label l : sample.getLabelList()) {
            String k = getStringFromTable(l.getKey(), stringTable);
            if (k == null) {
                continue;
            }
            for (String expect : keys) {
                if (k.equals(expect)) {
                    return getStringFromTable(l.getStr(), stringTable);
                }
            }
        }
        return null;
    }

    /**
     * Extract timestamp from sample labels
     */
    public static long extractTimestamp(ProfileProto.Sample sample, List<String> stringTable, boolean isStart) {
        String target = isStart ? "startTime" : "endTime";
        for (ProfileProto.Label l : sample.getLabelList()) {
            String k = getStringFromTable(l.getKey(), stringTable);
            if (k == null) {
                continue;
            }
            if (!target.equalsIgnoreCase(k)) {
                continue;
            }
            long v = l.getNum();
            if (v <= 0) {
                try {
                    String sv = getStringFromTable(l.getStr(), stringTable);
                    if (sv != null) {
                        v = Long.parseLong(sv.trim());
                    }
                } catch (Exception ignored) {
                    // ignore
                }
            }
            if (v > 0 && v < 1_000_000_000_000L) {
                // looks like seconds -> millis
                return v * 1000L;
            }
            return v;
        }
        return 0L;
    }

    /**
     * Resolve sampling period in milliseconds from pprof profile
     */
    public static long resolvePeriodMillis(ProfileProto.Profile profile) {
        try {
            long period = profile.getPeriod();
            String unit = null;
            if (profile.hasPeriodType()) {
                unit = getStringFromTable(profile.getPeriodType().getUnit(), profile.getStringTableList());
            }
            if (period > 0) {
                if (unit == null || unit.isEmpty() || "nanoseconds".equals(unit) || "nanosecond".equals(unit) || "ns".equals(unit)) {
                    return Math.max(1L, period / 1_000_000L);
                }
                if ("microseconds".equals(unit) || "us".equals(unit)) {
                    return Math.max(1L, period / 1_000L);
                }
                if ("milliseconds".equals(unit) || "ms".equals(unit)) {
                    return Math.max(1L, period);
                }
                if ("seconds".equals(unit) || "s".equals(unit)) {
                    return Math.max(1L, period * 1000L);
                }
                if ("hz".equals(unit) || "HZ".equals(unit)) {
                    // samples per second
                    return Math.max(1L, 1000L / Math.max(1L, period));
                }
            }
        } catch (Throwable t) {
            // keep silent in normal path; this is non-fatal and we fallback to default
        }
        return 10L; // default fallback
    }
}

