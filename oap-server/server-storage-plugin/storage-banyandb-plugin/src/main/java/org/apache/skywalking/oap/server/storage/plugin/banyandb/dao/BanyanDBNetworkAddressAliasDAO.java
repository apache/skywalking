package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;

import java.util.Collections;
import java.util.List;

public class BanyanDBNetworkAddressAliasDAO implements INetworkAddressAliasDAO {
    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long timeBucket) {
        return Collections.emptyList();
    }
}
