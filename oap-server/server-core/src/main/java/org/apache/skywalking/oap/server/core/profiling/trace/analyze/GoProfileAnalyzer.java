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
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayDeque;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.input.SegmentProfileAnalyzeQuery;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackElement;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackTree;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofSegmentParser;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzer for Go pprof samples. Builds a stack tree with total/self durations using sampling period.
 * This works independently from ThreadSnapshot, for Go profiles only.
 */
public class GoProfileAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoProfileAnalyzer.class);

    /**
     * Analyze a pprof profile for a specific segment and time window.
     */
    public ProfileAnalyzation analyze(final String segmentId,
                                      final long startTimeInclusive,
                                      final long endTimeInclusive,
                                      final ProfileProto.Profile profile) {
        final long periodMs = PprofSegmentParser.resolvePeriodMillis(profile);

        // Build ProfileStackElement directly (reuse FrameTreeBuilder's mergeSample logic)
        Map<String, Integer> key2Id = new HashMap<>(); // "parentId|name" -> id
        List<ProfileStackElement> elements = new ArrayList<>();

        // Strict per-segment filtering
        final List<String> stringTable = profile.getStringTableList();

        for (ProfileProto.Sample sample : profile.getSampleList()) {
            final String seg = PprofSegmentParser.extractSegmentIdFromLabels(sample.getLabelList(), stringTable);
            if (seg == null || !seg.equals(segmentId)) {
                continue;
            }
            long sampleCount = sample.getValueCount() > 0 ? sample.getValue(0) : 1L;
            long weightMs = sampleCount * periodMs;
            
            // Build function stack then ensure root->leaf order for aggregation
            List<String> stack = PprofSegmentParser.extractStackFromSample(sample, profile);
            Collections.reverse(stack);
            
            // Aggregate along path (similar to FrameTreeBuilder.mergeSample)
            int parentId = -1; // root
            for (String fn : stack) {
                String key = parentId + "|" + fn;
                Integer nodeId = key2Id.get(key);
                
                if (nodeId == null) {
                    ProfileStackElement element = new ProfileStackElement();
                    element.setId(elements.size());
                    element.setParentId(parentId);
                    element.setCodeSignature(fn);
                    element.setDuration(0);
                    element.setDurationChildExcluded(0);
                    element.setCount(0);
                    elements.add(element);
                    nodeId = element.getId();
                    key2Id.put(key, nodeId);
                }
                
                ProfileStackElement element = elements.get(nodeId);
                element.setDuration(element.getDuration() + (int) weightMs);
                element.setCount(element.getCount() + (int) sampleCount);
                
                parentId = nodeId;
            }
        }
        
        int rootCount = 0;
        for (ProfileStackElement e : elements) {
            if (e.getParentId() == -1) {
                rootCount++;
            }
        }
        if (rootCount > 1) {
            int virtualRootId = elements.size();
            ProfileStackElement virtualRoot = new ProfileStackElement();
            virtualRoot.setId(virtualRootId);
            virtualRoot.setParentId(-1);
            virtualRoot.setCodeSignature("root");
            virtualRoot.setDuration(0);
            virtualRoot.setDurationChildExcluded(0);
            virtualRoot.setCount(0);
            elements.add(virtualRoot);

            for (ProfileStackElement e : elements) {
                if (e.getId() == virtualRootId) {
                    continue;
                }
                if (e.getParentId() == -1) {
                    e.setParentId(virtualRootId);
                    virtualRoot.setDuration(virtualRoot.getDuration() + e.getDuration());
                    virtualRoot.setCount(virtualRoot.getCount() + e.getCount());
                }
            }
        }

        // Calculate self = total - sum(immediate children) in O(n)
        Map<Integer, Integer> childDurSum = new HashMap<>();
        for (ProfileStackElement child : elements) {
            int pid = child.getParentId();
            if (pid != -1) {
                childDurSum.put(pid, childDurSum.getOrDefault(pid, 0) + child.getDuration());
            }
        }
        for (ProfileStackElement elem : elements) {
            int childrenSum = childDurSum.getOrDefault(elem.getId(), 0);
            elem.setDurationChildExcluded(Math.max(0, elem.getDuration() - childrenSum));
        }
        
        // Reorder and reindex elements: ensure root first (id=0), parent before child
        Integer rootId = null;
        for (ProfileStackElement e : elements) {
            if (e.getParentId() == -1) {
                rootId = e.getId();
                break;
            }
        }
        if (rootId != null) {
            Map<Integer, List<ProfileStackElement>> childrenMap = new HashMap<>();
            for (ProfileStackElement e : elements) {
                childrenMap.computeIfAbsent(e.getParentId(), k -> new ArrayList<>()).add(e);
            }

            List<ProfileStackElement> ordered = new ArrayList<>();
            ArrayDeque<ProfileStackElement> queue = new ArrayDeque<>();
            // start from root
            for (ProfileStackElement e : elements) {
                if (e.getId() == rootId) {
                    queue.add(e);
                    break;
                }
            }
            while (!queue.isEmpty()) {
                ProfileStackElement cur = queue.removeFirst();
                ordered.add(cur);
                List<ProfileStackElement> children = childrenMap.get(cur.getId());
                if (children != null) {
                    // sort children by duration desc to make primary path first
                    children.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
                    queue.addAll(children);
                }
            }

            // reassign ids to ensure contiguous and root=0, fix parentId references
            Map<Integer, Integer> idRemap = new HashMap<>();
            for (int i = 0; i < ordered.size(); i++) {
                idRemap.put(ordered.get(i).getId(), i);
            }
            for (ProfileStackElement e : ordered) {
                int newId = idRemap.get(e.getId());
                int oldId = e.getId();
                int parentId = e.getParentId();
                e.setId(newId);
                if (parentId == -1) {
                    e.setParentId(-1);
                } else {
                    e.setParentId(idRemap.getOrDefault(parentId, -1));
                }
            }
            elements = ordered;
        }

        ProfileStackTree tree = new ProfileStackTree();
        tree.setElements(elements);
        
        ProfileAnalyzation result = new ProfileAnalyzation();
        result.getTrees().add(tree);
        return result;
    }

    /**
     * Analyze multiple Go profile records and return combined results
     */
    public ProfileAnalyzation analyzeRecords(List<ProfileThreadSnapshotRecord> records, List<SegmentProfileAnalyzeQuery> queries) {
        ProfileAnalyzation result = new ProfileAnalyzation();
        
        // Build query map for O(1) lookup
        Map<String, SegmentProfileAnalyzeQuery> queryMap = queries.stream()
            .collect(Collectors.toMap(SegmentProfileAnalyzeQuery::getSegmentId, q -> q));
        
        for (ProfileThreadSnapshotRecord record : records) {
            try {
                // Find the corresponding query for this segment
                SegmentProfileAnalyzeQuery query = queryMap.get(record.getSegmentId());
                
                if (query == null) {
                    LOGGER.warn("No query found for Go profile segment: {}", record.getSegmentId());
                    continue;
                }

                // Parse pprof data from stackBinary
                ProfileProto.Profile profile = PprofParser.parseProfile(record.getStackBinary());
                
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
