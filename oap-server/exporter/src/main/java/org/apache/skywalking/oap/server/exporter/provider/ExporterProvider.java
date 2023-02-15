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

package org.apache.skywalking.oap.server.exporter.provider;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.exporter.ExporterModule;
import org.apache.skywalking.oap.server.core.exporter.LogExportService;
import org.apache.skywalking.oap.server.core.exporter.MetricValuesExportService;
import org.apache.skywalking.oap.server.core.exporter.TraceExportService;
import org.apache.skywalking.oap.server.exporter.provider.grpc.GRPCMetricsExporter;
import org.apache.skywalking.oap.server.exporter.provider.kafka.log.KafkaLogExporter;
import org.apache.skywalking.oap.server.exporter.provider.kafka.trace.KafkaTraceExporter;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

public class ExporterProvider extends ModuleProvider {
    private ExporterSetting setting;
    private GRPCMetricsExporter grpcMetricsExporter;
    private KafkaTraceExporter kafkaTraceExporter;
    private KafkaLogExporter kafkaLogExporter;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return ExporterModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<ExporterSetting>() {
            @Override
            public Class type() {
                return ExporterSetting.class;
            }

            @Override
            public void onInitialized(final ExporterSetting initialized) {
                setting = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        grpcMetricsExporter = new GRPCMetricsExporter(setting);
        kafkaTraceExporter = new KafkaTraceExporter(getManager(), setting);
        kafkaLogExporter = new KafkaLogExporter(getManager(), setting);
        this.registerServiceImplementation(MetricValuesExportService.class, grpcMetricsExporter);
        this.registerServiceImplementation(TraceExportService.class, kafkaTraceExporter);
        this.registerServiceImplementation(LogExportService.class, kafkaLogExporter);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        if (setting.isEnableGRPCMetrics()) {
            grpcMetricsExporter.start();
        }
        if (setting.isEnableKafkaTrace()) {
            kafkaTraceExporter.start();
        }
        if (setting.isEnableKafkaLog()) {
            kafkaLogExporter.start();
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        if (setting.isEnableGRPCMetrics()) {
            grpcMetricsExporter.fetchSubscriptionList();
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
