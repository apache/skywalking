package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.exception.IndexOperateFailedException;
import com.a.eye.skywalking.storage.data.index.operator.pool.IndexOperatorPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.elasticsearch.client.transport.TransportClient;

public class IndexOperateExecutor {

    private static IndexOperatorPool indexOperatorPool;

    public static <T> T execute(Executor<T> executor) {
        TransportClient client = null;
        try {
            client = indexOperatorPool.borrowObject();
            return executor.execute(new IndexOperatorImpl(client));
        } catch (Exception e) {
            throw new IndexOperateFailedException("Index operate failed.", e);
        } finally {
            indexOperatorPool.returnObject(client);
        }
    }

    static {
        initializeIndexOperatorPool();
    }

    private static void initializeIndexOperatorPool() {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(Config.IndexOperator.Finder.TOTAL);
        poolConfig.setMaxIdle(Config.IndexOperator.Finder.IDEL);
        poolConfig.setTestOnBorrow(true);
        indexOperatorPool = new IndexOperatorPool(poolConfig);
    }
}
