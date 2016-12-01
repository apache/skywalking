package com.a.eye.skywalking.network.listener.client;

/**
 * Created by wusheng on 2016/11/27.
 */
public interface StorageClientListener {
    void onError(Throwable throwable);

    void onBatchFinished();
}
