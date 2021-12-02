package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.type.Database;

import java.util.List;

public class DatabaseMapper extends AbstractBanyanDBDeserializer<Database> {
    public DatabaseMapper() {
        super(ServiceTraffic.INDEX_NAME,
                ImmutableList.of(ServiceTraffic.NAME, ServiceTraffic.NODE_TYPE));
    }

    @Override
    public Database map(RowEntity row) {
        Database database = new Database();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        database.setId(row.getId());
        database.setName((String) searchable.get(0).getValue());
        return database;
    }
}
