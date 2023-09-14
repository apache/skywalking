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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.IndexRequest;
import org.apache.skywalking.library.elasticsearch.requests.UpdateRequest;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.Documents;

@Slf4j
@RequiredArgsConstructor
public final class DocumentClient {
    private final CompletableFuture<ElasticSearchVersion> version;

    private final WebClient client;

    @SneakyThrows
    public boolean exists(String index, String type, String id) {
        return version.thenCompose(
            v -> client.execute(v.requestFactory().document().exist(index, type, id))
                       .aggregate().thenApply(response -> response.status() == HttpStatus.OK)
                       .exceptionally(e -> {
                           log.error("Failed to check whether document exists", e);
                           return false;
                       })).get();
    }

    @SneakyThrows
    public Optional<Document> get(String index, String type, String id) {
        final CompletableFuture<Optional<Document>> future = version.thenCompose(
            v -> client.execute(v.requestFactory().document().get(index, type, id))
                       .aggregate().thenApply(response -> {
                    if (response.status() != HttpStatus.OK) {
                        throw new RuntimeException(response.contentUtf8());
                    }

                    try (final HttpData content = response.content();
                         final InputStream is = content.toInputStream()) {
                        final Document document = v.codec().decode(is, Document.class);
                        if (!document.isFound()) {
                            return Optional.empty();
                        }
                        return Optional.of(document);
                    } catch (Exception e) {
                        return Exceptions.throwUnsafely(e);
                    }
                }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to get doc by id {} in index {}", id, index, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Doc by id {} in index {}: {}", id, index, result);
            }
        });
        return future.get();
    }

    @SneakyThrows
    public Optional<Documents> mget(String type, Map<String, List<String>> indexIds) {
        final CompletableFuture<Optional<Documents>> future =
            version.thenCompose(
                v -> client.execute(v.requestFactory().document().mget(type, indexIds))
                           .aggregate().thenApply(response -> {
                        if (response.status() != HttpStatus.OK) {
                            throw new RuntimeException(response.contentUtf8());
                        }

                        try (final HttpData content = response.content();
                             final InputStream is = content.toInputStream()) {
                            return Optional.of(v.codec().decode(is, Documents.class));
                        } catch (Exception e) {
                            return Exceptions.throwUnsafely(e);
                        }
                    }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to get doc by indexIds {}", indexIds, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Docs by indexIds {}: {}", indexIds, result);
            }
        });
        return future.get();
    }

    @SneakyThrows
    public void index(IndexRequest request, Map<String, Object> params) {
        final CompletableFuture<Void> future = version.thenCompose(
            v -> client.execute(v.requestFactory().document().index(request, params))
                       .aggregate().thenAccept(response -> {
                    final HttpStatus status = response.status();
                    if (status != HttpStatus.CREATED && status != HttpStatus.OK) {
                        throw new RuntimeException(response.contentUtf8());
                    }
                }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to index doc: {}, params: {}", request, params, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded indexing doc: {}, params: {}", request, params);
            }
        });
        future.join();
    }

    @SneakyThrows
    public void update(UpdateRequest request, Map<String, Object> params) {
        final CompletableFuture<Void> future = version.thenCompose(
            v -> client.execute(v.requestFactory().document().update(request, params))
                       .aggregate().thenAccept(response -> {
                    final HttpStatus status = response.status();
                    if (status != HttpStatus.OK) {
                        throw new RuntimeException(response.contentUtf8());
                    }
                }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to update doc: {}, params: {}", request, params, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded updating doc: {}, params: {}", request, params);
            }
        });
        future.join();
    }

    @SneakyThrows
    public void deleteById(String index, String type, String id, Map<String, Object> params) {
        final CompletableFuture<Void> future = version.thenCompose(
            v -> client.execute(v.requestFactory().document().deleteById(index, type, id, params))
                       .aggregate().thenAccept(response -> {
                    final HttpStatus status = response.status();
                    if (status != HttpStatus.OK) {
                        throw new RuntimeException(response.contentUtf8());
                    }
                }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to delete doc by id {} in index {}, params: {}", id, index, params, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded delete doc by id {} in index {}, params: {}", id, params, index);
            }
        });
        future.join();
    }
}
