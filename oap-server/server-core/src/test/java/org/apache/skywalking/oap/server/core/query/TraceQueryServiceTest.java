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
package org.apache.skywalking.oap.server.core.query;

import com.google.common.collect.Lists;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.RefType;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.register.NetworkAddressInventory;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by dengming in 2019-05-19
 */
@PrepareForTest({TraceSegmentObject.class, UniqueId.class, SegmentReference.class,
        SpanObject.class, TraceSegmentReference.class,
        KeyStringValuePair.class,
        LogMessage.class,
        org.apache.skywalking.apm.network.language.agent.v2.Log.class,
        KeyWithStringValue.class,
        SegmentObject.class, SpanObjectV2.class})
@RunWith(PowerMockRunner.class)
public class TraceQueryServiceTest extends AbstractTest {

    private TraceQueryService queryService = new TraceQueryService(moduleManager);

    private ITraceQueryDAO queryDAO = mock(ITraceQueryDAO.class);

    private NetworkAddressInventoryCache networkAddressInventoryCache = mock(NetworkAddressInventoryCache.class);

    private NetworkAddressInventory networkAddressInventory = mock(NetworkAddressInventory.class);

    private IComponentLibraryCatalogService componentLibraryCatalogService = mock(IComponentLibraryCatalogService.class);

    private static final String NETWORK_ADDRESS_INVENTORY = "network-address-inventory";
    private static final String COMPONENT_LIBRARY_CATALOG_SERVICE = "component-library-catalog-service";

    @Before
    public void setUp() throws Exception {
        when(moduleServiceHolder.getService(NetworkAddressInventoryCache.class)).thenReturn(networkAddressInventoryCache);
        when(moduleServiceHolder.getService(ITraceQueryDAO.class)).thenReturn(queryDAO);
        when(moduleServiceHolder.getService(IComponentLibraryCatalogService.class)).thenReturn(componentLibraryCatalogService);

        when(networkAddressInventoryCache.get(anyInt())).thenReturn(networkAddressInventory);

        when(networkAddressInventory.getName()).thenReturn(NETWORK_ADDRESS_INVENTORY);

        when(componentLibraryCatalogService.getComponentName(anyInt())).thenReturn(COMPONENT_LIBRARY_CATALOG_SERVICE);
    }

    @Test
    public void queryBasicTraces() throws Exception {
        TraceBrief mockBrief = mock(TraceBrief.class);
        when(queryDAO.queryBasicTraces(anyLong(), anyLong(), anyLong(), anyLong(), anyString(),
                anyInt(), anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), any(TraceState.class), any(QueryOrder.class))).thenReturn(mockBrief);
        TraceBrief brief = queryService.queryBasicTraces(1, 2, 3,
                "4", "endpoint-name",
                10, 20,
                TraceState.ERROR, QueryOrder.BY_DURATION, PAGINATION, START_TB, END_TB);
        assertEquals(mockBrief, brief);
    }

    @Test
    public void queryTraceWithEmptySegmentRecord() throws Exception {

        List<Span> spanList = new ArrayList<>();
        Span rootSpan = new Span();
        spanList.add(rootSpan);
        rootSpan.setSegmentParentSpanId("null");

        for (int i = 0; i < 10; i++) {
            Span span = new Span();
            span.setSegmentParentSpanId("span-id-" + (i - 1));
            span.setSegmentId("span-id-" + i);
            spanList.add(span);
        }

        when(queryDAO.queryByTraceId(anyString())).thenReturn(Collections.emptyList());
        when(queryDAO.doFlexibleTraceQuery(anyString())).thenReturn(spanList);
        Trace trace = queryService.queryTrace("234");
        assertNotNull(trace);
        assertEquals(11, trace.getSpans().size());

        for (int i = 0; i <= 10; i++) {
            assertEquals(spanList.get(i), trace.getSpans().get(i));
        }
    }

    @Test
    public void queryTrace() throws Exception {

        List<Span> spanList = new ArrayList<>();
        Span rootSpan = new Span();
        spanList.add(rootSpan);
        rootSpan.setSegmentParentSpanId("null");

        for (int i = 0; i < 10; i++) {
            Span span = new Span();
            span.setSegmentParentSpanId("span-id-" + (i - 1));
            span.setSegmentId("span-id-" + i);
            spanList.add(span);
        }

        PowerMockito.mockStatic(TraceSegmentObject.class);

        when(TraceSegmentObject.parseFrom(any(byte[].class))).then(this::mockTraceSegmentObject);

        PowerMockito.mockStatic(SegmentObject.class);

        when(SegmentObject.parseFrom(any(byte[].class))).then(this::mockSegmentObject);

        when(queryDAO.queryByTraceId(anyString())).thenReturn(mockSegmentRecords());
        when(queryDAO.doFlexibleTraceQuery(anyString())).thenReturn(spanList);

        Trace trace = queryService.queryTrace("123");
        assertNotNull(trace);
        assertEquals(100, trace.getSpans().size());

        List<Span> result = trace.getSpans();

        for (int i = 0; i < 100; i++) {
            Span span = result.get(i);
            assertEquals("123", span.getTraceId());
            assertEquals(SpanType.Entry.name(), span.getType());
            assertEquals(SpanLayer.MQ.name(), span.getLayer());
            assertEquals(10, span.getRefs().size());
            assertEquals(SERVICE_INVENTORY_NAME, span.getServiceCode());
            assertEquals(3, span.getTags().size());
            assertEquals(5, span.getLogs().size());
            assertEquals("null" + Const.SEGMENT_SPAN_SPLIT + span.getSpanId(), span.getSegmentSpanId());

            if (span.getSpanId() % 2 == 1) {
                assertEquals(ENDPOINT_INVENTORY_NAME, span.getEndpointName());
            } else {
                assertNull(span.getEndpointName());
            }

            if (span.getSpanId() == 0) {
                assertEquals("segment-span-id-0", span.getComponent());
            } else {
                assertEquals(COMPONENT_LIBRARY_CATALOG_SERVICE, span.getComponent());
            }

            if (span.getSpanId() % 2 != 0) {
                assertEquals(NETWORK_ADDRESS_INVENTORY, span.getPeer());
            }

            //span from mockSegmentObject, v2
            if (span.getSpanId() >= 100) {

                for (KeyValue keyValue : span.getTags()) {
                    assertEquals("tag-v2-key-" + span.getSpanId(), keyValue.getKey());
                    assertEquals("tag-v2-value-" + span.getSpanId(), keyValue.getValue());
                }

                for (LogEntity logEntity : span.getLogs()) {
                    assertEquals("log-v2-data-key-" + span.getSpanId(), logEntity.getData().get(0).getKey());
                    assertEquals("log-v2-data-value-" + span.getSpanId(), logEntity.getData().get(0).getValue());
                }


            } else {
                for (KeyValue keyValue : span.getTags()) {
                    assertEquals("tag-key-" + span.getSpanId(), keyValue.getKey());
                    assertEquals("tag-value-" + span.getSpanId(), keyValue.getValue());
                }

                for (LogEntity logEntity : span.getLogs()) {
                    assertEquals("log-data-key-" + span.getSpanId(), logEntity.getData().get(0).getKey());
                    assertEquals("log-data-value-" + span.getSpanId(), logEntity.getData().get(0).getValue());
                }
            }
        }
    }

    private TraceSegmentObject mockTraceSegmentObject(InvocationOnMock invocation) {
        String data = new String(invocation.getArgumentAt(0, byte[].class));
        TraceSegmentObject object = mock(TraceSegmentObject.class);

        List<SpanObject> segmentSpanList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SpanObject span = mock(SpanObject.class);
            when(span.getSpanId()).thenReturn(i);
            when(span.getSpanLayer()).thenReturn(SpanLayer.MQ);
            when(span.getSpanType()).thenReturn(SpanType.Entry);
            when(span.getComponent()).thenReturn("segment-span-id-" + i);

            List<TraceSegmentReference> references = new ArrayList<>();

            for (int j = 0; j < 10; j++) {
                TraceSegmentReference reference = mock(TraceSegmentReference.class);
                when(reference.getRefType()).thenReturn(RefType.forNumber(i % 2));
                when(reference.getParentSpanId()).thenReturn(i);
                UniqueId parentTraceSegmentId = mock(UniqueId.class);
                when(parentTraceSegmentId.getIdPartsList()).thenReturn(Lists.newArrayList(1L, 2L, 3L));

                when(reference.getParentTraceSegmentId()).thenReturn(parentTraceSegmentId);
                references.add(reference);
            }


            List<KeyWithStringValue> tagList = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                KeyWithStringValue pair = mock(KeyWithStringValue.class);
                when(pair.getKey()).thenReturn("tag-key-" + i);
                when(pair.getValue()).thenReturn("tag-value-" + i);
                tagList.add(pair);
            }

            List<LogMessage> logList = new ArrayList<>();

            for (int j = 0; j < 5; j++) {
                LogMessage log = mock(LogMessage.class);

                KeyWithStringValue keyWithStringValue = mock(KeyWithStringValue.class);
                when(keyWithStringValue.getKey()).thenReturn("log-data-key-" + i);
                when(keyWithStringValue.getValue()).thenReturn("log-data-value-" + i);

                when(log.getDataList()).thenReturn(Lists.newArrayList(keyWithStringValue));

                when(log.getTime()).thenReturn(System.currentTimeMillis());
                logList.add(log);
            }

            when(span.getRefsList()).thenReturn(references);

            when(span.getRefsList()).thenReturn(references);
            when(span.getPeerId()).thenReturn(i % 2);
            when(span.getTagsList()).thenReturn(tagList);
            when(span.getLogsList()).thenReturn(logList);

            when(span.getOperationNameId()).thenReturn(i % 2);
            when(span.getComponentId()).thenReturn(i);

            segmentSpanList.add(span);
        }


        when(object.getSpansList()).thenReturn(segmentSpanList);

        return object;
    }

    private SegmentObject mockSegmentObject(InvocationOnMock invocation) {
        String data = new String(invocation.getArgumentAt(0, byte[].class));
        SegmentObject object = mock(SegmentObject.class);
        List<SpanObjectV2> segmentSpanList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SpanObjectV2 span = mock(SpanObjectV2.class);
            int spanId = i * 100 + 100;
            when(span.getSpanId()).thenReturn(spanId);
            when(span.getSpanLayer()).thenReturn(SpanLayer.MQ);
            when(span.getSpanType()).thenReturn(SpanType.Entry);
            when(span.getComponent()).thenReturn("segment-span-v2-id-" + spanId);

            List<SegmentReference> references = new ArrayList<>();

            for (int j = 0; j < 10; j++) {
                SegmentReference reference = mock(SegmentReference.class);
                when(reference.getRefType()).thenReturn(RefType.forNumber(i % 2));
                when(reference.getParentSpanId()).thenReturn(i);
                UniqueId parentTraceSegmentId = mock(UniqueId.class);

                when(parentTraceSegmentId.getIdPartsList()).thenReturn(Lists.newArrayList(1L, 2L, 3L));

                when(reference.getParentTraceSegmentId()).thenReturn(parentTraceSegmentId);
                references.add(reference);
            }

            List<KeyStringValuePair> tagList = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                KeyStringValuePair pair = mock(KeyStringValuePair.class);
                when(pair.getKey()).thenReturn("tag-v2-key-" + spanId);
                when(pair.getValue()).thenReturn("tag-v2-value-" + spanId);
                tagList.add(pair);
            }

            List<org.apache.skywalking.apm.network.language.agent.v2.Log> logList = new ArrayList<>();

            for (int j = 0; j < 5; j++) {
                org.apache.skywalking.apm.network.language.agent.v2.Log log = mock(org.apache.skywalking.apm.network.language.agent.v2.Log.class);

                KeyStringValuePair keyWithStringValue = mock(KeyStringValuePair.class);
                when(keyWithStringValue.getKey()).thenReturn("log-v2-data-key-" + spanId);
                when(keyWithStringValue.getValue()).thenReturn("log-v2-data-value-" + spanId);

                when(log.getDataList()).thenReturn(Lists.newArrayList(keyWithStringValue));

                when(log.getTime()).thenReturn(System.currentTimeMillis());
                logList.add(log);
            }

            when(span.getRefsList()).thenReturn(references);
            when(span.getPeerId()).thenReturn(spanId % 2);
            when(span.getTagsList()).thenReturn(tagList);
            when(span.getLogsList()).thenReturn(logList);

            when(span.getOperationNameId()).thenReturn(spanId % 2);
            when(span.getComponentId()).thenReturn(spanId);
            segmentSpanList.add(span);
        }

        when(object.getSpansList()).thenReturn(segmentSpanList);
        return object;
    }

    private List<SegmentRecord> mockSegmentRecords() {
        List<SegmentRecord> records = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            SegmentRecord record = new SegmentRecord();
            record.setDataBinary(("Hello, I'm record " + i).getBytes());
            record.setVersion(i % 3);
            records.add(record);
        }
        return records;
    }
}