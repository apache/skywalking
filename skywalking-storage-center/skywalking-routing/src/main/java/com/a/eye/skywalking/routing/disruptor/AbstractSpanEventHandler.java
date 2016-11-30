package com.a.eye.skywalking.routing.disruptor;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;
import com.lmax.disruptor.EventHandler;

/**
 * Created by xin on 2016/11/29.
 */
public abstract class AbstractSpanEventHandler<T> implements EventHandler<T> {
    protected Client client;
    protected int bufferSize = 100;
    protected boolean stop;
    protected volatile boolean previousSendResult = true;

    public AbstractSpanEventHandler(String connectionURL) {
        String[] urlSegment = connectionURL.split(":");
        if (urlSegment.length != 2) {
            throw new IllegalArgumentException();
        }
        client = new Client(urlSegment[0], Integer.valueOf(urlSegment[1]));
    }

    protected SpanStorageClient getStorageClient() {
        SpanStorageClient spanStorageClient = client.newSpanStorageClient(new StorageClientListener() {
            @Override
            public void onError(Throwable throwable) {
                HealthCollector.getCurrentHeathReading(getExtraId()).updateData(HeathReading.ERROR,
                        "Failed to send  span. error message :" + throwable.getMessage());
            }

            @Override
            public void onBatchFinished() {
                previousSendResult = true;
                HealthCollector.getCurrentHeathReading(getExtraId()).updateData(HeathReading.INFO,
                        " consumed Successfully");
            }
        });
        previousSendResult = false;
        return spanStorageClient;
    }

    public abstract String getExtraId();


    public void stop() {
        stop = true;
    }
}
