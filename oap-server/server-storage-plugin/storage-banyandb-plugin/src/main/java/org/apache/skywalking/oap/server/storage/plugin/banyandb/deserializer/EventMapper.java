package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.oap.server.core.query.type.event.Event;

import java.util.List;

public class EventMapper implements RowEntityMapper<Event> {
    @Override
    public List<String> searchableProjection() {
        return ImmutableList.of();
    }

    @Override
    public List<String> dataProjection() {
        return null;
    }

    @Override
    public Event map(RowEntity row) {
        return null;
    }
}
