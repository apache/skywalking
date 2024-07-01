/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.elasticsearch.requests.search;

import lombok.ToString;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static com.google.common.base.Preconditions.checkArgument;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

@ToString
public final class SearchParams implements Iterable<Map.Entry<String, Object>> {
    private static final String IGNORE_UNAVAILABLE = "ignore_unavailable";
    private static final String ALLOW_NO_INDICES = "allow_no_indices";
    private static final String EXPAND_WILDCARDS = "expand_wildcards";
    private static final String SCROLL = "scroll";
    private static final String ROUTING = "routing";

    private final Map<String, Object> params = new HashMap<>();

    public SearchParams ignoreUnavailable(boolean ignoreUnavailable) {
        params.put(IGNORE_UNAVAILABLE, ignoreUnavailable);
        return this;
    }

    public SearchParams allowNoIndices(boolean allowNoIndices) {
        params.put(ALLOW_NO_INDICES, allowNoIndices);
        return this;
    }

    public SearchParams expandWildcards(String wildcards) {
        params.put(EXPAND_WILDCARDS, wildcards);
        return this;
    }

    public SearchParams scroll(Duration contextRetention) {
        checkArgument(
            contextRetention != null && !contextRetention.isNegative()
                && !contextRetention.isZero(),
            "contextRetention must be positive, but was %s",
            contextRetention);
        params.put(SCROLL, contextRetention.getSeconds() + "s");
        return this;
    }

    public SearchParams routing(String routing) {
        checkArgument(StringUtil.isNotBlank(routing),
                "routing must be not blank");
        params.put(ROUTING, routing);
        return this;
    }

    public SearchParams routing(Iterable<String> routings) {
        checkArgument(routings != null,
                "routing set must be non-null");
        routing(String.join(",", routings));
        return this;
    }

    @Override
    public Iterator<Entry<String, Object>> iterator() {
        return params.entrySet().iterator();
    }
}
