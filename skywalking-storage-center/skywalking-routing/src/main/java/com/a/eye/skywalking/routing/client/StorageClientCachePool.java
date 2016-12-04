package com.a.eye.skywalking.routing.client;

import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by wusheng on 2016/12/3.
 */
public class StorageClientCachePool {
    public static StorageClientCachePool INSTANCE = new StorageClientCachePool();

    private Map<String, Client>            pool                  = new ConcurrentHashMap<>();
    private Map<String, SpanStorageClient> spanStorageClientPool = new ConcurrentHashMap<>();
    private Map<String, TraceSearchClient> traceSearchClientPool = new ConcurrentHashMap<>();
    private ReentrantLock                  lock                  = new ReentrantLock();

    private StorageClientCachePool() {
    }

    public Collection<TraceSearchClient> getClients() {
        return traceSearchClientPool.values();
    }

    public SpanStorageClient getSpanStorageClient(String connectionURL, StorageClientListener listener) {
        SpanStorageClient spanStorageClient = spanStorageClientPool.get(connectionURL);
        if (spanStorageClient != null) {
            return spanStorageClient;
        }

        lock.lock();
        try {
            spanStorageClient = spanStorageClientPool.get(connectionURL);
            if (spanStorageClient == null) {
                String[] urlSegment = connectionURL.split(":");
                if (urlSegment.length != 2) {
                    throw new IllegalArgumentException();
                }
                Client client = new Client(urlSegment[0], Integer.valueOf(urlSegment[1]));
                pool.put(connectionURL, client);
                spanStorageClientPool.put(connectionURL, client.newSpanStorageClient(listener));
                traceSearchClientPool.put(connectionURL, client.newTraceSearchClient());
            }
            return spanStorageClient;
        } finally {
            lock.unlock();
        }
    }

    public void shutdown(String connectionURL) {
        lock.lock();
        try {
            if (pool.containsKey(connectionURL)) {
                Client client = pool.remove(connectionURL);
                client.shutdown();
                spanStorageClientPool.remove(connectionURL);
                traceSearchClientPool.remove(connectionURL);
            }
        } finally {
            lock.unlock();
        }
    }
}
