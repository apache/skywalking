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
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.input.SegmentProfileAnalyzeQuery;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackElement;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackTree;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofSegmentParser;
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
        
        // Calculate self = total - sum(children) (reuse FrameTreeBuilder pattern)
        for (int i = elements.size() - 1; i >= 0; i--) {
            ProfileStackElement elem = elements.get(i);
            long childrenSum = 0;
            for (ProfileStackElement other : elements) {
                if (other.getParentId() == elem.getId()) {
                    childrenSum += other.getDuration();
                }
            }
            elem.setDurationChildExcluded(Math.max(0, elem.getDuration() - (int) childrenSum));
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
