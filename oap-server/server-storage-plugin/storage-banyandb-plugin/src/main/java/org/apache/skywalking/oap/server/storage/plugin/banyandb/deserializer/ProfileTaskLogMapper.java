package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;

import java.util.List;

public class ProfileTaskLogMapper extends AbstractBanyanDBDeserializer<ProfileTaskLog> {
    public ProfileTaskLogMapper() {
        super(ProfileTaskLogRecord.INDEX_NAME,
                ImmutableList.of(ProfileTaskLogRecord.OPERATION_TIME),
                ImmutableList.of(ProfileTaskLogRecord.TASK_ID, ProfileTaskLogRecord.INSTANCE_ID,
                        ProfileTaskLogRecord.OPERATION_TYPE));
    }

    @Override
    public ProfileTaskLog map(RowEntity row) {
        ProfileTaskLog profileTaskLog = new ProfileTaskLog();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        // searchable - operation_time
        profileTaskLog.setOperationTime(((Number) searchable.get(0).getValue()).longValue());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        // searchable - task_id
        profileTaskLog.setTaskId((String) data.get(0).getValue());
        // searchable - instance_id
        profileTaskLog.setInstanceId((String) data.get(1).getValue());
        // searchable - operation_type
        profileTaskLog.setOperationType(ProfileTaskLogOperationType.parse(((Number) data.get(2).getValue()).intValue()));
        return profileTaskLog;
    }
}
