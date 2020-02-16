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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils.compressTimeBucket;

public class TimeSeriesUtilsTest {
    @Test
    public void testCompressTimeBucket() {
        Assert.assertEquals(20000101L, compressTimeBucket(20000105, 11));
        Assert.assertEquals(20000101L, compressTimeBucket(20000111, 11));
        Assert.assertEquals(20000112L, compressTimeBucket(20000112, 11));
        Assert.assertEquals(20000112L, compressTimeBucket(20000122, 11));
        Assert.assertEquals(20000123L, compressTimeBucket(20000123, 11));
        Assert.assertEquals(20000123L, compressTimeBucket(20000125, 11));
    }
}
