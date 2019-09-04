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
 */

package org.apache.skywalking.oap.server.core.analysis.metrics;

import org.junit.*;

/**
 * @author peng-yongsheng
 */
public class IntKeyLongValueHashMapTestCase {

    private IntKeyLongValueHashMap intKeyLongValueHashMap;

    @Before
    public void init() {
        IntKeyLongValue v1 = new IntKeyLongValue(5, 500);
        IntKeyLongValue v2 = new IntKeyLongValue(6, 600);
        IntKeyLongValue v3 = new IntKeyLongValue(1, 100);
        IntKeyLongValue v4 = new IntKeyLongValue(2, 200);
        IntKeyLongValue v5 = new IntKeyLongValue(7, 700);

        intKeyLongValueHashMap = new IntKeyLongValueHashMap();
        intKeyLongValueHashMap.put(v1.getKey(), v1);
        intKeyLongValueHashMap.put(v2.getKey(), v2);
        intKeyLongValueHashMap.put(v3.getKey(), v3);
        intKeyLongValueHashMap.put(v4.getKey(), v4);
        intKeyLongValueHashMap.put(v5.getKey(), v5);
    }

    @Test
    public void toStorageData() {
        Assert.assertEquals("1,100|2,200|5,500|6,600|7,700", intKeyLongValueHashMap.toStorageData());
    }

    @Test
    public void toObject() {
        IntKeyLongValueHashMap intKeyLongValueHashMap = new IntKeyLongValueHashMap();
        intKeyLongValueHashMap.toObject("1,100|2,200|5,500|6,600|7,700");

        Assert.assertEquals(100, intKeyLongValueHashMap.get(1).getValue());
        Assert.assertEquals(200, intKeyLongValueHashMap.get(2).getValue());
        Assert.assertEquals(500, intKeyLongValueHashMap.get(5).getValue());
        Assert.assertEquals(600, intKeyLongValueHashMap.get(6).getValue());
        Assert.assertEquals(700, intKeyLongValueHashMap.get(7).getValue());
    }

    @Test
    public void copyFrom() {
        IntKeyLongValueHashMap intKeyLongValueHashMap = new IntKeyLongValueHashMap();
        intKeyLongValueHashMap.copyFrom(this.intKeyLongValueHashMap);

        Assert.assertEquals("1,100|2,200|5,500|6,600|7,700", intKeyLongValueHashMap.toStorageData());
    }
}
