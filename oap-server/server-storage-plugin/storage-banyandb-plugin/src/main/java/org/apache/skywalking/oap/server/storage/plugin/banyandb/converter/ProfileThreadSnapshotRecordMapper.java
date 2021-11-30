package org.apache.skywalking.oap.server.storage.plugin.banyandb.converter;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;

import java.util.Collections;
import java.util.List;

public class ProfileThreadSnapshotRecordMapper implements RowEntityMapper<ProfileThreadSnapshotRecord> {
    @Override
    public List<String> searchableProjection() {
        return ImmutableList.of(ProfileThreadSnapshotRecord.TASK_ID, ProfileThreadSnapshotRecord.SEGMENT_ID,
                ProfileThreadSnapshotRecord.DUMP_TIME, ProfileThreadSnapshotRecord.SEQUENCE,
                ProfileThreadSnapshotRecord.STACK_BINARY);
    }

    @Override
    public List<String> dataProjection() {
        return Collections.emptyList();
    }

    @Override
    public ProfileThreadSnapshotRecord map(RowEntity row) {
        return null;
    }
}
