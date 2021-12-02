package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.ByteString;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;

import java.util.List;

public class AlarmMessageMapper extends AbstractBanyanDBDeserializer<AlarmMessage> {
    private final Gson GSON = new Gson();

    public AlarmMessageMapper() {
        super(AlarmRecord.INDEX_NAME,
                ImmutableList.of(AlarmRecord.SCOPE, AlarmRecord.START_TIME),
                ImmutableList.of(AlarmRecord.ID0, AlarmRecord.ID1, AlarmRecord.ALARM_MESSAGE, AlarmRecord.TAGS_RAW_DATA));
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
            this.parseDataBinary(((ByteString) o).toByteArray(), alarmMessage.getTags());
        }
        return alarmMessage;
    }

    void parseDataBinary(byte[] dataBinary, List<KeyValue> tags) {
        List<Tag> tagList = GSON.fromJson(new String(dataBinary, Charsets.UTF_8), new TypeToken<List<Tag>>() {
        }.getType());
        tagList.forEach(pair -> tags.add(new KeyValue(pair.getKey(), pair.getValue())));
    }
}
