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

package org.apache.skywalking.apm.plugin.solrj;

import com.google.common.collect.Lists;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.solrj.commons.SolrjInstance;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.StringUtils;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SolrClientInterceptorTest {
    SolrClientInterceptor interceptor = new SolrClientInterceptor();

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private HttpSolrClient client;

    @Mock
    private Method method;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] arguments = null;
    private Class[] argumentType = new Class[] {
        SolrRequest.class,
        ResponseParser.class,
        String.class
    };
    private String collection = null;
    private HttpSolrClient.Builder builder;

    @Mock
    private SolrjInstance instance;
    private NamedList<Object> header;

    @Before
    public void setup() throws Exception {
        builder = new HttpSolrClient.Builder().withBaseSolrUrl("http://solr-server:8983/solr/collection");
        enhancedInstance = new EnhanceHttpSolrClient(builder);

        when(instance.getCollection()).thenReturn("collection");
        when(instance.getRemotePeer()).thenReturn("solr-server:8983");
        enhancedInstance.setSkyWalkingDynamicField(instance);

        header = new NamedList<Object>();
        header.add("status", 0);
        header.add("QTime", 5);
    }

    @Test
    public void testConstructor() throws Throwable {
        arguments = new Object[] {builder};
        interceptor.onConstruct(enhancedInstance, arguments);
        SolrjInstance instance = (SolrjInstance) enhancedInstance.getSkyWalkingDynamicField();
        Assert.assertEquals(instance.getRemotePeer(), "solr-server:8983");
        Assert.assertEquals(instance.getCollection(), "collection");
    }

    @Test
    public void testUpdateWithAdd() throws Throwable {
        UpdateRequest request = new UpdateRequest();
        List<SolrInputDocument> docs = Lists.newArrayList();
        for (int start = 0; start < 100; start++) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id", start);
            docs.add(doc);
        }
        arguments = new Object[] {
            request.add(docs),
            null,
            collection
        };
        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, getResponse());

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        Assert.assertEquals(segments.size(), 1);

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        int pox = 0;
        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_STATEMENT) {
            SpanAssert.assertTag(span, ++pox, "100");
        }
        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_OPS_PARAMS) {
            SpanAssert.assertTag(span, ++pox, "-1");
        }
        spanCommonAssert(span, pox, "solrJ/collection/update/ADD");
    }

    @Test
    public void testUpdateWithCommit() throws Throwable {
        final boolean softCommit = false;
        AbstractUpdateRequest request = (new UpdateRequest()).setAction(
            AbstractUpdateRequest.ACTION.COMMIT, true, true, false);
        arguments = new Object[] {
            request,
            null,
            collection
        };
        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, getResponse());

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        Assert.assertEquals(segments.size(), 1);

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));
        Assert.assertEquals(spans.size(), 1);

        int start = 0;
        AbstractTracingSpan span = spans.get(0);
        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_OPS_PARAMS) {
            SpanAssert.assertTag(span, ++start, String.valueOf(softCommit));
        }
        spanCommonAssert(span, start, "solrJ/collection/update/COMMIT");
    }

    @Test
    public void testUpdateWithOptimize() throws Throwable {
        final int maxSegments = 1;
        AbstractUpdateRequest request = (new UpdateRequest()).setAction(
            AbstractUpdateRequest.ACTION.OPTIMIZE, false, true, maxSegments);
        arguments = new Object[] {
            request,
            null,
            collection
        };
        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, getResponse());

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));

        Assert.assertEquals(segments.size(), 1);
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        int start = 0;
        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_OPS_PARAMS) {
            SpanAssert.assertTag(span, ++start, String.valueOf(maxSegments));
        }
        spanCommonAssert(span, start, "solrJ/collection/update/OPTIMIZE");
    }

    @Test
    public void testQuery() throws Throwable {
        QueryRequest request = new QueryRequest();
        arguments = new Object[] {
            request,
            null,
            collection
        };

        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, getQueryResponse());

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));

        Assert.assertEquals(segments.size(), 1);
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        querySpanAssert(span, "/select", 100, "solrJ/collection/select");
    }

    @Test
    public void testGet() throws Throwable {
        ModifiableSolrParams reqParams = new ModifiableSolrParams();
        if (StringUtils.isEmpty(reqParams.get("qt"))) {
            reqParams.set("qt", new String[] {"/get"});
        }
        reqParams.set("ids", new String[] {
            "99",
            "98"
        });
        QueryRequest request = new QueryRequest(reqParams);

        arguments = new Object[] {
            request,
            null,
            collection
        };
        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, getGetResponse());

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));
        Assert.assertEquals(segments.size(), 1);
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        querySpanAssert(span, "/get", 1, "solrJ/collection/get");
    }

    @Test
    public void testDeleteById() throws Throwable {
        UpdateRequest request = new UpdateRequest();
        arguments = new Object[] {
            request.deleteById("12"),
            null,
            collection
        };
        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, getResponse());

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));

        Assert.assertEquals(segments.size(), 1);
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        spanDeleteAssert(span, "solrJ/collection/update/DELETE_BY_IDS", "[12]");
    }

    @Test
    public void testDeleteByQuery() throws Throwable {
        UpdateRequest request = new UpdateRequest();
        arguments = new Object[] {
            request.deleteByQuery("id:[2 TO 5]"),
            null,
            collection
        };
        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, getResponse());

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));

        Assert.assertEquals(segments.size(), 1);
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        spanDeleteAssert(span, "solrJ/collection/update/DELETE_BY_QUERY", "[id:[2 TO 5]]");
    }

    @Test
    public void testException() throws Throwable {
        QueryRequest request = new QueryRequest();
        arguments = new Object[] {
            request,
            null,
            collection
        };
        NamedList<Object> response = new NamedList<Object>();
        NamedList<Object> header = new NamedList<Object>();
        header.add("status", 500);
        header.add("QTime", 5);
        response.add("responseHeader", header);

        interceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, null);
        interceptor.handleMethodException(
            enhancedInstance, method, arguments, argumentType,
            new SolrException(SolrException.ErrorCode.SERVER_ERROR, "for test", new Exception())
        );
        interceptor.afterMethod(enhancedInstance, method, arguments, argumentType, response);

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));

        Assert.assertEquals(segments.size(), 1);
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        SpanAssert.assertOccurException(span, true);
    }

    private void querySpanAssert(AbstractSpan span, String qt, int numFound, String operationName) {
        Assert.assertEquals(span.getOperationName(), operationName);
        SpanAssert.assertTag(span, 0, "Solr");
        SpanAssert.assertTag(span, 1, "0");
        SpanAssert.assertTag(span, 2, qt);

        int start = 3;
        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_STATEMENT) {
            start++;
        }
        SpanAssert.assertTag(span, start++, "5");
        SpanAssert.assertTag(span, start++, String.valueOf(numFound));
    }

    private void spanCommonAssert(AbstractSpan span, int start, String operationName) {
        SpanAssert.assertComponent(span, ComponentsDefine.SOLRJ);
        SpanAssert.assertOccurException(span, false);
        SpanAssert.assertLogSize(span, 0);
        SpanAssert.assertLayer(span, SpanLayer.DB);

        SpanAssert.assertTag(span, 0, "Solr");
        SpanAssert.assertTag(span, start + 1, "5");

        Assert.assertEquals(span.getOperationName(), operationName);
    }

    private void spanDeleteAssert(AbstractSpan span, String operationName, String statement) {
        Assert.assertEquals(span.getOperationName(), operationName);
        SpanAssert.assertComponent(span, ComponentsDefine.SOLRJ);
        SpanAssert.assertOccurException(span, false);
        SpanAssert.assertLogSize(span, 0);
        SpanAssert.assertLayer(span, SpanLayer.DB);

        SpanAssert.assertTag(span, 0, "Solr");

        int start = 0;
        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_STATEMENT) {
            SpanAssert.assertTag(span, ++start, statement);
        }
        if (SolrJPluginConfig.Plugin.SolrJ.TRACE_OPS_PARAMS) {
            SpanAssert.assertTag(span, ++start, "-1");
        }

        SpanAssert.assertTag(span, start + 1, "5");
    }

    private NamedList<Object> getResponse() {
        NamedList<Object> response = new NamedList<Object>();
        response.add("responseHeader", header);
        return response;
    }

    private NamedList<Object> getQueryResponse() {
        NamedList<Object> response = new NamedList<Object>();
        response.add("responseHeader", header);
        SolrDocumentList list = new SolrDocumentList();
        list.setStart(0);
        list.setNumFound(100);
        list.setMaxScore(.0f);

        for (int start = 0; start < 10; start++) {
            SolrDocument doc = new SolrDocument();
            doc.addField("id", start);
            doc.addField("_version", 1634676349644832768L);
            list.add(doc);
        }
        response.add("response", list);
        return response;
    }

    private NamedList<Object> getGetResponse() {
        NamedList<Object> response = new NamedList<Object>();
        response.add("responseHeader", header);
        SolrDocumentList list = new SolrDocumentList();
        list.setStart(0);
        list.setNumFound(1);
        list.setMaxScore(.0f);

        SolrDocument doc = new SolrDocument();
        doc.addField("id", 1);
        doc.addField("_version", 1634676349644832768L);
        list.add(doc);

        response.add("response", list);
        return response;
    }

    class EnhanceHttpSolrClient extends HttpSolrClient implements EnhancedInstance {
        Object value = null;

        protected EnhanceHttpSolrClient(Builder builder) {
            super(builder);
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return value;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.value = value;
        }
    }

}