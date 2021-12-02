package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;

import java.util.Collections;
import java.util.List;

public class ProfileThreadSnapshotRecordMapper extends AbstractBanyanDBDeserializer<ProfileThreadSnapshotRecord> {
    public ProfileThreadSnapshotRecordMapper() {
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
