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
import com.google.gson.JsonParser;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.RequestHeadersBuilder;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.ai.agent.conversation.Fixtures;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.FileNames;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.ConversationFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files route on a real server: chosen files come back framed so a reader recovers each one byte for byte,
 * compressed or not, and the error paths answer with their statuses.
 */
public class ConversationFilesHandlerTest {
    private static final String SERVICE = "agent";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final AtomicReference<String> LAST_INSTANCE_ID = new AtomicReference<>();
    private static final AtomicReference<String> LAST_SESSION = new AtomicReference<>();
    private static final AtomicReference<Boolean> LAST_COLD_STAGE = new AtomicReference<>();
    /** The last stored files the stub serves: the fixture's, and two whose framing is at its edges. */
    private static final byte[] UNENDED = "{\"h\":1}\n{\"t\":\"end\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LARGE = large();

    private static byte[] large() {
        final StringBuilder b = new StringBuilder("{\"h\":1}\n{\"data\":\"");
        while (b.length() < 200 * 1024) {
            b.append("caf\u00e9 \uD83D\uDE00 ");
        }
        return b.append("\"}\n").toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Serves the fixture's Session Data files by seq 1 to 4, and seq 5 without a final newline, seq 6 large and
     *  seq 7 empty, as the query service would, in seq order. Conversation "fails-after-one" fails after its first
     *  file. */
    private static final IConversationQueryService STUB = new IConversationQueryService() {
        @Override
        public ConversationList listConversations(final String serviceId, @Nullable final String serviceInstanceId,
                                                  @Nullable final String conversation, @Nullable final String title,
                                                  final Duration duration, @Nullable final Integer limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> buildConversationView(final String serviceId, final String serviceInstanceId,
                                                         final String conversation, final boolean coldStage,
                                                         final BooleanSupplier alive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean readConversationFiles(final String serviceId, final String serviceInstanceId,
                                             final String conversation, final String session,
                                             final Collection<Long> seqs, final boolean coldStage,
                                             final BooleanSupplier alive, final FileSink sink) throws IOException {
            LAST_INSTANCE_ID.set(serviceInstanceId);
            LAST_SESSION.set(session);
            LAST_COLD_STAGE.set(coldStage);
            if ("broken".equals(conversation)) {
                throw new IOException("storage is down");
            }
            final boolean failAfterOne = "fails-after-one".equals(conversation);
            if (!SERVICE_ID.equals(serviceId) || !Fixtures.SESSION.equals(conversation) && !failAfterOne) {
                return false;
            }
            for (final long seq : new TreeSet<>(seqs)) {
                final byte[] body;
                if (seq <= Fixtures.DATA_FILES.length) {
                    body = Fixtures.bytes(Fixtures.DATA_FILES[(int) seq - 1]);
                } else if (seq == 5) {
                    body = UNENDED;
                } else if (seq == 6) {
                    body = LARGE;
                } else if (seq == 7) {
                    body = new byte[0];
                } else {
                    continue;
                }
                final String id = seq <= Fixtures.DATA_FILES.length
                    ? FileNames.dataFile(SessionDataFile.header(body)) : session + "/unknown-00000" + seq + ".sd";
                sink.accept(new ConversationFile(id, seq, Digests.sha256Hex(body), body, 1));
                if (failAfterOne) {
                    throw new IOException("storage went away");
                }
            }
            return true;
        }
    };

    @RegisterExtension
    static final ServerExtension SERVER = new ServerExtension() {
        @Override
        protected void configure(final ServerBuilder sb) {
            sb.annotatedService(new ConversationFilesHandler(STUB, java.time.Duration.ofSeconds(30)));
        }
    };

    private static String path(final String conversation, final long... seqs) {
        final StringBuilder path = new StringBuilder(
            "/ai-agent/conversations/" + conversation + "/v1/files?service=" + SERVICE + "&instance=sender-1&session=" + Fixtures.SESSION);
        for (final long seq : seqs) {
            path.append("&seq=").append(seq);
        }
        return path.toString();
    }

    private static AggregatedHttpResponse get(final String path, final String... headers) {
        final RequestHeadersBuilder req = RequestHeaders.builder(HttpMethod.GET, path);
        for (int i = 0; i < headers.length; i += 2) {
            req.add(headers[i], headers[i + 1]);
        }
        return WebClient.of(SERVER.httpUri()).execute(req.build()).aggregate().join();
    }

    /** One file as a reader recovers it: its naming line, and the bytes of the lines that follow. */
    private static final class Framed {
        final JsonObject naming;
        final byte[] body;

        Framed(final JsonObject naming, final byte[] body) {
            this.naming = naming;
            this.body = body;
        }
    }

    /**
     * Reads the stream the way a client does: a naming line, then exactly <code>bytes</code> bytes, then the one
     * newline that follows a file not ending with its own.
     */
    private static List<Framed> frames(final byte[] stream) {
        final List<Framed> out = new ArrayList<>();
        int pos = 0;
        while (pos < stream.length) {
            int end = pos;
            while (stream[end] != '\n') {
                end++;
            }
            final JsonObject naming = JsonParser.parseString(new String(stream, pos, end - pos, StandardCharsets.UTF_8)).getAsJsonObject();
            pos = end + 1;
            final int bytes = naming.get("bytes").getAsInt();
            final byte[] body = Arrays.copyOfRange(stream, pos, pos + bytes);
            pos += bytes;
            if (bytes > 0 && body[bytes - 1] != '\n') {
                assertEquals('\n', stream[pos], "the newline after a file that does not end with one");
                pos++;
            }
            out.add(new Framed(naming, body));
        }
        return out;
    }

    @Test
    public void eachChosenFileComesBackByteForByte() throws Exception {
        final AggregatedHttpResponse res = get(path(Fixtures.SESSION, 3, 1));
        assertEquals(200, res.status().code());
        assertEquals("application/vnd.skywalking.asz.files+ndjson; charset=utf-8", String.valueOf(res.contentType()));
        final List<Framed> files = frames(res.content().array());
        assertEquals(2, files.size());
        // in seq order, whatever order they were named in
        assertEquals(1, files.get(0).naming.get("seq").getAsLong());
        assertEquals(3, files.get(1).naming.get("seq").getAsLong());
        for (final Framed f : files) {
            final byte[] expected = Fixtures.bytes(Fixtures.DATA_FILES[(int) f.naming.get("seq").getAsLong() - 1]);
            assertArrayEquals(expected, f.body);
            assertEquals(Digests.sha256Hex(expected), f.naming.get("digest").getAsString());
            assertEquals(expected.length, f.naming.get("bytes").getAsInt());
            assertEquals(5, f.naming.size(), "file, seq, lines, bytes, digest");
        }
        // a file is named by where it lives, from its own header
        assertEquals(Fixtures.SESSION + "/streams/" + Fixtures.CHILD_STREAM + "/" + Fixtures.DATA_FILES[2],
                     files.get(1).naming.get("file").getAsString());
        assertEquals(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "sender-1"), LAST_INSTANCE_ID.get());
        assertEquals(Fixtures.SESSION, LAST_SESSION.get());
    }

    /**
     * A file without a final newline, one past several chunks with characters of every width across the chunk
     * boundaries, and an empty one each come back whole, and a reader that takes lines counts the unended file's own.
     */
    @Test
    public void theFramingHoldsAtItsEdges() throws Exception {
        for (final boolean gzip : new boolean[] {false, true}) {
            // the empty file sits between two others, so the naming line after it must start right after its own
            final AggregatedHttpResponse res = gzip
                ? get(path(Fixtures.SESSION, 5, 6, 7, 1), "accept-encoding", "gzip")
                : get(path(Fixtures.SESSION, 5, 6, 7, 1));
            assertEquals(200, res.status().code());
            final byte[] stream = gzip ? inflate(res.content().array()) : res.content().array();
            final List<Framed> files = frames(stream);
            assertEquals(4, files.size());
            assertArrayEquals(Fixtures.bytes(Fixtures.DATA_FILES[0]), files.get(0).body);
            assertArrayEquals(UNENDED, files.get(1).body);
            assertEquals(1, files.get(1).naming.get("lines").getAsInt());
            assertEquals(Digests.sha256Hex(UNENDED), files.get(1).naming.get("digest").getAsString());
            assertArrayEquals(LARGE, files.get(2).body);
            assertEquals(0, files.get(3).body.length);
            assertEquals(Digests.sha256Hex(new byte[0]), files.get(3).naming.get("digest").getAsString());
        }
    }

    private static byte[] inflate(final byte[] gzipped) throws IOException {
        final ByteArrayOutputStream inflated = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            in.transferTo(inflated);
        }
        return inflated.toByteArray();
    }

    /**
     * gzip is used only when the client takes it: named or <code>*</code>, and not weighted zero.
     */
    @Test
    public void gzipOnlyWhenTheClientTakesIt() {
        assertTrue(ConversationFilesHandler.acceptsGzip("gzip"));
        assertTrue(ConversationFilesHandler.acceptsGzip("br, gzip;q=0.5"));
        assertTrue(ConversationFilesHandler.acceptsGzip("*"));
        assertFalse(ConversationFilesHandler.acceptsGzip(null));
        assertFalse(ConversationFilesHandler.acceptsGzip("identity"));
        assertFalse(ConversationFilesHandler.acceptsGzip("gzip;q=0"));
        assertFalse(ConversationFilesHandler.acceptsGzip("gzip; q=0.0, br"));
        // gzip's own weight decides over the wildcard, whichever comes first
        assertFalse(ConversationFilesHandler.acceptsGzip("gzip;q=0, *;q=1"));
        assertFalse(ConversationFilesHandler.acceptsGzip("*, gzip;q=0"));
        assertTrue(ConversationFilesHandler.acceptsGzip("identity;q=0, *"));
        assertFalse(ConversationFilesHandler.acceptsGzip("*;q=0"));
        // stray separators name nothing and break nothing
        assertFalse(ConversationFilesHandler.acceptsGzip(";"));
        assertFalse(ConversationFilesHandler.acceptsGzip(","));
        assertTrue(ConversationFilesHandler.acceptsGzip(";, gzip"));
        assertEquals(200, get(path(Fixtures.SESSION, 1), "accept-encoding", ";").status().code());
        // two header fields count as one list
        assertEquals("gzip", get(path(Fixtures.SESSION, 1), "accept-encoding", "br", "accept-encoding", "gzip")
            .headers().get(HttpHeaderNames.CONTENT_ENCODING));
        assertEquals(null, get(path(Fixtures.SESSION, 1), "accept-encoding", "gzip;q=0").headers().get(HttpHeaderNames.CONTENT_ENCODING));
    }

    /**
     * A failure after the first file ends the response early: the client does not get a complete response.
     */
    @Test
    public void aFailureAfterTheFirstFileEndsTheResponse() {
        assertThrows(Exception.class, () -> get(path("fails-after-one", 1, 2)));
    }

    @Test
    public void gzipOnAcceptEncoding() throws Exception {
        final AggregatedHttpResponse res = get(path(Fixtures.SESSION, 1, 2, 3, 4), "accept-encoding", "gzip");
        assertEquals(200, res.status().code());
        assertEquals("gzip", res.headers().get(HttpHeaderNames.CONTENT_ENCODING));
        final byte[] stream = inflate(res.content().array());
        assertTrue(res.content().length() < stream.length / 2, "compressed " + res.content().length() + " of " + stream.length);
        final List<Framed> files = frames(stream);
        assertEquals(4, files.size());
        for (int i = 0; i < files.size(); i++) {
            assertArrayEquals(Fixtures.bytes(Fixtures.DATA_FILES[i]), files.get(i).body);
        }
    }

    @Test
    public void aChoiceNoFileAnswersIsLeftOut() {
        final AggregatedHttpResponse res = get(path(Fixtures.SESSION, 9));
        assertEquals(200, res.status().code());
        assertEquals(0, res.content().length());
    }

    @Test
    public void statusesOfTheErrorPaths() {
        final String base = "/ai-agent/conversations/" + Fixtures.SESSION + "/v1/files?session=" + Fixtures.SESSION + "&seq=1";
        assertEquals(400, get(base).status().code());
        assertEquals(400, get(base + "&service=" + SERVICE).status().code());
        assertEquals(400, get(base + "&instance=sender-1").status().code());
        // no seq, a seq without its session, rounds, which the route does not read, and bad seqs and stages
        assertEquals(400, get(path(Fixtures.SESSION)).status().code());
        assertEquals(400, get("/ai-agent/conversations/" + Fixtures.SESSION + "/v1/files?service=" + SERVICE + "&instance=sender-1&seq=1").status().code());
        assertEquals(400, get("/ai-agent/conversations/" + Fixtures.SESSION + "/v1/files?service=" + SERVICE + "&instance=sender-1&round=1").status().code());
        for (final String bad : new String[] {"&seq=x", "&seq=0", "&seq=-1", "&seq=99999999999999999999", "&seq=1.5",
                                              "&seq=1&coldStage=garbage", "&seq=1&coldStage="}) {
            final AggregatedHttpResponse res = get(path(Fixtures.SESSION) + bad);
            assertEquals(400, res.status().code(), bad);
            assertTrue(res.contentType().is(MediaType.parse("application/problem+json")), bad);
        }
        // the most a request chooses, and one more; the most fits the 4 KB request line even as the largest numbers
        final long[] most = new long[ConversationFilesHandler.MAX_SEQS];
        Arrays.fill(most, Long.MAX_VALUE);
        final String mostPath = path(Fixtures.SESSION, most);
        assertTrue(("GET " + mostPath + " HTTP/1.1").length() < 4096, "request line " + mostPath.length());
        assertEquals(200, get(mostPath).status().code());
        assertEquals(400, get(mostPath + "&seq=1").status().code());
        LAST_COLD_STAGE.set(null);
        assertEquals(200, get(path(Fixtures.SESSION, 1) + "&coldStage=true").status().code());
        assertEquals(Boolean.TRUE, LAST_COLD_STAGE.get());
        assertEquals(404, get(path("no-such-conversation", 1)).status().code());
        final AggregatedHttpResponse broken = get(path("broken", 1));
        assertEquals(500, broken.status().code());
        assertEquals(
            "{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"status\":500,\"detail\":\"storage is down\"}",
            broken.contentUtf8());
    }
}
