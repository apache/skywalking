package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.type.Service;

import java.util.List;

public class ServiceMapper extends AbstractBanyanDBDeserializer<Service> {
    public ServiceMapper() {
        super(ServiceTraffic.INDEX_NAME,
                ImmutableList.of(ServiceTraffic.NAME, ServiceTraffic.NODE_TYPE, ServiceTraffic.GROUP));
    }

    @Override
    public Service map(RowEntity row) {
        Service service = new Service();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        service.setId(row.getId());
        service.setName((String) searchable.get(0).getValue());
        service.setGroup((String) searchable.get(2).getValue());
        return service;
    }
}
