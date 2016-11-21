package com.a.eye.skywalking.storage.data.index;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

public class IndexOperatorPooledObjectFactory extends BasePooledObjectFactory<IndexOperator> {

    @Override
    public IndexOperator create() throws Exception {
        return IndexOperatorFactory.createIndexOperator();
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
