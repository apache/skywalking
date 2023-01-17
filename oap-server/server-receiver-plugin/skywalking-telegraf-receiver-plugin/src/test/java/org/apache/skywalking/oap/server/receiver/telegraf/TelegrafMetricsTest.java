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
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonParseException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class TelegrafMetricsTest {

    protected CoreModuleProvider moduleProvider;
    protected ModuleManager moduleManager;
    protected MeterSystem meterSystem;
    protected TelegrafServiceHandler telegrafServiceHandler;

    private List<AcceptableValue> values = new ArrayList<>();

    @BeforeClass
    public static void setup() {
        MeterEntity.setNamingControl(
                new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @AfterClass
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Before
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
        doNothing().when(MetricsStreamProcessor.getInstance()).create(any(), (StreamDefinition) any(), any());
        CoreModule coreModule = Mockito.spy(CoreModule.class);

        Whitebox.setInternalState(coreModule, "loadedProvider", moduleProvider);
        when(moduleProvider.getService(MeterSystem.class)).thenReturn(meterSystem);

        telegrafServiceHandler = buildTelegrafServiceHandler();
    }

    protected TelegrafServiceHandler buildTelegrafServiceHandler() throws Exception {
        // Notifies meter system received metric
        doAnswer(invocationOnMock -> {
            values.add(invocationOnMock.getArgument(0, AcceptableValue.class));
            return null;
        }).when(meterSystem).doStreamingCalculation(any());

        // load context
        List<Rule> telegrafConfigs = Rules.loadRules(TelegrafModuleConfig.CONFIG_PATH, Arrays.asList("vm"));
        return new TelegrafServiceHandler(moduleManager, meterSystem, telegrafConfigs);
    }

    private TelegrafData assertTelegrafJSONConvert(String jsonMessage) throws Exception {
        Assert.assertNotNull(jsonMessage);
        ObjectMapper mapper = new ObjectMapper();
        TelegrafData telegrafData = mapper.readValue(jsonMessage, TelegrafData.class);
        Assert.assertNotNull(telegrafData);
        return telegrafData;
    }

    private void assertSample(Sample sample, String... name) {
        Assert.assertNotNull(sample);
        Assert.assertTrue(Arrays.asList(name).contains(sample.getName()));
        Assert.assertEquals(sample.getLabels(), ImmutableMap.copyOf(Collections.singletonMap("host", "localHost")));
        Assert.assertTrue(sample.getValue() >= 0);
        Assert.assertTrue(sample.getTimestamp() >= 0);
    }

    private void assertConvertToSample(TelegrafData telegrafData, int totalSamplesPerMetrics, String... name) {
        Assert.assertNotNull(telegrafData);
        List<TelegrafDatum> metrics = telegrafData.getMetrics();
        Assert.assertNotNull(metrics);
        for (TelegrafDatum t : metrics) {
            boolean convert = false;
            List<Sample> samples = telegrafServiceHandler.convertTelegraf(t);
            Assert.assertEquals(samples.size(), totalSamplesPerMetrics);
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
        Assert.assertNotNull(telegrafData);
        List<ImmutableMap<String, SampleFamily>> sampleFamilyCollections = telegrafServiceHandler.convertSampleFamily(telegrafData);
        Assert.assertNotNull(sampleFamilyCollections);
        int actualSize = 0;
        for (ImmutableMap<String, SampleFamily> samples : sampleFamilyCollections) {
            samples.forEach((k, v) -> Assert.assertEquals(v.samples.length, samplePerSampleFamily));
            actualSize += samples.size();
        }
        Assert.assertEquals(actualSize, sampleFamilySize);
        sampleFamilyCollections.forEach(sampleFamily -> {
            for (Map.Entry<String, SampleFamily> entry : sampleFamily.entrySet()) {
                String key = entry.getKey();
                SampleFamily value = entry.getValue();
                boolean convert = false;
                Assert.assertTrue(Arrays.asList(name).contains(key));
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

        Assert.assertThrows("Expected JsonParseException to throw, but it didn't.",
                JsonParseException.class, () -> assertTelegrafJSONConvert(invalidMetrics));
    }

    @Test
    public void testInvalidSampleNames() {
        String oneCpuMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"usage_idle\":95.32710280373831,\"usage_irq\":0.3115264797507788,\"usage_system\":1.7133956386292835,\"usage_user\":2.64797507788162}," +
                "\"name\":\"cpu\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        Assert.assertThrows("Expected AssertionError to throw, but it didn't.",
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(oneCpuMetrics);
                    String[] wrongMetricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 4, wrongMetricsNames);
                });
    }

    @Test
    public void testInvalidSampleFamilyNames() {
        String oneMemMetrics = "{\"metrics\":" +
                "[{\"fields\":" +
                "{\"available\":6047739904,\"available_percent\":35.41215070500567,\"total\":17078149120,\"used\":11030409216,\"used_percent\":64.58784929499433}," +
                "\"name\":\"mem\"," +
                "\"tags\":{\"host\":\"localHost\"}," +
                "\"timestamp\":1663391390}]}";

        Assert.assertThrows("Expected AssertionError to throw, but it didn't.",
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(oneMemMetrics);
                    String[] wrongMetricsNames = {"cpu_usage_idle", "cpu_usage_irq", "cpu_usage_system", "cpu_usage_user"};
                    assertConvertToSampleFamily(telegrafData, 5, 1, wrongMetricsNames);
                });
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

        Assert.assertThrows("Expected AssertionError to throw, but it didn't.",
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(twoCpuMetrics);
                    String[] metricsNames = {"cpu_usage_idle", "cpu_usage_irq", "cpu_usage_system", "cpu_usage_user"};
                    assertConvertToSample(telegrafData, 6, metricsNames);
                });
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

        Assert.assertThrows("Expected AssertionError to throw, but it didn't.",
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(threeMemMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 3, 1, metricsNames);
                });
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

        Assert.assertThrows("Expected AssertionError to throw, but it didn't.",
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(threeMemMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 15, 2, metricsNames);
                });
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

        Assert.assertThrows("Expected AssertionError to throw, but it didn't.",
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(fourCpuMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 3, 4, metricsNames);
                });
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

        Assert.assertThrows("Expected AssertionError to throw, but it didn't.",
                AssertionError.class, () -> {
                    TelegrafData telegrafData = assertTelegrafJSONConvert(fourCpuMetrics);
                    String[] metricsNames = {"mem_available", "mem_available_percent", "mem_total", "mem_used", "mem_used_percent"};
                    assertConvertToSample(telegrafData, 5, metricsNames);
                    assertConvertToSampleFamily(telegrafData, 4, 2, metricsNames);
                });
    }

}
