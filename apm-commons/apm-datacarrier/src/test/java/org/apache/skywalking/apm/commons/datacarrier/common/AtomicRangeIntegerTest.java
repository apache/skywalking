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


package org.apache.skywalking.apm.commons.datacarrier.common;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by xin on 2017/7/14.
 */
public class AtomicRangeIntegerTest {
    @Test
    public void testGetAndIncrement() {
        AtomicRangeInteger atomicI = new AtomicRangeInteger(0, 10);
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(i, atomicI.getAndIncrement());
        }
        Assert.assertEquals(0, atomicI.getAndIncrement());
        Assert.assertEquals(1, atomicI.get());
        Assert.assertEquals(1, atomicI.intValue());
        Assert.assertEquals(1, atomicI.longValue());
        Assert.assertEquals(1, (int)atomicI.floatValue());
        Assert.assertEquals(1, (int)atomicI.doubleValue());
    }
}
