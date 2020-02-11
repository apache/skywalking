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

package org.apache.skywalking.oap.server.library.util;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectionUtilsTest {

    @Test
    public void test() {
        assertTrue(CollectionUtils.isEmpty((Map) null));
        assertTrue(CollectionUtils.isEmpty(Collections.emptyMap()));
        assertFalse(CollectionUtils.isEmpty(ImmutableMap.of(1, 2)));
        assertFalse(CollectionUtils.isNotEmpty((Map) null));
        assertFalse(CollectionUtils.isNotEmpty(Collections.emptyMap()));
        assertTrue(CollectionUtils.isNotEmpty(ImmutableMap.of(1, 2)));

        assertTrue(CollectionUtils.isEmpty((List) null));
        assertTrue(CollectionUtils.isEmpty(Collections.emptyList()));
        assertFalse(CollectionUtils.isEmpty(Arrays.asList(1, 2)));
        assertFalse(CollectionUtils.isNotEmpty((List) null));
        assertFalse(CollectionUtils.isNotEmpty(Collections.emptyList()));
        assertTrue(CollectionUtils.isNotEmpty(Arrays.asList(1, 2)));

        assertTrue(CollectionUtils.isEmpty((Set) null));
        assertTrue(CollectionUtils.isEmpty(Collections.emptySet()));
        assertFalse(CollectionUtils.isEmpty(new HashSet<>(Arrays.asList(1, 2))));
        assertFalse(CollectionUtils.isNotEmpty((List) null));
        assertFalse(CollectionUtils.isNotEmpty(Collections.emptySet()));
        assertTrue(CollectionUtils.isNotEmpty(new HashSet<>(Arrays.asList(1, 2))));

        assertFalse(CollectionUtils.isNotEmpty((Object[]) null));
        assertTrue(CollectionUtils.isEmpty(new byte[0]));
        assertTrue(CollectionUtils.isEmpty((byte[]) null));
        assertTrue(CollectionUtils.isNotEmpty(new byte[1]));
    }
}