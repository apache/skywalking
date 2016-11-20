package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.exception.IndexOperatorInitializeFailedException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;

public class OperatorFactory {

    public static IndexOperator createIndexOperator() {
        try {
            return new IndexOperatorImpl(new PreBuiltTransportClient(Settings.EMPTY).addTransportAddress(
                    new InetSocketTransportAddress(InetAddress.getLocalHost(), Config.DataIndex.INDEX_LISTEN_PORT)));
        } catch (Exception e) {
            throw new IndexOperatorInitializeFailedException("Failed to initialize operator.", e);
        }
    }

}
