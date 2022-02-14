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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplates;
import org.apache.skywalking.library.elasticsearch.response.Mappings;

@Slf4j
@RequiredArgsConstructor
public final class TemplateClient {
    private final CompletableFuture<ElasticSearchVersion> version;

    private final WebClient client;

    @SneakyThrows
    public boolean exists(String name) {
        final CompletableFuture<Boolean> future = version.thenCompose(
            v -> client.execute(v.requestFactory().template().exists(name))
                       .aggregate().thenApply(response -> {
                    final HttpStatus status = response.status();
                    if (status == HttpStatus.OK) {
                        return true;
                    } else if (status == HttpStatus.NOT_FOUND) {
                        return false;
                    }
                    throw new RuntimeException(response.contentUtf8());
                }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to check whether template {} exists", name, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded to check whether template {} exists {}", name, result);
            }
        });
        return future.get();
    }

    @SneakyThrows
    public Optional<IndexTemplate> get(String name) {
        final CompletableFuture<Optional<IndexTemplate>> future =
            version.thenCompose(
                v -> client.execute(v.requestFactory().template().get(name))
                           .aggregate().thenApply(response -> {
                        final HttpStatus status = response.status();
                        if (status == HttpStatus.NOT_FOUND) {
                            return Optional.empty();
                        }
                        if (status != HttpStatus.OK) {
                            throw new RuntimeException(response.contentUtf8());
                        }

                        try (final HttpData content = response.content();
                             final InputStream is = content.toInputStream()) {
                            final IndexTemplates templates =
                                v.codec().decode(is, IndexTemplates.class);
                            return templates.get(name);
                        } catch (Exception e) {
                            return Exceptions.throwUnsafely(e);
                        }
                    }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to get index template {}", name, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded to get index template {}, {}", name, result);
            }
        });
        return future.get();
    }

    @SneakyThrows
    public boolean delete(String name) {
        final CompletableFuture<Boolean> future = version.thenCompose(
            v -> client.execute(v.requestFactory().template().delete(name))
                       .aggregate().thenApply(response -> {
                    if (response.status() == HttpStatus.OK) {
                        return true;
                    }
                    throw new RuntimeException(response.contentUtf8());
                }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to delete index template {}", name, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded to delete index template {}, {}", name, result);
            }
        });
        return future.get();
    }

    @SneakyThrows
    public boolean createOrUpdate(String name, Map<String, Object> settings,
                                  Mappings mappings, int order) {
        final CompletableFuture<Boolean> future = version.thenCompose(
            v -> client.execute(v.requestFactory().template()
                                 .createOrUpdate(name, settings, mappings, order))
                       .aggregate().thenApply(response -> {
                    if (response.status() == HttpStatus.OK) {
                        return true;
                    }
                    throw new RuntimeException(response.contentUtf8());
                }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to create / update index template {}", name, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Succeeded to create / update index template {}, {}", name, result);
            }
        });
        return future.get();
    }

}
