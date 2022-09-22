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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice;

import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.CacheReadLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.core.source.CacheAccess;
import org.apache.skywalking.oap.server.core.source.VirtualCacheOperation;
import org.apache.skywalking.oap.server.core.source.CacheSlowAccess;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Assert;
import org.junit.Test;

public class VirtualCacheProcessorTest {

    @Test
    public void testEmptySpan() {
        SpanObject spanObject = SpanObject.newBuilder().setSpanLayer(SpanLayer.Cache).build();
        SegmentObject segmentObject = SegmentObject.newBuilder().build();
        VirtualCacheProcessor cacheVirtualServiceProcessor = buildCacheVirtualServiceProcessor();
        cacheVirtualServiceProcessor.prepareVSIfNecessary(spanObject, segmentObject);
        ArrayList<Source> sources = new ArrayList<>();
        cacheVirtualServiceProcessor.emitTo(sources::add);
        Assert.assertTrue(sources.isEmpty());
    }

    @Test
    public void testExitSpan() {
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setSpanLayer(SpanLayer.Cache)
                                          .setSpanId(0)
                                          .addAllTags(buildTags())
                                          .setSpanType(SpanType.Exit)
                                          .setPeerBytes(
                                              ByteString.copyFrom("127.0.0.1:6379".getBytes(StandardCharsets.UTF_8)))
                                          .setStartTime(getTimeInMillis("2022-09-12 14:13:12.790"))
                                          .setEndTime(getTimeInMillis("2022-09-12 14:13:13.790"))
                                          .build();
        SegmentObject segmentObject = SegmentObject.newBuilder().setTraceId("trace-id").build();
        VirtualCacheProcessor cacheVirtualServiceProcessor = buildCacheVirtualServiceProcessor();
        cacheVirtualServiceProcessor.prepareVSIfNecessary(spanObject, segmentObject);
        ArrayList<Source> sources = new ArrayList<>();
        cacheVirtualServiceProcessor.emitTo(sources::add);
        Assert.assertEquals(sources.size(), 3);

        ServiceMeta serviceMeta = (ServiceMeta) sources.get(0);
        Assert.assertEquals("127.0.0.1:6379", serviceMeta.getName());
        Assert.assertEquals(202209121413L, serviceMeta.getTimeBucket());
        Assert.assertEquals(Layer.VIRTUAL_CACHE, serviceMeta.getLayer());

        CacheSlowAccess slowAccess = (CacheSlowAccess) sources.get(1);
        Assert.assertEquals("MTI3LjAuMC4xOjYzNzk=.0", slowAccess.getCacheServiceId());
        Assert.assertEquals(1000, slowAccess.getLatency());
        Assert.assertEquals(20220912141312L, slowAccess.getTimeBucket());
        Assert.assertEquals(VirtualCacheOperation.Read, slowAccess.getOperation());
        Assert.assertNotNull(slowAccess.getTraceId());
        Assert.assertNotNull(slowAccess.getCommand());
        Assert.assertNotNull(slowAccess.getKey());

        CacheAccess cacheAccess = (CacheAccess) sources.get(2);
        Assert.assertEquals("127.0.0.1:6379", cacheAccess.getName());
        Assert.assertEquals(1000, cacheAccess.getLatency());
        Assert.assertEquals(202209121413L, cacheAccess.getTimeBucket());
        Assert.assertNotNull(cacheAccess.getOperation());
    }

    @Test
    public void testExitSpanLessThreshold() {
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setSpanLayer(SpanLayer.Cache)
                                          .setSpanId(0)
                                          .addAllTags(buildTags())
                                          .setSpanType(SpanType.Exit)
                                          .setPeerBytes(
                                              ByteString.copyFrom("127.0.0.1:6379".getBytes(StandardCharsets.UTF_8)))
                                          .setStartTime(getTimeInMillis("2022-09-12 14:13:12.790"))
                                          .setEndTime(getTimeInMillis("2022-09-12 14:13:12.793"))
                                          .build();
        SegmentObject segmentObject = SegmentObject.newBuilder().build();
        VirtualCacheProcessor cacheVirtualServiceProcessor = buildCacheVirtualServiceProcessor();
        cacheVirtualServiceProcessor.prepareVSIfNecessary(spanObject, segmentObject);
        ArrayList<Source> sources = new ArrayList<>();
        cacheVirtualServiceProcessor.emitTo(sources::add);
        Assert.assertEquals(sources.size(), 2);

        ServiceMeta serviceMeta = (ServiceMeta) sources.get(0);
        Assert.assertEquals("127.0.0.1:6379", serviceMeta.getName());
        Assert.assertEquals(202209121413L, serviceMeta.getTimeBucket());
        Assert.assertEquals(Layer.VIRTUAL_CACHE, serviceMeta.getLayer());

        CacheAccess cacheAccess = (CacheAccess) sources.get(1);
        Assert.assertEquals("127.0.0.1:6379", cacheAccess.getName());
        Assert.assertEquals(3, cacheAccess.getLatency());
        Assert.assertEquals(202209121413L, cacheAccess.getTimeBucket());
        Assert.assertNotNull(cacheAccess.getOperation());
    }

    @Test
    public void testLocalSpan() {
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setSpanLayer(SpanLayer.Cache)
                                          .setSpanId(0)
                                          .addAllTags(buildTags())
                                          .setSpanType(SpanType.Local)
                                          .setStartTime(getTimeInMillis("2022-09-12 14:13:12.790"))
                                          .setEndTime(getTimeInMillis("2022-09-12 14:13:13.790"))
                                          .build();
        SegmentObject segmentObject = SegmentObject.newBuilder().build();
        VirtualCacheProcessor cacheVirtualServiceProcessor = buildCacheVirtualServiceProcessor();
        cacheVirtualServiceProcessor.prepareVSIfNecessary(spanObject, segmentObject);
        ArrayList<Source> sources = new ArrayList<>();
        cacheVirtualServiceProcessor.emitTo(sources::add);
        Assert.assertEquals(sources.size(), 3);

        ServiceMeta serviceMeta = (ServiceMeta) sources.get(0);
        Assert.assertEquals("redis-local", serviceMeta.getName());
        Assert.assertEquals(202209121413L, serviceMeta.getTimeBucket());
        Assert.assertEquals(Layer.VIRTUAL_CACHE, serviceMeta.getLayer());

        CacheSlowAccess slowAccess = (CacheSlowAccess) sources.get(1);
        Assert.assertEquals("cmVkaXMtbG9jYWw=.0", slowAccess.getCacheServiceId());
        Assert.assertEquals(1000, slowAccess.getLatency());
        Assert.assertEquals(20220912141312L, slowAccess.getTimeBucket());
        Assert.assertEquals(VirtualCacheOperation.Read, slowAccess.getOperation());
        Assert.assertNotNull(slowAccess.getTraceId());
        Assert.assertNotNull(slowAccess.getCommand());
        Assert.assertNotNull(slowAccess.getKey());

        CacheAccess cacheAccess = (CacheAccess) sources.get(2);
        Assert.assertEquals("redis-local", cacheAccess.getName());
        Assert.assertEquals(1000, cacheAccess.getLatency());
        Assert.assertEquals(202209121413L, cacheAccess.getTimeBucket());
        Assert.assertNotNull(cacheAccess.getOperation());
    }

    private long getTimeInMillis(String s) {
        return DateTime.parse(s, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS")).getMillis();
    }

    private List<KeyStringValuePair> buildTags() {
        return Arrays.asList(
            KeyStringValuePair.newBuilder().setKey(SpanTags.CACHE_KEY).setValue("test_key").build(),
            KeyStringValuePair.newBuilder().setKey(SpanTags.CACHE_TYPE).setValue("redis").build(),
            KeyStringValuePair.newBuilder().setKey(SpanTags.CACHE_CMD).setValue("get").build(),
            KeyStringValuePair.newBuilder().setKey(SpanTags.CACHE_OP).setValue("read").build()

        );
    }

    private VirtualCacheProcessor buildCacheVirtualServiceProcessor() {
        NamingControl namingControl = new NamingControl(512, 512, 512, new EndpointNameGrouping());
        AnalyzerModuleConfig config = new AnalyzerModuleConfig();
        config.setCacheReadLatencyThresholdsAndWatcher(new CacheReadLatencyThresholdsAndWatcher("default:10", null));
        return new VirtualCacheProcessor(namingControl, config);
    }

}
