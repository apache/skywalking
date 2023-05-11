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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LogHandlerTest {
    private static final String TOPIC_NAME = "skywalking-logs";
    private LogHandler handler = null;
    private KafkaFetcherConfig config = new KafkaFetcherConfig();

    private ModuleManager manager;

    @BeforeEach
    public void setup() {
        final ModuleManager manager = mock(ModuleManager.class, RETURNS_DEEP_STUBS);
        when(manager.find(LogAnalyzerModule.NAME).provider().getService(any()))
            .thenReturn(mock(ILogAnalyzerService.class));
        when(manager.find(TelemetryModule.NAME).provider().getService(any()))
            .thenReturn(mock(MetricsCreator.class));
        handler = new LogHandler(manager, config);
    }

    @Test
    public void testGetTopic() {
        assertEquals(handler.getTopic(), TOPIC_NAME);

        String namespace = "product";
        config.setNamespace(namespace);
        assertEquals(namespace + "-" + TOPIC_NAME, handler.getTopic());
    }
}
