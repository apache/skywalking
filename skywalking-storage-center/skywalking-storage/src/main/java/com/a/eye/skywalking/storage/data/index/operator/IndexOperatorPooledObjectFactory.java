package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.config.Config;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;

public class IndexOperatorPooledObjectFactory extends BasePooledObjectFactory<IndexOperator> {

    @Override
    public IndexOperator create() throws Exception {
        return OperatorFactory.createIndexOperator();
    }

    @Override
    public PooledObject<IndexOperator> wrap(IndexOperator client) {
        return new DefaultPooledObject<>(client);
    }

    @Override
    public void destroyObject(PooledObject<IndexOperator> p) throws Exception {
        p.getObject().close();
    }
}
