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

package org.apache.skywalking.apm.collector.cache.guava;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * @author nikitap492
 */
public class CacheUtilsUnitTest {

    private Cache<Integer, String> testCache = CacheBuilder.newBuilder().maximumSize(10).build();

    @Before
    public void init() {
        testCache.put(5, "five");
    }

    @Test
    public void retrieve() {
        String value = CacheUtils.retrieve(testCache, 5, () -> null);
        assertEquals(value, "five");

        value = CacheUtils.retrieve(testCache, 10, () -> "ten");
        assertEquals(value, "ten");
        assertEquals(value, testCache.getIfPresent(10)); //put into the cache

    }

    @Test
    public void retrieveOrElse() {
        String value = CacheUtils.retrieveOrElse(testCache, 12, () -> null, "twelve");
        assertEquals(value, "twelve");
    }
}