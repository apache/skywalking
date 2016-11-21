package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.exception.IndexOperatorBorrowFailedException;
import com.a.eye.skywalking.storage.data.exception.IndexOperatorInitializeFailedException;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;

public class OperatorFactory {

    private static IndexOperatorPool pool;

    public static IndexOperator createIndexOperator() {
        try {
            return new IndexOperator(new PreBuiltTransportClient(Settings.EMPTY).addTransportAddress(
                    new InetSocketTransportAddress(InetAddress.getLocalHost(), Config.DataIndex.INDEX_LISTEN_PORT)));
        } catch (Exception e) {
            throw new IndexOperatorInitializeFailedException("Failed to initialize operator.", e);
        }
    }

    public static IndexOperator getIndexOperatorFromPool() {
        try {
            return pool.borrowObject();
        } catch (Exception e) {
            throw new IndexOperatorBorrowFailedException();
        }
    }

    public static void returnIndexOperator(IndexOperator indexOperator) {
        pool.returnObject(indexOperator);
    }

    public static void initOperatorPool() {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(Config.IndexOperator.Finder.TOTAL);
        config.setMaxIdle(Config.IndexOperator.Finder.IDEL);
        pool = new IndexOperatorPool(config);
    }

}
