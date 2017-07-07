package org.skywalking.apm.agent.core.context;

import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.context.util.AbstractTracingSpanHelper;
import org.skywalking.apm.agent.core.context.util.SegmentHelper;
import org.skywalking.apm.agent.core.context.util.SegmentStorage;
import org.skywalking.apm.agent.core.context.util.SegmentStoragePoint;
import org.skywalking.apm.agent.core.context.util.TraceSegmentRefHelper;
import org.skywalking.apm.agent.core.context.util.TracingSegmentRunner;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(TracingSegmentRunner.class)
public class ContextManagerTest {

    @SegmentStoragePoint
    private SegmentStorage tracingData;

    @BeforeClass
    public static void setUpBeforeClass() {
        ServiceManager.INSTANCE.boot();
    }

    @Before
    public void setUp() throws Exception {
        RemoteDownstreamConfig.Agent.APPLICATION_ID = 1;
        RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID = 1;
    }

    @Test
    public void createSpanWithInvalidateContextCarrier() {
        ContextCarrier contextCarrier = new ContextCarrier().deserialize("S.1499176688384.581928182.80935.69.1|3|1|#192.168.1.8 :18002|#/portal/");

        AbstractSpan firstEntrySpan = ContextManager.createEntrySpan("/testEntrySpan", contextCarrier);
        firstEntrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(firstEntrySpan, "GET");
        Tags.URL.set(firstEntrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(firstEntrySpan);

        ContextManager.stopSpan();

        TraceSegment actualSegment = tracingData.getTraceSegments().get(0);
        assertNull(actualSegment.getRefs());

        List<AbstractTracingSpan> spanList = SegmentHelper.getSpan(actualSegment);
        assertThat(spanList.size(), is(1));

        AbstractTracingSpan actualEntrySpan = spanList.get(0);
        assertThat(actualEntrySpan.getOperationName(), is("/testEntrySpan"));
        assertThat(actualEntrySpan.getSpanId(), is(0));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualEntrySpan), is(-1));
    }

    @Test
    public void createMultipleEntrySpan() {
        ContextCarrier contextCarrier = new ContextCarrier().deserialize("S.1499176688384.581928182.80935.69.1|3|1|#192.168.1.8 :18002|#/portal/|T.1499176688386.581928182.80935.69.2");
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
        exitSpan.errorOccurred();
        exitSpan.log(new RuntimeException("exception"));
        exitSpan.setComponent(ComponentsDefine.HTTPCLIENT);

        ContextManager.stopSpan();
        ContextManager.stopSpan();
        ContextManager.stopSpan();

        assertThat(tracingData.getTraceSegments().size(), is(1));

        TraceSegment actualSegment = tracingData.getTraceSegments().get(0);
        assertThat(actualSegment.getRefs().size(), is(1));

        TraceSegmentRef ref = actualSegment.getRefs().get(0);
        assertThat(TraceSegmentRefHelper.getPeerHost(ref), is("192.168.1.8 :18002"));
        assertThat(ref.getOperationName(), is("/portal/"));
        assertThat(ref.getOperationId(), is(0));

        List<AbstractTracingSpan> spanList = SegmentHelper.getSpan(actualSegment);
        assertThat(spanList.size(), is(2));

        AbstractTracingSpan actualEntrySpan = spanList.get(1);
        assertThat(actualEntrySpan.getOperationName(), is("/testSecondEntry"));
        assertThat(actualEntrySpan.getSpanId(), is(0));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualEntrySpan), is(-1));

        AbstractTracingSpan actualExitSpan = spanList.get(0);
        assertThat(actualExitSpan.getOperationName(), is("/textExitSpan"));
        assertThat(actualExitSpan.getSpanId(), is(1));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualExitSpan), is(0));

        List<LogDataEntity> logs = AbstractTracingSpanHelper.getLogs(actualExitSpan);
        assertThat(logs.size(), is(1));
        assertThat(logs.get(0).getLogs().size(), is(4));

        assertThat(injectContextCarrier.getSpanId(), is(1));
        assertThat(injectContextCarrier.getEntryOperationName(), is("#/portal/"));
        assertThat(injectContextCarrier.getPeerHost(), is("#127.0.0.1:12800"));
    }

    @Test
    public void createMultipleExitSpan() {
        AbstractSpan entrySpan = ContextManager.createEntrySpan("/testEntrySpan", null);
        entrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(entrySpan, "GET");
        Tags.URL.set(entrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(entrySpan);

        ContextCarrier firstExitSpanContextCarrier = new ContextCarrier();
        AbstractSpan firstExitSpan = ContextManager.createExitSpan("/testFirstExit", firstExitSpanContextCarrier, "127.0.0.1:8080");
        firstExitSpan.setComponent(ComponentsDefine.DUBBO);
        Tags.URL.set(firstExitSpan, "dubbo://127.0.0.1:8080");
        SpanLayer.asRPCFramework(firstExitSpan);

        ContextCarrier secondExitSpanContextCarrier = new ContextCarrier();
        AbstractSpan secondExitSpan = ContextManager.createExitSpan("/testSecondExit", secondExitSpanContextCarrier, "127.0.0.1:9080");
        secondExitSpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(secondExitSpan, "GET");
        Tags.URL.set(secondExitSpan, "127.0.0.1:8080");
        SpanLayer.asHttp(secondExitSpan);

        ContextManager.stopSpan();
        ContextManager.stopSpan();
        ContextManager.stopSpan();

        assertThat(tracingData.getTraceSegments().size(), is(1));
        TraceSegment actualSegment = tracingData.getTraceSegments().get(0);
        assertNull(actualSegment.getRefs());

        List<AbstractTracingSpan> spanList = SegmentHelper.getSpan(actualSegment);
        assertThat(spanList.size(), is(2));

        AbstractTracingSpan actualFirstExitSpan = spanList.get(0);
        assertThat(actualFirstExitSpan.getOperationName(), is("/testFirstExit"));
        assertThat(actualFirstExitSpan.getSpanId(), is(1));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualFirstExitSpan), is(0));

        AbstractTracingSpan actualEntrySpan = spanList.get(1);
        assertThat(actualEntrySpan.getOperationName(), is("/testEntrySpan"));
        assertThat(actualEntrySpan.getSpanId(), is(0));
        assertThat(AbstractTracingSpanHelper.getParentSpanId(actualEntrySpan), is(-1));

        assertThat(firstExitSpanContextCarrier.getPeerHost(), is("#127.0.0.1:8080"));
        assertThat(firstExitSpanContextCarrier.getSpanId(), is(1));
        assertThat(firstExitSpanContextCarrier.getEntryOperationName(), is("#/testEntrySpan"));

        assertThat(secondExitSpanContextCarrier.getPeerHost(), is("#127.0.0.1:8080"));
        assertThat(secondExitSpanContextCarrier.getSpanId(), is(1));
        assertThat(secondExitSpanContextCarrier.getEntryOperationName(), is("#/testEntrySpan"));

    }

    @After
    public void tearDown() throws Exception {
        RemoteDownstreamConfig.Agent.APPLICATION_ID = DictionaryUtil.nullValue();
        RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID = DictionaryUtil.nullValue();
    }

}
