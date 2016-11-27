package com.a.eye.skywalking.network.listener.client;

import com.a.eye.skywalking.network.grpc.SendResult;

/**
 * Created by wusheng on 2016/11/27.
 */
public interface StorageClientListener {
    void onError(Throwable throwable);

    void onBatchFinished(SendResult sendResult);
}
