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

package org.apache.skywalking.oap.server.core.profile.analyze;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackTree;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
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
    public ProfileAnalyzation analyze(String segmentId, List<ProfileAnalyzeTimeRange> timeRanges) throws IOException {
        ProfileAnalyzation analyzation = new ProfileAnalyzation();

        // query sequence range list
        SequenceSearch sequenceSearch = getAllSequenceRange(segmentId, timeRanges);
        if (sequenceSearch == null) {
            analyzation.setTip("Data not found");
            return analyzation;
        }
        if (sequenceSearch.getTotalSequenceCount() > analyzeSnapshotMaxSize) {
            analyzation.setTip("Out of snapshot analyze limit, " + sequenceSearch.getTotalSequenceCount() + " snapshots found, but analysis first " + analyzeSnapshotMaxSize + " snapshots only.");
        }

        // query snapshots
        List<ProfileStack> stacks = sequenceSearch.getRanges().parallelStream().map(r -> {
            try {
                return getProfileThreadSnapshotQueryDAO().queryRecords(segmentId, r.getMinSequence(), r.getMaxSequence());
            } catch (IOException e) {
                LOGGER.warn(e.getMessage(), e);
                return Collections.<ProfileThreadSnapshotRecord>emptyList();
            }
        }).flatMap(Collection::stream).map(ProfileStack::deserialize).distinct().collect(Collectors.toList());

        // analyze
        final List<ProfileStackTree> trees = analyze(stacks);
        if (trees != null) {
            analyzation.getTrees().addAll(trees);
        }

        return analyzation;
    }

    protected SequenceSearch getAllSequenceRange(String segmentId, List<ProfileAnalyzeTimeRange> timeRanges) throws IOException {
        final List<SequenceSearch> searches = timeRanges.parallelStream().map(r -> {
            try {
                return getAllSequenceRange(segmentId, r.getStart(), r.getEnd());
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

        // data not found
        if (maxSequence <= 0) {
            return null;
        }

        SequenceSearch sequenceSearch = new SequenceSearch(maxSequence - minSequence);
        maxSequence = Math.min(maxSequence, minSequence + analyzeSnapshotMaxSize);

        do {
            int batchMax = Math.min(minSequence + threadSnapshotAnalyzeBatchSize, maxSequence);
            sequenceSearch.getRanges().add(new SequenceRange(minSequence, batchMax));
            minSequence = batchMax;
        }
        while (minSequence < maxSequence);

        return sequenceSearch;
    }

    /**
     * Analyze records
     */
    protected List<ProfileStackTree> analyze(List<ProfileStack> stacks) {
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
        private int minSequence;
        private int maxSequence;

        public SequenceRange(int minSequence, int maxSequence) {
            this.minSequence = minSequence;
            this.maxSequence = maxSequence;
        }

        public int getMinSequence() {
            return minSequence;
        }

        public int getMaxSequence() {
            return maxSequence;
        }

    }
}
