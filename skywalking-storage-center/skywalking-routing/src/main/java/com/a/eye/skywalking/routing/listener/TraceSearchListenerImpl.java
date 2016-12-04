package com.a.eye.skywalking.routing.listener;

import com.a.eye.skywalking.network.grpc.QueryTask;
import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import com.a.eye.skywalking.network.listener.client.SearchClientListener;
import com.a.eye.skywalking.network.listener.server.TraceSearchListener;
import com.a.eye.skywalking.routing.client.StorageClientCachePool;
import com.a.eye.skywalking.routing.config.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Created by wusheng on 2016/12/3.
 */
public class TraceSearchListenerImpl implements TraceSearchListener {
    @Override
    public List<Span> search(TraceId traceId) {
        Collection<TraceSearchClient> clients = StorageClientCachePool.INSTANCE.getClients();
        CountDownLatch countDownLatch = new CountDownLatch(clients.size());
        ConcurrentHashMap<SearchResult, Boolean> results = new ConcurrentHashMap<>();
        for (TraceSearchClient client : clients) {
            client.search(QueryTask.newBuilder().setTraceId(traceId).build(), new SearchClientListener(){

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onReturn(SearchResult searchResult) {
                    results.put(searchResult, true);
                    countDownLatch.countDown();
                }

                @Override
                public void onFinished() {

                }
            });
        }

        long waitTime = 0;
        while(countDownLatch.getCount() != 0){
            try {
                Thread.sleep(Config.Search.CHECK_CYCLE);
            } catch (InterruptedException e) {
            }
            waitTime += Config.Search.CHECK_CYCLE;

            if(waitTime > Config.Search.TIMEOUT){
                break;
            }
        }

        List<Span> spans = new ArrayList<>(30);
        for (SearchResult searchResult : results.keySet()) {
            List<Span> result = searchResult.getSpansList();
            if(result != null && result.size() > 0) {
                spans.addAll(result);
            }
        }

        return spans;
    }
}
