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
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.DatabaseAccess;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.ServiceDatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VirtualDatabaseProcessorTest {

    @Test
    public void testEmptySpan() {
        SpanObject spanObject = SpanObject.newBuilder().setSpanLayer(SpanLayer.Cache).build();
        SegmentObject segmentObject = SegmentObject.newBuilder().build();
        VirtualDatabaseProcessor processor = buildVirtualServiceProcessor();
        processor.prepareVSIfNecessary(spanObject, segmentObject);
        ArrayList<Source> sources = new ArrayList<>();
        processor.emitTo(sources::add);
        Assertions.assertTrue(sources.isEmpty());
    }

    @Test
    public void testExitSpan() {
        SpanObject spanObject = SpanObject.newBuilder()
                .setSpanLayer(SpanLayer.Database)
                .setSpanId(0)
                .addAllTags(buildTags())
                .setSpanType(SpanType.Exit)
                .setPeerBytes(ByteString.copyFrom("127.0.0.1:3306".getBytes(StandardCharsets.UTF_8)))
                .setStartTime(getTimeInMillis("2022-09-12 14:13:12.790"))
                .setEndTime(getTimeInMillis("2022-09-12 14:13:13.790"))
                .build();
        SegmentObject segmentObject = SegmentObject.newBuilder()
                .setTraceId("trace-id-1")
                .setService("test-service")
                .build();
        VirtualDatabaseProcessor processor = buildVirtualServiceProcessor();
        processor.prepareVSIfNecessary(spanObject, segmentObject);
        ArrayList<Source> sources = new ArrayList<>();
        processor.emitTo(sources::add);
        Assertions.assertEquals(sources.size(), 4);

        ServiceMeta serviceMeta = (ServiceMeta) sources.get(0);
        Assertions.assertEquals("127.0.0.1:3306", serviceMeta.getName());
        Assertions.assertEquals(202209121413L, serviceMeta.getTimeBucket());
        Assertions.assertEquals(Layer.VIRTUAL_DATABASE, serviceMeta.getLayer());

        DatabaseAccess databaseAccess = (DatabaseAccess) sources.get(1);
        Assertions.assertEquals("127.0.0.1:3306", databaseAccess.getName());
        Assertions.assertEquals(1000, databaseAccess.getLatency());
        Assertions.assertEquals(202209121413L, databaseAccess.getTimeBucket());

        DatabaseSlowStatement slowStatement = (DatabaseSlowStatement) sources.get(2);
        Assertions.assertEquals("MTI3LjAuMC4xOjMzMDY=.0", slowStatement.getDatabaseServiceId());
        Assertions.assertEquals(1000, slowStatement.getLatency());
        Assertions.assertEquals(20220912141312L, slowStatement.getTimeBucket());
        Assertions.assertEquals("trace-id-1", slowStatement.getTraceId());

        ServiceDatabaseSlowStatement serviceDatabaseSlowStatement = (ServiceDatabaseSlowStatement) sources.get(3);
        Assertions.assertEquals("dGVzdC1zZXJ2aWNl.1", serviceDatabaseSlowStatement.getServiceId());
        Assertions.assertEquals(1000, slowStatement.getLatency());
        Assertions.assertEquals(20220912141312L, slowStatement.getTimeBucket());
        Assertions.assertEquals("trace-id-1", slowStatement.getTraceId());

    }

    @Test
    public void testExitSpanLessThreshold() {
        SpanObject spanObject = SpanObject.newBuilder()
                .setSpanLayer(SpanLayer.Database)
                .setSpanId(0)
                .addAllTags(buildTags())
                .setSpanType(SpanType.Exit)
                .setPeerBytes(ByteString.copyFrom("127.0.0.1:3306".getBytes(StandardCharsets.UTF_8)))
                .setStartTime(getTimeInMillis("2022-09-12 14:13:12.790"))
                .setEndTime(getTimeInMillis("2022-09-12 14:13:12.793"))
                .build();
        SegmentObject segmentObject = SegmentObject.newBuilder().build();
        VirtualDatabaseProcessor processor = buildVirtualServiceProcessor();
        processor.prepareVSIfNecessary(spanObject, segmentObject);
        ArrayList<Source> sources = new ArrayList<>();
        processor.emitTo(sources::add);
        Assertions.assertEquals(sources.size(), 2);

        ServiceMeta serviceMeta = (ServiceMeta) sources.get(0);
        Assertions.assertEquals("127.0.0.1:3306", serviceMeta.getName());
        Assertions.assertEquals(202209121413L, serviceMeta.getTimeBucket());
        Assertions.assertEquals(Layer.VIRTUAL_DATABASE, serviceMeta.getLayer());

        DatabaseAccess databaseAccess = (DatabaseAccess) sources.get(1);

        Assertions.assertEquals("127.0.0.1:3306", databaseAccess.getName());
        Assertions.assertEquals(3, databaseAccess.getLatency());
        Assertions.assertEquals(202209121413L, databaseAccess.getTimeBucket());
    }

    private long getTimeInMillis(String s) {
        return DateTime.parse(s, DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS")).getMillis();
    }

    private List<KeyStringValuePair> buildTags() {
        return Arrays.asList(
                KeyStringValuePair.newBuilder().setKey(SpanTags.DB_STATEMENT).setValue("select * from dual").build(),
                KeyStringValuePair.newBuilder().setKey(SpanTags.DB_TYPE).setValue("Mysql").build()
        );
    }

    private VirtualDatabaseProcessor buildVirtualServiceProcessor() {
        NamingControl namingControl = new NamingControl(512, 512, 512, new EndpointNameGrouping());
        AnalyzerModuleConfig config = new AnalyzerModuleConfig();
        config.setDbLatencyThresholdsAndWatcher(new DBLatencyThresholdsAndWatcher("default:10", null));
        return new VirtualDatabaseProcessor(namingControl, config);
    }

}
