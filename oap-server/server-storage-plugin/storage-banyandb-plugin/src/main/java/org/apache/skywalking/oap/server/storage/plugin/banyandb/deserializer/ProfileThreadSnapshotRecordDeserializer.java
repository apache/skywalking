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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;

import java.util.Collections;
import java.util.List;

public class ProfileThreadSnapshotRecordDeserializer extends AbstractBanyanDBDeserializer<ProfileThreadSnapshotRecord> {
    public ProfileThreadSnapshotRecordDeserializer() {
        super(ProfileThreadSnapshotRecord.INDEX_NAME,
                ImmutableList.of(ProfileThreadSnapshotRecord.TASK_ID, ProfileThreadSnapshotRecord.SEGMENT_ID,
                        ProfileThreadSnapshotRecord.DUMP_TIME, ProfileThreadSnapshotRecord.SEQUENCE),
                Collections.singletonList(ProfileThreadSnapshotRecord.STACK_BINARY));
    }

    @Override
    public ProfileThreadSnapshotRecord map(RowEntity row) {
        ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        record.setTaskId((String) searchable.get(0).getValue());
        record.setSegmentId((String) searchable.get(1).getValue());
        record.setDumpTime(((Number) searchable.get(2).getValue()).longValue());
        record.setSequence(((Number) searchable.get(3).getValue()).intValue());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        record.setStackBinary(((ByteString) data.get(0).getValue()).toByteArray());
        return record;
    }
}
