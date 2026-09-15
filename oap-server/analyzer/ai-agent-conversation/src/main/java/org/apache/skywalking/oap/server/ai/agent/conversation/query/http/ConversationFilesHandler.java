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
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.ResponseHeadersBuilder;
import com.linecorp.armeria.common.util.TimeoutMode;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Param;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.ConversationFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * <code>GET /ai-agent/conversations/{conversation}/v1/files</code>: chosen Session Data files of a conversation's
 * session, streamed. A page loads what a step points at only when a reader opens it, such as the provider bodies an
 * <code>llm.call</code> names. There is no mode that reads every file: a reader chooses each file it wants. It lives
 * beside the view route and follows it: the same server, parameters, timeout and problem documents.
 *
 * <p>Query parameters: <code>service</code> and <code>instance</code>, both required, and <code>coldStage</code>,
 * optional and false by default, as for the view. <code>session</code> is required, and so is <code>seq</code>, one to
 * {@value #MAX_SEQS} times: a file's landed seq, which the Sessionizer assigns once per file in a session, and by which
 * with the session the storage reads it. The view document's <code>files[]</code> gives every file's seq and its
 * name, whose first part is its session. The session is the caller's choice within the sender and need not be one the
 * conversation names. Files are read over the time range of the conversation's newest intact round, even one the view
 * cannot fold, and a file stamped outside it is left out, as is any seq no stored file answers.
 *
 * <p>The body is chosen by <code>Accept</code>, and there is one format, which any <code>Accept</code> gets:
 * <code>application/vnd.skywalking.asz.files+ndjson</code>. Each stored file is a naming line,
 * <code>{"file","seq","lines","bytes","digest"}</code>, followed by exactly <code>bytes</code> bytes, the file, whose
 * sha256 is <code>digest</code>. A non-empty file that does not end with a newline is followed by one, which is not part
 * of it, so the next naming line starts a line; an empty file is followed by nothing. <code>lines</code> is the file's
 * own newline count: a file the Sessionizer wrote ends with a newline, so a reader may equally take that many lines.
 * The files come in seq order, which is the order a reader of provider bodies must add them in.
 *
 * <p>The body is compressed with gzip when <code>Accept-Encoding</code> allows it. The route compresses it itself, a
 * chunk at a time, because Armeria's encoder keeps every compressed chunk of a response in one growing buffer until the
 * response ends, which for large files is the whole compressed response held in memory.
 *
 * <p>Status: 200 with the files, none when no seq is stored; 400 when the service, the instance or the session is not
 * named, when no seq is, when more than {@value #MAX_SEQS} are, when one is not a positive whole number, or when
 * <code>coldStage</code> is neither true nor false; 404 when the sender stores no round of the conversation; 500 on a
 * storage failure before the first file. A failure after the first file ends the response early, and a reader sees a
 * file shorter than its naming line says, or a response that does not complete.
 */
@Slf4j
public class ConversationFilesHandler {
    public static final String PATH = "/ai-agent/conversations/{conversation}/v1/files";
    /**
     * The most seqs one request chooses. The Sessionizer cuts a file at 2 MiB, so a response holds about 64 MiB at
     * most and takes at most two storage reads of the default window; this many seqs, even as the largest numbers, stay
     * far under the OAP's 4 KB HTTP/1 request line.
     */
    static final int MAX_SEQS = 32;
    private static final String GZIP = "gzip";
    static final MediaType FILES = MediaType.parse("application/vnd.skywalking.asz.files+ndjson");
    private static final MediaType FILES_UTF_8 = FILES.withCharset(StandardCharsets.UTF_8);
    /** Bytes of a file handed to the response at a time. */
    private static final int CHUNK_BYTES = 64 * 1024;

    private final IConversationQueryService service;
    private final Duration timeout;

    public ConversationFilesHandler(final IConversationQueryService service, final Duration timeout) {
        this.service = service;
        this.timeout = timeout;
    }

    @Get(PATH)
    public HttpResponse files(final ServiceRequestContext ctx,
                              @Param("conversation") final String conversation,
                              @Param("service") @Nullable final String serviceName,
                              @Param("instance") @Nullable final String instanceName,
                              @Param("session") @Nullable final String session,
                              @Param("seq") @Nullable final List<String> seqParams,
                              @Param("coldStage") @Nullable final String coldStageParam,
                              @Header("Accept-Encoding") @Nullable final List<String> acceptEncoding) {
        if (StringUtil.isEmpty(serviceName) || StringUtil.isEmpty(instanceName)) {
            return ConversationViewHandler.badRequest("service and instance are required");
        }
        final Boolean coldStage = ConversationViewHandler.coldStage(coldStageParam);
        if (coldStage == null) {
            return ConversationViewHandler.badRequest("coldStage " + coldStageParam + " is neither true nor false");
        }
        if (StringUtil.isEmpty(session)) {
            return ConversationViewHandler.badRequest("session is required");
        }
        if (seqParams == null || seqParams.isEmpty()) {
            return ConversationViewHandler.badRequest("at least one seq is required");
        }
        if (seqParams.size() > MAX_SEQS) {
            return ConversationViewHandler.badRequest(
                "at most " + MAX_SEQS + " seqs are read at once, " + seqParams.size() + " were named");
        }
        final List<Long> seqs = new ArrayList<>(seqParams.size());
        for (final String text : seqParams) {
            final Long n = positive(text);
            if (n == null) {
                return ConversationViewHandler.badRequest("seq " + text + " is not a positive whole number");
            }
            seqs.add(n);
        }
        final String serviceId = IDManager.ServiceID.buildId(serviceName, true);
        final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, instanceName);
        final boolean gzip = acceptsGzip(acceptEncoding == null ? null : String.join(",", acceptEncoding));

        ctx.setRequestTimeout(TimeoutMode.SET_FROM_NOW, timeout);
        final HttpResponseWriter res = HttpResponse.streaming();
        // Every refusal this route makes is a problem document. A request that runs out of time would
        // otherwise take the server's own answer, which is plain text, so it is answered here instead -
        // while nothing has been written, which is the only moment a status can still be chosen.
        final AtomicBoolean answered = new AtomicBoolean();
        ctx.whenRequestCancelling().thenAccept(cause -> {
            if (answered.compareAndSet(false, true)) {
                ConversationViewHandler.problem(
                    res, HttpStatus.SERVICE_UNAVAILABLE,
                    "the request took longer than the " + timeout.toSeconds() + " seconds allowed");
            }
        });
        ctx.blockingTaskExecutor().execute(
            () -> stream(res, serviceId, instanceId, conversation, session, seqs, coldStage, gzip, answered));
        return res;
    }

    /**
     * @param header every <code>Accept-Encoding</code> field of the request, joined by commas, or null
     * @return whether the client takes gzip: its own entry decides when it has one, a zero weight refusing it;
     * otherwise a <code>*</code> entry decides the same way
     */
    static boolean acceptsGzip(@Nullable final String header) {
        if (header == null) {
            return false;
        }
        Boolean named = null;
        Boolean any = null;
        for (final String part : header.split(",", -1)) {
            final String[] fields = part.trim().split(";", -1);
            final String coding = fields[0].trim().toLowerCase(Locale.ROOT);
            if (coding.isEmpty()) {
                // an empty entry, such as a stray separator, names no coding
                continue;
            }
            boolean taken = true;
            for (int i = 1; i < fields.length; i++) {
                final String f = fields[i].trim().replace(" ", "").toLowerCase(Locale.ROOT);
                if (f.startsWith("q=")) {
                    try {
                        taken = Double.parseDouble(f.substring(2)) > 0;
                    } catch (final NumberFormatException e) {
                        taken = false;
                    }
                }
            }
            if (GZIP.equals(coding) || "x-gzip".equals(coding)) {
                named = named == null ? taken : named || taken;
            } else if ("*".equals(coding)) {
                any = any == null ? taken : any || taken;
            }
        }
        if (named != null) {
            return named;
        }
        return any != null && any;
    }

    @Nullable
    private static Long positive(final String text) {
        try {
            final long n = Long.parseLong(text.trim());
            return n > 0 ? n : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private void stream(final HttpResponseWriter res, final String serviceId, final String instanceId,
                        final String conversation, final String session, final List<Long> seqs,
                        final boolean coldStage, final boolean gzip, final AtomicBoolean answered) {
        final boolean[] started = {false};
        final Body body = gzip ? new GzipBody(res) : new Body(res);
        try {
            final boolean found;
            try {
                found = service.readConversationFiles(serviceId, instanceId, conversation, session, seqs, coldStage, res::isOpen, file -> {
                    if (!res.isOpen()) {
                        // the caller is gone, or the request ran out of time and was answered without us
                        throw new IllegalStateException("the response is closed");
                    }
                    if (!started[0]) {
                        if (!answered.compareAndSet(false, true)) {
                            throw new IllegalStateException("the request was answered before the first file");
                        }
                        res.write(headers(gzip));
                        started[0] = true;
                    }
                    write(body, file);
                });
                if (found) {
                    if (!started[0]) {
                        if (!answered.compareAndSet(false, true)) {
                            return;
                        }
                        res.write(headers(gzip));
                        started[0] = true;
                    }
                    body.finish();
                }
            } catch (final Exception e) {
                if (started[0]) {
                    log.debug("AI agent conversation {} files response ended early: {}", conversation, e.getMessage());
                    res.close(e);
                } else if (answered.compareAndSet(false, true)) {
                    // a storage client can surface a checked failure it never declared; whatever it is, the response
                    // must say so, or the caller waits for the request timeout
                    log.error("AI agent conversation {} files of service {} could not be read", conversation, serviceId, e);
                    ConversationViewHandler.problem(res, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                } else {
                    // the request ran out of time and was answered without us; the answer is being written,
                    // and closing the response here would cut it in half
                    log.debug("AI agent conversation {} files read ended after the request was answered: {}",
                              conversation, e.getMessage());
                }
                return;
            }
            if (!found) {
                if (answered.compareAndSet(false, true)) {
                    ConversationViewHandler.problem(res, HttpStatus.NOT_FOUND, ConversationViewHandler.notFound(conversation));
                }
                return;
            }
            res.close();
        } finally {
            // the compressor holds native memory; a client gone at any point, the header write included, must not keep it
            body.release();
        }
    }

    private static ResponseHeaders headers(final boolean gzip) {
        final ResponseHeadersBuilder headers = ResponseHeaders.builder(HttpStatus.OK).contentType(FILES_UTF_8);
        if (gzip) {
            headers.add(HttpHeaderNames.CONTENT_ENCODING, GZIP);
        }
        headers.add(HttpHeaderNames.VARY, "Accept-Encoding");
        return headers.build();
    }

    /**
     * One file: its naming line, its bytes, and a newline after them when a non-empty file does not end with one.
     */
    private static void write(final Body res, final ConversationFile file) throws IOException {
        final byte[] body = file.getBody();
        final JsonObject naming = new JsonObject();
        naming.addProperty("file", file.getId());
        naming.addProperty("seq", file.getSeq());
        naming.addProperty("lines", Digests.countLines(body));
        naming.addProperty("bytes", body.length);
        naming.addProperty("digest", file.getDigest());
        if (file.getCopies() > 1) {
            // the storage holds this sequence more than once; the bytes below are the first copy
            naming.addProperty("copies", file.getCopies());
        }
        res.write((naming + "\n").getBytes(StandardCharsets.UTF_8), 0, -1);
        for (int off = 0; off < body.length; off += CHUNK_BYTES) {
            res.write(body, off, Math.min(CHUNK_BYTES, body.length - off));
        }
        if (body.length > 0 && body[body.length - 1] != '\n') {
            res.write(NEWLINE, 0, 1);
        }
        // a reader handles each file as soon as it arrives, so the compressor gives up what it holds at a file's end
        res.flushFile();
    }

    private static final byte[] NEWLINE = {'\n'};

    /**
     * The response body, written a chunk at a time. Each chunk waits for the client to take it before the next is
     * written, so a slow client holds back the read instead of growing a buffer, and a client that went away ends the
     * read with an IOException.
     */
    private class Body {
        final HttpResponseWriter res;

        Body(final HttpResponseWriter res) {
            this.res = res;
        }

        /** Writes <code>len</code> bytes from <code>off</code>, or all of <code>bytes</code> when len is negative. */
        void write(final byte[] bytes, final int off, final int len) throws IOException {
            final int n = len < 0 ? bytes.length : len;
            if (n > 0) {
                send(HttpData.copyOf(bytes, off, n));
            }
        }

        void flushFile() throws IOException {
        }

        void finish() throws IOException {
        }

        void release() {
        }

        final void send(final HttpData data) throws IOException {
            if (!res.tryWrite(data)) {
                throw new IOException("the response is closed");
            }
            try {
                res.whenConsumed().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while the client read the response", e);
            } catch (final ExecutionException | TimeoutException e) {
                throw new IOException("the client stopped reading the response", e);
            }
        }
    }

    /**
     * The body compressed with gzip, RFC 1952, by a deflater whose output is handed on as soon as a buffer of it fills,
     * so nothing compressed accumulates: one input chunk and one output buffer are all that is held.
     */
    private final class GzipBody extends Body {
        private static final int OUT_BYTES = 64 * 1024;
        private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        private final CRC32 crc = new CRC32();
        private final byte[] out = new byte[OUT_BYTES];
        private long size;
        private boolean headerSent;

        GzipBody(final HttpResponseWriter res) {
            super(res);
        }

        @Override
        void write(final byte[] bytes, final int off, final int len) throws IOException {
            final int n = len < 0 ? bytes.length : len;
            header();
            if (n == 0) {
                return;
            }
            crc.update(bytes, off, n);
            size += n;
            deflater.setInput(bytes, off, n);
            while (!deflater.needsInput()) {
                drain(Deflater.NO_FLUSH);
            }
        }

        @Override
        void flushFile() throws IOException {
            header();
            int n;
            do {
                n = drain(Deflater.SYNC_FLUSH);
            } while (n == OUT_BYTES);
        }

        @Override
        void finish() throws IOException {
            header();
            deflater.finish();
            while (!deflater.finished()) {
                drain(Deflater.NO_FLUSH);
            }
            final byte[] trailer = new byte[8];
            final long value = crc.getValue();
            for (int i = 0; i < 4; i++) {
                trailer[i] = (byte) (value >>> (8 * i));
                trailer[4 + i] = (byte) (size >>> (8 * i));
            }
            send(HttpData.wrap(trailer));
            deflater.end();
        }

        @Override
        void release() {
            deflater.end();
        }

        private void header() throws IOException {
            if (!headerSent) {
                headerSent = true;
                // magic, deflate, no flags, no time, no extra flags, unknown system
                send(HttpData.wrap(new byte[] {0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0, 0, (byte) 0xff}));
            }
        }

        private int drain(final int flush) throws IOException {
            final int n = deflater.deflate(out, 0, out.length, flush);
            if (n > 0) {
                send(HttpData.copyOf(out, 0, n));
            }
            return n;
        }
    }
}
