package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;

import java.util.List;

/**
 * ???
 * {@link org.apache.skywalking.oap.server.core.source.Event} is a stream
 */
public class BanyanDBEventQueryDAO implements IEventQueryDAO {
    @Override
    public Events queryEvents(EventQueryCondition condition) throws Exception {
        return new Events();
    }

    @Override
    public Events queryEvents(List<EventQueryCondition> conditionList) throws Exception {
        return new Events();
    }
}
