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

import java.util.List;
import java.util.Optional;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

@RunWith(TracingSegmentRunner.class)
public class CorrelationContextTest {

    @SegmentStoragePoint
    private SegmentStorage tracingData;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @BeforeClass
    public static void beforeClass() {
        Config.Agent.KEEP_TRACING = true;
        Config.Correlation.ELEMENT_MAX_NUMBER = 2;
        Config.Correlation.VALUE_MAX_LENGTH = 8;
        Config.Correlation.AUTO_TAG_KEYS = "autotag";
    }

    @AfterClass
    public static void afterClass() {
        Config.Agent.KEEP_TRACING = false;
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testSet() {
        final CorrelationContext context = new CorrelationContext();

        // manual set
        Optional<String> previous = context.put("test1", "t1");
        Assert.assertNotNull(previous);
        Assert.assertFalse(previous.isPresent());

        // set with replace old value
        previous = context.put("test1", "t1New");
        Assert.assertNotNull(previous);
        Assert.assertEquals("t1", previous.get());

        // manual set
        previous = context.put("test2", "t2");
        Assert.assertNotNull(previous);
        Assert.assertFalse(previous.isPresent());

        // out of key count
        previous = context.put("test3", "t3");
        Assert.assertNotNull(previous);
        Assert.assertFalse(previous.isPresent());

        // key not null
        previous = context.put(null, "t3");
        Assert.assertNotNull(previous);
        Assert.assertFalse(previous.isPresent());

        // out of value length
        previous = context.put(null, "123456789");
        Assert.assertNotNull(previous);
        Assert.assertFalse(previous.isPresent());
    }

    @Test
    public void testGet() {
        final CorrelationContext context = new CorrelationContext();
        context.put("test1", "t1");

        // manual get
        Assert.assertEquals("t1", context.get("test1").get());
        // ket if null
        Assert.assertNull(context.get(null).orElse(null));
        // value if null
        context.put("test2", null);
        Assert.assertNull(context.get("test2").orElse(null));
    }

    @Test
    public void testSerialize() {
        // manual
        CorrelationContext context = new CorrelationContext();
        context.put("test1", "t1");
        context.put("test2", "t2");
        Assert.assertEquals("dGVzdDE=:dDE=,dGVzdDI=:dDI=", context.serialize());

        // empty value
        context = new CorrelationContext();
        context.put("test1", null);
        Assert.assertEquals("", context.serialize());

        // empty
        context = new CorrelationContext();
        Assert.assertEquals("", context.serialize());
    }

    @Test
    public void testDeserialize() {
        // manual
        CorrelationContext context = new CorrelationContext();
        context.deserialize("dGVzdDE=:dDE=,dGVzdDI=:dDI=");
        Assert.assertEquals("t1", context.get("test1").get());
        Assert.assertEquals("t2", context.get("test2").get());

        // empty value
        context = new CorrelationContext();
        context.deserialize("dGVzdDE=:");
        Assert.assertFalse(context.get("test1").isPresent());

        // empty string
        context = new CorrelationContext();
        context.deserialize("");
        Assert.assertNull(context.get("test1").orElse(null));
        context.deserialize(null);
        Assert.assertNull(context.get("test1").orElse(null));
    }

    @Test
    public void testHandleWhenAutoTagKeysEmpty() {
        ContextManager.createEntrySpan("/testFirstEntry", new ContextCarrier());
        ContextManager.getCorrelationContext().put("a", "b");
        ContextManager.stopSpan();
        TraceSegment traceSegment = tracingData.getTraceSegments().get(0);
        List<AbstractSpan> spans = Whitebox.getInternalState(traceSegment, "spans");
        Assert.assertNull(Whitebox.getInternalState(spans.get(0), "tags"));
    }

    @Test
    public void testHandleWhenAutoTagKeysNotEmpty() {
        ContextManager.createEntrySpan("/testFirstEntry", new ContextCarrier());
        ContextManager.getCorrelationContext().put("autotag", "b");
        ContextManager.stopSpan();
        TraceSegment traceSegment = tracingData.getTraceSegments().get(0);
        List<AbstractSpan> spans = Whitebox.getInternalState(traceSegment, "spans");
        List<TagValuePair> tags = Whitebox.getInternalState(spans.get(0), "tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(new TagValuePair(new StringTag("autotag"), "b"), tags.get(0));
    }

}
