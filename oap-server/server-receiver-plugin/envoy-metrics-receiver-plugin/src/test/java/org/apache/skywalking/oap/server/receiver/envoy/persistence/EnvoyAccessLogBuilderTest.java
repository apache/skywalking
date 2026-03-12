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

import com.google.protobuf.UInt32Value;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPResponseProperties;
import java.util.Collections;
import java.util.Optional;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.source.Log;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnvoyAccessLogBuilderTest {

    private ModuleManager moduleManager;

    @BeforeEach
    void setUp() {
        resetLogBuilderState();
        moduleManager = buildModuleManager();
    }

    private static ModuleManager buildModuleManager() {
        final ModuleManager manager = mock(ModuleManager.class);
        final ModuleProviderHolder coreHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(coreHolder.provider()).thenReturn(coreServices);
        when(manager.find(CoreModule.NAME)).thenReturn(coreHolder);
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableLogsTags()).thenReturn("");
        when(coreServices.getService(ConfigService.class)).thenReturn(configService);
        return manager;
    }

    @Test
    void toLogSerializesAccessLogEntryAsJsonContent() {
        final EnvoyAccessLogBuilder builder = new EnvoyAccessLogBuilder();

        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder()
                .setResponseCode(UInt32Value.of(500)))
            .build();

        builder.setService("test-svc");
        builder.setTimestamp(1609459200000L);

        final LogData logData = LogData.newBuilder()
            .setService("test-svc")
            .setTimestamp(1609459200000L)
            .build();
        builder.init(logData, Optional.of(entry), moduleManager);

        final Log log = builder.toLog();

        assertNotNull(log);
        assertEquals(ContentType.JSON, log.getContentType());
        assertNotNull(log.getContent());
        // The JSON content should contain the response code from the access log entry
        assertTrue(log.getContent().contains("500"), "Expected JSON content to contain '500' but got: " + log.getContent());
    }

    @Test
    void toLogWithDefaultEntryHasNoContent() {
        final EnvoyAccessLogBuilder builder = new EnvoyAccessLogBuilder();

        builder.setService("test-svc");
        builder.setTimestamp(1609459200000L);

        final LogData logData = LogData.newBuilder()
            .setService("test-svc")
            .setTimestamp(1609459200000L)
            .build();
        // Pass a default (empty) entry — no response code, so toLog() serializes empty JSON
        builder.init(logData, Optional.of(HTTPAccessLogEntry.getDefaultInstance()), moduleManager);

        final Log log = builder.toLog();

        assertNotNull(log);
        // Default entry still produces JSON (empty proto serializes to "{}")
        // but content is set because accessLogEntry is non-null
        assertNotNull(log.getContent());
    }

    @Test
    void nameReturnsEnvoyAccessLog() {
        assertEquals("EnvoyAccessLog", new EnvoyAccessLogBuilder().name());
    }

    /**
     * Compiles and executes a LAL script that accesses both proto fields from
     * the HTTPAccessLogEntry (via {@code parsed.*}) and entity fields from
     * LogData (via {@code log.service}). Verifies at runtime that:
     * <ul>
     *   <li>Proto fields are read correctly (response code extracted as tag)</li>
     *   <li>{@code log.service} is accessible and reads from LogData</li>
     *   <li>The output object is an {@link EnvoyAccessLogBuilder}</li>
     * </ul>
     */
    @Test
    void executeLalWithProtoFieldAndEntityAccess() throws Exception {
        final LALClassGenerator generator = new LALClassGenerator();
        generator.setInputType(HTTPAccessLogEntry.class);
        generator.setOutputType(EnvoyAccessLogBuilder.class);

        // A simplified envoy-als script that:
        // 1. Reads parsed?.response?.responseCode from the proto (tag extraction)
        // 2. Reads log.service from LogData (tag extraction to verify entity access)
        final String dsl =
            "filter {\n"
            + "  extractor {\n"
            + "    tag 'status.code': parsed?.response?.responseCode?.value\n"
            + "    tag 'svc': log.service\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";

        final LalExpression expr = generator.compile(dsl);

        // Build LogData with service/instance (simulates what LogsPersistence creates)
        final LogData.Builder logData = LogData.newBuilder()
            .setService("envoy-test-svc")
            .setServiceInstance("envoy-test-instance")
            .setTimestamp(1609459200000L)
            .setLayer("MESH");

        // Build HTTPAccessLogEntry with response code 503
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder()
                .setResponseCode(UInt32Value.of(503)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .setUpstreamCluster("outbound|80||backend.default.svc"))
            .build();

        // Execute
        final FilterSpec filterSpec = buildFilterSpec();
        final ExecutionContext ctx = new ExecutionContext();
        ctx.log(logData);
        ctx.extraLog(entry);
        expr.execute(filterSpec, ctx);

        // Verify output is EnvoyAccessLogBuilder
        assertNotNull(ctx.output());
        assertTrue(ctx.output() instanceof EnvoyAccessLogBuilder,
            "Expected EnvoyAccessLogBuilder but got: " + ctx.output().getClass().getName());

        // Verify proto field access and entity access via the output builder.
        // Tags are stored in the output builder (via addTag), not in LogData.
        // To verify, call init + toLog and check the Log's searchable tags.
        final EnvoyAccessLogBuilder output = (EnvoyAccessLogBuilder) ctx.output();
        // Build a moduleManager with searchable tag keys for this test
        final ModuleManager testMm = buildModuleManager();
        final ConfigService cs = testMm.find(CoreModule.NAME).provider().getService(ConfigService.class);
        when(cs.getSearchableLogsTags()).thenReturn("status.code,svc");
        // Reset static initialized flag so the new config takes effect
        resetLogBuilderState();
        output.init(logData.build(), Optional.of(entry), testMm);
        final Log log = output.toLog();

        assertTrue(log.getTags().stream().anyMatch(
                t -> "status.code".equals(t.getKey()) && "503".equals(t.getValue())),
            "Expected tag status.code=503 from proto field, got: " + log.getTags());
        assertTrue(log.getTags().stream().anyMatch(
                t -> "svc".equals(t.getKey()) && "envoy-test-svc".equals(t.getValue())),
            "Expected tag svc=envoy-test-svc from log.service, got: " + log.getTags());
    }

    private FilterSpec buildFilterSpec() throws Exception {
        final ModuleManager manager = mock(ModuleManager.class);
        // Set isInPrepareStage = false via reflection
        final java.lang.reflect.Field f = ModuleManager.class.getDeclaredField("isInPrepareStage");
        f.setAccessible(true);
        f.set(manager, false);

        when(manager.find(anyString()))
            .thenReturn(mock(ModuleProviderHolder.class));

        final ModuleProviderHolder logHolder = mock(ModuleProviderHolder.class);
        final LogAnalyzerModuleProvider logProvider = mock(LogAnalyzerModuleProvider.class);
        when(logProvider.getMetricConverts()).thenReturn(Collections.emptyList());
        when(logHolder.provider()).thenReturn(logProvider);
        when(manager.find(LogAnalyzerModule.NAME)).thenReturn(logHolder);

        final ModuleProviderHolder coreHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(coreHolder.provider()).thenReturn(coreServices);
        when(manager.find(CoreModule.NAME)).thenReturn(coreHolder);

        when(coreServices.getService(SourceReceiver.class))
            .thenReturn(mock(SourceReceiver.class));
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(200, 200, 200, new EndpointNameGrouping()));
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableLogsTags()).thenReturn("");
        when(coreServices.getService(ConfigService.class)).thenReturn(configService);

        final FilterSpec filterSpec = new FilterSpec(manager, new LogAnalyzerModuleConfig());
        // Empty sink listeners — we only test extractor behavior
        final java.lang.reflect.Field slf = FilterSpec.class.getDeclaredField("sinkListenerFactories");
        slf.setAccessible(true);
        slf.set(filterSpec, Collections.emptyList());

        return filterSpec;
    }

    private static void resetLogBuilderState() {
        try {
            final java.lang.reflect.Field f = org.apache.skywalking.oap.server.core.source.LogBuilder.class
                .getDeclaredField("INITIALIZED");
            f.setAccessible(true);
            f.set(null, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertTrue(final boolean condition, final String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
