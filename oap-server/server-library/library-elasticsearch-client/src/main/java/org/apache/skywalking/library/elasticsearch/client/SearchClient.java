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

package org.apache.skywalking.library.elasticsearch.client;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.util.Exceptions;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.search.Scroll;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;

@Slf4j
@RequiredArgsConstructor
public final class SearchClient {
    private final CompletableFuture<ElasticSearchVersion> version;

    private final WebClient client;

    @SneakyThrows
    public SearchResponse search(Search criteria,
                                 SearchParams params,
                                 String... index) {
        final CompletableFuture<SearchResponse> future =
            version.thenCompose(
                v -> client.execute(v.requestFactory().search().search(criteria, params, index))
                           .aggregate().thenApply(response -> {
                        if (response.status() != HttpStatus.OK) {
                            throw new RuntimeException(response.contentUtf8());
                        }

                        try (final HttpData content = response.content();
                             final InputStream is = content.toInputStream()) {
                            return v.codec().decode(is, SearchResponse.class);
                        } catch (Exception e) {
                            return Exceptions.throwUnsafely(e);
                        }
                    }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error(
                    "Failed to search, request {}, params {}, index {}",
                    criteria, params, index,
                    exception
                );
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded to search index {}, {}", index, result);
            }
        });
        return future.get();
    }

    @SneakyThrows
    public SearchResponse scroll(Scroll scroll) {
        final CompletableFuture<SearchResponse> future =
            version.thenCompose(
                v -> client.execute(v.requestFactory().search().scroll(scroll))
                           .aggregate().thenApply(response -> {
                        if (response.status() != HttpStatus.OK) {
                            throw new RuntimeException(response.contentUtf8());
                        }

                        try (final HttpData content = response.content();
                             final InputStream is = content.toInputStream()) {
                            return v.codec().decode(is, SearchResponse.class);
                        } catch (Exception e) {
                            return Exceptions.throwUnsafely(e);
                        }
                    }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to scroll, request {}, {}", scroll, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded to scroll, {}", result);
            }
        });
        return future.get();
    }

    @SneakyThrows
    public boolean deleteScrollContext(String scrollId) {
        final CompletableFuture<Boolean> future =
            version.thenCompose(
                v -> client.execute(v.requestFactory().search().deleteScrollContext(scrollId))
                           .aggregate().thenApply(response -> {
                            if (response.status() == HttpStatus.OK) {
                                return true;
                            }
                            throw new RuntimeException(response.contentUtf8());
                    }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to delete scroll context, request {}, {}", scrollId, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded to delete scroll context, {}", result);
            }
        });
        return future.get();
    }
}
