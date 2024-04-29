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
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.exporter.ExportData;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.exporter.MetricValuesExportService;
import org.apache.skywalking.oap.server.exporter.grpc.EventType;
import org.apache.skywalking.oap.server.exporter.grpc.ExportMetricValue;
import org.apache.skywalking.oap.server.exporter.grpc.ExportResponse;
import org.apache.skywalking.oap.server.exporter.grpc.KeyValue;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.grpc.MetricValue;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionMetric;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionReq;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionsResp;
import org.apache.skywalking.oap.server.exporter.provider.ExporterSetting;
import org.apache.skywalking.oap.server.exporter.provider.MetricFormatter;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.GRPCStreamStatus;

@Slf4j
public class GRPCMetricsExporter extends MetricFormatter implements MetricValuesExportService, IConsumer<ExportData> {
    /**
     * The period of subscription list fetching is hardcoded as 30s.
     */
    private static final long FETCH_SUBSCRIPTION_PERIOD = 30_000;
    private final ExporterSetting setting;
    private MetricExportServiceGrpc.MetricExportServiceStub exportServiceFutureStub;
    private MetricExportServiceGrpc.MetricExportServiceBlockingStub blockingStub;
    private DataCarrier exportBuffer;
    private ReentrantLock fetchListLock;
    private volatile List<SubscriptionMetric> subscriptionList;
    private volatile long lastFetchTimestamp = 0;

    public GRPCMetricsExporter(ExporterSetting setting) {
        this.setting = setting;
    }

    @Override
    public void start() {
        GRPCClient client = new GRPCClient(setting.getGRPCTargetHost(), setting.getGRPCTargetPort());
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

    @Override
    public boolean isEnabled() {
        return setting.isEnableGRPCMetrics();
    }

    /**
     * Read the subscription list.
     */
    public void fetchSubscriptionList() {
        final long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastFetchTimestamp > FETCH_SUBSCRIPTION_PERIOD) {
            fetchListLock.lock();
            try {
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
    public void consume(List<ExportData> data) {
        if (CollectionUtils.isNotEmpty(data)) {
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
                                                   log.error("Export metrics to {}:{} fails.",
                                                             setting.getGRPCTargetHost(),
                                                             setting.getGRPCTargetPort(), throwable
                                                   );
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
                    MetricValue.Builder valueBuilder = MetricValue.newBuilder();
                    valueBuilder.setLongValue(value);
                    builder.addMetricValues(valueBuilder);
                } else if (metrics instanceof IntValueHolder) {
                    long value = ((IntValueHolder) metrics).getValue();
                    MetricValue.Builder valueBuilder = MetricValue.newBuilder();
                    valueBuilder.setLongValue(value);
                    builder.addMetricValues(valueBuilder);
                } else if (metrics instanceof LabeledValueHolder) {
                    DataTable values = ((LabeledValueHolder) metrics).getValue();
                    values.keys().forEach(key -> {
                        MetricValue.Builder valueBuilder = MetricValue.newBuilder();
                        valueBuilder.setLongValue(values.get(key));
                        DataLabel labels = new DataLabel();
                        labels.put(key);
                        labels.forEach((labelName, LabelValue) -> {
                            KeyValue.Builder kvBuilder = KeyValue.newBuilder();
                            kvBuilder.setKey(labelName);
                            kvBuilder.setValue(LabelValue);
                            valueBuilder.addLabels(kvBuilder);
                        });
                        builder.addMetricValues(valueBuilder);
                    });
                } else {
                    return;
                }

                MetricsMetaInfo meta = row.getMeta();
                builder.setMetricName(meta.getMetricsName());
                builder.setEventType(
                    ExportEvent.EventType.INCREMENT.equals(row.getEventType()) ? EventType.INCREMENT : EventType.TOTAL);
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
                        "Export {} metrics to {}:{}, wait {} milliseconds.", exportNum.get(),
                        setting.getGRPCTargetHost(),
                        setting
                            .getGRPCTargetPort(), sleepTime
                    );
                    cycle = 2000L;
                }
            }

            log.debug(
                "Exported {} metrics to {}:{} in {} milliseconds.", exportNum.get(), setting.getGRPCTargetHost(),
                setting
                    .getGRPCTargetPort(), sleepTime
            );
        }
        fetchSubscriptionList();
    }

    @Override
    public void onError(List<ExportData> data, Throwable t) {
        log.error(t.getMessage(), t);
    }

    private boolean eventTypeMatch(ExportEvent.EventType eventType,
                                   org.apache.skywalking.oap.server.exporter.grpc.EventType subscriptionType) {
        return (ExportEvent.EventType.INCREMENT.equals(eventType) && EventType.INCREMENT.equals(subscriptionType))
            || (ExportEvent.EventType.TOTAL.equals(eventType) && EventType.TOTAL.equals(subscriptionType));
    }
}
