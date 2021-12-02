package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.util.List;

/**
 * {@link NetworkAddressAlias} is a stream
 */
public class BanyanDBNetworkAddressAliasDAO extends AbstractBanyanDBDAO implements INetworkAddressAliasDAO {
    public BanyanDBNetworkAddressAliasDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long timeBucket) {
        return query(NetworkAddressAlias.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET, timeBucket));
            }
        });
    }
}
