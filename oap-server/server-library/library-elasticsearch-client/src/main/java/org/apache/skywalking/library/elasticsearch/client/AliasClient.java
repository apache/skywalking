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

import com.fasterxml.jackson.core.type.TypeReference;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.util.Exceptions;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.response.Index;

@Slf4j
@RequiredArgsConstructor
public final class AliasClient {
    private final CompletableFuture<ElasticSearchVersion> version;

    private final WebClient client;

    @SneakyThrows
    public Map<String, Index> indices(String name) {
        final CompletableFuture<Map<String, Index>> future =
            version.thenCompose(
                v -> client.execute(v.requestFactory().alias().indices(name))
                           .aggregate().thenApply(response -> {
                        final HttpStatus status = response.status();
                        if (status != HttpStatus.OK) {
                            throw new RuntimeException(response.contentUtf8());
                        }

                        try (final HttpData content = response.content();
                             final InputStream is = content.toInputStream()) {
                            return v.codec().decode(is, new TypeReference<Map<String, Index>>() {
                            });
                        } catch (Exception e) {
                            return Exceptions.throwUnsafely(e);
                        }
                    }));
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to get indices by alias {}.", name, exception);
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Indices by alias {}: {}", name, result);
            }
        });
        return future.get();
    }
}
