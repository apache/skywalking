package com.a.eye.skywalking.routing.disruptor;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.client.SpanStorageClient;
import com.a.eye.skywalking.network.listener.client.StorageClientListener;
import com.a.eye.skywalking.routing.config.Config;
import com.lmax.disruptor.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/11/29.
 */
public abstract class AbstractRouteSpanEventHandler<T> implements EventHandler<T> {
    protected       Client  client;
    protected final int     bufferSize;
    protected       boolean stop;
    private volatile boolean previousSendFinish = true;

    public AbstractRouteSpanEventHandler(String connectionURL) {
        bufferSize = Config.Disruptor.FLUSH_SIZE;
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
                previousSendFinish = true;
                HealthCollector.getCurrentHeathReading(getExtraId()).updateData(HeathReading.ERROR, "Failed to send  span. error message :" + throwable.getMessage());
            }

            @Override
            public void onBatchFinished() {
                previousSendFinish = true;
                HealthCollector.getCurrentHeathReading(getExtraId()).updateData(HeathReading.INFO, " consumed Successfully");
            }
        });
        previousSendFinish = false;
        return spanStorageClient;
    }

    public abstract String getExtraId();


    public void stop() {
        stop = true;
    }

    public void wait2Finish() {
        // wait 20s, most
        int countDown = 1000 * 20;
        while (!previousSendFinish) {
            try {
                Thread.sleep(1L);
                if(countDown-- < 0){
                    previousSendFinish = true;
                }
            } catch (InterruptedException e) {
            }
        }
    }
}
