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

import com.google.gson.Gson;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.Documents;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchScroller;

public abstract class EsDAO extends AbstractDAO<ElasticSearchClient> {
    protected static final Duration SCROLL_CONTEXT_RETENTION = Duration.ofSeconds(30);

    public EsDAO(ElasticSearchClient client) {
        super(client);
    }

    protected SearchResponse searchDebuggable(Supplier<String[]> indices, Search search) {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Elasticsearch: search");
                builder.append("Condition: ").append("indices: ").append(Arrays.toString(indices.get()));
                span.setMsg(builder.toString());
            }
            SearchResponse response = getClient().search(indices, search);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(new Gson().toJson(response));
                span.setMsg(builder.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    protected SearchResponse searchDebuggable(String indexName, Search search, SearchParams params) {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Elasticsearch");
                builder.append("Condition: ").append("indices: ").append(indexName)
                       .append(", params: ").append(params);
                span.setMsg(builder.toString());
            }
            SearchResponse response = params == null ? getClient().search(indexName, search) : getClient().search(
                indexName, search, params);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(new Gson().toJson(response));
                span.setMsg(builder.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    protected Optional<Documents> idsDebuggable(Map<String, List<String>> indexIdsGroup) {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Elasticsearch: ids");
                builder.append("Condition: ").append("indices: ").append(indexIdsGroup.keySet());
                span.setMsg(builder.toString());
            }
            Optional<Documents> response = getClient().ids(indexIdsGroup);
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(response.isPresent() ? new Gson().toJson(response.get()) : "null");
                span.setMsg(builder.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    protected <T> List<T> scrollDebuggable(ElasticSearchScroller<T> scroller, String index, SearchParams params) {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Elasticsearch: scroll");
                builder.append("Condition: ").append("index: ").append(index).append(", params: ").append(params);
                span.setMsg(builder.toString());
            }

            var response = scroller.scroll();
            if (traceContext != null && traceContext.isDumpStorageRsp()) {
                builder.append("\n").append(" Response: ").append(new Gson().toJson(response));
                span.setMsg(builder.toString());
            }
            return response;
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }
}
