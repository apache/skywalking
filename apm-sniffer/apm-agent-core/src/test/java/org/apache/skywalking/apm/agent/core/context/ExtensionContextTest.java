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

import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
public class ExtensionContextTest {

    @Test
    public void testSerialize() {
        final ExtensionContext context = new ExtensionContext();
        Assert.assertEquals(context.serialize(), "0- ");

        context.deserialize("1- ");
        Assert.assertEquals(context.serialize(), "1- ");

        context.deserialize("1-1");
        Assert.assertEquals(context.serialize(), "1-1");

    }

    @Test
    public void testDeSerialize() {
        final ExtensionContext context = new ExtensionContext();
        context.deserialize("");
        Assert.assertEquals(context.serialize(), "0- ");

        context.deserialize("0- ");
        Assert.assertEquals(context.serialize(), "0- ");

        context.deserialize("test- ");
        Assert.assertEquals(context.serialize(), "0- ");

        context.deserialize("1-test");
        Assert.assertEquals(context.serialize(), "1- ");

        context.deserialize("0-1602743904804");
        Assert.assertEquals(context.serialize(), "0-1602743904804");
    }

    @Test
    public void testClone() {
        final ExtensionContext context = new ExtensionContext();
        Assert.assertEquals(context, context.clone());

        context.deserialize("0-1602743904804");
        Assert.assertEquals(context, context.clone());
    }

    @Test
    public void testHandle() throws Exception {
        final ExtensionContext context = new ExtensionContext();
        context.deserialize("1- ");
        NoopSpan span = Mockito.mock(NoopSpan.class);
        context.handle(span);
        verify(span, times(1)).skipAnalysis();

        context.deserialize("0- ");
        span = Mockito.mock(NoopSpan.class);
        context.handle(span);
        verify(span, times(0)).skipAnalysis();

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(1602743904804L + 500);
        span = PowerMockito.mock(NoopSpan.class);
        context.deserialize("0-1602743904804");
        context.handle(span);
        verify(span, times(0)).tag(Tags.TRANSMISSION_LATENCY, "500");
    }

    @Test
    public void testEqual() {
        Assert.assertEquals(new ExtensionContext(), new ExtensionContext());
        ExtensionContext context = new ExtensionContext();
        context.setSendingTimestamp(1L);
        Assert.assertNotEquals(context, new ExtensionContext());
        Assert.assertNotEquals(new ExtensionContext(), context);
        ExtensionContext another = new ExtensionContext();
        another.setSendingTimestamp(1L);
        Assert.assertEquals(context, another);
        Assert.assertEquals(another, context);
    }
}
