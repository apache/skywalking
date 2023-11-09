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

package org.apache.skywalking.oap.server.library.client.elasticsearch;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

@Builder
@RequiredArgsConstructor
public class ElasticSearchScroller<T> {
    public static final Duration SCROLL_CONTEXT_RETENTION = Duration.ofSeconds(30);

    final ElasticSearchClient client;
    final Search search;
    final String index;
    @Builder.Default
    final int queryMaxSize = 0;
    @Builder.Default
    final SearchParams params = new SearchParams();
    final Function<SearchHit, T> resultConverter;

    public List<T> scroll() {
        final var results = new ArrayList<T>();
        final var scrollIds = new HashSet<String>();

        params.scroll(SCROLL_CONTEXT_RETENTION);

        var response = client.search(index, search, params);

        try {
            while (true) {
                final var scrollId = response.getScrollId();
                scrollIds.add(scrollId);
                if (response.getHits().getTotal() == 0) {
                    break;
                }
                for (final var searchHit : response.getHits()) {
                    results.add(resultConverter.apply(searchHit));
                    if (queryMaxSize > 0 && results.size() >= queryMaxSize) {
                        return results;
                    }
                }
                if (search.getSize() != null && response.getHits().getHits().size() < search.getSize()) {
                    return results;
                }
                response = client.scroll(SCROLL_CONTEXT_RETENTION, scrollId);
            }
        } finally {
            scrollIds.forEach(client::deleteScrollContextQuietly);
        }

        return results;
    }
}
