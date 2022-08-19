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

package org.apache.skywalking.library.elasticsearch.requests.factory.v6;

import com.google.common.collect.ImmutableMap;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpRequestBuilder;
import com.linecorp.armeria.common.MediaType;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.IndexRequest;
import org.apache.skywalking.library.elasticsearch.requests.UpdateRequest;
import org.apache.skywalking.library.elasticsearch.requests.factory.DocumentFactory;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.isEmpty;
import static java.util.Objects.requireNonNull;

@Slf4j
@RequiredArgsConstructor
final class V6DocumentFactory implements DocumentFactory {
    private final ElasticSearchVersion version;

    @Override
    public HttpRequest exist(String index, String type, String id) {
        checkArgument(!isNullOrEmpty(index), "index cannot be null or empty");
        checkArgument(!isNullOrEmpty(type), "type cannot be null or empty");
        checkArgument(!isNullOrEmpty(id), "id cannot be null or empty");

        return HttpRequest.builder()
                          .head("/{index}/{type}/{id}")
                          .pathParam("index", index)
                          .pathParam("type", type)
                          .pathParam("id", id)
                          .build();
    }

    @Override
    public HttpRequest get(String index, String type, String id) {
        checkArgument(!isNullOrEmpty(index), "index cannot be null or empty");
        checkArgument(!isNullOrEmpty(type), "type cannot be null or empty");
        checkArgument(!isNullOrEmpty(id), "id cannot be null or empty");

        return HttpRequest.builder()
                          .get("/{index}/{type}/{id}")
                          .pathParam("index", index)
                          .pathParam("type", type)
                          .pathParam("id", id)
                          .build();
    }

    @SneakyThrows
    @Override
    public HttpRequest mget(String index, String type, Iterable<String> ids) {
        checkArgument(!isNullOrEmpty(index), "index cannot be null or empty");
        checkArgument(!isNullOrEmpty(type), "type cannot be null or empty");
        checkArgument(ids != null && !isEmpty(ids), "ids cannot be null or empty");

        final Map<String, Iterable<String>> m = ImmutableMap.of("ids", ids);
        final byte[] content = version.codec().encode(m);

        if (log.isDebugEnabled()) {
            log.debug("mget {} ids: {}", index, ids);
        }

        return HttpRequest.builder()
                          .get("/{index}/{type}/_mget")
                          .pathParam("index", index)
                          .pathParam("type", type)
                          .content(MediaType.JSON, content)
                          .build();
    }

    @SneakyThrows
    @Override
    public HttpRequest mget(final String type, final Map<String, List<String>> indexIds) {
        checkArgument(!isNullOrEmpty(type), "type cannot be null or empty");
        checkArgument(indexIds != null && !indexIds.isEmpty(), "ids cannot be null or empty");
        final List<Map<String, String>> indexIdList = new ArrayList<>();
        indexIds.forEach((index, ids) -> {
            checkArgument(ids != null && !isEmpty(ids), "ids cannot be null or empty");
            ids.forEach(id -> {
                indexIdList.add(ImmutableMap.of("_index", index, "_type", type, "_id", id));
            });
        });
        final Map<String, Iterable<Map<String, String>>> m = ImmutableMap.of("docs", indexIdList);
        final byte[] content = version.codec().encode(m);
        if (log.isDebugEnabled()) {
            log.debug("mget indexIds request: {}", new String(content, Charset.defaultCharset()));
        }

        return HttpRequest.builder()
                          .get("/_mget")
                          .content(MediaType.JSON, content)
                          .build();
    }

    @SneakyThrows
    @Override
    public HttpRequest index(IndexRequest request, Map<String, ?> params) {
        requireNonNull(request, "request");

        final String index = request.getIndex();
        final String type = request.getType();
        final String id = request.getId();
        final Map<String, ?> doc = request.getDoc();

        checkArgument(!isNullOrEmpty(index), "request.index cannot be null or empty");
        checkArgument(!isNullOrEmpty(type), "request.type cannot be null or empty");
        checkArgument(!isNullOrEmpty(id), "request.id cannot be null or empty");

        final HttpRequestBuilder builder = HttpRequest.builder();
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        final byte[] content = version.codec().encode(doc);

        builder.put("/{index}/{type}/{id}")
               .pathParam("index", index)
               .pathParam("type", type)
               .pathParam("id", id)
               .content(MediaType.JSON, content);

        return builder.build();
    }

    @SneakyThrows
    @Override
    public HttpRequest update(UpdateRequest request, Map<String, ?> params) {
        requireNonNull(request, "request");

        final String index = request.getIndex();
        final String type = request.getType();
        final String id = request.getId();
        final Map<String, Object> doc = request.getDoc();

        checkArgument(!isNullOrEmpty(index), "index cannot be null or empty");
        checkArgument(!isNullOrEmpty(type), "type cannot be null or empty");
        checkArgument(!isNullOrEmpty(id), "id cannot be null or empty");
        checkArgument(doc != null && !isEmpty(doc.entrySet()), "doc cannot be null or empty");

        final HttpRequestBuilder builder = HttpRequest.builder();
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        final byte[] content = version.codec().encode(ImmutableMap.of("doc", doc));

        builder.post("/{index}/{type}/{id}/_update")
               .pathParam("index", index)
               .pathParam("type", type)
               .pathParam("id", id)
               .content(MediaType.JSON, content);

        return builder.build();
    }

    @SneakyThrows
    @Override
    public HttpRequest delete(String index, String type, Query query,
                              Map<String, ?> params) {
        checkArgument(!isNullOrEmpty(index), "index cannot be null or empty");
        checkArgument(!isNullOrEmpty(type), "type cannot be null or empty");
        requireNonNull(query, "query");

        final HttpRequestBuilder builder = HttpRequest.builder();
        if (params != null) {
            params.forEach(builder::queryParam);
        }

        final byte[] content = version.codec().encode(ImmutableMap.of("query", query));

        return builder.delete("/{index}/{type}/_delete_by_query")
                      .pathParam("index", index)
                      .pathParam("type", type)
                      .content(MediaType.JSON, content)
                      .build();
    }
}
