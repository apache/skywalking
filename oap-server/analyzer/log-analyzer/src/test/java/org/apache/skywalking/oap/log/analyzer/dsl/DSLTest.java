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

package org.apache.skywalking.oap.log.analyzer.dsl;

import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DSLTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new String[] {
                        "parser",
                        "filter {\n" +
                                "  json {\n" +
                                "    abortOnFailure false // for test purpose, we want to persist all logs\n" +
                                "  }\n" +
                                "  text {\n" +
                                "    abortOnFailure false // for test purpose, we want to persist all logs\n" +
                                "    regexp $/(?s)(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3}) \\[TID:(?<tid>.+?)] \\[(?<thread>.+?)] (?<level>\\w{4,}) (?<logger>.{1,36}) (?<msg>.+)/$" +
                                "  }\n" +
                                "  yaml {\n" +
                                "    abortOnFailure false // for test purpose, we want to persist all logs\n" +
                                "  }" +
                                "}",
                },
                new String[] {
                        "extractor",
                        "filter {\n" +
                                "  extractor {\n" +
                                "    service \"test\"\n" +
                                "    instance \"test\"\n" +
                                "    endpoint \"test\"\n" +
                                "    layer \"mesh\"\n" +
                                "    traceId \"123\"\n" +
                                "    segmentId \"123\"\n" +
                                "    spanId \"123\"\n" +
                                "    timestamp \"123\"\n" +
                                "    metrics {\n" +
                                "      name \"metricsName\"\n" +
                                "      value 123\n" +
                                "      timestamp \"123\"\n" +
                                "      labels \"k1\": \"v1\"\n" +
                                "    }\n" +
                                "  }\n" +
                                "}",
                },
                new String[] {
                        "sink",
                        "filter {\n" +
                                "  sink {\n" +
                                "    enforcer {\n" +
                                "    }\n" +
                                "    dropper {\n" +
                                "    }\n" +
                                "    sampler {\n" +
                                "      if (parsed?.commonProperties?.responseFlags) {\n" +
                                "        // use service:errorCode as sampler id so that each service:errorCode has its own sampler,\n" +
                                "        // e.g. checkoutservice:[upstreamConnectionFailure], checkoutservice:[upstreamRetryLimitExceeded]\n" +
                                "        rateLimit(\"${log.service}:${log.body.json.json}:${log.tags.getData(0).key}:${parsed?.commonProperties?.responseFlags}\") {\n" +
                                "          rpm 100\n" +
                                "        }\n" +
                                "      } else {\n" +
                                "        // use service:responseCode as sampler id so that each service:responseCode has its own sampler,\n" +
                                "        // e.g. checkoutservice:500, checkoutservice:404.\n" +
                                "        rateLimit(\"${log.service}:${log.body?.type}:${log.traceContext?.traceId}:${parsed?.response?.responseCode}\") {\n" +
                                "          rpm 100\n" +
                                "        }\n" +
                                "      }\n" +
                                "    }\n" +
                                "  }\n" +
                                "}",
                },
                new String[] {
                        "e2e",
                        "filter {\n" +
                                "  text {\n" +
                                "    abortOnFailure false // for test purpose, we want to persist all logs\n" +
                                "    regexp $/(?s)(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3}) \\[TID:(?<tid>.+?)] \\[(?<thread>.+?)] (?<level>\\w{4,}) (?<logger>.{1,36}) (?<msg>.+)/$\n" +
                                "  }\n" +
                                "  extractor {\n" +
                                "    metrics {\n" +
                                "      timestamp \"${log.timestamp}\"\n" +
                                "      labels level: parsed.level, service: log.service, instance: log.serviceInstance\n" +
                                "      name \"log_count\"\n" +
                                "      value 1\n" +
                                "    }\n" +
                                "  }\n" +
                                "  sink {\n" +
                                "  }\n" +
                                "}\n",
                },
                new String[] {
                        "e2e",
                        "filter {\n" +
                                "  json {\n" +
                                "  }\n" +
                                "  // only collect abnormal logs (http status code >= 300, or commonProperties?.responseFlags is not empty)\n" +
                                "  if (parsed?.response?.responseCode as Integer < 400 && !parsed?.commonProperties?.responseFlags) {\n" +
                                "    abort {}\n" +
                                "  }\n" +
                                "  extractor {\n" +
                                "    if (parsed?.response?.responseCode) {\n" +
                                "      tag 'status.code': parsed?.response?.responseCode as int\n" +
                                "    }\n" +
                                "    tag 'response.flag': (parsed?.commonProperties?.responseFlags as Map)?.keySet()\n" +
                                "  }\n" +
                                "  sink {\n" +
                                "    sampler {\n" +
                                "      if (parsed?.commonProperties?.responseFlags) {\n" +
                                "        // use service:errorCode as sampler id so that each service:errorCode has its own sampler,\n" +
                                "        // e.g. checkoutservice:[upstreamConnectionFailure], checkoutservice:[upstreamRetryLimitExceeded]\n" +
                                "        rateLimit(\"${log.service}:${(parsed?.commonProperties?.responseFlags as Map)?.keySet()}\") {\n" +
                                "          rpm 100\n" +
                                "        }\n" +
                                "      } else {\n" +
                                "        // use service:responseCode as sampler id so that each service:responseCode has its own sampler,\n" +
                                "        // e.g. checkoutservice:500, checkoutservice:404.\n" +
                                "        rateLimit(\"${log.service}:${parsed?.response?.responseCode}\") {\n" +
                                "          rpm 100\n" +
                                "        }\n" +
                                "      }\n" +
                                "    }\n" +
                                "  }\n" +
                                "}\n",
                },
                new String[] {
                        "extractor-slowSql",
                        "filter {\n" +
                                "        json{\n" +
                                "        }\n" +
                                "        extractor{\n" +
                                "          layer parsed.layer as String\n" +
                                "          service parsed.service as String\n" +
                                "          timestamp parsed.time as String\n" +
                                "          if (tag(\"LOG_KIND\") == \"SLOW_SQL\") {\n" +
                                "             slowSql {\n" +
                                "                      id parsed.id as String\n" +
                                "                      statement parsed.statement as String\n" +
                                "                      latency parsed.query_time as Long\n" +
                                "                     }\n" +
                                "          }\n" +
                                "        }\n" +
                                "      }"
                },
                new String[] {
                    "extractor-patterned-timestamp",
                    "filter {\n" +
                        "  extractor {\n" +
                        "    service \"test\"\n" +
                        "    instance \"test\"\n" +
                        "    endpoint \"test\"\n" +
                        "    timestamp \"2023-11-01 22:10:10\", \"yyyy-MM-dd HH:mm:ss\"\n" +
                        "  }\n" +
                        "}",
                }
        );
    }

    final ModuleManager manager = mock(ModuleManager.class);

    @BeforeEach
    public void setup() {
        Whitebox.setInternalState(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));
        when(manager.find(CoreModule.NAME).provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(manager.find(CoreModule.NAME).provider().getService(SourceReceiver.class))
                .thenReturn(mock(SourceReceiver.class));
        when(manager.find(CoreModule.NAME).provider().getService(ConfigService.class))
                .thenReturn(mock(ConfigService.class));
        when(manager.find(CoreModule.NAME)
                .provider()
                .getService(ConfigService.class)
                .getSearchableLogsTags())
                .thenReturn("");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testDslStaticCompile(String name, String script) throws ModuleStartException {
        final DSL dsl = DSL.of(manager, new LogAnalyzerModuleConfig(), script);
        Whitebox.setInternalState(
            Whitebox.getInternalState(dsl, "filterSpec"), "sinkListenerFactories", Collections.emptyList()
        );

        dsl.bind(new Binding().log(LogData.newBuilder().build()));
        dsl.evaluate();
    }
}
