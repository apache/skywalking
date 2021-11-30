package org.apache.skywalking.oap.server.storage.plugin.banyandb.converter;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;

import java.util.Collections;
import java.util.List;

public class ProfileTaskMapper implements RowEntityMapper<ProfileTask> {
    @Override
    public List<String> searchableProjection() {
        return ImmutableList.of(ProfileTaskRecord.SERVICE_ID, ProfileTaskRecord.ENDPOINT_NAME, ProfileTaskRecord.START_TIME,
                ProfileTaskRecord.DURATION, ProfileTaskRecord.MIN_DURATION_THRESHOLD, ProfileTaskRecord.DUMP_PERIOD,
                ProfileTaskRecord.CREATE_TIME, ProfileTaskRecord.MAX_SAMPLING_COUNT);
    }

    @Override
    public List<String> dataProjection() {
        return Collections.emptyList();
    }

    @Override
    public ProfileTask map(RowEntity row) {
        return null;
    }
}
