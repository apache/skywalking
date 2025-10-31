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

package org.apache.skywalking.oap.server.core.profiling.trace.analyze;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileLanguageType;
import org.apache.skywalking.oap.server.core.query.input.SegmentProfileAnalyzeQuery;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackTree;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyze {@link ProfileStack} data to {@link ProfileAnalyzation}
 *
 * See: https://github.com/apache/skywalking/blob/421ba88dbfba48cdc5845547381aa4763775b4b1/docs/en/guides/backend-profile.md#thread-analyst
 */
public class ProfileAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileAnalyzer.class);

    private static final ProfileAnalyzeCollector ANALYZE_COLLECTOR = new ProfileAnalyzeCollector();

    private final int threadSnapshotAnalyzeBatchSize;
    private final int analyzeSnapshotMaxSize;

    private final ModuleManager moduleManager;
    protected IProfileThreadSnapshotQueryDAO profileThreadSnapshotQueryDAO;

    public ProfileAnalyzer(ModuleManager moduleManager, int snapshotAnalyzeBatchSize, int analyzeSnapshotMaxSize) {
        this.moduleManager = moduleManager;
        this.threadSnapshotAnalyzeBatchSize = snapshotAnalyzeBatchSize;
        this.analyzeSnapshotMaxSize = analyzeSnapshotMaxSize;
    }

    /**
     * search snapshots and analyze
     */
    public ProfileAnalyzation analyze(final List<SegmentProfileAnalyzeQuery> queries) throws IOException {
        ProfileAnalyzation analyzation = new ProfileAnalyzation();

        // Step 1: Try Java profile analysis first (original logic with time window)
        SequenceSearch sequenceSearch = getAllSequenceRange(queries);
        List<ProfileThreadSnapshotRecord> javaRecords = new ArrayList<>();
        
        if (sequenceSearch != null) {
            if (sequenceSearch.getTotalSequenceCount() > analyzeSnapshotMaxSize) {
                analyzation.setTip("Out of snapshot analyze limit, " + sequenceSearch.getTotalSequenceCount() + " snapshots found, but analysis first " + analyzeSnapshotMaxSize + " snapshots only.");
            }

            // query snapshots within time window
            List<ProfileThreadSnapshotRecord> records = sequenceSearch.getRanges().parallelStream().map(r -> {
                try {
                    return getProfileThreadSnapshotQueryDAO().queryRecords(r.getSegmentId(), r.getMinSequence(), r.getMaxSequence());
                } catch (IOException e) {
                    LOGGER.warn(e.getMessage(), e);
                    return Collections.<ProfileThreadSnapshotRecord>emptyList();
                }
            }).flatMap(Collection::stream)
                .collect(Collectors.toList());

            if (LOGGER.isDebugEnabled()) {
                final int totalRanges = sequenceSearch.getRanges().size();
                LOGGER.debug("Profile analyze fetched records, segmentId(s)={}, ranges={}, recordsCount={}",
                    sequenceSearch.getRanges().stream().map(SequenceRange::getSegmentId).distinct().collect(Collectors.toList()),
                    totalRanges, records.size());
            }

            // Filter Java records
            javaRecords = records.stream()
                .filter(rec -> rec.getLanguage() == ProfileLanguageType.JAVA)
                .collect(Collectors.toList());
        } else {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Profile analyze: no Java records found in time window, will try Go fallback");
            }
        }

        // Analyze Java profiles if found
        if (!javaRecords.isEmpty()) {
            LOGGER.info("Analyzing {} Java profile records", javaRecords.size());
            List<ProfileStack> stacks = javaRecords.stream()
                .map(rec -> {
                    try {
                        return ProfileStack.deserialize(rec);
                    } catch (Exception ex) {
                        LOGGER.warn("Deserialize stack failed, segmentId={}, sequence={}, dumpTime={}",
                            rec.getSegmentId(), rec.getSequence(), rec.getDumpTime(), ex);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

            final List<ProfileStackTree> trees = analyzeByStack(stacks);
            if (trees != null && !trees.isEmpty()) {
                analyzation.getTrees().addAll(trees);
                // Java analysis found data, return early
                return analyzation;
            }
        }

        // Step 2: Fallback to Go profile analysis if Java found no data
        // For Go profiles, ignore time window and fetch all records per segment
        List<ProfileThreadSnapshotRecord> goRecords = new ArrayList<>();
        for (SegmentProfileAnalyzeQuery q : queries) {
            final String segId = q.getSegmentId();
            try {
                int minSeq = getProfileThreadSnapshotQueryDAO().queryMinSequence(segId, 0L, Long.MAX_VALUE);
                int maxSeqExclusive = getProfileThreadSnapshotQueryDAO().queryMaxSequence(segId, 0L, Long.MAX_VALUE) + 1;
                if (maxSeqExclusive > minSeq) {
                    List<ProfileThreadSnapshotRecord> full = getProfileThreadSnapshotQueryDAO().queryRecords(segId, minSeq, maxSeqExclusive);
                    for (ProfileThreadSnapshotRecord r : full) {
                        if (r.getLanguage() == ProfileLanguageType.GO) {
                            goRecords.add(r);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Go fallback: full-range fetch failed for segmentId={}", segId, e);
            }
        }

        if (!goRecords.isEmpty()) {
            LOGGER.info("Java analysis found no data, fallback to Go: analyzing {} Go profile records", goRecords.size());
            GoProfileAnalyzer goAnalyzer = new GoProfileAnalyzer();
            ProfileAnalyzation goAnalyzation = goAnalyzer.analyzeRecords(goRecords, queries);
            if (goAnalyzation != null && !goAnalyzation.getTrees().isEmpty()) {
                analyzation.getTrees().addAll(goAnalyzation.getTrees());
            }
        } else if (sequenceSearch == null && javaRecords.isEmpty()) {
            // Both Java (time window) and Go (full range) found nothing
            analyzation.setTip("Data not found");
        }

        return analyzation;
    }

    protected SequenceSearch getAllSequenceRange(final List<SegmentProfileAnalyzeQuery> queries) {
        final List<SequenceSearch> searches = queries.parallelStream().map(r -> {
            try {
                return getAllSequenceRange(r.getSegmentId(), r.getTimeRange().getStart(), r.getTimeRange().getEnd());
            } catch (IOException e) {
                LOGGER.warn(e.getMessage(), e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        // using none parallels to combine nodes
        return searches.stream().reduce(new SequenceSearch(0), SequenceSearch::combine);
    }

    protected SequenceSearch getAllSequenceRange(String segmentId, long start, long end) throws IOException {
        // query min and max sequence(include last seqeucne)
        int minSequence = getProfileThreadSnapshotQueryDAO().queryMinSequence(segmentId, start, end);
        int maxSequence = getProfileThreadSnapshotQueryDAO().queryMaxSequence(segmentId, start, end) + 1;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Profile analyze sequence window: segmentId={}, start={}, end={}, minSeq={}, maxSeq(exclusive)={}",
                segmentId, start, end, minSequence, maxSequence);
        }

        // data not found
        if (maxSequence <= 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Profile analyze not found any sequence in window: segmentId={}, start={}, end={}",
                    segmentId, start, end);
            }
            return null;
        }

        SequenceSearch sequenceSearch = new SequenceSearch(maxSequence - minSequence);
        maxSequence = Math.min(maxSequence, minSequence + analyzeSnapshotMaxSize);

        do {
            int batchMax = Math.min(minSequence + threadSnapshotAnalyzeBatchSize, maxSequence);
            sequenceSearch.getRanges().add(new SequenceRange(segmentId, minSequence, batchMax));
            minSequence = batchMax;
        }
        while (minSequence < maxSequence);

        return sequenceSearch;
    }

    /**
     * Analyze records
     */
    protected List<ProfileStackTree> analyzeByStack(List<ProfileStack> stacks) {
        if (CollectionUtils.isEmpty(stacks)) {
            return null;
        }

        // using parallel stream
        Map<String, ProfileStackTree> stackTrees = stacks.parallelStream()
                                                         // stack list cannot be empty
                                                         .filter(s -> CollectionUtils.isNotEmpty(s.getStack()))
                                                         .collect(Collectors.groupingBy(s -> s.getStack()
                                                                                              .get(0), ANALYZE_COLLECTOR));

        return new ArrayList<>(stackTrees.values());
    }

    protected IProfileThreadSnapshotQueryDAO getProfileThreadSnapshotQueryDAO() {
        if (profileThreadSnapshotQueryDAO == null) {
            profileThreadSnapshotQueryDAO = moduleManager.find(StorageModule.NAME)
                                                         .provider()
                                                         .getService(IProfileThreadSnapshotQueryDAO.class);
        }
        return profileThreadSnapshotQueryDAO;
    }

    private static class SequenceSearch {
        private LinkedList<SequenceRange> ranges = new LinkedList<>();
        private int totalSequenceCount;

        public SequenceSearch(int totalSequenceCount) {
            this.totalSequenceCount = totalSequenceCount;
        }

        public LinkedList<SequenceRange> getRanges() {
            return ranges;
        }

        public int getTotalSequenceCount() {
            return totalSequenceCount;
        }

        public SequenceSearch combine(SequenceSearch search) {
            this.ranges.addAll(search.ranges);
            this.totalSequenceCount += search.totalSequenceCount;
            return this;
        }
    }

    private static class SequenceRange {
        private String segmentId;
        private int minSequence;
        private int maxSequence;

        public SequenceRange(String segmentId, int minSequence, int maxSequence) {
            this.segmentId = segmentId;
            this.minSequence = minSequence;
            this.maxSequence = maxSequence;
        }

        public String getSegmentId() {
            return segmentId;
        }

        public int getMinSequence() {
            return minSequence;
        }

        public int getMaxSequence() {
            return maxSequence;
        }

    }

}
