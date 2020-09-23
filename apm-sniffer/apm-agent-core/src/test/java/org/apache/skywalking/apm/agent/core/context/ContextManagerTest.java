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

package org.apache.skywalking.apm.agent.core.context;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.context.util.AbstractTracingSpanHelper;
import org.apache.skywalking.apm.agent.core.context.util.SegmentHelper;
import org.apache.skywalking.apm.agent.core.context.util.SpanHelper;
import org.apache.skywalking.apm.agent.core.context.util.TraceSegmentRefHelper;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.Log;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(TracingSegmentRunner.class)
public class ContextManagerTest {

    @SegmentStoragePoint
    private SegmentStorage tracingData;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @BeforeClass
    public static void beforeClass() {
        Config.Agent.KEEP_TRACING = true;
    }

    @AfterClass
    public static void afterClass() {
        Config.Agent.KEEP_TRACING = false;
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void createSpanWithInvalidateContextCarrier() {
        ContextCarrier contextCarrier = new ContextCarrier();

        AbstractSpan firstEntrySpan = ContextManager.createEntrySpan("/testEntrySpan", contextCarrier);
        firstEntrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(firstEntrySpan, "GET");
        Tags.URL.set(firstEntrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(firstEntrySpan);

        ContextManager.stopSpan();

        TraceSegment actualSegment = tracingData.getTraceSegments().get(0);
        assertNull(actualSegment.getRefs());

        List<AbstractTracingSpan> spanList = SegmentHelper.getSpan(actualSegment);
        assertThat(Objects.requireNonNull(spanList).size(), is(1));

        AbstractTracingSpan actualEntrySpan = spanList.get(0);
        assertThat(actualEntrySpan.getOperationName(), is("/testEntrySpan"));
        assertThat(actualEntrySpan.getSpanId(), is(0));
        MatcherAssert.assertThat(AbstractTracingSpanHelper.getParentSpanId(actualEntrySpan), is(-1));
    }

    @Test
    public void createMultipleEntrySpan() {
        ContextCarrier contextCarrier = new ContextCarrier().deserialize(
            "1-My40LjU=-MS4yLjM=-4-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=",
            ContextCarrier.HeaderVersion.v3
        );
        assertTrue(contextCarrier.isValid());

        AbstractSpan firstEntrySpan = ContextManager.createEntrySpan("/testFirstEntry", contextCarrier);
        firstEntrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(firstEntrySpan, "GET");
        Tags.URL.set(firstEntrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(firstEntrySpan);

        AbstractSpan secondEntrySpan = ContextManager.createEntrySpan("/testSecondEntry", contextCarrier);
        secondEntrySpan.setComponent(ComponentsDefine.DUBBO);
        Tags.URL.set(firstEntrySpan, "dubbo://127.0.0.1:8080");
        SpanLayer.asRPCFramework(secondEntrySpan);

        ContextCarrier injectContextCarrier = new ContextCarrier();
        AbstractSpan exitSpan = ContextManager.createExitSpan("/textExitSpan", injectContextCarrier, "127.0.0.1:12800");
        exitSpan.log(new RuntimeException("exception"));
        exitSpan.setComponent(ComponentsDefine.HTTPCLIENT);

        ContextManager.stopSpan();
        ContextManager.stopSpan();
        SpanLayer.asHttp(firstEntrySpan);
        firstEntrySpan.setOperationName("/testFirstEntry-setOperationName");
        ContextManager.stopSpan();

        assertThat(tracingData.getTraceSegments().size(), is(1));

        TraceSegment actualSegment = tracingData.getTraceSegments().get(0);
        assertThat(actualSegment.getRefs().size(), is(1));

        TraceSegmentRef ref = actualSegment.getRefs().get(0);
        MatcherAssert.assertThat(TraceSegmentRefHelper.getPeerHost(ref), is("127.0.0.1:8080"));

        List<AbstractTracingSpan> spanList = SegmentHelper.getSpan(actualSegment);
        assertThat(spanList.size(), is(2));

        AbstractTracingSpan actualEntrySpan = spanList.get(1);
        assertThat(actualEntrySpan.getOperationName(), is("/testSecondEntry"));
        assertThat(actualEntrySpan.getSpanId(), is(0));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualEntrySpan), is(-1));
        assertThat(SpanHelper.getComponentId(actualEntrySpan), is(ComponentsDefine.DUBBO.getId()));
        assertThat(SpanHelper.getLayer(actualEntrySpan), is(SpanLayer.RPC_FRAMEWORK));

        AbstractTracingSpan actualExitSpan = spanList.get(0);
        assertThat(actualExitSpan.getOperationName(), is("/textExitSpan"));
        assertThat(actualExitSpan.getSpanId(), is(1));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualExitSpan), is(0));

        List<LogDataEntity> logs = AbstractTracingSpanHelper.getLogs(actualExitSpan);
        assertThat(logs.size(), is(1));
        assertThat(logs.get(0).getLogs().size(), is(4));

        assertThat(injectContextCarrier.getSpanId(), is(1));
        assertThat(injectContextCarrier.getAddressUsedAtClient(), is("127.0.0.1:12800"));
    }

    @Test
    public void createMultipleExitSpan() {
        AbstractSpan entrySpan = ContextManager.createEntrySpan("/testEntrySpan", null);
        entrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(entrySpan, "GET");
        Tags.URL.set(entrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(entrySpan);

        ContextCarrier firstExitSpanContextCarrier = new ContextCarrier();
        AbstractSpan firstExitSpan = ContextManager.createExitSpan(
            "/testFirstExit", firstExitSpanContextCarrier, "127.0.0.1:8080");
        firstExitSpan.setComponent(ComponentsDefine.DUBBO);
        Tags.URL.set(firstExitSpan, "dubbo://127.0.0.1:8080");
        SpanLayer.asRPCFramework(firstExitSpan);

        ContextCarrier secondExitSpanContextCarrier = new ContextCarrier();
        AbstractSpan secondExitSpan = ContextManager.createExitSpan(
            "/testSecondExit", secondExitSpanContextCarrier, "127.0.0.1:9080");
        secondExitSpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(secondExitSpan, "GET");
        Tags.URL.set(secondExitSpan, "127.0.0.1:8080");
        SpanLayer.asHttp(secondExitSpan);
        secondExitSpan.setOperationName("/testSecondExit-setOperationName");

        ContextManager.stopSpan();
        ContextManager.stopSpan();
        ContextManager.stopSpan();

        assertThat(tracingData.getTraceSegments().size(), is(1));
        TraceSegment actualSegment = tracingData.getTraceSegments().get(0);
        assertNull(actualSegment.getRefs());

        List<AbstractTracingSpan> spanList = SegmentHelper.getSpan(actualSegment);
        assertThat(Objects.requireNonNull(spanList).size(), is(2));

        AbstractTracingSpan actualFirstExitSpan = spanList.get(0);
        assertThat(actualFirstExitSpan.getOperationName(), is("/testFirstExit"));
        assertThat(actualFirstExitSpan.getSpanId(), is(1));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualFirstExitSpan), is(0));
        assertThat(SpanHelper.getComponentId(actualFirstExitSpan), is(ComponentsDefine.DUBBO.getId()));
        assertThat(SpanHelper.getLayer(actualFirstExitSpan), is(SpanLayer.RPC_FRAMEWORK));

        AbstractTracingSpan actualEntrySpan = spanList.get(1);
        assertThat(actualEntrySpan.getOperationName(), is("/testEntrySpan"));
        assertThat(actualEntrySpan.getSpanId(), is(0));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualEntrySpan), is(-1));

        assertThat(firstExitSpanContextCarrier.getAddressUsedAtClient(), is("127.0.0.1:8080"));
        assertThat(firstExitSpanContextCarrier.getSpanId(), is(1));

        assertThat(secondExitSpanContextCarrier.getSpanId(), is(1));

    }

    @Test
    public void testTransform() throws InvalidProtocolBufferException {
        ContextCarrier contextCarrier = new ContextCarrier().deserialize(
            "1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=",
            ContextCarrier.HeaderVersion.v3
        );
        assertTrue(contextCarrier.isValid());

        AbstractSpan firstEntrySpan = ContextManager.createEntrySpan("/testFirstEntry", contextCarrier);
        firstEntrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(firstEntrySpan, "GET");
        Tags.URL.set(firstEntrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(firstEntrySpan);

        AbstractSpan secondEntrySpan = ContextManager.createEntrySpan("/testSecondEntry", contextCarrier);
        secondEntrySpan.setComponent(ComponentsDefine.DUBBO);
        Tags.URL.set(firstEntrySpan, "dubbo://127.0.0.1:8080");
        SpanLayer.asRPCFramework(secondEntrySpan);

        ContextCarrier injectContextCarrier = new ContextCarrier();
        AbstractSpan exitSpan = ContextManager.createExitSpan("/textExitSpan", injectContextCarrier, "127.0.0.1:12800");
        exitSpan.log(new RuntimeException("exception"));
        exitSpan.setComponent(ComponentsDefine.HTTPCLIENT);
        SpanLayer.asHttp(exitSpan);

        ContextManager.stopSpan();
        ContextManager.stopSpan();
        ContextManager.stopSpan();

        TraceSegment actualSegment = tracingData.getTraceSegments().get(0);

        SegmentObject traceSegmentObject = actualSegment.transform();
        SegmentReference reference = traceSegmentObject.getSpans(1).getRefs(0);

        assertThat(reference.getNetworkAddressUsedAtPeer(), is("127.0.0.1:8080"));
        assertThat(reference.getParentSpanId(), is(3));

        assertThat(traceSegmentObject.getSpans(1).getRefsCount(), is(1));

        assertThat(traceSegmentObject.getSpansCount(), is(2));

        SpanObject actualSpan = traceSegmentObject.getSpans(1);
        assertThat(actualSpan.getComponentId(), is(3));

        assertThat(actualSpan.getOperationName(), is("/testSecondEntry"));
        assertThat(actualSpan.getParentSpanId(), is(-1));
        assertThat(actualSpan.getSpanId(), is(0));
        assertThat(actualSpan.getSpanType(), is(SpanType.Entry));

        SpanObject exitSpanObject = traceSegmentObject.getSpans(0);
        assertThat(exitSpanObject.getComponentId(), is(2));
        assertThat(exitSpanObject.getSpanType(), is(SpanType.Exit));

        assertThat(exitSpanObject.getOperationName(), is("/textExitSpan"));
        assertThat(exitSpanObject.getParentSpanId(), is(0));
        assertThat(exitSpanObject.getSpanId(), is(1));

        assertThat(exitSpanObject.getLogsCount(), is(1));
        Log logMessage = exitSpanObject.getLogs(0);
        assertThat(logMessage.getDataCount(), is(4));
        List<KeyStringValuePair> values = logMessage.getDataList();

        assertThat(values.get(0).getValue(), is("error"));
        assertThat(values.get(1).getValue(), is(RuntimeException.class.getName()));
        assertThat(values.get(2).getValue(), is("exception"));
        assertTrue(values.get(2).getValue().length() <= 4000);
    }
}
