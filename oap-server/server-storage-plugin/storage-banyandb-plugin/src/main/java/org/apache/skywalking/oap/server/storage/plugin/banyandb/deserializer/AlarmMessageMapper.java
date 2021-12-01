package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;

import java.util.List;

@RequiredArgsConstructor
public class AlarmMessageMapper implements RowEntityMapper<AlarmMessage> {
    private final IAlarmQueryDAO alarmQueryDAO;

    @Override
    public List<String> searchableProjection() {
        return ImmutableList.of(AlarmRecord.SCOPE, // 0
                AlarmRecord.START_TIME); // 1
    }

    @Override
    public List<String> dataProjection() {
        return ImmutableList.of(AlarmRecord.ID0, // 0
                AlarmRecord.ID1, // 1
                AlarmRecord.ALARM_MESSAGE, // 2
                AlarmRecord.TAGS_RAW_DATA); // 3
    }

    @Override
    public AlarmMessage map(RowEntity row) {
        AlarmMessage alarmMessage = new AlarmMessage();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        int scopeID = ((Number) searchable.get(0).getValue()).intValue();
        alarmMessage.setScopeId(scopeID);
        alarmMessage.setScope(Scope.Finder.valueOf(scopeID));
        alarmMessage.setStartTime(((Number) searchable.get(1).getValue()).longValue());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        alarmMessage.setId((String) data.get(0).getValue());
        alarmMessage.setId1((String) data.get(1).getValue());
        alarmMessage.setMessage((String) data.get(2).getValue());
        Object o = data.get(3).getValue();
        if (o instanceof ByteString && !((ByteString) o).isEmpty()) {
            this.alarmQueryDAO.parserDataBinary(((ByteString) o).toByteArray(), alarmMessage.getTags());
        }
        return alarmMessage;
    }
}
