package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.AbstractBanyanDBDeserializer;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.BanyanDBDeserializerFactory;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractBanyanDBDAO extends AbstractDAO<BanyanDBStorageClient> {
    protected AbstractBanyanDBDAO(BanyanDBStorageClient client) {
        super(client);
    }

    protected <T> List<T> query(Class<T> clazz, QueryBuilder builder) {
        return this.query(clazz, builder, 0, 0);
    }

    protected <T> List<T> query(Class<T> clazz, QueryBuilder builder, long startTimestamp, long endTimestamp) {
        AbstractBanyanDBDeserializer<T> deserializer = BanyanDBDeserializerFactory.INSTANCE.findDeserializer(clazz);

        final StreamQuery query;
        if (startTimestamp != 0 && endTimestamp != 0) {
            query = deserializer.buildStreamQuery();
        } else {
            query = deserializer.buildStreamQuery(startTimestamp, endTimestamp);
        }

        builder.apply(query);

        final StreamQueryResponse resp = getClient().query(query);
        return resp.getElements().stream().map(deserializer::map).collect(Collectors.toList());
    }


    interface QueryBuilder {
        void apply(final StreamQuery query);
    }
}
