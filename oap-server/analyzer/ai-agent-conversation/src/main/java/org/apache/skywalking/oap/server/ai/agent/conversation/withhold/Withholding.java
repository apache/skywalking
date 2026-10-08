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

package org.apache.skywalking.oap.server.ai.agent.conversation.withhold;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.providerbody.BodyStore;
import org.apache.skywalking.oap.server.ai.agent.conversation.providerbody.Manifest;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Withholds the system prompt and the tool schemas from a Session Data file as it is served, by the names in
 * {@link Hide}. The stored file is never changed: the served bytes are built from it on each request.
 *
 * <p>A record of any file but a <code>provider_body</code> file is withheld whole when it carries a hidden name,
 * as {@link SessionDataFile.Record#withheld()} does for the document. A request body is rebuilt from the session's
 * bodies, what it holds of the names is replaced by markers, and the request is written back as a body of its own:
 * one literal segment, no copy and no piece, a digest and a size of its own, so a reader that checks a body against
 * its manifest still can. A request that cannot be rebuilt, or holds a reference the served files no longer answer,
 * is withheld whole rather than served with a reference that leads nowhere. A response holds neither name and is
 * served as stored, unless it refers to a request's bytes, when it is written whole the same way. A record of a
 * <code>provider_body</code> file with no manifest this reader knows is withheld whole, since nothing says what it
 * holds, and so is any row that is not the one its session holds, as {@link BodyStore#holds} says. A file with
 * nothing to withhold is served byte for byte.
 *
 * <p>A rewritten file gets a closing line of its own, so it still reads and checks on its own. Its records keep
 * their rows, so every reference into it still resolves. A file whose stored closing line does not check keeps
 * that line, so a reader refuses the served file as it refuses the stored one. A file that a reader could not read
 * to its end ends where the reader stops, since what follows could hold a record this reader may not see.
 */
public final class Withholding {
    static final String MARKER_SYSTEM = "[withheld: " + Hide.SYSTEM_PROMPT + "]";
    static final String MARKER_TOOLS = Hide.TOOL_SCHEMAS;
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final byte[] ESCAPE = "\\u".getBytes(StandardCharsets.UTF_8);

    /**
     * The keys a request carries its system prompt under, at any depth, compared without case, underscores or
     * dashes, as the LangChain page lists them: <code>system</code> as Anthropic's API takes it,
     * <code>instructions</code> as OpenAI's Responses, <code>system_instruction</code> as Gemini's,
     * <code>instruction</code>, <code>preamble</code>, <code>system_message</code>, <code>system_prompt</code> and
     * <code>system_instructions</code> as applications and other clients name it; and <code>prompts</code>, the
     * prompts a completion model is sent, which carry the instructions and the question in one string. The
     * Sessionizer names a record by the first eight; the last is the OAP's own, since its viewer serves no body.
     */
    private static final Set<String> PROMPT_KEYS = new HashSet<>(Arrays.asList(
        "system", "systemprompt", "systeminstruction", "systeminstructions", "systemmessage", "instruction",
        "instructions", "preamble", "prompts"));
    /**
     * The keys a request offers its tools under, compared the same way: <code>tools</code> and <code>functions</code>,
     * Gemini's <code>function_declarations</code>, and <code>tool_definitions</code> and <code>available_tools</code>.
     */
    private static final Set<String> TOOL_KEYS = new HashSet<>(Arrays.asList(
        "tools", "functions", "functiondeclarations", "tooldefinitions", "availabletools"));

    private Withholding() {
    }

    /**
     * The served form of one stored file.
     */
    @Getter
    public static final class Served {
        private final byte[] bytes;
        /** Whether anything was withheld; when not, the bytes are the stored ones. */
        private final boolean changed;

        Served(final byte[] bytes, final boolean changed) {
            this.bytes = bytes;
            this.changed = changed;
        }
    }

    /**
     * @param stored the file as stored
     * @param hidden the names withheld, sorted
     * @param store  the session's bodies up to this file, in landing order, for a <code>provider_body</code> file;
     *               null for any other
     * @return the file as served
     */
    public static Served file(final byte[] stored, final List<String> hidden, @Nullable final BodyStore store) {
        if (hidden.isEmpty()) {
            return new Served(stored, false);
        }
        final SessionDataFile.Header header;
        try {
            header = SessionDataFile.header(stored);
        } catch (final IllegalArgumentException e) {
            // The first line is not a header, so a reader opens nothing of this file. The bytes travel all the
            // same, and could hold a record this reader may not see, so none of them do.
            return new Served(new byte[0], true);
        }
        final boolean bodies = Manifest.KIND.equals(header.getKind());
        if (!bodies && !mayHold(stored, hidden)) {
            return new Served(stored, false);
        }
        final SessionDataFile file = SessionDataFile.parse(stored);
        final String text = new String(stored, StandardCharsets.UTF_8);
        final String[] lines = text.split("\n", -1);
        // A reader stops at the first line that is not a record, so what follows one is never read as part of the
        // conversation. It travels in the stored bytes all the same, and could hold a record this reader may not
        // see, so a file that did not read to its closing line is always rewritten, and ends where a reader stops.
        final boolean readToEnd = file.getDeclaredRecords() >= 0 && file.getRecords().size() == file.getDeclaredRecords();
        final ByteArrayOutputStream out = new ByteArrayOutputStream(stored.length);
        boolean changed = !readToEnd;
        write(out, lines[0]);
        final Map<String, Boolean> rewritten = new HashMap<>();
        for (final SessionDataFile.Record rec : file.getRecords()) {
            String line = null;
            if (bodies) {
                line = body(header.getSeq(), rec, store, hidden, rewritten);
            } else if (Hide.carriesAny(rec.flags(), hidden)) {
                line = withheldLine(rec);
            }
            changed |= line != null;
            write(out, line == null ? lines[rec.getRow()] : line);
        }
        if (!changed) {
            return new Served(stored, false);
        }
        // The closing line covers every byte before it. A file cut short before its own gets none, and one whose own
        // does not check keeps it, so either reads as it did: a reader refuses both, as the Session Data page says,
        // and a closing line computed here would make a damaged file read as a whole one.
        if (readToEnd) {
            final int closing = file.getRecords().size() + 1;
            if (closes(stored, closing, file.getDeclaredDigest())) {
                final byte[] before = out.toByteArray();
                write(out, "{\"t\":\"end\",\"records\":" + file.getDeclaredRecords() + ",\"digest\":\""
                    + Digests.sha256Hex(before) + "\"}");
            } else {
                write(out, lines[closing]);
            }
        }
        return new Served(out.toByteArray(), true);
    }

    /** Whether the stored bytes before the closing line, on the given line, have the digest it declares. */
    private static boolean closes(final byte[] stored, final int closingLine, @Nullable final String declared) {
        int end = 0;
        for (int line = 0; line < closingLine; line++) {
            while (stored[end] != '\n') {
                end++;
            }
            end++;
        }
        return Digests.sha256Hex(Arrays.copyOf(stored, end)).equals(declared);
    }

    /**
     * Whether the stored bytes of a file that is not a <code>provider_body</code> file can hold a record carrying a
     * hidden name: only where the name is written in them, as a flag is, or where a backslash-u escape could
     * spell it another way. A file with neither is served as stored without being read, which is most files:
     * measured on three local sessions, 153 of 364, 87 of 221 and 14 of 48 files held a named record. This decides
     * nothing about a record, only whether the file has to be read to decide.
     */
    private static boolean mayHold(final byte[] stored, final List<String> hidden) {
        if (indexOf(stored, ESCAPE) >= 0) {
            return true;
        }
        for (final String name : hidden) {
            if (indexOf(stored, ("\"" + name + "\"").getBytes(StandardCharsets.UTF_8)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(final byte[] in, final byte[] what) {
        final int last = in.length - what.length;
        outer:
        for (int i = 0; i <= last; i++) {
            for (int j = 0; j < what.length; j++) {
                if (in[i + j] != what[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * @return the bytes as text, or null when they are not UTF-8: replacing what does not decode would change the
     * bytes a digest was taken of
     */
    @Nullable
    private static String utf8(final byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                                         .onMalformedInput(CodingErrorAction.REPORT)
                                         .onUnmappableCharacter(CodingErrorAction.REPORT)
                                         .decode(ByteBuffer.wrap(bytes))
                                         .toString();
        } catch (final CharacterCodingException e) {
            return null;
        }
    }

    private static void write(final ByteArrayOutputStream out, final String line) {
        final byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        out.write('\n');
    }

    /**
     * @return the served line of one record of a <code>provider_body</code> file, or null for the stored line: a
     * request is masked, or withheld whole when it cannot be rebuilt or has no id to be held under; a response is
     * written whole when it refers to a request's bytes, and withheld whole when it cannot be; a record with no
     * manifest this reader knows, such as one of a later schema, is withheld whole, since it may be a request, and so
     * is a row that is not the one the session holds, a role other than request or response among the reasons
     */
    @Nullable
    private static String body(final long seq, final SessionDataFile.Record rec, @Nullable final BodyStore store,
                               final List<String> hidden, final Map<String, Boolean> rewritten) {
        final Manifest m = Manifest.of(rec);
        // Only the row the session holds is served. One with no id, or a role, a size, a reference or a depth the page
        // does not allow, or one landed again under an id held before it, is withheld whole: a reader holds the first
        // and refuses or passes over the rest, and what such a row refers to may be gone from what is served.
        if (m == null || store == null || StringUtil.isEmpty(rec.getId()) || !store.holds(seq, rec.getRow(), rec.getId())) {
            return withheldLine(rec);
        }
        if (m.isRequest()) {
            return request(rec, m, store, hidden, rewritten);
        }
        if (leansOnRewritten(rec.getId(), m, store, rewritten)) {
            final String whole = standalone(rec, m, store);
            return whole == null ? withheldLine(rec) : whole;
        }
        return null;
    }

    /** The record with every part's content gone, as the document reads it. */
    private static String withheldLine(final SessionDataFile.Record rec) {
        return GSON.toJson(rec.withheld().getJson());
    }

    /**
     * Whether a body refers to bytes that are not served as stored: the front of another body, or a piece held by
     * a record that is rewritten, which is every request, since a masked request keeps only the pieces that are not
     * what was withheld and is served with a digest of its own, and every body that leans on one in turn, since such
     * a body may be withheld whole when it cannot be written whole. Such a body would no longer rebuild from what is
     * served, so it is written whole instead, as {@link #standalone} writes it.
     *
     * <p>The bodies are walked with a stack of their own, not the thread's: a response can use a piece of a response
     * that uses a piece of another, as far back as the session goes, and nothing bounds that the way the depth
     * bounds a chain of copies.
     *
     * @param rewritten what was decided for each record id so far, so a chain of bodies is walked once
     */
    private static boolean leansOnRewritten(final String id, final Manifest m, final BodyStore store,
                                            final Map<String, Boolean> rewritten) {
        final Boolean known = rewritten.get(id);
        if (known != null) {
            return known;
        }
        final Deque<Walk> path = new ArrayDeque<>();
        path.push(Walk.enter(id, m, rewritten));
        while (true) {
            final Walk w = path.peek();
            Walk next = null;
            final List<Manifest.Segment> segments = w.manifest.getSegments();
            while (!w.leans && next == null && w.at < segments.size()) {
                final Manifest.Segment seg = segments.get(w.at++);
                if (seg.getCopyFrom() != null) {
                    w.leans = true;
                } else if (seg.getPiece() != null) {
                    final String holder = store.holderIdOf(seg.getPiece());
                    final Manifest holding = holder == null ? null : store.manifest(holder);
                    if (holding != null) {
                        final Boolean decided = rewritten.get(holder);
                        if (decided != null) {
                            w.leans = decided;
                        } else {
                            next = Walk.enter(holder, holding, rewritten);
                        }
                    }
                }
            }
            if (next != null) {
                path.push(next);
                continue;
            }
            rewritten.put(w.id, w.leans);
            path.pop();
            if (path.isEmpty()) {
                return w.leans;
            }
            // the body that used this one's piece leans when this one does, and goes on to its next segment if not
            path.peek().leans = w.leans;
        }
    }

    /** One body on the walk of {@link #leansOnRewritten}: the segment it is at, and whether it leans so far. */
    private static final class Walk {
        private final String id;
        private final Manifest manifest;
        private int at;
        private boolean leans;

        private Walk(final String id, final Manifest manifest) {
            this.id = id;
            this.manifest = manifest;
            this.leans = manifest.isRequest();
        }

        /** A body entered counts as leaning until it is decided: a cycle of references cannot rebuild. */
        static Walk enter(final String id, final Manifest manifest, final Map<String, Boolean> rewritten) {
            rewritten.put(id, true);
            return new Walk(id, manifest);
        }
    }

    /**
     * @return the record's line with its body rebuilt and written as one literal segment, its own pieces kept, and
     * its digest and size as they were; or null when the body does not rebuild, or is not UTF-8 text, which a literal
     * segment cannot hold byte for byte
     */
    @Nullable
    private static String standalone(final SessionDataFile.Record rec, final Manifest m, final BodyStore store) {
        final String body;
        try {
            body = utf8(store.body(rec.getId()));
        } catch (final BodyStore.BodyException e) {
            return null;
        }
        if (body == null) {
            return null;
        }
        final JsonObject manifest = m.getJson().deepCopy();
        manifest.addProperty("depth", 0);
        manifest.remove("segments");
        manifest.add("segments", Manifest.Segment.literal(body));
        final List<String> parts = new ArrayList<>();
        final List<SessionDataFile.Part> own = rec.getParts();
        for (int i = 0; i < m.getPartIndex(); i++) {
            parts.add(partText(own.get(i).getJson(), own.get(i).data()));
        }
        parts.add(manifestPart(own.get(m.getPartIndex()).getJson(), manifest));
        return recordText(rec.getJson(), parts);
    }

    /** The manifest part with its data replaced, and its size that of the new data. */
    private static String manifestPart(final JsonObject part, final JsonObject manifest) {
        final JsonObject copy = part.deepCopy();
        final String text = GSON.toJson(manifest);
        copy.addProperty("bytes", text.getBytes(StandardCharsets.UTF_8).length);
        return partText(copy, text);
    }

    /**
     * @return the request record's line with its body masked; the line written whole when the body holds nothing
     * to withhold but refers to a request's bytes; null for the stored line when it holds nothing and refers to
     * none; the record withheld whole when its body does not rebuild or is not one JSON object in UTF-8 text
     */
    @Nullable
    private static String request(final SessionDataFile.Record rec, final Manifest m, final BodyStore store,
                                  final List<String> hidden, final Map<String, Boolean> rewritten) {
        final String text;
        try {
            text = utf8(store.body(rec.getId()));
        } catch (final BodyStore.BodyException e) {
            return withheldLine(rec);
        }
        final JsonElement parsed = text == null ? null : Schema.parse(text);
        if (parsed == null || !parsed.isJsonObject()) {
            // a body that is not one JSON object in UTF-8 text cannot be read for what it holds, so all of it is
            // withheld
            return withheldLine(rec);
        }
        final JsonObject body = parsed.getAsJsonObject();
        // every string of what is masked, so a piece that held one goes with it
        final Set<String> maskedTexts = new HashSet<>();
        final boolean changed = mask(body, hidden, maskedTexts);
        if (!changed) {
            // nothing of the names here, but a body that leans on a rewritten one would not rebuild as stored
            if (leansOnRewrittenReferences(m, store, rewritten)) {
                final String whole = standalone(rec, m, store);
                return whole == null ? withheldLine(rec) : whole;
            }
            rewritten.put(rec.getId(), false);
            return null;
        }
        final String masked = GSON.toJson(body);
        final byte[] maskedBytes = masked.getBytes(StandardCharsets.UTF_8);

        final JsonObject manifest = m.getJson().deepCopy();
        manifest.addProperty("sha256", Digests.sha256Hex(maskedBytes));
        manifest.addProperty("bytes", maskedBytes.length);
        manifest.addProperty("depth", 0);
        manifest.remove("segments");
        final JsonArray withheld = new JsonArray();
        for (final String name : hidden) {
            withheld.add(name);
        }
        manifest.add("withheld", withheld);
        manifest.add("segments", Manifest.Segment.literal(masked));

        final List<String> parts = new ArrayList<>();
        final List<SessionDataFile.Part> own = rec.getParts();
        for (int i = 0; i < m.getPartIndex(); i++) {
            final SessionDataFile.Part p = own.get(i);
            if (!dropped(p, hidden, maskedTexts)) {
                parts.add(partText(p.getJson(), p.data()));
            }
        }
        parts.add(manifestPart(own.get(m.getPartIndex()).getJson(), manifest));
        return recordText(rec.getJson(), parts);
    }

    /** Whether a request's own references lean on a rewritten body; a request is otherwise served as stored. */
    private static boolean leansOnRewrittenReferences(final Manifest m, final BodyStore store, final Map<String, Boolean> rewritten) {
        for (final Manifest.Segment seg : m.getSegments()) {
            if (seg.getCopyFrom() != null) {
                return true;
            }
            if (seg.getPiece() != null) {
                final String holder = store.holderIdOf(seg.getPiece());
                final Manifest holding = holder == null ? null : store.manifest(holder);
                if (holding != null && leansOnRewritten(holder, holding, store, rewritten)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A part of the record whose bytes are what was withheld: a tool definition, which is the one kind of object a
     * piece can be, a string that is one of the masked texts, or the body kept whole in an <code>unknown</code>
     * part. It goes, since its bytes are the secret. Any other piece stays, so a later body that refers to it by its
     * digest still rebuilds.
     */
    private static boolean dropped(final SessionDataFile.Part p, final List<String> hidden, final Set<String> maskedTexts) {
        final String data = p.data();
        if (data == null || !"data".equals(p.getKind())) {
            // an unknown part is the body kept whole, with what is withheld in it; no later body refers to one
            return true;
        }
        final String trimmed = data.trim();
        if (trimmed.startsWith("{")) {
            return hidden.contains(Hide.TOOL_SCHEMAS);
        }
        if (trimmed.startsWith("\"")) {
            final JsonElement s = Schema.parse(trimmed);
            return s != null && s.isJsonPrimitive() && s.getAsJsonPrimitive().isString()
                && maskedTexts.contains(s.getAsString());
        }
        return false;
    }

    /**
     * Masks what a value holds of the hidden names, at any depth, the way the Sessionizer's LangChain adapter finds
     * them by shape (<code>shapeOf</code> on its LangChain page): a client may nest its tools in a configuration of
     * its own, such as Bedrock's <code>toolConfig</code> or Gemini's <code>config</code>, and its instructions among
     * OpenAI's Responses <code>input</code> rather than under <code>messages</code>. A value masked is not walked
     * again. The depth is bounded by {@link Schema#MAX_DEPTH}, since the body was read with it.
     *
     * @return whether anything was masked
     */
    private static boolean mask(final JsonElement v, final List<String> hidden, final Set<String> maskedTexts) {
        boolean changed = maskShape(v, hidden, maskedTexts);
        if (v.isJsonObject()) {
            final JsonObject o = v.getAsJsonObject();
            for (final String key : new ArrayList<>(o.keySet())) {
                changed |= maskMember(o, key, hidden, maskedTexts);
            }
        } else if (v.isJsonArray()) {
            for (final JsonElement item : v.getAsJsonArray()) {
                changed |= mask(item, hidden, maskedTexts);
            }
        }
        return changed;
    }

    /**
     * Masks what a value is as a whole, before its members are: a system message, when it is an object, and the system
     * messages of a list of messages written as LangChain's pairs, when it is a list. Every value the walk reaches is
     * looked at this way, a set of tools too.
     */
    private static boolean maskShape(final JsonElement v, final List<String> hidden, final Set<String> maskedTexts) {
        if (!hidden.contains(Hide.SYSTEM_PROMPT)) {
            return false;
        }
        if (v.isJsonObject()) {
            return maskSystemMessage(v.getAsJsonObject(), maskedTexts);
        }
        if (!v.isJsonArray() || !systemPairIn(v.getAsJsonArray())) {
            return false;
        }
        boolean changed = false;
        for (final JsonElement item : v.getAsJsonArray()) {
            final JsonArray pair = item.isJsonArray() ? item.getAsJsonArray() : null;
            if (pair != null && systemRole(pair.get(0)) && holdsSomething(pair.get(1))) {
                collect(pair.get(1), maskedTexts);
                pair.set(1, new JsonPrimitive(MARKER_SYSTEM));
                changed = true;
            }
        }
        return changed;
    }

    /** Masks one member of an object: a prompt under a prompt key, tools under a tool key, and otherwise what it holds. */
    private static boolean maskMember(final JsonObject o, final String key, final List<String> hidden, final Set<String> maskedTexts) {
        final JsonElement inner = o.get(key);
        final String k = keyOf(key);
        if (hidden.contains(Hide.SYSTEM_PROMPT) && PROMPT_KEYS.contains(k) && holdsSomething(inner)) {
            o.add(key, maskedPrompt(inner, maskedTexts));
            return true;
        }
        if (hidden.contains(Hide.TOOL_SCHEMAS) && TOOL_KEYS.contains(k) && offersTools(inner)) {
            return maskTools(o, key, hidden, maskedTexts);
        }
        return mask(inner, hidden, maskedTexts);
    }

    /** A key as the prompt and tool keys are compared: without case, underscores or dashes. */
    private static String keyOf(final String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /** Text, or a list or an object that is not empty, as the Sessionizer's adapter reads a value. */
    private static boolean holdsSomething(@Nullable final JsonElement v) {
        if (v == null) {
            return false;
        }
        if (isString(v)) {
            return !v.getAsString().isEmpty();
        }
        return v.isJsonArray() && v.getAsJsonArray().size() > 0 || v.isJsonObject() && v.getAsJsonObject().size() > 0;
    }

    /**
     * Whether a value offers tools: a list or an object with at least one object in it. A list of names or a flag
     * is not; an object of another kind counts, since nothing tells it from a tool.
     */
    private static boolean offersTools(final JsonElement v) {
        final List<JsonElement> items = new ArrayList<>();
        if (v.isJsonArray()) {
            v.getAsJsonArray().forEach(items::add);
        } else if (v.isJsonObject()) {
            v.getAsJsonObject().entrySet().forEach(e -> items.add(e.getValue()));
        }
        for (final JsonElement item : items) {
            if (item.isJsonObject()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a list is a list of messages with a system message among them written as LangChain's pair of a role
     * and its content. Every item must be such a pair or an object, so a list of two words of the application's
     * own, such as tags or roles, is not one.
     */
    private static boolean systemPairIn(final JsonArray list) {
        boolean found = false;
        for (final JsonElement item : list) {
            if (item.isJsonObject()) {
                continue;
            }
            if (!item.isJsonArray() || item.getAsJsonArray().size() != 2 || !isString(item.getAsJsonArray().get(0))) {
                return false;
            }
            found |= systemRole(item.getAsJsonArray().get(0)) && holdsSomething(item.getAsJsonArray().get(1));
        }
        return found;
    }

    /** Every string anywhere in a value, so the pieces that held them can go. */
    private static void collect(@Nullable final JsonElement v, final Set<String> texts) {
        if (v == null) {
            return;
        }
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
            texts.add(v.getAsString());
        } else if (v.isJsonArray()) {
            for (final JsonElement e : v.getAsJsonArray()) {
                collect(e, texts);
            }
        } else if (v.isJsonObject()) {
            for (final Map.Entry<String, JsonElement> e : v.getAsJsonObject().entrySet()) {
                collect(e.getValue(), texts);
            }
        }
    }

    /**
     * A prompt value with its text replaced by the marker, whatever shape the client gave it: a string; a list of
     * strings or of blocks; an object holding its text under <code>text</code>, <code>content</code> or blocks under
     * <code>parts</code>, as Gemini's <code>system_instruction</code> does. Every string is text but a
     * <code>type</code> or a <code>role</code>, which say what a block is, not what it says, so its shape stays
     * readable. Each string replaced is remembered, so a piece that held it goes with it.
     */
    private static JsonElement maskedPrompt(final JsonElement v, final Set<String> maskedTexts) {
        if (isString(v)) {
            maskedTexts.add(v.getAsString());
            return new JsonPrimitive(MARKER_SYSTEM);
        }
        if (v.isJsonArray()) {
            final JsonArray out = new JsonArray();
            for (final JsonElement e : v.getAsJsonArray()) {
                out.add(maskedPrompt(e, maskedTexts));
            }
            return out;
        }
        if (!v.isJsonObject()) {
            return v;
        }
        final JsonObject out = new JsonObject();
        for (final Map.Entry<String, JsonElement> e : v.getAsJsonObject().entrySet()) {
            final boolean says = ("type".equals(e.getKey()) || "role".equals(e.getKey())) && isString(e.getValue());
            out.add(e.getKey(), says ? e.getValue() : maskedPrompt(e.getValue(), maskedTexts));
        }
        return out;
    }

    /**
     * Masks the content of a message of the system or developer role, as the LangChain page names one: its role or
     * its type says so, in any case, beside content with something in it. LangChain's serialized message says so in
     * its <code>kwargs</code>, which the walk reaches as an object of its own; LangChain JS writes no role there, only
     * the class that ends the message's <code>id</code>, so that is read too.
     *
     * @return whether the content was masked
     */
    private static boolean maskSystemMessage(final JsonObject o, final Set<String> maskedTexts) {
        if ((systemRole(o.get("role")) || systemRole(o.get("type"))) && holdsSomething(o.get("content"))) {
            maskContent(o, maskedTexts);
            return true;
        }
        final JsonElement kwargs = o.get("kwargs");
        if (serializedSystem(o.get("id")) && kwargs != null && kwargs.isJsonObject()
            && holdsSomething(kwargs.getAsJsonObject().get("content"))) {
            maskContent(kwargs.getAsJsonObject(), maskedTexts);
            return true;
        }
        return false;
    }

    /** Whether a serialized LangChain object's id ends with the class of a system message. */
    private static boolean serializedSystem(@Nullable final JsonElement id) {
        if (id == null || !id.isJsonArray() || id.getAsJsonArray().size() == 0) {
            return false;
        }
        final JsonElement last = id.getAsJsonArray().get(id.getAsJsonArray().size() - 1);
        return isString(last) && ("SystemMessage".equals(last.getAsString()) || "SystemMessageChunk".equals(last.getAsString()));
    }

    /** Whether a role or a type names the instructions before a conversation: system or developer, in any case. */
    private static boolean systemRole(@Nullable final JsonElement role) {
        return isString(role) && ("system".equalsIgnoreCase(role.getAsString()) || "developer".equalsIgnoreCase(role.getAsString()));
    }

    private static void maskContent(final JsonObject message, final Set<String> maskedTexts) {
        collect(message.get("content"), maskedTexts);
        message.addProperty("content", MARKER_SYSTEM);
    }

    /** The set of tools under a tool key, masked as {@link #maskToolSet} says. */
    private static boolean maskTools(final JsonObject holder, final String key, final List<String> hidden,
                                     final Set<String> maskedTexts) {
        final JsonElement tools = holder.get(key);
        return tools != null && maskToolSet(tools, hidden, maskedTexts);
    }

    /**
     * One set of tools, a list or an object keyed by name. What the set is as a whole is masked first, as every value
     * is. Then each tool keeps its name, since the transcript's tool calls name it anyway, and loses the rest, and
     * every string of it is remembered, so a piece that held one, such as a long description, goes with it. A list
     * inside the set is more of its tools. Anything else in it is masked like any other member, as the Sessionizer's
     * walk finds it. A masked tool is not walked again, so a tool named like a prompt key keeps the tools' marker.
     */
    private static boolean maskToolSet(final JsonElement tools, final List<String> hidden, final Set<String> maskedTexts) {
        boolean changed = maskShape(tools, hidden, maskedTexts);
        if (tools.isJsonArray()) {
            final JsonArray list = tools.getAsJsonArray();
            for (int i = 0; i < list.size(); i++) {
                final JsonElement tool = list.get(i);
                if (tool.isJsonObject()) {
                    collect(tool, maskedTexts);
                    list.set(i, maskedTool(nameOf(tool.getAsJsonObject())));
                    changed = true;
                } else if (tool.isJsonArray()) {
                    changed |= maskToolSet(tool, hidden, maskedTexts);
                }
            }
        } else if (tools.isJsonObject()) {
            final JsonObject byName = tools.getAsJsonObject();
            for (final String name : new ArrayList<>(byName.keySet())) {
                final JsonElement tool = byName.get(name);
                if (tool.isJsonObject()) {
                    collect(tool, maskedTexts);
                    byName.add(name, maskedTool(null));
                    changed = true;
                } else if (tool.isJsonArray() && !PROMPT_KEYS.contains(keyOf(name))) {
                    changed |= maskToolSet(tool, hidden, maskedTexts);
                } else {
                    changed |= maskMember(byName, name, hidden, maskedTexts);
                }
            }
        }
        return changed;
    }

    /**
     * A tool's name: its own, or that of the one object it wraps, as OpenAI's <code>function</code> and Bedrock's
     * <code>toolSpec</code> hold it; null when it has none.
     */
    @Nullable
    private static JsonElement nameOf(final JsonObject tool) {
        if (isString(tool.get("name"))) {
            return tool.get("name");
        }
        for (final Map.Entry<String, JsonElement> e : tool.entrySet()) {
            if (e.getValue().isJsonObject() && isString(e.getValue().getAsJsonObject().get("name"))) {
                return e.getValue().getAsJsonObject().get("name");
            }
        }
        return null;
    }

    private static JsonObject maskedTool(@Nullable final JsonElement name) {
        final JsonObject masked = new JsonObject();
        if (isString(name)) {
            masked.addProperty("name", name.getAsString());
        }
        masked.addProperty("withheld", MARKER_TOOLS);
        return masked;
    }

    private static boolean isString(@Nullable final JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    /**
     * One part as a line holds it: its fields in their order, the data as the text given, so a kept piece keeps the
     * bytes its digest covers.
     */
    private static String partText(final JsonObject part, @Nullable final String data) {
        final StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (final Map.Entry<String, JsonElement> e : part.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(GSON.toJson(e.getKey())).append(':');
            if ("data".equals(e.getKey())) {
                out.append(data == null ? "null" : data);
            } else {
                out.append(GSON.toJson(e.getValue()));
            }
        }
        return out.append('}').toString();
    }

    /**
     * One record line: the envelope's fields in their order, with the parts given in place of its own.
     */
    private static String recordText(final JsonObject envelope, final List<String> parts) {
        final StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (final Map.Entry<String, JsonElement> e : envelope.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(GSON.toJson(e.getKey())).append(':');
            if ("parts".equals(e.getKey())) {
                out.append('[').append(String.join(",", parts)).append(']');
            } else {
                out.append(GSON.toJson(e.getValue()));
            }
        }
        return out.append('}').toString();
    }
}
