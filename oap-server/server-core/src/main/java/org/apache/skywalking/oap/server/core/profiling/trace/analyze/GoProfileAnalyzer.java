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
 */

package org.apache.skywalking.oap.server.core.profiling.trace.analyze;

import com.google.perftools.profiles.ProfileProto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.input.SegmentProfileAnalyzeQuery;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackElement;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzer for Go pprof samples. Builds a stack tree with total/self durations using sampling period.
 * This works independently from ThreadSnapshot, for Go profiles only.
 */
public class GoProfileAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoProfileAnalyzer.class);

    /**
     * Aggregation node for building the stack tree.
     */
    private static final class NodeAgg {
        int id;
        int parentId;
        String name;
        long totalMs;
        long count;
        List<Integer> children = new ArrayList<>();
    }

    /**
     * Analyze a pprof profile for a specific segment and time window.
     * periodMs: derived from pprof period/periodType; fallback to 10ms when absent.
     */
    public ProfileAnalyzation analyze(final String segmentId,
                                      final long startTimeInclusive,
                                      final long endTimeInclusive,
                                      final ProfileProto.Profile profile) {
        final long periodMs = resolvePeriodMillis(profile);

        // Build indices for quick lookup
        final List<String> stringTable = profile.getStringTableList();
        final Map<Long, ProfileProto.Location> id2Location = new HashMap<>();
        for (final ProfileProto.Location loc : profile.getLocationList()) {
            id2Location.put(loc.getId(), loc);
        }
        final Map<Long, String> funcId2Name = new HashMap<>();
        for (final ProfileProto.Function fn : profile.getFunctionList()) {
            funcId2Name.put(fn.getId(), getStringFromTable(fn.getName(), stringTable));
        }

        final Map<String, Integer> key2NodeId = new HashMap<>(); // key: parentId + "|" + name
        final List<NodeAgg> nodes = new ArrayList<>();

        // Ensure root
        int rootId = ensureNode(key2NodeId, nodes, -1, "<root>");

        // Iterate samples
        for (final ProfileProto.Sample sample : profile.getSampleList()) {
            // Filter by segmentId label
            String seg = extractLabel(sample, stringTable, "traceSegmentID", "traceSegmentId", "segmentId", "segment_id", "trace_segment_id");
            if (seg == null || !seg.equals(segmentId)) {
                continue;
            }

            // Do not filter by time window; analyze all samples for this segment

            long sampleCount = sample.getValueCount() > 0 ? sample.getValue(0) : 1L;
            long weightMs = sampleCount * periodMs;

            // Build function stack from root->leaf
            final List<String> stack = new ArrayList<>();
            for (int i = sample.getLocationIdCount() - 1; i >= 0; i--) {
                final long locId = sample.getLocationId(i);
                final ProfileProto.Location loc = id2Location.get(locId);
                if (loc == null) { continue; }
                String fnName = "unknown_function";
                if (loc.getLineCount() > 0) {
                    final ProfileProto.Line line = loc.getLine(0);
                    final String name = funcId2Name.get(line.getFunctionId());
                    if (name != null && !name.isEmpty()) {
                        fnName = name;
                    }
                }
                stack.add(fnName);
            }

            // Aggregate along the path
            int parent = rootId;
            for (final String fn : stack) {
                final int nodeId = ensureNode(key2NodeId, nodes, parent, fn);
                final NodeAgg node = nodes.get(nodeId);
                node.totalMs += weightMs;
                node.count += sampleCount;
                parent = nodeId;
            }
        }

        // Compute self = total - sum(children.total)
        final long[] totalMsArr = new long[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            totalMsArr[i] = nodes.get(i).totalMs;
        }
        final long[] selfMsArr = new long[nodes.size()];
        for (int i = nodes.size() - 1; i >= 0; i--) { // children processed before parents if appended in order
            long childrenSum = 0L;
            for (final int cid : nodes.get(i).children) {
                childrenSum += totalMsArr[cid];
            }
            selfMsArr[i] = Math.max(0L, totalMsArr[i] - childrenSum);
        }

        // Build output tree (skip pure root if no data)
        final ProfileAnalyzation result = new ProfileAnalyzation();
        final ProfileStackTree tree = new ProfileStackTree();
        for (int i = 0; i < nodes.size(); i++) {
            final NodeAgg n = nodes.get(i);
            // Skip synthetic root if it has no children data
            if (i == rootId && n.children.isEmpty()) {
                continue;
            }
            final ProfileStackElement e = new ProfileStackElement();
            e.setId(i);
            e.setParentId(n.parentId);
            e.setCodeSignature(n.name);
            e.setDuration((int) Math.min(Integer.MAX_VALUE, totalMsArr[i]));
            e.setDurationChildExcluded((int) Math.min(Integer.MAX_VALUE, selfMsArr[i]));
            e.setCount((int) Math.min(Integer.MAX_VALUE, n.count));
            tree.getElements().add(e);
        }
        result.getTrees().add(tree);
        return result;
    }

    private int ensureNode(final Map<String, Integer> key2NodeId,
                           final List<NodeAgg> nodes,
                           final int parentId,
                           final String name) {
        final String key = parentId + "|" + name;
        Integer id = key2NodeId.get(key);
        if (id != null) {
            return id;
        }
        NodeAgg n = new NodeAgg();
        n.id = nodes.size();
        n.parentId = parentId;
        n.name = name;
        nodes.add(n);
        key2NodeId.put(key, n.id);
        if (parentId >= 0) {
            nodes.get(parentId).children.add(n.id);
        }
        return n.id;
    }

    private String getStringFromTable(long idx, List<String> table) {
        if (idx >= 0 && idx < table.size()) {
            return table.get((int) idx);
        }
        return null;
    }

    private String extractLabel(ProfileProto.Sample sample, List<String> stringTable, String... keys) {
        for (final ProfileProto.Label l : sample.getLabelList()) {
            final String k = getStringFromTable(l.getKey(), stringTable);
            if (k == null) { continue; }
            for (final String expect : keys) {
                if (k.equals(expect)) {
                    return getStringFromTable(l.getStr(), stringTable);
                }
            }
        }
        return null;
    }

    private long extractTimestamp(ProfileProto.Sample sample, List<String> stringTable, boolean isStart) {
        final String target = isStart ? "startTime" : "endTime";
        for (final ProfileProto.Label l : sample.getLabelList()) {
            final String k = getStringFromTable(l.getKey(), stringTable);
            if (k == null) { continue; }
            if (!target.equalsIgnoreCase(k)) { continue; }
            long v = l.getNum();
            if (v <= 0) {
                try {
                    String sv = getStringFromTable(l.getStr(), stringTable);
                    if (sv != null) { v = Long.parseLong(sv.trim()); }
                } catch (Exception ignored) {
                }
            }
            if (v > 0 && v < 1_000_000_000_000L) { // seconds -> millis
                return v * 1000L;
            }
            return v;
        }
        return 0L;
    }

    private long resolvePeriodMillis(ProfileProto.Profile profile) {
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
                if ("hz".equals(unit) || "HZ".equals(unit)) { // samples per second
                    return period > 0 ? Math.max(1L, 1000L / Math.max(1L, period)) : 10L;
                }
            }
        } catch (Throwable t) {
            // keep silent in normal path; this is non-fatal and we fallback to default
        }
        return 10L; // default fallback
    }

    /**
     * Analyze multiple Go profile records and return combined results
     */
    public ProfileAnalyzation analyzeRecords(List<ProfileThreadSnapshotRecord> records, List<SegmentProfileAnalyzeQuery> queries) {
        ProfileAnalyzation result = new ProfileAnalyzation();
        
        for (ProfileThreadSnapshotRecord record : records) {
            try {
                // Find the corresponding query for this segment
                SegmentProfileAnalyzeQuery query = queries.stream()
                    .filter(q -> q.getSegmentId().equals(record.getSegmentId()))
                    .findFirst()
                    .orElse(null);
                
                if (query == null) {
                    LOGGER.warn("No query found for Go profile segment: {}", record.getSegmentId());
                    continue;
                }

                // Parse pprof data from stackBinary
                ProfileProto.Profile profile = ProfileProto.Profile.parseFrom(record.getStackBinary());
                
                // Analyze this record
                ProfileAnalyzation recordAnalyzation = analyze(
                    record.getSegmentId(),
                    query.getTimeRange().getStart(),
                    query.getTimeRange().getEnd(),
                    profile
                );

                if (recordAnalyzation != null && !recordAnalyzation.getTrees().isEmpty()) {
                    result.getTrees().addAll(recordAnalyzation.getTrees());
                    
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Go profile analysis completed: segmentId={}, window=[{}-{}], trees={}",
                            record.getSegmentId(), query.getTimeRange().getStart(), query.getTimeRange().getEnd(),
                            recordAnalyzation.getTrees().size());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to analyze Go profile record: segmentId={}, sequence={}, dumpTime={}",
                    record.getSegmentId(), record.getSequence(), record.getDumpTime(), e);
            }
        }
        
        return result;
    }
}
