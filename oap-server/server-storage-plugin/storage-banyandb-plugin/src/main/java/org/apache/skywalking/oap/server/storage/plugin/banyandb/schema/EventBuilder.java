package org.apache.skywalking.oap.server.storage.plugin.banyandb.schema;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.Banyandb;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.model.Model;

import java.util.ArrayList;
import java.util.List;

public class EventBuilder extends BanyanDBMetricsBuilder<Event> {
    @Override
    protected List<SerializableTag<Banyandb.TagValue>> searchableTags(Event entity) {
        List<SerializableTag<Banyandb.TagValue>> searchable = new ArrayList<>(8);
        searchable.add(TagAndValue.stringField(entity.getUuid()));
        searchable.add(TagAndValue.stringField(entity.getService()));
        searchable.add(TagAndValue.stringField(entity.getServiceInstance()));
        searchable.add(TagAndValue.stringField(entity.getEndpoint()));
        searchable.add(TagAndValue.stringField(entity.getName()));
        searchable.add(TagAndValue.stringField(entity.getType()));
        searchable.add(TagAndValue.longField(entity.getStartTime()));
        searchable.add(TagAndValue.longField(entity.getEndTime()));
        return searchable;
    }

    @Override
    protected long timestamp(Model model, Event entity) {
        return TimeBucket.getTimestamp(entity.getTimeBucket(), model.getDownsampling());
    }

    @Override
    protected List<SerializableTag<Banyandb.TagValue>> dataTags(Event entity) {
        return ImmutableList.of(
                TagAndValue.stringField(entity.getMessage()),
                TagAndValue.stringField(entity.getParameters())
        );
    }
}
