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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * One workspace change record, <code>changes/1</code>: which files one tool call changed, as a git-style change
 * log. It is read from a <code>data</code> part and rendered with its keys in the order the Sessionizer's
 * <code>changes.Record</code> lists them, so the document carries the record as the Sessionizer prints it and not
 * as its producer spelled it.
 *
 * <p>Decoding follows Go's <code>json.Unmarshal</code> into that struct: a key the struct does not have is dropped,
 * a null leaves the field empty, and a value of the wrong type, a string where a number goes or a fraction where an
 * integer goes, makes the part not a change record at all. Rendering follows its <code>json.Marshal</code>: a
 * string, list or nested object marked <code>omitempty</code> there is absent here when empty, a pointer to a
 * number is null when unset, and a list without the mark is null when it was never given.
 */
public final class ChangesRecord {
    public static final String SCHEMA = "changes/1";
    /** The runtime recorded the patch itself, on its own editing tool. */
    public static final String CAPTURED_BY_CLAUDE_CODE = "claude-code";
    private static final Pattern INTEGER_LITERAL = Pattern.compile("-?\\d+");

    @Getter
    private final String id;
    @Getter
    private final String capturedBy;
    /** The tool-use id the record joins to; empty on an unattributed record. */
    @Getter
    private final String tool;
    /** When the observation ended, RFC 3339 as written; the document sorts on it as text. */
    @Getter
    private final String time;
    private final Map<String, Object> fields;

    private ChangesRecord(final JsonObject json) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", nullToEmpty(str(json, "schema")));
        id = nullToEmpty(str(json, "id"));
        m.put("id", id);
        capturedBy = nullToEmpty(str(json, "captured_by"));
        m.put("captured_by", capturedBy);
        m.put("session", nullToEmpty(str(json, "session")));
        m.put("stream", nullToEmpty(str(json, "stream")));
        tool = nullToEmpty(str(json, "tool"));
        putIfNotEmpty(m, "tool", tool);
        putIfNotEmpty(m, "tool_name", str(json, "tool_name"));
        time = nullToEmpty(str(json, "time"));
        m.put("time", time);
        m.put("basis", nullToEmpty(str(json, "basis")));
        final JsonObject root = object(json, "root");
        if (root != null) {
            final Map<String, Object> r = new LinkedHashMap<>();
            r.put("path", nullToEmpty(str(root, "path")));
            putIfNotEmpty(r, "id", str(root, "id"));
            m.put("root", r);
        }
        final JsonObject policy = object(json, "policy");
        if (policy != null) {
            final Map<String, Object> p = new LinkedHashMap<>();
            putIfNotEmpty(p, "exclusions", str(policy, "exclusions"));
            putIfNotEmpty(p, "read_only", str(policy, "read_only"));
            putIfNotEmpty(p, "expanded", strings(policy, "expanded"));
            m.put("policy", p);
        }
        final JsonObject window = object(json, "window");
        if (window != null) {
            final Map<String, Object> w = new LinkedHashMap<>();
            w.put("before", interval(object(window, "before")));
            w.put("after", interval(object(window, "after")));
            m.put("window", w);
        }
        final JsonObject outcome = object(json, "outcome");
        if (outcome != null) {
            final Map<String, Object> o = new LinkedHashMap<>();
            o.put("state", nullToEmpty(str(outcome, "state")));
            o.put("exit_code", integer(outcome, "exit_code"));
            m.put("outcome", o);
        }
        putIfNotEmpty(m, "coverage", str(json, "coverage"));
        putIfNotEmpty(m, "gaps", strings(json, "gaps"));
        final JsonArray overlaps = array(json, "overlaps");
        if (overlaps != null && overlaps.size() > 0) {
            final List<Object> list = new ArrayList<>();
            for (final JsonElement e : overlaps) {
                final JsonObject x = objectOf(e);
                final Map<String, Object> o = new LinkedHashMap<>();
                o.put("capture", nullToEmpty(str(x, "capture")));
                o.put("session", nullToEmpty(str(x, "session")));
                o.put("stream", nullToEmpty(str(x, "stream")));
                putIfNotEmpty(o, "tool", str(x, "tool"));
                putIfNotEmpty(o, "tool_name", str(x, "tool_name"));
                o.put("state", nullToEmpty(str(x, "state")));
                list.add(o);
            }
            m.put("overlaps", list);
        }
        m.put("changed_files", integer(json, "changed_files"));
        final JsonArray changes = array(json, "changes");
        m.put("changes", changes == null ? null : fileChanges(changes));
        fields = Collections.unmodifiableMap(m);
    }

    /**
     * @param raw the data of a part, as the Sessionizer wrote it
     * @return the record, or null when the data is not a change record: not an object, not this schema, or not the
     * shape of one. Anything else is not an error, because the same data part may hold other things.
     */
    @Nullable
    public static ChangesRecord decode(@Nullable final String raw) {
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '{') {
            return null;
        }
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(raw);
        } catch (final RuntimeException e) {
            return null;
        }
        if (!parsed.isJsonObject()) {
            return null;
        }
        try {
            final ChangesRecord r = new ChangesRecord(parsed.getAsJsonObject());
            return SCHEMA.equals(r.fields.get("schema")) ? r : null;
        } catch (final Mismatch e) {
            return null;
        }
    }

    /**
     * @return the record's fields, keys in the order the format lists them
     */
    public Map<String, Object> fields() {
        return fields;
    }

    private static Map<String, Object> interval(@Nullable final JsonObject o) {
        final Map<String, Object> i = new LinkedHashMap<>();
        i.put("from", o == null ? "" : nullToEmpty(str(o, "from")));
        i.put("to", o == null ? "" : nullToEmpty(str(o, "to")));
        return i;
    }

    private static List<Object> fileChanges(final JsonArray changes) {
        final List<Object> out = new ArrayList<>();
        for (final JsonElement e : changes) {
            final JsonObject c = objectOf(e);
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", nullToEmpty(str(c, "path")));
            m.put("operation", nullToEmpty(str(c, "operation")));
            m.put("before", endpoint(object(c, "before")));
            m.put("after", endpoint(object(c, "after")));
            m.put("diff", nullToEmpty(str(c, "diff")));
            putIfNotEmpty(m, "attribution", str(c, "attribution"));
            putIfNotEmpty(m, "windows", strings(c, "windows"));
            m.put("additions", integer(c, "additions"));
            m.put("deletions", integer(c, "deletions"));
            final JsonArray hunks = array(c, "hunks");
            if (hunks != null && hunks.size() > 0) {
                final List<Object> list = new ArrayList<>();
                for (final JsonElement h : hunks) {
                    final JsonObject x = objectOf(h);
                    final Map<String, Object> hm = new LinkedHashMap<>();
                    hm.put("old_start", integerOrZero(x, "old_start"));
                    hm.put("old_lines", integerOrZero(x, "old_lines"));
                    hm.put("new_start", integerOrZero(x, "new_start"));
                    hm.put("new_lines", integerOrZero(x, "new_lines"));
                    hm.put("lines", strings(x, "lines"));
                    list.add(hm);
                }
                m.put("hunks", list);
            }
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> endpoint(@Nullable final JsonObject o) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("present", o != null && bool(o, "present"));
        m.put("bytes", o == null ? null : integer(o, "bytes"));
        if (o != null) {
            putIfNotEmpty(m, "sha256", str(o, "sha256"));
            if (bool(o, "no_newline_at_end")) {
                m.put("no_newline_at_end", true);
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- reading as Go's json.Unmarshal reads

    /** A value of a type the field cannot hold; Go refuses the whole record. */
    private static final class Mismatch extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Mismatch(final String what) {
            super(what);
        }
    }

    @Nullable
    private static String str(final JsonObject o, final String key) {
        final JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return e.getAsString();
        }
        throw new Mismatch(key + " is not a string");
    }

    @Nullable
    private static Long integer(final JsonObject o, final String key) {
        final JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            // as written: Go decodes an integer field from an integer literal only, so 1.0 and 1e2 are refused
            final String literal = e.getAsString();
            if (INTEGER_LITERAL.matcher(literal).matches()) {
                try {
                    return Long.parseLong(literal);
                } catch (final NumberFormatException ignored) {
                    throw new Mismatch(key + " overflows");
                }
            }
        }
        throw new Mismatch(key + " is not an integer");
    }

    private static long integerOrZero(final JsonObject o, final String key) {
        final Long v = integer(o, key);
        return v == null ? 0L : v;
    }

    private static boolean bool(final JsonObject o, final String key) {
        final JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return false;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        throw new Mismatch(key + " is not a boolean");
    }

    @Nullable
    private static List<String> strings(final JsonObject o, final String key) {
        final JsonArray a = array(o, key);
        if (a == null) {
            return null;
        }
        final List<String> out = new ArrayList<>(a.size());
        for (final JsonElement e : a) {
            if (e.isJsonNull()) {
                out.add("");
            } else if (e.isJsonPrimitive() && ((JsonPrimitive) e).isString()) {
                out.add(e.getAsString());
            } else {
                throw new Mismatch(key + " holds a value that is not a string");
            }
        }
        return out;
    }

    @Nullable
    private static JsonObject object(final JsonObject o, final String key) {
        final JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonObject()) {
            return e.getAsJsonObject();
        }
        throw new Mismatch(key + " is not an object");
    }

    private static JsonObject objectOf(final JsonElement e) {
        if (e.isJsonObject()) {
            return e.getAsJsonObject();
        }
        throw new Mismatch("a list element is not an object");
    }

    @Nullable
    private static JsonArray array(final JsonObject o, final String key) {
        final JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonArray()) {
            return e.getAsJsonArray();
        }
        throw new Mismatch(key + " is not a list");
    }

    private static void putIfNotEmpty(final Map<String, Object> m, final String key, @Nullable final String value) {
        if (StringUtil.isNotEmpty(value)) {
            m.put(key, value);
        }
    }

    private static void putIfNotEmpty(final Map<String, Object> m, final String key, @Nullable final List<String> value) {
        if (value != null && !value.isEmpty()) {
            m.put(key, value);
        }
    }

    private static String nullToEmpty(@Nullable final String s) {
        return s == null ? "" : s;
    }
}
