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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import com.google.common.base.Strings;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.otel.Handler;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Slf4j
@RequiredArgsConstructor
public class OpenTelemetryLogHandler
    extends LogsServiceGrpc.LogsServiceImplBase
    implements Handler {
    private final ModuleManager manager;

    private ILogAnalyzerService logAnalyzerService;

    @Override
    public String type() {
        return "otlp-logs";
    }

    @Override
    public void active() throws ModuleStartException {
        GRPCHandlerRegister grpcHandlerRegister = manager.find(SharingServerModule.NAME)
                                                         .provider()
                                                         .getService(GRPCHandlerRegister.class);
        grpcHandlerRegister.addHandler(this);
    }

    @Override
    public void export(ExportLogsServiceRequest request, StreamObserver<ExportLogsServiceResponse> responseObserver) {
        request.getResourceLogsList().forEach(resourceLogs -> {
            final var resource = resourceLogs.getResource();
            final var attributes = resource
                .getAttributesList().stream()
                .map(it -> Map.entry(it.getKey(), buildTagValue(it)))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            final var service = attributes.get("service.name");
            if (Strings.isNullOrEmpty(service)) {
                log.warn("No service name found in resource attributes, discarding the log");
                return;
            }
            final var layer = attributes.getOrDefault("service.layer", "");
            final var serviceInstance = attributes.getOrDefault("service.instance", "");

            resourceLogs
                .getScopeLogsList()
                .stream()
                .flatMap(it -> it.getLogRecordsList().stream())
                .forEach(
                    logRecord ->
                        doAnalysisQuietly(service, layer, serviceInstance, logRecord));
            responseObserver.onNext(ExportLogsServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        });
    }

    private void doAnalysisQuietly(String service, String layer, String serviceInstance, LogRecord logRecord) {
        try {
            logAnalyzerService().doAnalysis(
                LogData
                    .newBuilder()
                    .setService(service)
                    .setServiceInstance(serviceInstance)
                    .setTimestamp(logRecord.getTimeUnixNano() / 1_000_000)
                    .setTags(buildTags(logRecord))
                    .setBody(buildBody(logRecord))
                    .setLayer(layer),
                null);
        } catch (Exception e) {
            log.error("Failed to analyze logs", e);
        }
    }

    private static LogDataBody buildBody(LogRecord logRecord) {
        return LogDataBody.newBuilder().setText(
            TextLog.newBuilder().setText(
                logRecord.getBody().getStringValue()
            ).build()
        ).build();
    }

    private LogTags buildTags(LogRecord logRecord) {
        return LogTags.newBuilder().addAllData(
            logRecord
                .getAttributesList()
                .stream()
                .collect(toMap(KeyValue::getKey, this::buildTagValue))
                .entrySet()
                .stream()
                .map(it -> KeyStringValuePair
                    .newBuilder()
                    .setKey(it.getKey())
                    .setValue(it.getValue())
                    .build())
                .collect(Collectors.toList())
        ).build();
    }

    private String buildTagValue(KeyValue it) {
        final var value = it.getValue();
        return value.hasStringValue() ? value.getStringValue() :
            value.hasIntValue() ? String.valueOf(value.getIntValue()) :
                value.hasDoubleValue() ? String.valueOf(value.getDoubleValue()) :
                    value.hasBoolValue() ? String.valueOf(value.getBoolValue()) :
                        value.hasArrayValue() ? value.getArrayValue().toString() :
                            "";
    }

    private ILogAnalyzerService logAnalyzerService() {
        if (logAnalyzerService == null) {
            logAnalyzerService =
                manager.find(LogAnalyzerModule.NAME)
                       .provider()
                       .getService(ILogAnalyzerService.class);
        }
        return logAnalyzerService;
    }
}
