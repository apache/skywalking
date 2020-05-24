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

public class CounterTest {

    @Test
    public void testBuild() {
        Counter counter1 = Counter.create("test_counter").tag("k1", "v1").build();
        Assert.assertEquals(counter1.getTag("k1"), "v1");
        Assert.assertNull(counter1.getTag("k2"));
        Assert.assertEquals(counter1.getName(), "test_counter");

        final Counter counter2 = Counter.create("test_counter").build();
        Assert.assertNull(counter2.getTag("k1"));

        counter1.increment(1L);
        counter2.increment(1L);
    }

}
