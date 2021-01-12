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

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public class CorrelationContextTest {

    @Before
    public void setupConfig() {
        Config.Correlation.ELEMENT_MAX_NUMBER = 2;
        Config.Correlation.VALUE_MAX_LENGTH = 8;
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
}
