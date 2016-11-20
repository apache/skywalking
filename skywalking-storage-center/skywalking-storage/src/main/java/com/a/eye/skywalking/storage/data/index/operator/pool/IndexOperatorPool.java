package com.a.eye.skywalking.storage.data.index.operator.pool;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.elasticsearch.client.transport.TransportClient;

public class IndexOperatorPool extends GenericObjectPool<TransportClient> {
    public IndexOperatorPool(GenericObjectPoolConfig poolConfig) {
        super(new IndexOperatorPooledObjectFactory(), poolConfig);
    }
}
