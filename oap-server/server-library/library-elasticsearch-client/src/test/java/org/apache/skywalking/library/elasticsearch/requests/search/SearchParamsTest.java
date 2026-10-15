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

package org.apache.skywalking.library.elasticsearch.requests.search;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchParamsTest {
    @Test
    public void copyShouldKeepTheParamsAndLeaveTheOriginalUnchanged() {
        final SearchParams original = new SearchParams().routing("trace");

        final SearchParams copy = original.copy().ignoreUnavailable(true);

        assertThat(toMap(original)).containsOnly(Map.entry("routing", "trace"));
        assertThat(toMap(copy)).containsOnly(Map.entry("routing", "trace"), Map.entry("ignore_unavailable", true));
    }

    private static Map<String, Object> toMap(final SearchParams params) {
        final Map<String, Object> map = new HashMap<>();
        params.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        return map;
    }
}
