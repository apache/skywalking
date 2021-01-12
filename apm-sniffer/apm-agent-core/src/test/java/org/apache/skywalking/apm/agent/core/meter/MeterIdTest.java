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

package org.apache.skywalking.apm.agent.core.meter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class MeterIdTest {

    @Test
    public void testTransformTags() {
        MeterId meterId = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));

        // Label message check
        List<Label> labels = meterId.transformTags();
        Assert.assertEquals(1, labels.size());
        final Label label = labels.get(0);
        Assert.assertEquals("k1", label.getName());
        Assert.assertEquals("v1", label.getValue());
        Assert.assertEquals(MeterType.COUNTER, meterId.getType());

        // Must cache the Label message
        final List<Label> cacheLabels = (List<Label>) Whitebox.getInternalState(meterId, "labels");
        Assert.assertEquals(labels, cacheLabels);

        // Check empty tags
        meterId = new MeterId("test", MeterType.COUNTER, Collections.emptyList());
        labels = meterId.transformTags();
        Assert.assertEquals(0, labels.size());
    }

    @Test
    public void testEquals() {
        final MeterId meterId1 = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));
        final MeterId meterId2 = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));

        Assert.assertEquals(meterId1, meterId2);
    }

}
