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

import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.tcp.TCPAccessLogAnalyzer;
import org.apache.skywalking.oap.server.receiver.envoy.als.tcp.TCPLogEntry2MetricsAdapter;

/**
 * {@code LogsPersistence} analyzes the error logs and persists them to the log system.
 */
@Slf4j
public class TCPLogsPersistence implements TCPAccessLogAnalyzer {
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
    public Result analysis(
        final Result result,
        final StreamAccessLogsMessage.Identifier identifier,
        final TCPAccessLogEntry entry,
        final Role role
    ) {
        try {
            if (result.getService() == null) {
                return result;
            }

            final LogData logData = convertToLogData(entry, result);
            logAnalyzerService.doAnalysis(logData, entry);
        } catch (final Exception e) {
            log.error("Failed to persist Envoy access log", e);
        }
        return result;
    }

    @Override
    public Role identify(final StreamAccessLogsMessage.Identifier alsIdentifier, final Role prev) {
        return prev;
    }

    public LogData convertToLogData(final TCPAccessLogEntry logEntry, final Result result) {
        final ServiceMetaInfo service = result.getService();

        final ServiceMeshMetric.Builder metrics =
            new TCPLogEntry2MetricsAdapter(logEntry, null, null).adaptCommonPart();

        return LogData
            .newBuilder()
            .setService(service.getServiceName())
            .setServiceInstance(service.getServiceInstanceName())
            .setTimestamp(metrics.getEndTime())
            .build();
    }
}
