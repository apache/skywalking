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

package org.apache.skywalking.apm.toolkit.meter;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class MeterIdTest {

    @Test
    public void testCopyTo() {
        final MeterId meterId = new MeterId("test", MeterId.MeterType.COUNTER, Arrays.asList(new MeterId.Tag("k1", "v1")));
        final MeterId copied = meterId.copyTo("test_copied", MeterId.MeterType.GAUGE);

        Assert.assertEquals("test_copied", copied.getName());
        Assert.assertEquals(MeterId.MeterType.GAUGE, copied.getType());
        Assert.assertEquals(meterId.getTags(), copied.getTags());
    }
}
