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

package org.apache.skywalking.library.elasticsearch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.WebClientBuilder;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.logging.LoggingClient;
import com.linecorp.armeria.client.retry.RetryRule;
import com.linecorp.armeria.client.retry.RetryingClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.auth.AuthToken;
import com.linecorp.armeria.common.util.Exceptions;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import org.apache.skywalking.library.elasticsearch.client.AliasClient;
import org.apache.skywalking.library.elasticsearch.client.DocumentClient;
import org.apache.skywalking.library.elasticsearch.client.IndexClient;
import org.apache.skywalking.library.elasticsearch.client.SearchClient;
import org.apache.skywalking.library.elasticsearch.client.TemplateClient;
import org.apache.skywalking.library.elasticsearch.requests.search.Scroll;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.NodeInfo;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
@Accessors(fluent = true)
public final class ElasticSearch implements Closeable {
    private final ObjectMapper mapper = new ObjectMapper(
        new JsonFactoryBuilder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
            .build())
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Getter
    private final WebClient client;
    @Getter
    private final CompletableFuture<ElasticSearchVersion> version;

    private final EndpointGroup endpointGroup;
    private final ClientFactory clientFactory;
    private final Consumer<List<Endpoint>> healthyEndpointListener;

    private final TemplateClient templateClient;
    private final IndexClient indexClient;
    private final DocumentClient documentClient;
    private final AliasClient aliasClient;
    private final SearchClient searchClient;

    ElasticSearch(SessionProtocol protocol,
                  String username, String password,
                  EndpointGroup endpointGroup,
                  ClientFactory clientFactory,
                  Consumer<Boolean> healthyListener,
                  Duration responseTimeout) {
        this.endpointGroup = endpointGroup;
        this.clientFactory = clientFactory;
        if (healthyListener != null) {
            healthyEndpointListener = it -> healthyListener.accept(!it.isEmpty());
        } else {
            healthyEndpointListener = it -> {
            };
        }

        final WebClientBuilder builder =
            WebClient.builder(protocol, endpointGroup)
                     .factory(clientFactory)
                     .responseTimeout(responseTimeout)
                     .decorator(LoggingClient.builder()
                                             .logger(log)
                                             .newDecorator())
                     .decorator(RetryingClient.builder(RetryRule.failsafe())
                                              .maxTotalAttempts(3)
                                              .newDecorator());
        if (StringUtil.isNotBlank(username) && StringUtil.isNotBlank(password)) {
            builder.auth(AuthToken.ofBasic(username, password));
        }
        client = builder.build();
        version = new CompletableFuture<>();

        templateClient = new TemplateClient(version, client);
        documentClient = new DocumentClient(version, client);
        indexClient = new IndexClient(version, client);
        aliasClient = new AliasClient(version, client);
        searchClient = new SearchClient(version, client);
    }

    public static ElasticSearchBuilder builder() {
        return new ElasticSearchBuilder();
    }

    public CompletableFuture<ElasticSearchVersion> connect() {
        final CompletableFuture<ElasticSearchVersion> future =
            client.get("/").aggregate().thenApply(response -> {
                final HttpStatus status = response.status();
                if (status != HttpStatus.OK) {
                    throw new RuntimeException(
                        "Failed to connect to ElasticSearch server: " + response.contentUtf8());
                }
                try (final HttpData content = response.content();
                     final InputStream is = content.toInputStream()) {
                    final NodeInfo node = mapper.readValue(is, NodeInfo.class);
                    final String vn = node.getVersion().getNumber();
                    final String distribution = node.getVersion().getDistribution();
                    return ElasticSearchVersion.of(distribution, vn);
                } catch (IOException e) {
                    return Exceptions.throwUnsafely(e);
                }
            });
        future.whenComplete((v, throwable) -> {
            if (throwable != null) {
                final RuntimeException cause =
                    new RuntimeException("Failed to determine ElasticSearch version", throwable);
                version.completeExceptionally(cause);
                healthyEndpointListener.accept(Collections.emptyList());
                return;
            }
            log.info("ElasticSearch version is: {}", v);
            version.complete(v);
        });
        endpointGroup.whenReady().thenAccept(healthyEndpointListener);
        endpointGroup.addListener(healthyEndpointListener);
        return future;
    }

    public TemplateClient templates() {
        return templateClient;
    }

    public DocumentClient documents() {
        return documentClient;
    }

    public IndexClient index() {
        return indexClient;
    }

    public AliasClient alias() {
        return aliasClient;
    }

    public SearchResponse search(Search search, SearchParams params, String... index) {
        return searchClient.search(search, params, index);
    }

    public SearchResponse search(Search search, String... index) {
        return search(search, null, index);
    }

    public SearchResponse scroll(Duration contextRetention, String scrollId) {
        return searchClient.scroll(
            Scroll.builder()
                  .contextRetention(contextRetention)
                  .scrollId(scrollId)
                  .build());
    }

    public boolean deleteScrollContext(String scrollId) {
        return searchClient.deleteScrollContext(scrollId);
    }

    @Override
    public void close() {
        endpointGroup.removeListener(healthyEndpointListener);
        clientFactory.close();
        endpointGroup.close();
    }
}
