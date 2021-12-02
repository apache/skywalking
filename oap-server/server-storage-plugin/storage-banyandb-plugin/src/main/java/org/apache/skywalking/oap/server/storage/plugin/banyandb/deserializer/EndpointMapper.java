package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;

import java.util.List;

public class EndpointMapper extends AbstractBanyanDBDeserializer<Endpoint> {
    public EndpointMapper() {
        super(EndpointTraffic.INDEX_NAME,
                ImmutableList.of(EndpointTraffic.NAME, EndpointTraffic.SERVICE_ID));
    }

    @Override
    public Endpoint map(RowEntity row) {
        Endpoint endpoint = new Endpoint();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        endpoint.setName((String) searchable.get(0).getValue());
        endpoint.setId((String) searchable.get(1).getValue());
        return endpoint;
    }
}
