package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.NetworkAddressAliasMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link NetworkAddressAlias} is a stream
 */
public class BanyanDBNetworkAddressAliasDAO extends AbstractDAO<BanyanDBStorageClient> implements INetworkAddressAliasDAO {
    private static final RowEntityMapper<NetworkAddressAlias> MAPPER = new NetworkAddressAliasMapper();

    public BanyanDBNetworkAddressAliasDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long timeBucket) {
        StreamQuery query = new StreamQuery(NetworkAddressAlias.INDEX_NAME, MAPPER.searchableProjection());
        query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET, timeBucket));

        StreamQueryResponse resp = getClient().query(query);
        return resp.getElements().stream().map(MAPPER::map).collect(Collectors.toList());
    }
}
