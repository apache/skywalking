/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.exporter.provider.grpc;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.MultiIntValuesHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.exporter.ExportData;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.exporter.MetricValuesExportService;
import org.apache.skywalking.oap.server.exporter.grpc.EventType;
import org.apache.skywalking.oap.server.exporter.grpc.ExportMetricValue;
import org.apache.skywalking.oap.server.exporter.grpc.ExportResponse;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionMetric;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionReq;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionsResp;
import org.apache.skywalking.oap.server.exporter.grpc.ValueType;
import org.apache.skywalking.oap.server.exporter.provider.MetricFormatter;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.util.GRPCStreamStatus;

@Slf4j
public class GRPCExporter extends MetricFormatter implements MetricValuesExportService, IConsumer<ExportData> {
    /**
     * The period of subscription list fetching is hardcoded as 30s.
     */
    private static final long FETCH_SUBSCRIPTION_PERIOD = 30_000;
    private final GRPCExporterSetting setting;
    private final MetricExportServiceGrpc.MetricExportServiceStub exportServiceFutureStub;
    private final MetricExportServiceGrpc.MetricExportServiceBlockingStub blockingStub;
    private final DataCarrier exportBuffer;
    private final ReentrantLock fetchListLock;
    private volatile List<SubscriptionMetric> subscriptionList;
    private volatile long lastFetchTimestamp = 0;

    public GRPCExporter(GRPCExporterSetting setting) {
        this.setting = setting;
        GRPCClient client = new GRPCClient(setting.getTargetHost(), setting.getTargetPort());
        client.connect();
        ManagedChannel channel = client.getChannel();
        exportServiceFutureStub = MetricExportServiceGrpc.newStub(channel);
        blockingStub = MetricExportServiceGrpc.newBlockingStub(channel);
        exportBuffer = new DataCarrier<ExportData>(setting.getBufferChannelNum(), setting.getBufferChannelSize());
        exportBuffer.consume(this, 1, 200);
        subscriptionList = new ArrayList<>();
        fetchListLock = new ReentrantLock();
    }

    @Override
    public void export(ExportEvent event) {
        Metrics metrics = event.getMetrics();
        if (metrics instanceof WithMetadata) {
            MetricsMetaInfo meta = ((WithMetadata) metrics).getMeta();
            if (subscriptionList.size() == 0 && ExportEvent.EventType.INCREMENT.equals(event.getType())) {
                exportBuffer.produce(new ExportData(meta, metrics, event.getType()));
            } else {
                subscriptionList.forEach(subscriptionMetric -> {
                    if (subscriptionMetric.getMetricName().equals(meta.getMetricsName()) &&
                        eventTypeMatch(event.getType(), subscriptionMetric.getEventType())) {
                        exportBuffer.produce(new ExportData(meta, metrics, event.getType()));
                    }
                });
            }

            fetchSubscriptionList();
        }
    }

    /**
     * Read the subscription list.
     */
    public void fetchSubscriptionList() {
        final long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastFetchTimestamp > FETCH_SUBSCRIPTION_PERIOD) {
            try {
                fetchListLock.lock();
                if (currentTimeMillis - lastFetchTimestamp > FETCH_SUBSCRIPTION_PERIOD) {
                    lastFetchTimestamp = currentTimeMillis;
                    SubscriptionsResp subscription = blockingStub.withDeadlineAfter(10, TimeUnit.SECONDS)
                                                                 .subscription(SubscriptionReq.newBuilder().build());
                    subscriptionList = subscription.getMetricsList();
                    log.debug("Get exporter subscription list, {}", subscriptionList);
                }
            } catch (Throwable e) {
                log.error("Getting exporter subscription list fails.", e);
            } finally {
                fetchListLock.unlock();
            }
        }
    }

    @Override
    public void init() {

    }

    @Override
    public void consume(List<ExportData> data) {
        GRPCStreamStatus status = new GRPCStreamStatus();
        StreamObserver<ExportMetricValue> streamObserver =
            exportServiceFutureStub.withDeadlineAfter(10, TimeUnit.SECONDS)
                                   .export(
                                       new StreamObserver<ExportResponse>() {
                                           @Override
                                           public void onNext(
                                               ExportResponse response) {

                                           }

                                           @Override
                                           public void onError(
                                               Throwable throwable) {
                                               status.done();
                                           }

                                           @Override
                                           public void onCompleted() {
                                               status.done();
                                           }
                                       });
        AtomicInteger exportNum = new AtomicInteger();
        data.forEach(row -> {
            ExportMetricValue.Builder builder = ExportMetricValue.newBuilder();

            Metrics metrics = row.getMetrics();
            if (metrics instanceof LongValueHolder) {
                long value = ((LongValueHolder) metrics).getValue();
                builder.setLongValue(value);
                builder.setType(ValueType.LONG);
            } else if (metrics instanceof IntValueHolder) {
                long value = ((IntValueHolder) metrics).getValue();
                builder.setLongValue(value);
                builder.setType(ValueType.LONG);
            } else if (metrics instanceof DoubleValueHolder) {
                double value = ((DoubleValueHolder) metrics).getValue();
                builder.setDoubleValue(value);
                builder.setType(ValueType.DOUBLE);
            } else if (metrics instanceof MultiIntValuesHolder) {
                int[] values = ((MultiIntValuesHolder) metrics).getValues();
                for (int value : values) {
                    builder.addLongValues(value);
                }
                builder.setType(ValueType.MULTI_LONG);
            } else {
                return;
            }

            MetricsMetaInfo meta = row.getMeta();
            builder.setMetricName(meta.getMetricsName());
            builder.setEventType(
                EventType.INCREMENT.equals(row.getEventType()) ? EventType.INCREMENT : EventType.TOTAL);
            String entityName = getEntityName(meta);
            if (entityName == null) {
                return;
            }
            builder.setEntityName(entityName);
            builder.setEntityId(meta.getId());

            builder.setTimeBucket(metrics.getTimeBucket());

            streamObserver.onNext(builder.build());
            exportNum.getAndIncrement();
        });

        streamObserver.onCompleted();

        long sleepTime = 0;
        long cycle = 100L;

        //For memory safe of oap, we must wait for the peer confirmation.
        while (!status.isDone()) {
            try {
                sleepTime += cycle;
                Thread.sleep(cycle);
            } catch (InterruptedException e) {
            }

            if (sleepTime > 2000L) {
                log.warn(
                    "Export {} metrics to {}:{}, wait {} milliseconds.", exportNum.get(), setting.getTargetHost(),
                    setting
                        .getTargetPort(), sleepTime
                );
                cycle = 2000L;
            }
        }

        log.debug(
            "Exported {} metrics to {}:{} in {} milliseconds.", exportNum.get(), setting.getTargetHost(), setting
                .getTargetPort(), sleepTime);

        fetchSubscriptionList();
    }

    @Override
    public void onError(List<ExportData> data, Throwable t) {
        log.error(t.getMessage(), t);
    }

    @Override
    public void onExit() {

    }

    private boolean eventTypeMatch(ExportEvent.EventType eventType,
                                   org.apache.skywalking.oap.server.exporter.grpc.EventType subscriptionType) {
        return (ExportEvent.EventType.INCREMENT.equals(eventType) && EventType.INCREMENT.equals(subscriptionType))
            || (ExportEvent.EventType.TOTAL.equals(eventType) && EventType.TOTAL.equals(subscriptionType));
    }
}
