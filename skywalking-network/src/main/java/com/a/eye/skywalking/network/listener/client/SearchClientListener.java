package com.a.eye.skywalking.network.listener.client;

import com.a.eye.skywalking.network.grpc.SearchResult;

/**
 * Created by wusheng on 2016/12/3.
 */
public interface SearchClientListener {
    void onError(Throwable throwable);

    void onReturn(SearchResult result);

    void onFinished();
}
