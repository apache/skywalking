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

package org.apache.skywalking.oap.server.receiver.telegraf;

import com.google.common.collect.ImmutableMap;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.telegraf.mock.MockModuleManager;
import org.apache.skywalking.oap.server.receiver.telegraf.mock.MockModuleProvider;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.TelegrafModuleConfig;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.TelegrafServiceHandler;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.pojo.TelegrafData;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.pojo.TelegrafDatum;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonParseException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class TelegrafMetricsTest {

    protected CoreModuleProvider moduleProvider;
    protected ModuleManager moduleManager;
    protected MeterSystem meterSystem;
    protected TelegrafServiceHandler telegrafServiceHandler;

    private List<AcceptableValue<?>> values = new ArrayList<>();

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
                new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @BeforeEach
    public void setupMetrics() throws Throwable {
        moduleProvider = Mockito.mock(CoreModuleProvider.class);
        moduleManager = new MockModuleManager() {
            @Override
            protected void init() {
            register(CoreModule.NAME, () -> new MockModuleProvider() {
                @Override
                protected void register() {
                    registerServiceImplementation(NamingControl.class, new NamingControl(
                            512, 512, 512, new EndpointNameGrouping()));
                }
            });
            register(TelemetryModule.NAME, () -> new MockModuleProvider() {
                @Override
                protected void register() {
                    registerServiceImplementation(MetricsCreator.class, new MetricsCreatorNoop());
                }
            });
            }
        };

        // prepare the context
        meterSystem = Mockito.mock(MeterSystem.class);
        Whitebox.setInternalState(MetricsStreamProcessor.class, "PROCESSOR",
                Mockito.spy(MetricsStreamProcessor.getInstance()));
        CoreModule coreModule = Mockito.spy(CoreModule.class);

        Whitebox.setInternalState(coreModule, "loadedProvider", moduleProvider);

        telegrafServiceHandler = buildTelegrafServiceHandler();
    }

    protected TelegrafServiceHandler buildTelegrafServiceHandler() throws Exception {
        // load context
        List<Rule> telegrafConfigs = Rules.loadRules(TelegrafModuleConfig.CONFIG_PATH, Arrays.asList("vm"));
        return new TelegrafServiceHandler(moduleManager, meterSystem, telegrafConfigs);
    }

    private TelegrafData assertTelegrafJSONConvert(String jsonMessage) throws IOException {
        Assertions.assertNotNull(jsonMessage);
        ObjectMapper mapper = new ObjectMapper();
        TelegrafData telegrafData = mapper.readValue(jsonMessage, TelegrafData.class);
        Assertions.assertNotNull(telegrafData);
        return telegrafData;
    }

    private void assertSample(Sample sample, String... name) {
        Assertions.assertNotNull(sample);
        Assertions.assertTrue(Arrays.asList(name).contains(sample.getName()));
        Assertions.assertEquals(sample.getLabels(), ImmutableMap.copyOf(Collections.singletonMap("host", "localHost")));
        Assertions.assertTrue(sample.getValue() >= 0);
        Assertions.assertTrue(sample.getTimestamp() >= 0);
    }

    private void assertConvertToSample(TelegrafData telegrafData, int totalSamplesPerMetrics, String... name) {
        Assertions.assertNotNull(telegrafData);
        List<TelegrafDatum> metrics = telegrafData.getMetrics();
        Assertions.assertNotNull(metrics);
        for (TelegrafDatum t : metrics) {
            boolean convert = false;
            List<Sample> samples = telegrafServiceHandler.convertTelegraf(t);
            Assertions.assertEquals(samples.size(), totalSamplesPerMetrics);
            for (Sample s : samples) {
                assertSample(s, name);
                convert = true;
            }
            if (!convert) {
                // Throw exception when key not found
                throw new AssertionError("Failed to convert json telegraf message to Sample.");
            }
        }
    }

    private void assertConvertToSampleFamily(TelegrafData telegrafData, int sampleFamilySize, int samplePerSampleFamily, String... name) {
        Assertions.assertNotNull(telegrafData);
        List<ImmutableMap<String, SampleFamily>> sampleFamilyCollections = telegrafServiceHandler.convertSampleFamily(telegrafData);
        Assertions.assertNotNull(sampleFamilyCollections);
        int actualSize = 0;
        for (ImmutableMap<String, SampleFamily> samples : sampleFamilyCollections) {
            samples.forEach((k, v) -> Assertions.assertEquals(v.samples.length, samplePerSampleFamily));
            actualSize += samples.size();
        }
        Assertions.assertEquals(actualSize, sampleFamilySize);
        sampleFamilyCollections.forEach(sampleFamily -> {
            for (Map.Entry<String, SampleFamily> entry : sampleFamily.entrySet()) {
                String key = entry.getKey();
                SampleFamily value = entry.getValue();
                boolean convert = false;
                Assertions.assertTrue(Arrays.asList(name).contains(key));
                for (Sample s : value.samples) {
                    assertSample(s, name);
                    convert = true;
                }
                if (!convert) {
                    // Throw exception when key not found
                    throw new AssertionError("Failed to convert json telegraf message to Sample.");
                }
            }
        });
    }

    @Test
    public void testOneMemMetrics() throws Throwable {
        String oneMemMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"available\":6047739904,\"available_percent\":35.41215070500567,\"total\":17078149120,\"used\":11030409216,\"used_percent\":64.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        TelegrafData telegrafData = assertTelegrafJSONConvert(oneMemMetrics);
        String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
        assertConvertToSample(telegrafData, 5, metricsNames);
        assertConvertToSampleFamily(telegrafData, 5, 1, metricsNames);
    }

    @Test
    public void testMultipleMemMetrics() throws Throwable {
        String threeMemMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"available\":6047739904,\"available_percent\":35.41215070500567,\"total\":17078149120,\"used\":11030409216,\"used_percent\":64.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"available\":6047563904,\"available_percent\":56.41215070500567,\"total\":27048549120,\"used\":340364409216,\"used_percent\":44.58454929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663491390}, " +
                "{\"fields\":" +
                "{\"available\":5047739904,\"available_percent\":43.41215070500567,\"total\":46078149120,\"used\":45030409216,\"used_percent\":23.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1453365320}]}";

        TelegrafData telegrafData = assertTelegrafJSONConvert(threeMemMetrics);
        String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
        assertConvertToSample(telegrafData, 5, metricsNames);
        assertConvertToSampleFamily(telegrafData, 15, 1, metricsNames);
    }

    @Test
    public void testOneCpuMetrics() throws Throwable {
        String oneCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        TelegrafData telegrafData = assertTelegrafJSONConvert(oneCpuMetrics);
        String[] metricsNames = {"cpu_usage_idle", "cpu_usage_irq", "cpu_usage_system", "cpu_usage_user"};
        assertConvertToSample(telegrafData, 4, metricsNames);
        assertConvertToSampleFamily(telegrafData, 4, 1, metricsNames);
    }

    @Test
    public void testMultipleCpuMetrics() throws Throwable {
        String twoCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":45.32710280373831,\"usage_irq\":0.4515344797507788,\"usage_system\":3.4533956386292835,\"usage_user\":45.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1453365320}]}";

        TelegrafData telegrafData = assertTelegrafJSONConvert(twoCpuMetrics);
        String[] metricsNames = {"cpu_usage_idle", "cpu_usage_irq", "cpu_usage_system", "cpu_usage_user"};
        assertConvertToSample(telegrafData, 4, metricsNames);
        assertConvertToSampleFamily(telegrafData, 8, 1, metricsNames);
    }

    @Test
    public void testMultipleCpuMetricsWithSameTimestamp() throws Throwable {
        String twoCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":67.32710280373831,\"usage_irq\":0.5415264797507788,\"usage_system\":1.6533956386292835,\"usage_user\":76.54797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":74.32710280373831,\"usage_irq\":1.2535264797507788,\"usage_system\":4.5633956386292835,\"usage_user\":54.23797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":45.32710280373831,\"usage_irq\":0.4515344797507788,\"usage_system\":3.4533956386292835,\"usage_user\":45.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        TelegrafData telegrafData = assertTelegrafJSONConvert(twoCpuMetrics);
        String[] metricsNames = {"cpu_usage_idle", "cpu_usage_irq", "cpu_usage_system", "cpu_usage_user"};
        assertConvertToSample(telegrafData, 4, metricsNames);
        assertConvertToSampleFamily(telegrafData, 4, 4, metricsNames);
    }

    @Test
    public void testInvalidJSONConvert() {
        String invalidMetrics = "This is a invalid metrics message";

        Assertions.assertThrows(
                JsonParseException.class, () -> assertTelegrafJSONConvert(invalidMetrics),
                "Expected JsonParseException to throw, but it didn't.");
    }

    @Test
    public void testInvalidSampleNames() {
        String oneCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        Assertions.assertThrows(
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(oneCpuMetrics);
                    String[] wrongMetricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 4, wrongMetricsNames);
                },
                "Expected AssertionError to throw, but it didn't.");
    }

    @Test
    public void testInvalidSampleFamilyNames() {
        String oneMemMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"available\":6047739904,\"available_percent\":35.41215070500567,\"total\":17078149120,\"used\":11030409216,\"used_percent\":64.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        Assertions.assertThrows(
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(oneMemMetrics);
                    String[] wrongMetricsNames = {"cpu_usage_idle", "cpu_usage_irq", "cpu_usage_system", "cpu_usage_user"};
                    assertConvertToSampleFamily(telegrafData, 5, 1, wrongMetricsNames);
                },
                "Expected AssertionError to throw, but it didn't.");
    }

    @Test
    public void testWrongSampleNumbers() {
        String twoCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":45.32710280373831,\"usage_irq\":0.4515344797507788,\"usage_system\":3.4533956386292835,\"usage_user\":45.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1453365320}]}";

        Assertions.assertThrows(
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(twoCpuMetrics);
                    String[] metricsNames = {"cpu_usage_idle", "cpu_usage_irq", "cpu_usage_system", "cpu_usage_user"};
                    assertConvertToSample(telegrafData, 6, metricsNames);
                },
                "Expected AssertionError to throw, but it didn't.");
    }

    @Test
    public void testWrongSampleFamilySize() {
        String threeMemMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"available\":6047739904,\"available_percent\":35.41215070500567,\"total\":17078149120,\"used\":11030409216,\"used_percent\":64.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"available\":6047563904,\"available_percent\":56.41215070500567,\"total\":27048549120,\"used\":340364409216,\"used_percent\":44.58454929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663491390}, " +
                "{\"fields\":" +
                "{\"available\":5047739904,\"available_percent\":43.41215070500567,\"total\":46078149120,\"used\":45030409216,\"used_percent\":23.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1453365320}]}";

        Assertions.assertThrows(
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(threeMemMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 3, 1, metricsNames);
                },
                "Expected AssertionError to throw, but it didn't.");
    }

    @Test
    public void testWrongSampleNumbersOfSampleFamily() {
        String threeMemMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"available\":6047739904,\"available_percent\":35.41215070500567,\"total\":17078149120,\"used\":11030409216,\"used_percent\":64.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"available\":6047563904,\"available_percent\":56.41215070500567,\"total\":27048549120,\"used\":340364409216,\"used_percent\":44.58454929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663491390}, " +
                "{\"fields\":" +
                "{\"available\":5047739904,\"available_percent\":43.41215070500567,\"total\":46078149120,\"used\":45030409216,\"used_percent\":23.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1453365320}]}";

        Assertions.assertThrows(
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(threeMemMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 15, 2, metricsNames);
                },
                "Expected AssertionError to throw, but it didn't.");
    }

    @Test
    public void testWrongSampleFamilySizeWithSameTimestamp() {
        String fourCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":67.32710280373831,\"usage_irq\":0.5415264797507788,\"usage_system\":1.6533956386292835,\"usage_user\":76.54797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":74.32710280373831,\"usage_irq\":1.2535264797507788,\"usage_system\":4.5633956386292835,\"usage_user\":54.23797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":45.32710280373831,\"usage_irq\":0.4515344797507788,\"usage_system\":3.4533956386292835,\"usage_user\":45.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        Assertions.assertThrows(
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(fourCpuMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 3, 4, metricsNames);
                },
                "Expected AssertionError to throw, but it didn't.");
    }

    @Test
    public void testWrongSampleNumbersOfSampleFamilyWithSameTimestamp() {
        String fourCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":67.32710280373831,\"usage_irq\":0.5415264797507788,\"usage_system\":1.6533956386292835,\"usage_user\":76.54797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":74.32710280373831,\"usage_irq\":1.2535264797507788,\"usage_system\":4.5633956386292835,\"usage_user\":54.23797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}, " +
                "{\"fields\":" +
                "{\"usage_idle\":45.32710280373831,\"usage_irq\":0.4515344797507788,\"usage_system\":3.4533956386292835,\"usage_user\":45.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        Assertions.assertThrows(
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(fourCpuMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 4, 2, metricsNames);
                },
                "Expected AssertionError to throw, but it didn't.");
    }

}
