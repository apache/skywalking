package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;

import java.util.List;

public class NetworkAddressAliasMapper extends AbstractBanyanDBDeserializer<NetworkAddressAlias> {
    public NetworkAddressAliasMapper() {
        super(NetworkAddressAlias.INDEX_NAME,
                ImmutableList.of(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET),
                ImmutableList.of(Metrics.TIME_BUCKET, "address", "represent_service_id", "represent_service_instance_id"));
    }

    @Override
    public NetworkAddressAlias map(RowEntity row) {
        NetworkAddressAlias model = new NetworkAddressAlias();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        // searchable - last_update_time_bucket
        model.setLastUpdateTimeBucket(((Number) searchable.get(0).getValue()).longValue());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        // data - time_bucket
        model.setTimeBucket(((Number) data.get(0).getValue()).longValue());
        // data - address
        model.setAddress((String) data.get(1).getValue());
        // data - represent_service_id
        model.setRepresentServiceId((String) data.get(2).getValue());
        // data - represent_service_instance_id
        model.setRepresentServiceInstanceId((String) data.get(3).getValue());
        return model;
    }
}
