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

import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.util.TimeoutMode;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Decorator;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Param;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.ai.agent.conversation.view.ViewJson;
import org.apache.skywalking.oap.server.ai.agent.conversation.view.ViewYaml;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * <code>GET /ai-agent/conversations/{conversation}/v1/view</code>: the whole conversation as one
 * <code>asz.view</code> 1.0 document, streamed. It lives on the core HTTP server beside <code>/graphql</code>,
 * and is not a GraphQL query because the document is as large as the conversation, tens of megabytes for a
 * long one: it is written to the response as it is rendered, never held whole as one string, compressed when
 * the client allows, and given its own request timeout in place of the server's default.
 *
 * <p>Query parameters: <code>service</code>, the service name, or <code>serviceId</code>; optionally
 * <code>instance</code>, the sender's instance name. What the body is, the HTTP layer says: the media type
 * names the document format and its version, <code>application/vnd.skywalking.asz.view+json; version=1.0</code>, or the
 * <code>+yaml</code> twin when <code>Accept</code> asks for YAML. The document's own first two keys repeat it.
 *
 * <p>Status: 200 with the document; 400 when no service is named; 404 when the service stores no round of the
 * conversation; 500 on a storage failure. An error is <code>application/problem+json</code> (RFC 9457):
 * <code>{"type": "about:blank", "title": "...", "status": 404, "detail": "..."}</code>.
 */
@Slf4j
public class ConversationViewHandler {
    public static final String PATH = "/ai-agent/conversations/{conversation}/v1/view";
    /** The document format and version as a media type; the format is the type, the version a parameter. */
    static final MediaType JSON = MediaType.parse("application/vnd.skywalking.asz.view+json");
    static final MediaType YAML = MediaType.parse("application/vnd.skywalking.asz.view+yaml");
    private static final String VERSION_PARAMETER = "version";
    private static final MediaType PROBLEM = MediaType.parse("application/problem+json").withCharset(StandardCharsets.UTF_8);
    private static final MediaType JSON_UTF_8 = JSON.withParameter(VERSION_PARAMETER, ViewYaml.VERSION).withCharset(StandardCharsets.UTF_8);
    private static final MediaType YAML_UTF_8 = YAML.withParameter(VERSION_PARAMETER, ViewYaml.VERSION).withCharset(StandardCharsets.UTF_8);
    /** Bytes rendered between two writes to the response. */
    private static final int CHUNK_CHARS = 64 * 1024;

    private final IConversationQueryService service;
    private final Duration timeout;

    public ConversationViewHandler(final IConversationQueryService service, final Duration timeout) {
        this.service = service;
        this.timeout = timeout;
    }

    @Get(PATH)
    @Decorator(CompressResponse.class)
    public HttpResponse view(final ServiceRequestContext ctx,
                             @Param("conversation") final String conversation,
                             @Param("service") @Nullable final String serviceName,
                             @Param("serviceId") @Nullable final String serviceIdParam,
                             @Param("instance") @Nullable final String instanceName,
                             @Header("Accept") @Nullable final String accept) {
        final String serviceId;
        if (StringUtil.isNotEmpty(serviceIdParam)) {
            serviceId = serviceIdParam;
        } else if (StringUtil.isNotEmpty(serviceName)) {
            serviceId = IDManager.ServiceID.buildId(serviceName, true);
        } else {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, PROBLEM, problem(HttpStatus.BAD_REQUEST, "service or serviceId is required"));
        }
        final String instanceId = StringUtil.isEmpty(instanceName)
            ? null : IDManager.ServiceInstanceID.buildId(serviceId, instanceName);
        final boolean yaml = accept != null && accept.contains("yaml");

        ctx.setRequestTimeout(TimeoutMode.SET_FROM_NOW, timeout);
        final HttpResponseWriter res = HttpResponse.streaming();
        ctx.blockingTaskExecutor().execute(() -> stream(res, serviceId, instanceId, conversation, yaml));
        return res;
    }

    private void stream(final HttpResponseWriter res, final String serviceId, @Nullable final String instanceId,
                        final String conversation, final boolean yaml) {
        final Map<String, Object> doc;
        try {
            doc = service.buildConversationView(serviceId, instanceId, conversation);
        } catch (final IOException | RuntimeException e) {
            log.error("AI agent conversation {} of service {} could not be read", conversation, serviceId, e);
            problem(res, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            return;
        }
        if (doc == null) {
            problem(res, HttpStatus.NOT_FOUND, "no round of conversation " + conversation + " is stored for this service");
            return;
        }
        res.write(ResponseHeaders.builder(HttpStatus.OK).contentType(yaml ? YAML_UTF_8 : JSON_UTF_8).build());
        try (Writer out = new ChunkWriter(res, timeout)) {
            if (yaml) {
                ViewYaml.write(doc, out);
            } else {
                ViewJson.write(doc, out);
            }
        } catch (final IOException e) {
            log.debug("AI agent conversation {} response ended early: {}", conversation, e.getMessage());
            res.close(e);
            return;
        }
        res.close();
    }

    private static void problem(final HttpResponseWriter res, final HttpStatus status, @Nullable final String detail) {
        res.write(ResponseHeaders.builder(status).contentType(PROBLEM).build());
        res.write(HttpData.ofUtf8(problem(status, detail)));
        res.close();
    }

    /**
     * @return an RFC 9457 problem: the status and its reason phrase, and what went wrong in words
     */
    private static String problem(final HttpStatus status, @Nullable final String detail) {
        final JsonObject body = new JsonObject();
        body.addProperty("type", "about:blank");
        body.addProperty("title", status.reasonPhrase());
        body.addProperty("status", status.code());
        body.addProperty("detail", detail == null ? "" : detail);
        return body.toString();
    }

    /**
     * Hands the rendered text to the response in UTF-8 chunks and waits for each to be consumed before
     * rendering more, so a slow client holds back the render instead of growing a buffer. A chunk never ends
     * between the two halves of a surrogate pair: a trailing high surrogate waits for the next chunk, so every
     * code point is encoded whole. A client that went away ends the render with an IOException at the next
     * chunk.
     */
    static final class ChunkWriter extends Writer {
        private final HttpResponseWriter res;
        private final Duration timeout;
        private final StringBuilder buffer = new StringBuilder(CHUNK_CHARS + 1024);

        ChunkWriter(final HttpResponseWriter res, final Duration timeout) {
            this.res = res;
            this.timeout = timeout;
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len) throws IOException {
            int from = off;
            int left = len;
            while (left > 0) {
                final int take = Math.min(left, CHUNK_CHARS - buffer.length());
                buffer.append(cbuf, from, take);
                from += take;
                left -= take;
                if (buffer.length() >= CHUNK_CHARS) {
                    flush();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            write(false);
        }

        private void write(final boolean last) throws IOException {
            int end = buffer.length();
            if (!last && end > 0 && Character.isHighSurrogate(buffer.charAt(end - 1))) {
                end--;
            }
            if (end == 0) {
                return;
            }
            if (!res.tryWrite(HttpData.ofUtf8(buffer.substring(0, end)))) {
                throw new IOException("the response is closed");
            }
            buffer.delete(0, end);
            try {
                res.whenConsumed().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while the client read the response", e);
            } catch (final ExecutionException | TimeoutException e) {
                throw new IOException("the client stopped reading the response", e);
            }
        }

        @Override
        public void close() throws IOException {
            write(true);
        }
    }
}
