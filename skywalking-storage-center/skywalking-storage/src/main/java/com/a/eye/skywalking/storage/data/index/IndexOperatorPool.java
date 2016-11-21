package com.a.eye.skywalking.storage.data.index;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class IndexOperatorPool extends GenericObjectPool<IndexOperator> {
    public IndexOperatorPool(GenericObjectPoolConfig poolConfig) {
        super(new IndexOperatorPooledObjectFactory(), poolConfig);
    }
}
