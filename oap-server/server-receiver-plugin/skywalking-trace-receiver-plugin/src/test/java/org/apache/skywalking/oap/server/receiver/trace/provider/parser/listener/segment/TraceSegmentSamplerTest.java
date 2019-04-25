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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.segment;

import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.junit.Assert;
import org.junit.Test;

public class TraceSegmentSamplerTest {
    @Test
    public void sample() {
        TraceSegmentSampler sampler = new TraceSegmentSampler(100);
        Assert.assertTrue(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(0).build()));
        Assert.assertTrue(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(50).build()));
        Assert.assertTrue(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(99).build()));
        Assert.assertFalse(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(100).build()));
        Assert.assertFalse(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(101).build()));
        Assert.assertTrue(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(10000).build()));
        Assert.assertTrue(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(10001).build()));
        Assert.assertFalse(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(1019903).build()));
    }

    @Test
    public void IllegalTraceIDSample() {
        TraceSegmentSampler sampler = new TraceSegmentSampler(100);
        Assert.assertFalse(sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).build()));

        Assert.assertFalse(
            sampler.shouldSample(UniqueId.newBuilder().addIdParts(123).addIdParts(2).addIdParts(23).addIdParts(3).build()));
    }
}
