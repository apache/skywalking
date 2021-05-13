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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadStack;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ProfileSnapshotDumper {

    public static final int QUERY_PROFILE_SNAPSHOT_RETRY_COUNT = 3;
    public static final int QUERY_PROFILE_WRITE_PROCESS_LOG = 3;

    /**
     * dump snapshots to file
     */
    public static File dump(ProfiledBasicInfo basicInfo, ModuleManager manager) throws IOException {
        IProfileThreadSnapshotQueryDAO snapshotQueryDAO = manager.find(StorageModule.NAME).provider().getService(IProfileThreadSnapshotQueryDAO.class);
        List<ProfiledBasicInfo.SequenceRange> sequenceRanges = basicInfo.buildSequenceRanges();
        int rangeCount = sequenceRanges.size();

        String segmentId = basicInfo.getSegmentId();
        File snapshotFile = new File(basicInfo.getConfig().getAnalyzeResultDist() + File.separator + "snapshot.data");

        // reading data and write to file
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(snapshotFile))) {
            for (int i = 0; i < rangeCount; i++) {
                List<ProfileThreadSnapshotRecord> records = querySnapshot(segmentId, snapshotQueryDAO, sequenceRanges.get(i));
                for (ProfileThreadSnapshotRecord record : records) {
                    // transform to proto data and save it
                    ThreadSnapshot.newBuilder()
                            .setStack(ThreadStack.parseFrom(record.getStackBinary()))
                            .setSequence(record.getSequence())
                            .setTime(record.getDumpTime())
                            .build()
                            .writeDelimitedTo(outputStream);
                }

                // print process log if need
                if ((i > 0 && i % QUERY_PROFILE_WRITE_PROCESS_LOG == 0) || i == rangeCount - 1) {
                    log.info("Dump snapshots process:[{}/{}]:{}%", i + 1, rangeCount, (int) ((double) (i + 1) / rangeCount * 100));
                }
            }
        }

        return snapshotFile;
    }

    /**
     * query snapshots with retry mechanism
     */
    private static List<ProfileThreadSnapshotRecord> querySnapshot(String segmentId, IProfileThreadSnapshotQueryDAO threadSnapshotQueryDAO, ProfiledBasicInfo.SequenceRange sequenceRange) throws IOException {
        for (int i = 1; i <= QUERY_PROFILE_SNAPSHOT_RETRY_COUNT; i++) {
            try {
                return threadSnapshotQueryDAO.queryRecords(segmentId, sequenceRange.getMin(), sequenceRange.getMax());
            } catch (IOException e) {
                if (i == QUERY_PROFILE_SNAPSHOT_RETRY_COUNT) {
                    throw e;
                }
            }
        }
        return null;
    }

    /**
     * load thread snapshots in appointing time range
     */
    public static List<ThreadSnapshot> parseFromFileWithTimeRange(File file, List<ProfileAnalyzeTimeRange> timeRanges) throws IOException {
        try (final FileInputStream fileInputStream = new FileInputStream(file)) {
            ThreadSnapshot snapshot;
            final ArrayList<ThreadSnapshot> data = new ArrayList<>();
            while ((snapshot = ThreadSnapshot.parseDelimitedFrom(fileInputStream)) != null) {
                ThreadSnapshot finalSnapshot = snapshot;
                if (timeRanges.stream().filter(t -> finalSnapshot.getTime() >= t.getStart() && finalSnapshot.getTime() <= t.getEnd()).findFirst().isPresent()) {
                    data.add(snapshot);
                }
            }
            return data;
        }
    }
}
