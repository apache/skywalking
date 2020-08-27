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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.apache.skywalking.oap.server.exporter.grpc.ExportMetricValue;
import org.apache.skywalking.oap.server.exporter.grpc.ExportResponse;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionReq;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionsResp;
import org.apache.skywalking.oap.server.exporter.grpc.ValueType;
import org.apache.skywalking.oap.server.exporter.provider.MetricFormatter;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.util.GRPCStreamStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GRPCExporter extends MetricFormatter implements MetricValuesExportService, IConsumer<ExportData> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GRPCExporter.class);

    private GRPCExporterSetting setting;
    private final MetricExportServiceGrpc.MetricExportServiceStub exportServiceFutureStub;
    private final MetricExportServiceGrpc.MetricExportServiceBlockingStub blockingStub;
    private final DataCarrier exportBuffer;
    private final Set<String> subscriptionSet;

    public GRPCExporter(GRPCExporterSetting setting) {
        this.setting = setting;
        GRPCClient client = new GRPCClient(setting.getTargetHost(), setting.getTargetPort());
        client.connect();
        ManagedChannel channel = client.getChannel();
        exportServiceFutureStub = MetricExportServiceGrpc.newStub(channel);
        blockingStub = MetricExportServiceGrpc.newBlockingStub(channel);
        exportBuffer = new DataCarrier<ExportData>(setting.getBufferChannelNum(), setting.getBufferChannelSize());
        exportBuffer.consume(this, 1, 200);
        subscriptionSet = new HashSet<>();
    }

    @Override
    public void export(ExportEvent event) {
        if (ExportEvent.EventType.TOTAL == event.getType()) {
            Metrics metrics = event.getMetrics();
            if (metrics instanceof WithMetadata) {
                MetricsMetaInfo meta = ((WithMetadata) metrics).getMeta();
                if (subscriptionSet.size() == 0 || subscriptionSet.contains(meta.getMetricsName())) {
                    exportBuffer.produce(new ExportData(meta, metrics));
                }
            }
        }
    }

    public void initSubscriptionList() {
        SubscriptionsResp subscription = blockingStub.withDeadlineAfter(10, TimeUnit.SECONDS)
                                                     .subscription(SubscriptionReq.newBuilder().build());
        subscription.getMetricNamesList().forEach(subscriptionSet::add);
        LOGGER.debug("Get exporter subscription list, {}", subscriptionSet);
    }

    @Override
    public void init() {

    }

    @Override
    public void consume(List<ExportData> data) {
        if (data.size() == 0) {
            return;
        }

        GRPCStreamStatus status = new GRPCStreamStatus();
        StreamObserver<ExportMetricValue> streamObserver = exportServiceFutureStub.withDeadlineAfter(
            10, TimeUnit.SECONDS)
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
                LOGGER.warn(
                    "Export {} metrics to {}:{}, wait {} milliseconds.", exportNum.get(), setting.getTargetHost(),
                    setting
                        .getTargetPort(), sleepTime
                );
                cycle = 2000L;
            }
        }

        LOGGER.debug(
            "Exported {} metrics to {}:{} in {} milliseconds.", exportNum.get(), setting.getTargetHost(), setting
                .getTargetPort(), sleepTime);
    }

    @Override
    public void onError(List<ExportData> data, Throwable t) {
        LOGGER.error(t.getMessage(), t);
    }

    @Override
    public void onExit() {

    }
}
