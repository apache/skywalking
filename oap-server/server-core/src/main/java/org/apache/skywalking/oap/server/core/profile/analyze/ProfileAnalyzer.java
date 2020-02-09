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

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyze {@link ProfileStack} data to {@link ProfileAnalyzation}
 *
 * See: https://github.com/apache/skywalking/blob/421ba88dbfba48cdc5845547381aa4763775b4b1/docs/en/guides/backend-profile.md#thread-analyst
 */
public class ProfileAnalyzer {

    private static final ProfileAnalyzeCollector ANALYZE_COLLECTOR = new ProfileAnalyzeCollector();

    private static final int THREAD_SNAPSHOT_ANALYZE_BATCH_SIZE = 100;

    private final ModuleManager moduleManager;
    private IProfileThreadSnapshotQueryDAO profileThreadSnapshotQueryDAO;

    public ProfileAnalyzer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    /**
     * search snapshots and analyze
     * @param segmentId
     * @param start
     * @param end
     * @return
     */
    public ProfileAnalyzation analyze(String segmentId, long start, long end) throws IOException {
        LinkedList<ProfileStack> snapshots = new LinkedList<>();

        // paging by min sequence
        int minSequence = 0;
        List<ProfileThreadSnapshotRecord> record = null;
        do {
            record = getProfileThreadSnapshotQueryDAO().queryRecordsWithPaging(segmentId, start, end, minSequence, THREAD_SNAPSHOT_ANALYZE_BATCH_SIZE);
            if (CollectionUtils.isNotEmpty(record)) {
                snapshots.addAll(record.stream().map(ProfileStack::deserialize).collect(Collectors.toList()));
                minSequence = record.get(record.size() - 1).getSequence() + 1;
            }
        } while (CollectionUtils.isNotEmpty(record));

        return analyze(snapshots);
    }

    /**
     * Analyze records
     * @param stacks
     * @return
     */
    protected ProfileAnalyzation analyze(List<ProfileStack> stacks) {
        if (CollectionUtils.isEmpty(stacks)) {
            return null;
        }

        // using parallel stream
        Map<String, ProfileStackTree> stackTrees = stacks.parallelStream()
                // stack list cannot be empty
                .filter(s -> CollectionUtils.isNotEmpty(s.getStack()))
                .collect(Collectors.groupingBy(s -> s.getStack().get(0), ANALYZE_COLLECTOR));

        ProfileAnalyzation analyzer = new ProfileAnalyzation();
        analyzer.setTrees(new ArrayList<>(stackTrees.values()));
        return analyzer;
    }

    private IProfileThreadSnapshotQueryDAO getProfileThreadSnapshotQueryDAO() {
        if (profileThreadSnapshotQueryDAO == null) {
            profileThreadSnapshotQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IProfileThreadSnapshotQueryDAO.class);
        }
        return profileThreadSnapshotQueryDAO;
    }
}
