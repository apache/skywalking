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

package org.apache.skywalking.oap.server.ai.agent.conversation.query.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.RequestHeadersBuilder;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.ai.agent.conversation.Fixtures;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRawFiles;
import org.apache.skywalking.oap.server.ai.agent.conversation.view.ConversationViewBuilder;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route on a real server: the fixture conversation comes back as the same document the Sessionizer prints,
 * in both bodies, compressed or not, in more than one chunk, and the error paths answer with their statuses.
 */
public class ConversationViewHandlerTest {
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final String SERVICE = "agent";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final Map<String, Object> DOC = fixtureDocument();
    private static final AtomicReference<String> LAST_INSTANCE_ID = new AtomicReference<>();

    private static final IConversationQueryService STUB = new IConversationQueryService() {
        @Override
        public ConversationList listConversations(final String serviceId, @Nullable final String serviceInstanceId,
                                                  final Duration duration, @Nullable final Integer limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        @Nullable
        public Map<String, Object> buildConversationView(final String serviceId,
                                                         @Nullable final String serviceInstanceId,
                                                         final String conversation) throws IOException {
            LAST_INSTANCE_ID.set(serviceInstanceId);
            if ("broken".equals(conversation)) {
                throw new IOException("storage is down");
            }
            return SERVICE_ID.equals(serviceId) && Fixtures.SESSION.equals(conversation) ? DOC : null;
        }

        @Override
        public ConversationRawFiles getConversationRawFiles(final String serviceId,
                                                            @Nullable final String serviceInstanceId,
                                                            final String conversation,
                                                            @Nullable final List<String> files,
                                                            final boolean includeBody) {
            throw new UnsupportedOperationException();
        }
    };

    @RegisterExtension
    static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(final ServerBuilder sb) {
            sb.annotatedService(new ConversationViewHandler(STUB, java.time.Duration.ofSeconds(30)));
        }
    };

    private static Map<String, Object> fixtureDocument() {
        try {
            final byte[] bytes = Fixtures.bytes(Fixtures.ROUND_FILE);
            final SessionFlowRound round = SessionFlowRound.parse(bytes);
            final ConversationFold fold = new ConversationFold();
            fold.apply(round);
            final List<ConversationViewBuilder.RoundInput> rounds = new ArrayList<>();
            rounds.add(new ConversationViewBuilder.RoundInput(round, Digests.sha256Hex(bytes)));
            return new ConversationViewBuilder(fold, rounds, Fixtures.dataFiles(), new ArrayList<>()).build();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String path(final String conversation) {
        return "/ai-agent/conversations/" + conversation + "/v1/view?service=" + SERVICE;
    }

    private static AggregatedHttpResponse get(final String path, final String... headers) {
        final RequestHeadersBuilder req = RequestHeaders.builder(HttpMethod.GET, path);
        for (int i = 0; i < headers.length; i += 2) {
            req.add(headers[i], headers[i + 1]);
        }
        return WebClient.of(SERVER.httpUri()).execute(req.build()).aggregate().join();
    }

    @Test
    public void jsonIsTheSessionizersDocument() throws Exception {
        final AggregatedHttpResponse res = get(path(Fixtures.SESSION));
        assertEquals(200, res.status().code());
        assertEquals("application/vnd.skywalking.asz.view+json; version=1.0; charset=utf-8", String.valueOf(res.contentType()));
        final JsonElement expected = JsonParser.parseString(
            new String(Fixtures.bytes(Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        assertEquals(expected, JsonParser.parseString(res.contentUtf8()));
        // the key order too, as the format page fixes it
        assertEquals(GSON.toJson(expected), res.contentUtf8());
    }

    @Test
    public void yamlOnAccept() {
        final AggregatedHttpResponse res = get(path(Fixtures.SESSION), "accept", "application/yaml");
        assertEquals(200, res.status().code());
        assertEquals("application/vnd.skywalking.asz.view+yaml; version=1.0; charset=utf-8", String.valueOf(res.contentType()));
        assertTrue(res.contentUtf8().startsWith("format: asz.view\nversion: '1.0'\n"));
        assertEquals(GSON.toJsonTree(DOC), GSON.toJsonTree(new Yaml().load(res.contentUtf8())));
    }

    @Test
    public void gzipOnAcceptEncoding() throws Exception {
        final AggregatedHttpResponse res = get(path(Fixtures.SESSION), "accept-encoding", "gzip");
        assertEquals(200, res.status().code());
        assertEquals("gzip", res.headers().get(HttpHeaderNames.CONTENT_ENCODING));
        final byte[] inflated;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(res.content().array()))) {
            inflated = in.readAllBytes();
        }
        assertEquals(GSON.toJsonTree(DOC), JsonParser.parseString(new String(inflated, StandardCharsets.UTF_8)));
        assertTrue(res.content().length() < inflated.length / 3, "compressed " + res.content().length() + " of " + inflated.length);
    }

    /**
     * The render is handed to the response chunk by chunk, so a document never exists whole in memory.
     */
    @Test
    public void theRenderIsWrittenInChunks() throws Exception {
        final HttpResponseWriter res = HttpResponse.streaming();
        final CompletableFuture<List<HttpObject>> collected = res.collect();
        res.write(ResponseHeaders.of(200));
        final char[] text = new char[300 * 1024];
        Arrays.fill(text, 'x');
        try (Writer out = new ConversationViewHandler.ChunkWriter(res, java.time.Duration.ofSeconds(10))) {
            out.write(text);
        }
        res.close();
        int chunks = 0;
        long bytes = 0;
        for (final HttpObject o : collected.get(10, TimeUnit.SECONDS)) {
            if (o instanceof HttpData) {
                chunks++;
                bytes += ((HttpData) o).length();
            }
        }
        assertEquals(text.length, bytes);
        assertTrue(chunks >= 4, "chunks: " + chunks);
    }

    /**
     * A chunk boundary never falls between the two halves of a surrogate pair, so a four-byte character at the
     * boundary reaches the client whole.
     */
    @Test
    public void aCodePointAtTheChunkBoundaryStaysWhole() throws Exception {
        final HttpResponseWriter res = HttpResponse.streaming();
        final CompletableFuture<List<HttpObject>> collected = res.collect();
        res.write(ResponseHeaders.of(200));
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < 64 * 1024 - 1; i++) {
            text.append('x');
        }
        text.append("\uD83D\uDE00").append("tail");
        try (Writer out = new ConversationViewHandler.ChunkWriter(res, java.time.Duration.ofSeconds(10))) {
            out.write(text.toString());
        }
        res.close();
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int chunks = 0;
        for (final HttpObject o : collected.get(10, TimeUnit.SECONDS)) {
            if (o instanceof HttpData) {
                chunks++;
                bytes.write(((HttpData) o).array());
            }
        }
        assertEquals(text.toString(), new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        assertTrue(chunks >= 2, "chunks: " + chunks);
    }

    @Test
    public void theInstanceParameterNamesTheSender() {
        get(path(Fixtures.SESSION) + "&instance=sender-1");
        assertEquals(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "sender-1"), LAST_INSTANCE_ID.get());
    }

    @Test
    public void statusesOfTheErrorPaths() {
        assertEquals(404, get(path("no-such-conversation")).status().code());
        assertEquals(400, get("/ai-agent/conversations/" + Fixtures.SESSION + "/v1/view").status().code());
        final AggregatedHttpResponse broken = get(path("broken"));
        assertEquals(500, broken.status().code());
        assertTrue(broken.contentType().is(MediaType.parse("application/problem+json")), String.valueOf(broken.contentType()));
        assertEquals(
            "{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"status\":500,\"detail\":\"storage is down\"}",
            broken.contentUtf8());
    }
}
