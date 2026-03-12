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
 */

package org.apache.skywalking.oap.server.receiver.envoy.persistence;

import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.Value;
import io.envoyproxy.envoy.config.core.v3.Metadata;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPResponseProperties;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import javassist.ClassPool;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.Log;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LAL compilation and execution tests for envoy ALS proto chains.
 *
 * <p>Tests {@code def} local variables with {@code toJson()} to extract
 * JWT claims from envoy {@code filter_metadata} Struct, and verifies
 * runtime tag extraction against real proto objects.
 *
 * <p>Generated {@code .class} files are dumped to
 * {@code target/lal-generated-classes/} with names like
 * {@code EnvoyAlsLalTest_defJwtFromFilterMetadata.class}.
 */
class EnvoyAlsLalTest {

    private LALClassGenerator generator;
    private ModuleManager moduleManager;

    @BeforeEach
    void setUp(final TestInfo testInfo) {
        resetLogBuilderState();
        generator = new LALClassGenerator(new ClassPool(true));
        generator.setInputType(HTTPAccessLogEntry.class);
        generator.setOutputType(EnvoyAccessLogBuilder.class);
        generator.setClassOutputDir(DslClassOutput.unitTestDir("lal"));
        final String methodName = testInfo.getTestMethod()
            .map(m -> m.getName()).orElse("unknown");
        generator.setClassNameHint("EnvoyAlsLalTest_" + methodName);
        moduleManager = buildCoreModuleManager("");
    }

    private static ModuleManager buildCoreModuleManager(final String searchableTags) {
        final ModuleManager manager = mock(ModuleManager.class);
        final ModuleProviderHolder coreHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(coreHolder.provider()).thenReturn(coreServices);
        when(manager.find(CoreModule.NAME)).thenReturn(coreHolder);
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableLogsTags()).thenReturn(searchableTags);
        when(coreServices.getService(ConfigService.class)).thenReturn(configService);
        return manager;
    }

    private static void resetLogBuilderState() {
        try {
            final Field f = org.apache.skywalking.oap.server.core.source.LogBuilder.class
                .getDeclaredField("INITIALIZED");
            f.setAccessible(true);
            f.set(null, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== def + toJson: JWT from filter_metadata ===========

    /**
     * LAL script under test:
     * <pre>{@code
     * filter {
     *   extractor {
     *     def jwt = toJson(parsed?.commonProperties?.metadata
     *         ?.filterMetadataMap?.get("envoy.filters.http.jwt_authn"))
     *     def payload = jwt?.getAsJsonObject("payload")
     *     if (payload != null) {
     *       tag 'email': payload?.get("email")?.getAsString()
     *       tag 'group': payload?.get("group")?.getAsString()
     *     }
     *     tag 'status.code': parsed?.response?.responseCode?.value
     *   }
     *   sink {}
     * }
     * }</pre>
     */
    @Test
    void defJwtFromFilterMetadata() throws Exception {
        final String dsl =
            "filter {\n"
            + "  extractor {\n"
            + "    def jwt = toJson(parsed?.commonProperties?.metadata"
            + "?.filterMetadataMap?.get(\"envoy.filters.http.jwt_authn\"))\n"
            + "    def payload = jwt?.getAsJsonObject(\"payload\")\n"
            + "    if (payload != null) {\n"
            + "      tag 'email': payload?.get(\"email\")?.getAsString()\n"
            + "      tag 'group': payload?.get(\"group\")?.getAsString()\n"
            + "    }\n"
            + "    tag 'status.code': parsed?.response?.responseCode?.value\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";

        final LalExpression expr = generator.compile(dsl);

        final Struct jwtStruct = Struct.newBuilder()
            .putFields("payload", Value.newBuilder()
                .setStructValue(Struct.newBuilder()
                    .putFields("email", Value.newBuilder()
                        .setStringValue("alice@example.com").build())
                    .putFields("group", Value.newBuilder()
                        .setStringValue("admin").build()))
                .build())
            .build();

        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder()
                .setResponseCode(UInt32Value.of(200)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .setMetadata(Metadata.newBuilder()
                    .putFilterMetadata(
                        "envoy.filters.http.jwt_authn", jwtStruct)))
            .build();

        final Log log = executeAndBuildLog(expr, entry,
            "email", "group", "status.code");

        assertTag(log, "email", "alice@example.com");
        assertTag(log, "group", "admin");
        assertTag(log, "status.code", "200");
    }

    // ==================== def + toJson: missing metadata ===================

    /**
     * Same LAL script as {@link #defJwtFromFilterMetadata()}, but the
     * input proto has no filter_metadata. Verifies null-safe navigation
     * handles the missing Struct gracefully (no crash, no tags extracted).
     * <pre>{@code
     * filter {
     *   extractor {
     *     def jwt = toJson(parsed?.commonProperties?.metadata
     *         ?.filterMetadataMap?.get("envoy.filters.http.jwt_authn"))
     *     def payload = jwt?.getAsJsonObject("payload")
     *     if (payload != null) {
     *       tag 'email': payload?.get("email")?.getAsString()
     *     }
     *   }
     *   sink {}
     * }
     * }</pre>
     */
    @Test
    void defJwtMissingMetadata() throws Exception {
        final String dsl =
            "filter {\n"
            + "  extractor {\n"
            + "    def jwt = toJson(parsed?.commonProperties?.metadata"
            + "?.filterMetadataMap?.get(\"envoy.filters.http.jwt_authn\"))\n"
            + "    def payload = jwt?.getAsJsonObject(\"payload\")\n"
            + "    if (payload != null) {\n"
            + "      tag 'email': payload?.get(\"email\")?.getAsString()\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";

        final LalExpression expr = generator.compile(dsl);

        // Entry with NO filter_metadata — jwt should be null, no crash
        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder()
                .setResponseCode(UInt32Value.of(404)))
            .build();

        final ExecutionContext ctx = execute(expr, entry);
        assertNotNull(ctx.output());
    }

    // ==================== def: multiple filter_metadata keys ===============

    /**
     * Two {@code def} variables extracting from different filter_metadata
     * keys (jwt_authn for JWT claims, rbac for authorization result).
     * Uses {@code def payload} to avoid duplicate
     * {@code getAsJsonObject("payload")} calls.
     * <pre>{@code
     * filter {
     *   extractor {
     *     def jwt = toJson(parsed?.commonProperties?.metadata
     *         ?.filterMetadataMap?.get("envoy.filters.http.jwt_authn"))
     *     def rbac = toJson(parsed?.commonProperties?.metadata
     *         ?.filterMetadataMap?.get("envoy.filters.http.rbac"))
     *     def payload = jwt?.getAsJsonObject("payload")
     *     if (payload != null) {
     *       tag 'email': payload?.get("email")?.getAsString()
     *     }
     *     if (rbac?.has("shadow_engine_result")) {
     *       tag 'rbac': rbac?.get("shadow_engine_result")?.getAsString()
     *     }
     *   }
     *   sink {}
     * }
     * }</pre>
     */
    @Test
    void defMultipleFilterMetadataKeys() throws Exception {
        final String dsl =
            "filter {\n"
            + "  extractor {\n"
            + "    def jwt = toJson(parsed?.commonProperties?.metadata"
            + "?.filterMetadataMap?.get(\"envoy.filters.http.jwt_authn\"))\n"
            + "    def rbac = toJson(parsed?.commonProperties?.metadata"
            + "?.filterMetadataMap?.get(\"envoy.filters.http.rbac\"))\n"
            + "    def payload = jwt?.getAsJsonObject(\"payload\")\n"
            + "    if (payload != null) {\n"
            + "      tag 'email': payload?.get(\"email\")?.getAsString()\n"
            + "    }\n"
            + "    if (rbac?.has(\"shadow_engine_result\")) {\n"
            + "      tag 'rbac': rbac?.get(\"shadow_engine_result\")"
            + "?.getAsString()\n"
            + "    }\n"
            + "  }\n"
            + "  sink {}\n"
            + "}";

        final LalExpression expr = generator.compile(dsl);

        final Struct jwtStruct = Struct.newBuilder()
            .putFields("payload", Value.newBuilder()
                .setStructValue(Struct.newBuilder()
                    .putFields("email", Value.newBuilder()
                        .setStringValue("bob@example.com").build()))
                .build())
            .build();
        final Struct rbacStruct = Struct.newBuilder()
            .putFields("shadow_engine_result", Value.newBuilder()
                .setStringValue("DENY").build())
            .build();

        final HTTPAccessLogEntry entry = HTTPAccessLogEntry.newBuilder()
            .setResponse(HTTPResponseProperties.newBuilder()
                .setResponseCode(UInt32Value.of(403)))
            .setCommonProperties(AccessLogCommon.newBuilder()
                .setMetadata(Metadata.newBuilder()
                    .putFilterMetadata(
                        "envoy.filters.http.jwt_authn", jwtStruct)
                    .putFilterMetadata(
                        "envoy.filters.http.rbac", rbacStruct)))
            .build();

        final Log log = executeAndBuildLog(expr, entry, "email", "rbac");

        assertTag(log, "email", "bob@example.com");
        assertTag(log, "rbac", "DENY");
    }

    // ==================== Helpers ==========================================

    private ExecutionContext execute(
            final LalExpression expr,
            final HTTPAccessLogEntry entry) throws Exception {
        final LogData.Builder logData = LogData.newBuilder()
            .setService("als-test-svc")
            .setTimestamp(1609459200000L)
            .setLayer("MESH");

        final FilterSpec filterSpec = buildFilterSpec();
        final ExecutionContext ctx = new ExecutionContext();
        ctx.log(logData);
        ctx.extraLog(entry);
        expr.execute(filterSpec, ctx);
        return ctx;
    }

    private Log executeAndBuildLog(
            final LalExpression expr,
            final HTTPAccessLogEntry entry,
            final String... searchableTagKeys) throws Exception {
        final ExecutionContext ctx = execute(expr, entry);

        assertNotNull(ctx.output());
        final EnvoyAccessLogBuilder output =
            (EnvoyAccessLogBuilder) ctx.output();
        // Reset and rebuild with the desired searchable tag keys
        resetLogBuilderState();
        final ModuleManager tagMm = buildCoreModuleManager(
            String.join(",", searchableTagKeys));
        output.init(ctx.log().build(), Optional.of(entry), tagMm);
        return output.toLog();
    }

    private static void assertTag(final Log log,
                                   final String key,
                                   final String expectedValue) {
        assertTrue(log.getTags().stream().anyMatch(
                t -> key.equals(t.getKey())
                    && expectedValue.equals(t.getValue())),
            "Expected tag " + key + "=" + expectedValue
                + ", got: " + log.getTags());
    }

    private FilterSpec buildFilterSpec() throws Exception {
        final ModuleManager manager = mock(ModuleManager.class);
        final Field f =
            ModuleManager.class.getDeclaredField("isInPrepareStage");
        f.setAccessible(true);
        f.set(manager, false);

        when(manager.find(anyString()))
            .thenReturn(mock(ModuleProviderHolder.class));

        final ModuleProviderHolder logHolder =
            mock(ModuleProviderHolder.class);
        final LogAnalyzerModuleProvider logProvider =
            mock(LogAnalyzerModuleProvider.class);
        when(logProvider.getMetricConverts())
            .thenReturn(Collections.emptyList());
        when(logHolder.provider()).thenReturn(logProvider);
        when(manager.find(LogAnalyzerModule.NAME)).thenReturn(logHolder);

        final ModuleProviderHolder coreHolder =
            mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices =
            mock(ModuleServiceHolder.class);
        when(coreHolder.provider()).thenReturn(coreServices);
        when(manager.find(CoreModule.NAME)).thenReturn(coreHolder);

        when(coreServices.getService(SourceReceiver.class))
            .thenReturn(mock(SourceReceiver.class));
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableLogsTags()).thenReturn("");
        when(coreServices.getService(ConfigService.class))
            .thenReturn(configService);

        final FilterSpec filterSpec =
            new FilterSpec(manager, new LogAnalyzerModuleConfig());
        final Field slf =
            FilterSpec.class.getDeclaredField("sinkListenerFactories");
        slf.setAccessible(true);
        slf.set(filterSpec, Collections.emptyList());

        return filterSpec;
    }
}
