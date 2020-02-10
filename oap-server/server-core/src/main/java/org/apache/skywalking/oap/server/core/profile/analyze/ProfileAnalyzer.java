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

import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackTree;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
    private IProfileThreadSnapshotQueryDAO profileThreadSnapshotQueryDAO;

    public ProfileAnalyzer(ModuleManager moduleManager, int snapshotAnalyzeBatchSize, int analyzeSnapshotMaxSize) {
        this.moduleManager = moduleManager;
        this.threadSnapshotAnalyzeBatchSize = snapshotAnalyzeBatchSize;
        this.analyzeSnapshotMaxSize = analyzeSnapshotMaxSize;
    }

    /**
     * search snapshots and analyze
     * @param segmentId
     * @param start
     * @param end
     * @return
     */
    public ProfileAnalyzation analyze(String segmentId, long start, long end) throws IOException {
        ProfileAnalyzation analyzation = new ProfileAnalyzation();

        // query sequence range list
        SequenceSearch sequenceSearch = getAllSequenceRange(segmentId, start, end);
        if (sequenceSearch == null) {
            analyzation.setTip("Data not found");
            return analyzation;
        }
        if (sequenceSearch.totalSequenceCount > analyzeSnapshotMaxSize) {
            analyzation.setTip("Out of snapshot analyze limit, current size:" + sequenceSearch.totalSequenceCount + ", only analyze snapshot count: " + analyzeSnapshotMaxSize);
        }

        // query snapshots
        List<ProfileStack> stacks = sequenceSearch.ranges.parallelStream().map(r -> {
            try {
                return getProfileThreadSnapshotQueryDAO().queryRecords(segmentId, r.minSequence, r.maxSequence);
            } catch (IOException e) {
                LOGGER.warn(e.getMessage(), e);
                return Collections.<ProfileThreadSnapshotRecord>emptyList();
            }
        }).flatMap(t -> t.stream()).map(ProfileStack::deserialize).collect(Collectors.toList());

        // analyze
        analyzation.setTrees(analyze(stacks));

        return analyzation;
    }

    private SequenceSearch getAllSequenceRange(String segmentId, long start, long end) throws IOException {
        // query min and max sequence
        int minSequence = getProfileThreadSnapshotQueryDAO().queryMinSequence(segmentId, start, end);
        int maxSequence = getProfileThreadSnapshotQueryDAO().queryMaxSequence(segmentId, start, end);

        // data not found
        if (maxSequence <= 0) {
            return null;
        }

        SequenceSearch sequenceSearch = new SequenceSearch();
        sequenceSearch.totalSequenceCount = maxSequence - minSequence;
        maxSequence = Math.min(maxSequence, minSequence + analyzeSnapshotMaxSize);

        do {
            int batchMax = Math.min(minSequence + threadSnapshotAnalyzeBatchSize, maxSequence);
            sequenceSearch.ranges.add(new SequenceRange(minSequence, batchMax));
            minSequence = batchMax + 1;
        } while (minSequence < maxSequence);

        // increase last range max sequence, need to include last sequence data
        sequenceSearch.ranges.getLast().maxSequence++;

        return sequenceSearch;
    }

    /**
     * Analyze records
     * @param stacks
     * @return
     */
    protected List<ProfileStackTree> analyze(List<ProfileStack> stacks) {
        if (CollectionUtils.isEmpty(stacks)) {
            return null;
        }

        // using parallel stream
        Map<String, ProfileStackTree> stackTrees = stacks.parallelStream()
                // stack list cannot be empty
                .filter(s -> CollectionUtils.isNotEmpty(s.getStack()))
                .collect(Collectors.groupingBy(s -> s.getStack().get(0), ANALYZE_COLLECTOR));

        return new ArrayList<>(stackTrees.values());
    }

    private IProfileThreadSnapshotQueryDAO getProfileThreadSnapshotQueryDAO() {
        if (profileThreadSnapshotQueryDAO == null) {
            profileThreadSnapshotQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IProfileThreadSnapshotQueryDAO.class);
        }
        return profileThreadSnapshotQueryDAO;
    }

    private static class SequenceSearch {
        LinkedList<SequenceRange> ranges = new LinkedList<>();
        int totalSequenceCount;
    }

    private static class SequenceRange {
        int minSequence;
        int maxSequence;

        public SequenceRange(int minSequence, int maxSequence) {
            this.minSequence = minSequence;
            this.maxSequence = maxSequence;
        }
    }
}
