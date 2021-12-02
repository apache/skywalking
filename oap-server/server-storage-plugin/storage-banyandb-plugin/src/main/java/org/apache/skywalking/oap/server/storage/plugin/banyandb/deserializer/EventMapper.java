package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.Event;

import java.util.List;

public class EventMapper extends AbstractBanyanDBDeserializer<org.apache.skywalking.oap.server.core.query.type.event.Event> {
    public EventMapper() {
        super(Event.INDEX_NAME,
                ImmutableList.of(Event.UUID, Event.SERVICE, Event.SERVICE_INSTANCE, Event.ENDPOINT, Event.NAME,
                        Event.TYPE, Event.START_TIME, Event.END_TIME),
                ImmutableList.of(Event.MESSAGE, Event.PARAMETERS));
    }

    @Override
    public org.apache.skywalking.oap.server.core.query.type.event.Event map(RowEntity row) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event resultEvent = new org.apache.skywalking.oap.server.core.query.type.event.Event();
        // searchable
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        resultEvent.setUuid((String) searchable.get(0).getValue());
        resultEvent.setSource(new Source((String) searchable.get(1).getValue(), (String) searchable.get(2).getValue(), (String) searchable.get(3).getValue()));
        resultEvent.setName((String) searchable.get(4).getValue());
        resultEvent.setType(EventType.parse((String) searchable.get(5).getValue()));
        resultEvent.setStartTime(((Number) searchable.get(6).getValue()).longValue());
        resultEvent.setEndTime(((Number) searchable.get(7).getValue()).longValue());
        // data
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        resultEvent.setMessage((String) data.get(0).getValue());
        resultEvent.setParameters((String) data.get(1).getValue());
        return resultEvent;
    }
}
