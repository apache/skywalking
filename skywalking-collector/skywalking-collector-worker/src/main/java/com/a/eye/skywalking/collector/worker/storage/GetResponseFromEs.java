package com.a.eye.skywalking.collector.worker.storage;

import org.elasticsearch.action.get.GetResponse;

/**
 * @author pengys5
 */
public enum GetResponseFromEs {
    INSTANCE;

    public GetResponse get(String index, String type, String id) {
        return EsClient.INSTANCE.getClient().prepareGet(index, type, id).get();
    }
}
