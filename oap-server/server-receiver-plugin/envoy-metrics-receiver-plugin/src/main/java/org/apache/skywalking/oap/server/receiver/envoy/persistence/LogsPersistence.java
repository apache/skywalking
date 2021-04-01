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

package org.apache.skywalking.oap.server.receiver.envoy.persistence;

import com.google.protobuf.TextFormat;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ALSHTTPAnalysis;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;

import static org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils.toJSON;

/**
 * {@code LogsPersistence} analyzes the error logs and persists them to the log system.
 */
@Slf4j
public class LogsPersistence implements ALSHTTPAnalysis {
    private ILogAnalyzerService logAnalyzerService;

    @Override
    public String name() {
        return "persistence";
    }

    @Override
    public void init(final ModuleManager manager, final EnvoyMetricReceiverConfig config) throws ModuleStartException {
        logAnalyzerService = manager.find(LogAnalyzerModule.NAME)
                                    .provider()
                                    .getService(ILogAnalyzerService.class);
    }

    @Override
    public List<ServiceMeshMetric.Builder> analysis(
        final List<ServiceMeshMetric.Builder> result,
        final StreamAccessLogsMessage.Identifier identifier,
        final HTTPAccessLogEntry entry,
        final Role role
    ) {
        try {
            result.stream()
                  .findFirst()
                  .ifPresent(metrics -> {
                      try {
                          final LogData logData = convertToLogData(entry, metrics);
                          logAnalyzerService.doAnalysis(logData);
                      } catch (IOException e) {
                          log.error(
                              "Failed to parse error log entry to log data: {}",
                              TextFormat.shortDebugString(entry),
                              e
                          );
                      }
                  });
        } catch (final Exception e) {
            log.error("Failed to persist Envoy access log", e);
        }
        return result;
    }

    @Override
    public Role identify(final StreamAccessLogsMessage.Identifier alsIdentifier, final Role prev) {
        return prev;
    }

    public LogData convertToLogData(final HTTPAccessLogEntry logEntry,
                                    final ServiceMeshMetric.Builder metrics) throws IOException {
        final boolean isServerSide = metrics.getDetectPoint() == DetectPoint.server;
        final String svc = isServerSide ? metrics.getDestServiceName() : metrics.getSourceServiceName();
        final String svcInst = isServerSide ? metrics.getDestServiceInstance() : metrics.getSourceServiceInstance();

        return LogData
            .newBuilder()
            .setService(svc)
            .setServiceInstance(svcInst)
            .setEndpoint(metrics.getEndpoint())
            .setTimestamp(metrics.getEndTime())
            .setBody(
                LogDataBody
                    .newBuilder()
                    .setJson(
                        JSONLog
                            .newBuilder()
                            .setJson(toJSON(logEntry))
                    )
            )
            .build();
    }
}
