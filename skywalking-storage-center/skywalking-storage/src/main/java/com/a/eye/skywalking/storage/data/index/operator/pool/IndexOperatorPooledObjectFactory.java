package com.a.eye.skywalking.storage.data.index.operator.pool;

import com.a.eye.skywalking.storage.config.Config;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;

public class IndexOperatorPooledObjectFactory extends BasePooledObjectFactory<TransportClient> {

    @Override
    public TransportClient create() throws Exception {
        return new PreBuiltTransportClient(Settings.EMPTY).addTransportAddress(
                        new InetSocketTransportAddress(InetAddress.getLocalHost(), Config.DataIndex.INDEX_LISTEN_PORT));
    }

    @Override
    public PooledObject<TransportClient> wrap(org.elasticsearch.client.transport.TransportClient client) {
        return new DefaultPooledObject<>(client);
    }

    @Override
    public void destroyObject(PooledObject<TransportClient> p) throws Exception {
        super.destroyObject(p);
    }
}
