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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.analysis.indicator.*;
import org.apache.skywalking.oap.server.core.exporter.MetricValuesExportService;
import org.apache.skywalking.oap.server.exporter.grpc.*;
import org.apache.skywalking.oap.server.exporter.provider.MetricFormatter;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class GRPCExporter extends MetricFormatter implements MetricValuesExportService, IConsumer<GRPCExporter.ExportData> {
    private static final Logger logger = LoggerFactory.getLogger(GRPCExporter.class);

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

    @Override public void export(IndicatorMetaInfo meta, Indicator indicator) {
        if (subscriptionSet.size() == 0 || subscriptionSet.contains(meta.getIndicatorName())) {
            exportBuffer.produce(new ExportData(meta, indicator));
        }
    }

    public void initSubscriptionList() {
        SubscriptionsResp subscription = blockingStub.subscription(SubscriptionReq.newBuilder().build());
        subscription.getMetricNamesList().forEach(subscriptionSet::add);
        logger.debug("Get exporter subscription list, {}", subscriptionSet);
    }

    @Override public void init() {

    }

    @Override public void consume(List<ExportData> data) {
        if (data.size() == 0) {
            return;
        }

        ExportStatus status = new ExportStatus();
        StreamObserver<ExportMetricValue> streamObserver = exportServiceFutureStub.export(
            new StreamObserver<ExportResponse>() {
                @Override public void onNext(ExportResponse response) {

                }

                @Override public void onError(Throwable throwable) {
                    status.done();
                }

                @Override public void onCompleted() {
                    status.done();
                }
            }
        );
        AtomicInteger exportNum = new AtomicInteger();
        data.forEach(row -> {
            ExportMetricValue.Builder builder = ExportMetricValue.newBuilder();

            Indicator indicator = row.getIndicator();
            if (indicator instanceof LongValueHolder) {
                long value = ((LongValueHolder)indicator).getValue();
                builder.setLongValue(value);
                builder.setType(ValueType.LONG);
            } else if (indicator instanceof IntValueHolder) {
                long value = ((IntValueHolder)indicator).getValue();
                builder.setLongValue(value);
                builder.setType(ValueType.LONG);
            } else if (indicator instanceof DoubleValueHolder) {
                double value = ((DoubleValueHolder)indicator).getValue();
                builder.setDoubleValue(value);
                builder.setType(ValueType.DOUBLE);
            } else {
                return;
            }

            IndicatorMetaInfo meta = row.getMeta();
            builder.setMetricName(meta.getIndicatorName());
            String entityName = getEntityName(meta);
            if (entityName == null) {
                return;
            }
            builder.setEntityName(entityName);
            builder.setEntityId(meta.getId());

            builder.setTimeBucket(indicator.getTimeBucket());

            streamObserver.onNext(builder.build());
            exportNum.getAndIncrement();
        });

        streamObserver.onCompleted();

        long sleepTime = 0;
        long cycle = 100L;
        /**
         * For memory safe of oap, we must wait for the peer confirmation.
         */
        while (!status.isDone()) {
            try {
                sleepTime += cycle;
                Thread.sleep(cycle);
            } catch (InterruptedException e) {
            }

            if (sleepTime > 2000L) {
                logger.warn("Export {} metric(s) to {}:{}, wait {} milliseconds.",
                    exportNum.get(), setting.getTargetHost(), setting.getTargetPort(), sleepTime);
                cycle = 2000L;
            }
        }

        logger.debug("Exported {} metric(s) to {}:{} in {} milliseconds.",
            exportNum.get(), setting.getTargetHost(), setting.getTargetPort(), sleepTime);
    }

    @Override public void onError(List<ExportData> data, Throwable t) {
        logger.error(t.getMessage(), t);
    }

    @Override public void onExit() {

    }

    @Getter(AccessLevel.PRIVATE)
    public class ExportData {
        private IndicatorMetaInfo meta;
        private Indicator indicator;

        public ExportData(IndicatorMetaInfo meta, Indicator indicator) {
            this.meta = meta;
            this.indicator = indicator;
        }
    }

    private class ExportStatus {
        private boolean done = false;

        private void done() {
            done = true;
        }

        public boolean isDone() {
            return done;
        }
    }
}
