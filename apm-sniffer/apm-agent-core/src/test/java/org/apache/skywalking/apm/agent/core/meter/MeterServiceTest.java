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

package org.apache.skywalking.apm.agent.core.meter;

import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;

@RunWith(TracingSegmentRunner.class)
public class MeterServiceTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Rule
    public GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    private MeterService registryService = new MeterService();
    private List<MeterData> upstreamMeters;

    private MeterSender sender = new MeterSender();

    private MeterReportServiceGrpc.MeterReportServiceImplBase serviceImplBase = new MeterReportServiceGrpc.MeterReportServiceImplBase() {
        @Override
        public StreamObserver<MeterData> collect(final StreamObserver<Commands> responseObserver) {
            return new StreamObserver<MeterData>() {
                @Override
                public void onNext(MeterData value) {
                    upstreamMeters.add(value);
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(Commands.getDefaultInstance());
                    responseObserver.onCompleted();
                }
            };
        }
    };

    @BeforeClass
    public static void beforeClass() {
        Config.Meter.ACTIVE = true;
        Config.Agent.SERVICE_NAME = "testService";
        Config.Agent.INSTANCE_NAME = "testServiceInstance";
    }

    @AfterClass
    public static void afterClass() {
        Config.Agent.KEEP_TRACING = false;
        ServiceManager.INSTANCE.shutdown();
    }

    @Before
    public void setUp() throws Throwable {
        spy(sender);
        spy(registryService);

        Whitebox.setInternalState(
            sender, "meterReportServiceStub", MeterReportServiceGrpc.newStub(grpcServerRule.getChannel()));
        Whitebox.setInternalState(sender, "status", GRPCChannelStatus.CONNECTED);

        Whitebox.setInternalState(registryService, "sender", sender);

        upstreamMeters = new ArrayList<>();
    }

    @Test
    public void testRegister() {
        grpcServerRule.getServiceRegistry().addService(serviceImplBase);

        // Register null
        registryService.register(null);
        assertThat(upstreamMeters.size(), is(0));

        // Empty meter
        registryService.run();
        assertThat(upstreamMeters.size(), is(0));

        // Add one
        final MeterId counterId = new MeterId("test1", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));
        final Counter counter = new Counter(counterId, CounterMode.INCREMENT);
        counter.increment(2);
        registryService.register(counter);
        registryService.run();
        assertThat(upstreamMeters.size(), is(1));
        isSameWithCounter(upstreamMeters.get(0), true, counterId, 2);

        // Add second
        upstreamMeters.clear();
        final MeterId percentileId = new MeterId("test2", MeterType.HISTOGRAM, Arrays.asList(new MeterTag("k1", "v1")));
        final Histogram histogram = new Histogram(percentileId, Arrays.asList(2d));
        registryService.register(histogram);
        histogram.addValue(3);
        registryService.run();
        assertThat(upstreamMeters.size(), is(2));
        for (int i = 0; i < upstreamMeters.size(); i++) {
            if (Objects.equals(upstreamMeters.get(i).getMetricCase(), MeterData.MetricCase.HISTOGRAM)) {
                isSameWithHistogram(upstreamMeters.get(i), i == 0, percentileId, 2d, 1L);
            } else {
                isSameWithCounter(upstreamMeters.get(i), i == 0, counterId, 2);
            }
        }
    }

    @Test
    public void testMeterSizeAndShutdown() throws Throwable {
        final Map<MeterId, BaseMeter> map = Whitebox.getInternalState(registryService, "meterMap");
        map.clear();

        // Check max meter size
        for (Integer i = 0; i < Config.Meter.MAX_METER_SIZE + 1; i++) {
            final MeterId counterId = new MeterId("test_" + i, MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));
            final Counter counter = new Counter(counterId, CounterMode.INCREMENT);
            registryService.register(counter);
        }
        assertThat(map.size(), is(Config.Meter.MAX_METER_SIZE));

        // Check shutdown
        registryService.shutdown();
        assertThat(map.size(), is(0));
    }

    /**
     * Check counter message
     */
    private void isSameWithCounter(MeterData meterData, boolean firstData, MeterId meterId, long count) {
        Assert.assertNotNull(meterData);
        if (firstData) {
            Assert.assertEquals(meterData.getService(), "testService");
            Assert.assertEquals(meterData.getServiceInstance(), "testServiceInstance");
            Assert.assertTrue(meterData.getTimestamp() > 0);
        } else {
            Assert.assertEquals(meterData.getService(), "");
            Assert.assertEquals(meterData.getServiceInstance(), "");
            Assert.assertTrue(meterData.getTimestamp() == 0L);
        }

        Assert.assertEquals(meterData.getMetricCase(), MeterData.MetricCase.SINGLEVALUE);
        Assert.assertNotNull(meterData.getSingleValue());
        final MeterSingleValue singleValue = meterData.getSingleValue();

        Assert.assertEquals(singleValue.getName(), meterId.getName());
        Assert.assertEquals(singleValue.getLabelsList(), meterId.transformTags());
        Assert.assertEquals(singleValue.getValue(), count, 0.0);
    }

    /**
     * Check histogram message
     */
    public void isSameWithHistogram(MeterData meterData, boolean firstData, MeterId meterId, Object... values) {
        Assert.assertNotNull(meterData);
        if (firstData) {
            Assert.assertEquals(meterData.getService(), "testService");
            Assert.assertEquals(meterData.getServiceInstance(), "testServiceInstance");
            Assert.assertTrue(meterData.getTimestamp() > 0);
        } else {
            Assert.assertEquals(meterData.getService(), "");
            Assert.assertEquals(meterData.getServiceInstance(), "");
            Assert.assertTrue(meterData.getTimestamp() == 0L);
        }

        Assert.assertEquals(meterData.getMetricCase(), MeterData.MetricCase.HISTOGRAM);
        Assert.assertNotNull(meterData.getSingleValue());
        final MeterHistogram histogram = meterData.getHistogram();

        Assert.assertEquals(histogram.getName(), meterId.getName());
        Assert.assertEquals(histogram.getLabelsList(), meterId.transformTags());
        for (int i = 0; i < values.length; i += 2) {
            final MeterBucketValue bucketValue = histogram.getValues(i);
            Assert.assertNotNull(bucketValue);
            Assert.assertEquals(bucketValue.getBucket(), (double) values[i], 0.0);
            Assert.assertEquals(bucketValue.getCount(), values[i + 1]);
        }
    }

}
