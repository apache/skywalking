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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.Analyzer;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramPercentileFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgLabeledFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AnalyzerTest {

    @Mock
    private ModuleManager moduleManager;
    private MeterSystem meterSystem;
    private Analyzer analyzer;

    @Before
    public void setup() throws StorageException {
        meterSystem = spy(new MeterSystem(moduleManager));
        Whitebox.setInternalState(MetricsStreamProcessor.class, "PROCESSOR",
                                  Mockito.spy(MetricsStreamProcessor.getInstance())
        );
        doNothing().when(MetricsStreamProcessor.getInstance()).create(any(), (StreamDefinition) any(), any());

    }

    @Test
    public void testSingle() {
        analyzer = Analyzer.build(
            "sum_service_instance",
            "http_success_request.sum(['region', 'idc']).instance(['idc'] , ['region'])",
            meterSystem
        );
        ImmutableMap<String, SampleFamily> input = ImmutableMap.of(
            "http_success_request", SampleFamilyBuilder.newBuilder(
                Sample.builder().labels(of("idc", "t1")).value(50).build(),
                Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
            ).build()
        );

        Map<String, AvgFunction> actValues = new HashMap<>();
        doAnswer(invocationOnMock -> {
            AvgFunction actValue = (AvgFunction) invocationOnMock.getArgument(
                0, AcceptableValue.class);
            actValues.put(actValue.getEntityId(), actValue);
            return null;
        }).when(meterSystem).doStreamingCalculation(any());
        analyzer.analyse(input);

        AvgFunction t1 = actValues.get(IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("t1", true), ""));
        AvgFunction t1Us = actValues.get(IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("t1", true), "us"));
        AvgFunction t3Cn = actValues.get(IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("t3", true), "cn"));

        Assert.assertEquals(50L, t1.getSummation(), 0.0);
        Assert.assertEquals(1L, t1.getCount(), 0.0);

        Assert.assertEquals(150L, t1Us.getSummation(), 0.0);
        Assert.assertEquals(1L, t1Us.getCount(), 0.0);

        Assert.assertEquals(54L, t3Cn.getSummation(), 0.0);
        Assert.assertEquals(1L, t3Cn.getCount(), 0.0);
    }

    @Test
    public void testLabeled() {
        analyzer = Analyzer.build(
            "sum_service_instance_labels",
            "http_success_request.sum(['region', 'idc' , 'instance']).instance(['idc'] , ['region'])",
            meterSystem
        );
        ImmutableMap<String, SampleFamily> input = ImmutableMap.of(
            "http_success_request", SampleFamilyBuilder.newBuilder(
                Sample.builder().labels(of("idc", "t1")).value(50).build(),
                Sample.builder().labels(of("idc", "t3", "region", "cn", "svc", "catalog")).value(51).name("http_success_request").build(),
                Sample.builder().labels(of("idc", "t1", "region", "us", "svc", "product")).value(50).name("http_success_request").build(),
                Sample.builder().labels(of("idc", "t1", "region", "us", "instance", "10.0.0.1")).value(100).name("http_success_request").build(),
                Sample.builder().labels(of("idc", "t3", "region", "cn", "instance", "10.0.0.1")).value(3).name("http_success_request").build()
            ).build()
        );

        Map<String, AvgLabeledFunction> actValues = new HashMap<>();
        doAnswer(invocationOnMock -> {
            AvgLabeledFunction actValue = (AvgLabeledFunction) invocationOnMock.getArgument(
                0, AcceptableValue.class);
            actValues.put(actValue.getEntityId(), actValue);
            return null;
        }).when(meterSystem).doStreamingCalculation(any());

        analyzer.analyse(input);

        AvgLabeledFunction t1 = actValues.get(IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("t1", true), ""));
        AvgLabeledFunction t1Us = actValues.get(IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("t1", true), "us"));
        AvgLabeledFunction t3Cn = actValues.get(IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("t3", true), "cn"));

        Assert.assertEquals(50L, t1.getSummation().get(""), 0.0);
        Assert.assertEquals(1L, t1.getCount().get(""), 0.0);

        Assert.assertEquals(50L, t1Us.getSummation().get(""), 0.0);
        Assert.assertEquals(100L, t1Us.getSummation().get("10.0.0.1"), 0.0);
        Assert.assertEquals(1L, t1Us.getCount().get(""), 0.0);
        Assert.assertEquals(1L, t1Us.getCount().get("10.0.0.1"), 0.0);

        Assert.assertEquals(51L, t3Cn.getSummation().get(""), 0.0);
        Assert.assertEquals(3L, t3Cn.getSummation().get("10.0.0.1"), 0.0);
        Assert.assertEquals(1L, t3Cn.getCount().get(""), 0.0);
        Assert.assertEquals(1L, t3Cn.getCount().get("10.0.0.1"), 0.0);
    }

    @Test
    public void testHistogramPercentile() {
        analyzer = Analyzer.build(
            "instance_cpu_percentage",
            "instance_cpu_percentage.sum(['le' , 'service' , 'instance']).histogram().histogram_percentile([75,99]).service(['service'])",
            meterSystem
        );
        ImmutableMap<String, SampleFamily> input = ImmutableMap.of(
            "instance_cpu_percentage", SampleFamilyBuilder.newBuilder(
                Sample.builder()
                      .labels(of("le", "0.025", "service", "service1", "instance", "instance1"))
                      .value(100)
                      .name("instance_cpu_percentage")
                      .build(),
                Sample.builder()
                      .labels(of("le", "1.25", "service", "service1", "instance", "instance1"))
                      .value(300)
                      .name("instance_cpu_percentage")
                      .build(),
                Sample.builder()
                      .labels(of("le", "0.75", "service", "service1", "instance", "instance2"))
                      .value(122)
                      .name("instance_cpu_percentage")
                      .build(),
                Sample.builder()
                      .labels(of("le", String.valueOf(Integer.MAX_VALUE), "service", "service1", "instance",
                                 "instance2"
                      ))
                      .value(410)
                      .name("instance_cpu_percentage")
                      .build()
            ).build()
        );

        Map<String, AvgHistogramPercentileFunction> actValues = new HashMap<>();
        doAnswer(invocationOnMock -> {
            AvgHistogramPercentileFunction actValue = (AvgHistogramPercentileFunction) invocationOnMock.getArgument(
                0, AcceptableValue.class);
            if (actValue.getSummation().hasKey("instance1:0")) {
                actValues.put("instance1", actValue);
            } else {
                actValues.put("instance2", actValue);

            }
            return null;
        }).when(meterSystem).doStreamingCalculation(any());

        analyzer.analyse(input);
        Assert.assertEquals(2, actValues.size());
        String expServiceId = IDManager.ServiceID.buildId("service1", true);
        IntList expRanks = new IntList(2) {
            {
                add(75);
                add(99);
            }
        };
        actValues.forEach((key, actValue) -> {
            Assert.assertEquals(expServiceId, actValue.getEntityId());
            Assert.assertThat(expRanks, is(actValue.getRanks()));

        });
        AvgHistogramPercentileFunction instance1 = actValues.get("instance1");
        AvgHistogramPercentileFunction instance2 = actValues.get("instance2");
        Assert.assertEquals(100L, instance1.getSummation().get("instance1:0"), 0.0);
        Assert.assertEquals(178L, instance1.getSummation().get("instance1:750"), 0.0);
        Assert.assertEquals(1L, instance1.getCount().get("instance1:0"), 0.0);
        Assert.assertEquals(1L, instance1.getCount().get("instance1:750"), 0.0);

        Assert.assertEquals(22L, instance2.getSummation().get("instance2:25"), 0.0);
        Assert.assertEquals(110L, instance2.getSummation().get("instance2:1250"), 0.0);
        Assert.assertEquals(1L, instance2.getCount().get("instance2:25"), 0.0);
        Assert.assertEquals(1L, instance2.getCount().get("instance2:1250"), 0.0);
    }
}
