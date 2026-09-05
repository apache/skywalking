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

package org.apache.skywalking.oap.server.ai.agent.conversation.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.FileNames;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Ref;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Times;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Builds the <code>asz.view</code> 1.0 document of one conversation, key for key as the Sessionizer's
 * <code>pkg/sessionview</code> defines it and its <code>internal/view</code> builds it, so the document the OAP
 * answers with equals the one <code>asz conversation -json</code> prints for the same files.
 *
 * <p>The document is deterministic: no wall-clock time, keys in the order the format page lists them, lists in
 * record order. Keys a node has no value for are absent; the fixed keys of rounds and files are null when absent.
 */
public final class ConversationViewBuilder {
    /** The readable text a node carries is clipped to this many bytes; the full size is in <code>bytes</code>. */
    static final int PREVIEW_BYTES = 2000;
    private static final int MAX_DEPTH = 12;
    private static final Pattern INTEGER_LITERAL = Pattern.compile("-?\\d+");
    /** A parent walk stops here; a well-formed fold is far shallower, a malformed one must not loop. */
    private static final int MAX_ANCESTORS = 64;
    static final String STATE_VERIFIED = "verified";
    static final String STATE_INCOMPLETE = "incomplete";
    static final String STATE_MISMATCH = "mismatch";

    private final ConversationFold fold;
    private final List<RoundInput> rounds;
    private final Map<Long, SessionDataFile> files;
    private final List<String> problems;
    /** Every timed record's moment in nanoseconds, the precision the Sessionizer computes intervals with. */
    private final Map<Ref, Long> at = new HashMap<>();

    /**
     * @param fold     the fold of the rounds, in order
     * @param rounds   every stored round in number order, decoded or with the reason it does not read
     * @param files    the session's landed files by seq
     * @param problems what stopped the fold short of the chain's last round, in words; they lead the document's
     *                 problems, and a fold that stopped short leaves the document incomplete at best
     */
    public ConversationViewBuilder(final ConversationFold fold,
                                   final List<RoundInput> rounds,
                                   final Map<Long, SessionDataFile> files,
                                   final List<String> problems) {
        this.fold = fold;
        this.rounds = rounds;
        this.files = files;
        this.problems = problems;
        for (final SessionDataFile f : files.values()) {
            for (final SessionDataFile.Record r : f.getRecords()) {
                if (r.getTimeNanos() != 0) {
                    at.put(new Ref(f.getHeader().getSeq(), r.getRow(), null), r.getTimeNanos());
                }
            }
        }
    }

    /**
     * @return the document as ordered maps
     */
    public Map<String, Object> build() {
        final Overview o = overview();
        final Chain chain = chain();

        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("format", ViewYaml.FORMAT);
        doc.put("version", ViewYaml.VERSION);
        doc.put("conversation", nullToEmpty(fold.getConversation()));
        doc.put("sessions", sessions());
        final Map<String, Object> head = new LinkedHashMap<>();
        head.put("round", fold.getRound());
        head.put("digest", nullToEmpty(fold.getDigest()));
        doc.put("head", head);
        doc.put("parser", nullToEmpty(fold.getParser()));
        doc.put("policy", nullToEmpty(fold.getPolicy()));

        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", o.title);
        summary.put("state", chain.state);
        summary.put("problems", chain.problems);
        summary.put("talks", o.talks.size());
        summary.put("steps", stepNodes());
        summary.put("streams", o.streams.size());
        summary.put("segments", o.segments.size());
        summary.put("rounds", chain.rounds.size());
        summary.put("unresolved", fold.openUnresolved().size());
        final SessionFlowRound.Node sessionNode = fold.node(sessionNodeId());
        summary.put("from", sessionNode == null ? 0L : Times.millis(sessionNode.attr("from_time")));
        summary.put("to", sessionNode == null ? 0L : Times.millis(sessionNode.attr("through_time")));
        summary.put("kinds", o.kinds);
        summary.put("relation_types", o.relationTypes);
        summary.put("quality", o.quality);
        doc.put("summary", summary);
        doc.put("rounds", chain.rounds);
        final List<Map<String, Object>> fileList = files();
        fileList.addAll(chain.roundFiles);
        doc.put("files", fileList);
        doc.put("streams", o.streams);
        doc.put("segments", o.segments);
        doc.put("talks", talks(o));
        doc.put("loose", loose());
        doc.put("relations", relations());
        doc.put("unresolved", unresolved());
        return doc;
    }

    /**
     * The conversation's own session first, then every other session node the fold holds, by node id, as the
     * Sessionizer lists them.
     */
    private List<String> sessions() {
        final List<String> out = new ArrayList<>();
        out.add(nullToEmpty(fold.getSession()));
        final List<String> others = new ArrayList<>();
        for (final SessionFlowRound.Node n : fold.nodesOfKind("session")) {
            final String id = n.getId();
            final String s = id.startsWith("session/") ? id.substring("session/".length()) : id;
            if (!s.equals(fold.getSession()) && !others.contains(s)) {
                others.add(s);
            }
        }
        Collections.sort(others);
        out.addAll(others);
        return out;
    }

    /**
     * The id of the session node, as the Sessionizer joins ids: a slash inside a part becomes an underscore.
     */
    private String sessionNodeId() {
        return "session/" + nullToEmpty(fold.getSession()).replace('/', '_');
    }

    private int stepNodes() {
        int steps = 0;
        for (final SessionFlowRound.Node n : fold.getNodes().values()) {
            if (isStep(n.getKind())) {
                steps++;
            }
        }
        return steps;
    }

    // ---------------------------------------------------------------- the chain check

    private static final class Chain {
        final List<Map<String, Object>> rounds = new ArrayList<>();
        final List<Map<String, Object>> roundFiles = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        String state = STATE_VERIFIED;

        void incomplete(final String problem) {
            problems.add(problem);
            if (STATE_VERIFIED.equals(state)) {
                state = STATE_INCOMPLETE;
            }
        }

        void mismatch(final String problem) {
            problems.add(problem);
            state = STATE_MISMATCH;
        }
    }

    /**
     * Each round must follow the round before by number, name its commit digest, continue its seq window, have
     * every file of that window, and chain their digests to its own input digest; a missing round or file leaves
     * the chain incomplete, a wrong digest is a mismatch. A round that does not read is a mismatch and is not
     * listed. The fold's own problems lead, and a fold that stopped short is incomplete at best.
     */
    private Chain chain() {
        final Chain chain = new Chain();
        String prevDigest = "";
        String prevInput = "";
        long prevThrough = 0;
        long prevRound = 0;
        for (final RoundInput in : rounds) {
            if (in.error != null) {
                chain.mismatch("round " + in.number + ": " + in.error);
                continue;
            }
            final SessionFlowRound r = in.round;
            final SessionFlowRound.Header h = r.getHeader();
            boolean ok = true;
            if (h.getRound() != prevRound + 1) {
                // a missing round: this one cannot link to what is not there, and nothing after it folds
                chain.incomplete("round " + (prevRound + 1) + " is missing before round " + h.getRound());
                ok = false;
            } else if (!nullToEmpty(h.getPrevious()).equals(prevDigest)) {
                chain.mismatch("round " + h.getRound() + " names previous " + first12(h.getPrevious())
                                   + ", the round before is " + first12(prevDigest));
                ok = false;
            }
            if (h.getFromSeq() != prevThrough + 1) {
                chain.incomplete("round " + h.getRound() + " starts at seq " + h.getFromSeq()
                                     + ", the round before ended at " + prevThrough);
                ok = false;
            }
            final List<String> added = new ArrayList<>();
            for (long seq = h.getFromSeq(); seq <= h.getThroughSeq(); seq++) {
                final SessionDataFile f = files.get(seq);
                if (f == null) {
                    chain.incomplete("round " + h.getRound() + ": landed file seq " + seq + " is missing");
                    ok = false;
                    continue;
                }
                added.add(f.getFileDigest());
            }
            if (ok && !Digests.chainInputDigest(prevInput, added).equals(h.getInputDigest())) {
                chain.mismatch("round " + h.getRound() + ": the input digest does not match the landed files");
                ok = false;
            }
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("round", h.getRound());
            m.put("digest", nullToEmpty(r.getCommitDigest()));
            m.put("previous", StringUtil.isEmpty(h.getPrevious()) ? null : h.getPrevious());
            m.put("from_seq", h.getFromSeq());
            m.put("through_seq", h.getThroughSeq());
            m.put("input_digest", nullToEmpty(h.getInputDigest()));
            m.put("from_time", millisOrNull(h.getFromTime()));
            m.put("through_time", millisOrNull(h.getThroughTime()));
            m.put("verified", ok);
            chain.rounds.add(m);

            final Map<String, Object> f = new LinkedHashMap<>();
            f.put("file", FileNames.roundFile(h.getConversation(), h.getRound(), r.getCommitDigest()));
            f.put("format", "sf");
            f.put("kind", "round");
            f.put("seq", null);
            f.put("round", h.getRound());
            f.put("stream", null);
            f.put("run", null);
            f.put("lines", r.getLines());
            f.put("bytes", r.getBytes());
            f.put("digest", nullToEmpty(in.fileDigest));
            f.put("from_time", millisOrNull(h.getFromTime()));
            f.put("through_time", millisOrNull(h.getThroughTime()));
            chain.roundFiles.add(f);

            prevDigest = nullToEmpty(r.getCommitDigest());
            prevInput = nullToEmpty(h.getInputDigest());
            prevThrough = h.getThroughSeq();
            prevRound = h.getRound();
        }
        if (!problems.isEmpty()) {
            chain.problems.addAll(0, problems);
            if (STATE_VERIFIED.equals(chain.state)) {
                chain.state = STATE_INCOMPLETE;
            }
        }
        return chain;
    }

    private List<Map<String, Object>> files() {
        final List<Map<String, Object>> out = new ArrayList<>();
        final List<Long> seqs = new ArrayList<>(files.keySet());
        Collections.sort(seqs);
        for (final Long seq : seqs) {
            final SessionDataFile f = files.get(seq);
            final SessionDataFile.Header h = f.getHeader();
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", FileNames.dataFile(h));
            m.put("format", "sd");
            m.put("kind", nullToEmpty(h.getKind()));
            m.put("seq", h.getSeq());
            m.put("round", null);
            m.put("stream", StringUtil.isEmpty(h.getStream()) ? null : h.getStream());
            m.put("run", StringUtil.isEmpty(h.getBatch()) ? null : h.getBatch());
            m.put("lines", f.getLines());
            m.put("bytes", f.getBytes());
            m.put("digest", f.getFileDigest());
            m.put("from_time", f.getFromTime() == 0 ? null : f.getFromTime());
            m.put("through_time", f.getFromTime() == 0 ? null : f.getThroughTime());
            out.add(m);
        }
        return out;
    }

    // ---------------------------------------------------------------- overview

    private static final class TalkRow {
        String label = "";
        int runs;
        int steps;
        int tools;
        long from;
        long to;
        boolean child;
        String segment = "";
        String reply = "";
        List<Ref> labelAt = Collections.emptyList();
        Ref replyAt;
        SessionFlowRound.Node node;
    }

    private static final class Overview {
        String title = "";
        Map<String, Object> kinds;
        Map<String, Object> relationTypes;
        Map<String, Object> quality;
        List<TalkRow> talks;
        List<Map<String, Object>> streams;
        List<Map<String, Object>> segments;
    }

    private Overview overview() {
        final Overview o = new Overview();
        final TreeMap<String, Object> kinds = new TreeMap<>();
        for (final SessionFlowRound.Node n : fold.getNodes().values()) {
            kinds.merge(nullToEmpty(n.getKind()), 1, (a, b) -> (Integer) a + (Integer) b);
            if ("session".equals(n.getKind())) {
                o.title = nullToEmpty(n.attr("title"));
            }
        }
        final TreeMap<String, Object> quality = new TreeMap<>();
        final TreeMap<String, Object> rels = new TreeMap<>();
        for (final SessionFlowRound.Relation r : fold.getRelations().values()) {
            rels.merge(nullToEmpty(r.getType()), 1, (a, b) -> (Integer) a + (Integer) b);
            quality.merge(nullToEmpty(r.getQuality()), 1, (a, b) -> (Integer) a + (Integer) b);
        }
        o.kinds = kinds;
        o.relationTypes = rels;
        o.quality = quality;

        final Map<String, Boolean> childStream = new HashMap<>();
        for (final SessionFlowRound.Node st : streamNodes()) {
            if (!"main".equals(st.attr("role"))) {
                childStream.put(st.getStream(), true);
            }
        }
        final Map<String, String> inSegment = new HashMap<>();
        for (final SessionFlowRound.Relation r : fold.getRelations().values()) {
            if ("in_segment".equals(r.getType())) {
                inSegment.put(r.getFrom(), r.getTo());
            }
        }
        final List<TalkRow> talks = new ArrayList<>();
        for (final SessionFlowRound.Node t : fold.nodesOfKind("talk")) {
            final TalkRow row = new TalkRow();
            row.node = t;
            row.child = childStream.containsKey(t.getStream());
            final long[] span = span(t);
            row.from = span[0];
            row.to = span[1];
            row.segment = inSegment.getOrDefault(t.getId(), "");
            walkCounts(t.getId(), row);
            row.labelAt = labelRefs(t);
            row.replyAt = replyRef(t);
            talks.add(row);
        }
        for (final TalkRow row : talks) {
            for (final Ref r : row.labelAt) {
                final String text = readableAt(r);
                if (trimSpace(text).startsWith("{\"type\":\"deferred_tools_delta\"")) {
                    continue;
                }
                if (!text.isEmpty()) {
                    row.label = clip(text);
                    break;
                }
            }
            if (row.replyAt != null) {
                row.reply = clip(readableAt(row.replyAt));
            }
        }
        o.talks = talks;
        o.streams = streamRows(talks);
        o.segments = segmentRows(talks);
        return o;
    }

    private void walkCounts(final String id, final TalkRow row) {
        for (final SessionFlowRound.Node k : fold.children(id)) {
            if ("run".equals(k.getKind())) {
                row.runs++;
            }
            if (isStep(k.getKind())) {
                row.steps++;
            }
            if ("tool".equals(k.getKind()) || "agent.call".equals(k.getKind())) {
                row.tools++;
            }
            walkCounts(k.getId(), row);
        }
    }

    private List<Ref> labelRefs(final SessionFlowRound.Node t) {
        final List<Ref> inj = new ArrayList<>();
        final Ref[] ext = new Ref[1];
        walkLabel(t.getId(), inj, ext);
        if (ext[0] != null) {
            return Collections.singletonList(ext[0]);
        }
        return inj;
    }

    private boolean walkLabel(final String id, final List<Ref> inj, final Ref[] ext) {
        for (final SessionFlowRound.Node k : fold.children(id)) {
            if ("message.external".equals(k.getKind()) && k.getRef() != null) {
                ext[0] = k.getRef();
                return true;
            }
            if ("context.injection".equals(k.getKind()) && k.getRef() != null && inj.size() < 3) {
                inj.add(k.getRef());
            }
            if (walkLabel(k.getId(), inj, ext)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Ref replyRef(final SessionFlowRound.Node t) {
        final Ref[] found = new Ref[1];
        walkReply(t.getId(), found);
        return found[0];
    }

    private void walkReply(final String id, final Ref[] found) {
        for (final SessionFlowRound.Node k : fold.children(id)) {
            if (("message.assistant".equals(k.getKind()) || "agent.output".equals(k.getKind())) && k.getRef() != null) {
                found[0] = k.getRef();
            }
            walkReply(k.getId(), found);
        }
    }

    private List<Map<String, Object>> segmentRows(final List<TalkRow> talks) {
        final Map<String, long[]> span = new HashMap<>();
        final Map<String, Integer> count = new HashMap<>();
        for (final TalkRow t : talks) {
            if (t.segment.isEmpty()) {
                continue;
            }
            count.merge(t.segment, 1, Integer::sum);
            final long[] s = span.computeIfAbsent(t.segment, x -> new long[2]);
            if (t.from != 0 && (s[0] == 0 || t.from < s[0])) {
                s[0] = t.from;
            }
            if (t.to > s[1]) {
                s[1] = t.to;
            }
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final SessionFlowRound.Node n : fold.nodesOfKind("segment")) {
            final long[] s = span.getOrDefault(n.getId(), new long[2]);
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("state", nullToEmpty(n.attr("state")));
            m.put("committable", n.attrBool("committable"));
            m.put("talks", count.getOrDefault(n.getId(), 0));
            m.put("from", s[0]);
            m.put("to", s[1]);
            out.add(m);
        }
        return out;
    }

    private List<SessionFlowRound.Node> streamNodes() {
        final List<SessionFlowRound.Node> out = new ArrayList<>(fold.nodesOfKind("stream"));
        out.sort((a, b) -> {
            final boolean ma = "main".equals(a.attr("role"));
            final boolean mb = "main".equals(b.attr("role"));
            if (ma != mb) {
                return ma ? -1 : 1;
            }
            return ConversationFold.compare(a, b);
        });
        return out;
    }

    private List<Map<String, Object>> streamRows(final List<TalkRow> talks) {
        final Map<String, String> firstTalk = new HashMap<>();
        final Map<String, Integer> steps = new HashMap<>();
        for (final TalkRow t : talks) {
            final String stream = nullToEmpty(t.node.getStream());
            firstTalk.putIfAbsent(stream, t.node.getId());
            steps.merge(stream, t.steps, Integer::sum);
        }
        final Map<String, String> parent = new HashMap<>();
        final Map<String, List<Map<String, Object>>> openedBy = new HashMap<>();
        for (final SessionFlowRound.Relation r : fold.getRelations().values()) {
            if (!"starts".equals(r.getType())) {
                continue;
            }
            final SessionFlowRound.Node n = fold.node(r.getFrom());
            if (n != null && StringUtil.isNotEmpty(n.getStream())) {
                parent.put(r.getTo(), n.getStream());
                final Map<String, Object> origin = new LinkedHashMap<>();
                origin.put("step", r.getFrom());
                origin.put("stream", n.getStream());
                origin.put("talk", talkOf(r.getFrom()));
                origin.put("quality", nullToEmpty(r.getQuality()));
                openedBy.computeIfAbsent(r.getTo(), x -> new ArrayList<>()).add(origin);
            }
        }
        final Map<String, String> journalNames = journalNames();
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final SessionFlowRound.Node st : streamNodes()) {
            String label = nullToEmpty(st.attr("label"));
            String namedBy = "";
            if (label.isEmpty() && journalNames.containsKey(st.getStream())) {
                label = journalNames.get(st.getStream());
                namedBy = "journal";
            }
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", st.getId());
            m.put("name", nullToEmpty(st.getStream()));
            m.put("role", nullToEmpty(st.attr("role")));
            m.put("label", label);
            m.put("parent", parent.getOrDefault(st.getId(), ""));
            m.put("records", (int) st.attrNumber("records"));
            m.put("steps", steps.getOrDefault(nullToEmpty(st.getStream()), 0));
            m.put("talk", firstTalk.getOrDefault(nullToEmpty(st.getStream()), ""));
            m.put("named_by", namedBy);
            m.put("opened_by", openedBy.getOrDefault(st.getId(), new ArrayList<>()));
            out.add(m);
        }
        return out;
    }

    /**
     * A child stream a workflow started has no label of its own; its journal's result row names it.
     */
    private Map<String, String> journalNames() {
        final Map<String, String> names = new HashMap<>();
        final List<Long> seqs = new ArrayList<>(files.keySet());
        Collections.sort(seqs);
        for (final Long seq : seqs) {
            final SessionDataFile f = files.get(seq);
            if (StringUtil.isEmpty(f.getHeader().getBatch())) {
                continue;
            }
            for (final SessionDataFile.Record rec : f.getRecords()) {
                final String child = rec.child();
                if (StringUtil.isEmpty(child) || names.containsKey(child)) {
                    continue;
                }
                for (final String raw : candidates(rec)) {
                    final JsonObject row;
                    try {
                        final JsonElement parsed = JsonParser.parseString(raw);
                        if (!parsed.isJsonObject()) {
                            continue;
                        }
                        row = parsed.getAsJsonObject();
                    } catch (final RuntimeException e) {
                        continue;
                    }
                    // the row must decode as Go's typed struct, or the candidate is skipped
                    if (!isStringOrAbsent(row, "type") || !isObjectOrAbsent(row, "result")) {
                        continue;
                    }
                    if (!"result".equals(string(row, "type"))) {
                        continue;
                    }
                    final JsonObject result = row.has("result") && row.get("result").isJsonObject()
                        ? row.getAsJsonObject("result") : new JsonObject();
                    if (!isStringOrAbsent(result, "surface") || !isStringOrAbsent(result, "summary")
                        || !isStringOrAbsent(result, "verdict") || !isClaimsOrAbsent(result)) {
                        continue;
                    }
                    String name = string(result, "surface");
                    if (StringUtil.isEmpty(name)) {
                        name = string(result, "summary");
                    }
                    if (StringUtil.isEmpty(name) && StringUtil.isNotEmpty(string(result, "verdict"))) {
                        name = string(result, "verdict");
                        final JsonElement refuted = result.get("refuted_claims");
                        if (refuted != null && refuted.isJsonArray() && refuted.getAsJsonArray().size() > 0) {
                            final JsonElement first = refuted.getAsJsonArray().get(0);
                            if (first.isJsonObject() && StringUtil.isNotEmpty(string(first.getAsJsonObject(), "claim"))) {
                                name += " · " + string(first.getAsJsonObject(), "claim");
                            }
                        }
                    }
                    name = shortName(name);
                    if (!name.isEmpty()) {
                        names.put(child, name);
                    }
                    break;
                }
            }
        }
        return names;
    }

    private static boolean isObjectOrAbsent(final JsonObject json, final String key) {
        final JsonElement e = json.get(key);
        return e == null || e.isJsonNull() || e.isJsonObject();
    }

    /**
     * @return whether <code>refuted_claims</code> is absent or a list of objects whose <code>claim</code> is a
     * string, the shape Go decodes
     */
    private static boolean isClaimsOrAbsent(final JsonObject result) {
        final JsonElement e = result.get("refuted_claims");
        if (e == null || e.isJsonNull()) {
            return true;
        }
        if (!e.isJsonArray()) {
            return false;
        }
        for (final JsonElement x : e.getAsJsonArray()) {
            if (!x.isJsonObject() || !isStringOrAbsent(x.getAsJsonObject(), "claim")) {
                return false;
            }
        }
        return true;
    }

    private static String shortName(@Nullable final String s) {
        if (s == null) {
            return "";
        }
        final String joined = String.join(" ", fields(s));
        final int limit = 160;
        final byte[] bytes = joined.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= limit) {
            return joined;
        }
        // Go backs up to a rune boundary here, unlike clip, so no replacement character
        int cut = limit;
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) {
            cut--;
        }
        return new String(bytes, 0, cut, StandardCharsets.UTF_8) + "…";
    }

    /**
     * The text split on Unicode white space, as Go's <code>strings.Fields</code>, so a no-break space splits too.
     */
    private static List<String> fields(final String s) {
        final List<String> out = new ArrayList<>();
        final StringBuilder word = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            if (isSpace(cp)) {
                if (word.length() > 0) {
                    out.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        if (word.length() > 0) {
            out.add(word.toString());
        }
        return out;
    }

    /**
     * Go's <code>unicode.IsSpace</code>: the Latin-1 spaces and the Unicode space separators.
     */
    private static boolean isSpace(final int cp) {
        return cp == ' ' || cp == '\t' || cp == '\n' || cp == '\u000B' || cp == '\f' || cp == '\r' || cp == 0x85
            || cp == 0xA0 || cp == 0x2028 || cp == 0x2029 || Character.getType(cp) == Character.SPACE_SEPARATOR;
    }

    /**
     * Go's <code>strings.TrimSpace</code>.
     */
    private static String trimSpace(final String s) {
        int start = 0;
        int end = s.length();
        while (start < end) {
            final int cp = s.codePointAt(start);
            if (!isSpace(cp)) {
                break;
            }
            start += Character.charCount(cp);
        }
        while (end > start) {
            final int cp = s.codePointBefore(end);
            if (!isSpace(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        return s.substring(start, end);
    }

    private String talkOf(final String start) {
        String id = start;
        for (int i = 0; i < 24 && StringUtil.isNotEmpty(id); i++) {
            final SessionFlowRound.Node n = fold.node(id);
            if (n == null) {
                return "";
            }
            if ("talk".equals(n.getKind())) {
                return n.getId();
            }
            id = n.getParent();
        }
        return "";
    }

    // ---------------------------------------------------------------- talks and their trees

    private List<Map<String, Object>> talks(final Overview o) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final TalkRow row : o.talks) {
            out.add(step(row.node, 0, row));
        }
        return out;
    }

    /**
     * One node, keys in the order of <code>sessionview.Node</code>, empty values absent. A talk carries its row's
     * summary keys between the record keys and the tool keys.
     */
    private Map<String, Object> step(final SessionFlowRound.Node n, final int depth, @Nullable final TalkRow talk) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", n.getId());
        out.put("kind", nullToEmpty(n.getKind()));
        if (StringUtil.isNotEmpty(n.getParent())) {
            out.put("parent", n.getParent());
        }
        if (StringUtil.isNotEmpty(n.getStream())) {
            out.put("stream", n.getStream());
        }
        out.put("at", time(n));
        if (n.getRef() != null) {
            out.put("ref", n.getRef().toMap());
        }
        if (!n.getRefs().isEmpty()) {
            final List<Map<String, Object>> refs = new ArrayList<>();
            for (final Ref r : n.getRefs()) {
                refs.add(r.toMap());
            }
            out.put("refs", refs);
        }
        if (n.getRawAttrs() != null) {
            // as the Sessionizer prints the raw attrs: an empty object stays {}, an explicit null stays null
            out.put("attrs", jsonToValue(n.getRawAttrs()));
        }

        // text, state, bytes; then usage, flags, dropped; then the talk keys; then the tool keys
        final Map<String, Object> content = new LinkedHashMap<>();
        final Map<String, Object> tool = new LinkedHashMap<>();
        if (n.getRef() != null && carriesContent(n.getKind())) {
            final SessionDataFile.Record rec = record(n.getRef());
            if (rec != null) {
                fill(content, tool, rec, n.getRef().getBlock());
                fillDuration(content, tool, n, rec);
                if (!rec.flags().isEmpty()) {
                    content.put("flags", new ArrayList<>(rec.flags()));
                }
                final JsonArray dropped = rec.dropped();
                if (dropped != null && dropped.size() > 0) {
                    final List<Object> drops = new ArrayList<>();
                    for (final JsonElement d : dropped) {
                        drops.add(d.isJsonObject() ? jsonToMap(d.getAsJsonObject()) : d.toString());
                    }
                    content.put("dropped", drops);
                }
            }
            for (int i = 1; i < n.getRefs().size(); i++) {
                final Ref r = n.getRefs().get(i);
                final SessionDataFile.Record rr = record(r);
                if (rr != null && !tool.containsKey("result")) {
                    fillResult(tool, rr, r.getBlock());
                }
            }
            fillRequestToResult(tool, n);
        }
        final JsonObject usage = usageAt(n);
        if (usage != null) {
            // as the Sessionizer prints the record's raw usage object, an empty one included
            content.put("usage", jsonToMap(usage));
        }
        // sessionview.Node lists text, state, bytes, then usage, flags, dropped
        for (final String key : new String[] {"text", "state", "bytes", "usage", "flags", "dropped"}) {
            if (content.containsKey(key)) {
                out.put(key, content.get(key));
            }
        }
        if (talk != null) {
            if (!talk.label.isEmpty()) {
                out.put("label", talk.label);
            }
            if (!talk.reply.isEmpty()) {
                out.put("reply", talk.reply);
            }
            if (talk.runs != 0) {
                out.put("runs", talk.runs);
            }
            if (talk.steps != 0) {
                out.put("steps", talk.steps);
            }
            if (talk.tools != 0) {
                out.put("tools", talk.tools);
            }
            if (talk.from != 0) {
                out.put("from", talk.from);
            }
            if (talk.to != 0) {
                out.put("to", talk.to);
            }
            if (talk.child) {
                out.put("child", true);
            }
            if (!talk.segment.isEmpty()) {
                out.put("segment", talk.segment);
            }
        }
        for (final String key : new String[] {
            "name", "failed", "result", "result_state", "result_bytes", "request_to_result_ms", "request_to_result_join",
            "duration_ms", "duration_measured_by"}) {
            if (tool.containsKey(key)) {
                out.put(key, tool.get(key));
            }
        }
        if (depth < MAX_DEPTH) {
            final List<Map<String, Object>> children = new ArrayList<>();
            for (final SessionFlowRound.Node k : fold.children(n.getId())) {
                children.add(step(k, depth + 1, null));
            }
            if (!children.isEmpty()) {
                out.put("children", children);
            }
        }
        // every relation touching the node, by relation id and then direction, as the Sessionizer lists them
        final List<Map<String, Object>> edges = new ArrayList<>();
        for (final SessionFlowRound.Relation r : fold.relationsFrom(n.getId())) {
            edges.add(edge(r, r.getTo(), "out"));
        }
        for (final SessionFlowRound.Relation r : fold.relationsTo(n.getId())) {
            edges.add(edge(r, r.getFrom(), "in"));
        }
        edges.sort(Comparator.comparing((Map<String, Object> e) -> (String) e.get("id"))
                             .thenComparing(e -> (String) e.get("dir")));
        for (final Map<String, Object> e : edges) {
            e.remove("id");
        }
        if (!edges.isEmpty()) {
            out.put("edges", edges);
        }
        return out;
    }

    /**
     * The runs and steps no talk contains, as trees from their highest such ancestor, in record order: a step is
     * contained when a talk is above it; one whose ancestors are only structure, the session, a stream, an epoch
     * or a segment, is loose. With <code>talks</code> this holds every run and step of the fold.
     */
    private List<Map<String, Object>> loose() {
        final Map<String, SessionFlowRound.Node> roots = new HashMap<>();
        for (final SessionFlowRound.Node n : fold.getNodes().values()) {
            if (!"run".equals(n.getKind()) && !isStep(n.getKind())) {
                continue;
            }
            SessionFlowRound.Node top = n;
            boolean covered = false;
            SessionFlowRound.Node cur = n;
            for (int i = 0; cur != null && i < MAX_ANCESTORS; i++) {
                if ("talk".equals(cur.getKind())) {
                    covered = true;
                    break;
                }
                if ("run".equals(cur.getKind()) || isStep(cur.getKind())) {
                    top = cur;
                }
                cur = StringUtil.isEmpty(cur.getParent()) ? null : fold.node(cur.getParent());
            }
            if (!covered) {
                roots.put(top.getId(), top);
            }
        }
        final List<SessionFlowRound.Node> ordered = new ArrayList<>(roots.values());
        ordered.sort(ConversationFold::compare);
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final SessionFlowRound.Node n : ordered) {
            out.add(step(n, 0, null));
        }
        return out;
    }

    private static Map<String, Object> edge(final SessionFlowRound.Relation r, final String other, final String dir) {
        final Map<String, Object> e = new LinkedHashMap<>();
        // the relation id orders the edges and is removed before the edge is emitted
        e.put("id", nullToEmpty(r.getId()));
        e.put("type", nullToEmpty(r.getType()));
        e.put("other", nullToEmpty(other));
        e.put("dir", dir);
        e.put("quality", nullToEmpty(r.getQuality()));
        if (StringUtil.isNotEmpty(r.getVia())) {
            e.put("via", r.getVia());
        }
        return e;
    }

    private static void fill(final Map<String, Object> content, final Map<String, Object> tool,
                             final SessionDataFile.Record rec, @Nullable final Integer block) {
        SessionDataFile.Part p = null;
        if (block != null && block < rec.getParts().size()) {
            p = rec.getParts().get(block);
        } else if (rec.getParts().size() == 1) {
            p = rec.getParts().get(0);
        }
        if (p == null) {
            final String text = clip(readable(rec));
            if (!text.isEmpty()) {
                content.put("text", text);
            }
            return;
        }
        if (StringUtil.isNotEmpty(p.getName())) {
            tool.put("name", p.getName());
        }
        if (p.getFailed() != null) {
            tool.put("failed", p.getFailed());
        }
        if (StringUtil.isNotEmpty(p.getState())) {
            content.put("state", p.getState());
        }
        if (p.getBytes() != 0) {
            content.put("bytes", p.getBytes());
        }
        final String data = p.data();
        if (StringUtil.isNotEmpty(p.getText())) {
            content.put("text", clip(p.getText()));
        } else if (data != null) {
            final String t = readable(rec);
            if (!t.isEmpty() && !t.equals(data.trim())) {
                content.put("text", clip(t));
            } else {
                content.put("text", clip(data));
            }
        }
    }

    @Nullable
    private JsonObject usageAt(final SessionFlowRound.Node n) {
        if (!"llm.call".equals(n.getKind()) || n.getAttrs() == null || !n.getAttrs().has("usage_at")
            || !n.getAttrs().get("usage_at").isJsonObject()) {
            return null;
        }
        final SessionDataFile.Record at = record(Ref.of(n.getAttrs().getAsJsonObject("usage_at")));
        return at == null ? null : at.usage();
    }

    private static void fillResult(final Map<String, Object> tool, final SessionDataFile.Record rec,
                                   @Nullable final Integer block) {
        SessionDataFile.Part p = null;
        if (block != null && block < rec.getParts().size()) {
            p = rec.getParts().get(block);
        } else {
            for (final SessionDataFile.Part x : rec.getParts()) {
                if ("result".equals(x.getKind())) {
                    p = x;
                    break;
                }
            }
        }
        if (p == null) {
            return;
        }
        // assigned, not merged: a later result record with no state or size clears the earlier one's, as Go does
        if (StringUtil.isNotEmpty(p.getState())) {
            tool.put("result_state", p.getState());
        } else {
            tool.remove("result_state");
        }
        if (p.getBytes() != 0) {
            tool.put("result_bytes", p.getBytes());
        } else {
            tool.remove("result_bytes");
        }
        if (p.getFailed() != null && !tool.containsKey("failed")) {
            tool.put("failed", p.getFailed());
        }
        final String data = p.data();
        if (StringUtil.isNotEmpty(p.getText())) {
            tool.put("result", clip(p.getText()));
        } else if (data != null) {
            tool.put("result", clip(data));
        }
    }

    private void fillRequestToResult(final Map<String, Object> tool, final SessionFlowRound.Node n) {
        if (n.getRefs().size() < 2) {
            return;
        }
        final String join = n.attr("result_join");
        if (!"exact_unique".equals(join)) {
            return;
        }
        // in nanoseconds, as the Sessionizer subtracts them, floored to milliseconds only at the end
        final long from = nanosAt(n.getRefs().get(0));
        final long to = nanosAt(n.getRefs().get(1));
        if (from == 0 || to == 0 || to < from) {
            return;
        }
        final long ms = (to - from) / 1_000_000L;
        if (ms != 0) {
            // Go leaves a zero interval out (omitempty) and keeps the join
            tool.put("request_to_result_ms", ms);
        }
        tool.put("request_to_result_join", join);
    }

    private static void fillDuration(final Map<String, Object> content, final Map<String, Object> tool,
                                     final SessionFlowRound.Node n, final SessionDataFile.Record rec) {
        if (!"turn.duration".equals(n.getKind())) {
            return;
        }
        long durationMs = 0;
        for (final String raw : candidates(rec)) {
            try {
                final JsonElement e = JsonParser.parseString(raw);
                if (e.isJsonObject() && e.getAsJsonObject().has("durationMs")) {
                    final JsonElement d = e.getAsJsonObject().get("durationMs");
                    if (!d.isJsonPrimitive() || !d.getAsJsonPrimitive().isNumber()
                        || !INTEGER_LITERAL.matcher(d.getAsString()).matches()) {
                        // not the integer Go decodes into; the candidate is skipped
                        continue;
                    }
                    durationMs = Long.parseLong(d.getAsString());
                    if (durationMs != 0) {
                        break;
                    }
                }
            } catch (final RuntimeException ignored) {
                // not JSON; the next candidate may be
            }
        }
        if (durationMs == 0) {
            return;
        }
        tool.put("duration_ms", durationMs);
        final String measuredBy = n.attr("measured_by");
        if (StringUtil.isNotEmpty(measuredBy)) {
            tool.put("duration_measured_by", measuredBy);
        }
        content.remove("text");
    }

    private List<Map<String, Object>> relations() {
        final List<SessionFlowRound.Relation> sorted = new ArrayList<>(fold.getRelations().values());
        sorted.sort(Comparator.comparing(SessionFlowRound.Relation::getId));
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final SessionFlowRound.Relation r : sorted) {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("type", nullToEmpty(r.getType()));
            m.put("from", nullToEmpty(r.getFrom()));
            m.put("to", nullToEmpty(r.getTo()));
            m.put("quality", nullToEmpty(r.getQuality()));
            if (StringUtil.isNotEmpty(r.getVia())) {
                m.put("via", r.getVia());
            }
            if (!r.getEvidence().isEmpty()) {
                final List<Map<String, Object>> evidence = new ArrayList<>();
                for (final Ref ref : r.getEvidence()) {
                    evidence.add(ref.toMap());
                }
                m.put("evidence", evidence);
            }
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> unresolved() {
        final List<SessionFlowRound.Unresolved> sorted = new ArrayList<>(fold.getUnresolved().values());
        sorted.sort(Comparator.comparing(SessionFlowRound.Unresolved::getId));
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final SessionFlowRound.Unresolved u : sorted) {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("kind", nullToEmpty(u.getKind()));
            m.put("ref", nullToEmpty(u.getRef()));
            m.put("reason", nullToEmpty(u.getReason()));
            m.put("state", nullToEmpty(u.getState()));
            out.add(m);
        }
        return out;
    }

    // ---------------------------------------------------------------- records and times

    @Nullable
    private SessionDataFile.Record record(@Nullable final Ref ref) {
        if (ref == null) {
            return null;
        }
        final SessionDataFile f = files.get(ref.getSeq());
        return f == null ? null : f.record(ref.getRow());
    }

    private long timeAt(@Nullable final Ref ref) {
        // floored, as Go's UnixMilli, so a moment before 1970 rounds the same way; a duration truncates instead
        return Math.floorDiv(nanosAt(ref), 1_000_000L);
    }

    private long nanosAt(@Nullable final Ref ref) {
        if (ref == null) {
            return 0;
        }
        final Long t = at.get(new Ref(ref.getSeq(), ref.getRow(), null));
        return t == null ? 0 : t;
    }

    private long time(final SessionFlowRound.Node n) {
        return timeAt(n.getRef());
    }

    private long[] span(final SessionFlowRound.Node n) {
        final long[] lohi = new long[2];
        walkSpan(n, lohi);
        return lohi;
    }

    private void walkSpan(final SessionFlowRound.Node x, final long[] lohi) {
        final long t = time(x);
        if (t != 0) {
            if (lohi[0] == 0 || t < lohi[0]) {
                lohi[0] = t;
            }
            if (t > lohi[1]) {
                lohi[1] = t;
            }
        }
        for (final SessionFlowRound.Node k : fold.children(x.getId())) {
            walkSpan(k, lohi);
        }
    }

    private String readableAt(final Ref ref) {
        final SessionDataFile.Record rec = record(ref);
        return rec == null ? "" : readable(rec);
    }

    /**
     * The readable text of a record: its text parts, or, for a queued command, the prompt texts inside its data.
     */
    static String readable(final SessionDataFile.Record rec) {
        final String t = trimSpace(rec.text());
        if (!t.isEmpty()) {
            return t;
        }
        for (final String raw : candidates(rec)) {
            try {
                final JsonElement e = JsonParser.parseString(raw);
                if (!e.isJsonObject() || !"queued_command".equals(string(e.getAsJsonObject(), "type"))) {
                    continue;
                }
                final JsonElement prompt = e.getAsJsonObject().get("prompt");
                if (prompt == null || !prompt.isJsonArray()) {
                    continue;
                }
                // the whole candidate is the shape Go decodes, or it is skipped: every element an object whose
                // text, when present, is a string
                final List<String> out = new ArrayList<>();
                boolean shaped = true;
                for (final JsonElement p : prompt.getAsJsonArray()) {
                    if (!p.isJsonObject() || !isStringOrAbsent(p.getAsJsonObject(), "text")) {
                        shaped = false;
                        break;
                    }
                    if (StringUtil.isNotEmpty(string(p.getAsJsonObject(), "text"))) {
                        out.add(string(p.getAsJsonObject(), "text"));
                    }
                }
                if (shaped && !out.isEmpty()) {
                    return trimSpace(String.join("\n", out));
                }
            } catch (final RuntimeException ignored) {
                // not JSON; the next candidate may be
            }
        }
        return "";
    }

    private static boolean isStringOrAbsent(final JsonObject json, final String key) {
        final JsonElement e = json.get(key);
        return e == null || e.isJsonNull() || e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    static List<String> candidates(final SessionDataFile.Record rec) {
        final List<String> out = new ArrayList<>();
        for (final SessionDataFile.Part p : rec.getParts()) {
            final String data = p.data();
            if (data != null) {
                out.add(data);
            }
            if (StringUtil.isNotEmpty(p.getText())) {
                out.add(p.getText());
            }
        }
        final String t = rec.text();
        if (!t.isEmpty()) {
            out.add(t);
        }
        return out;
    }

    static boolean isStep(final String kind) {
        switch (nullToEmpty(kind)) {
            case "session":
            case "segment":
            case "stream":
            case "epoch":
            case "talk":
            case "run":
                return false;
            default:
                return true;
        }
    }

    static boolean carriesContent(final String kind) {
        switch (nullToEmpty(kind)) {
            case "llm.call":
            case "session":
            case "segment":
            case "stream":
            case "epoch":
            case "talk":
            case "run":
                return false;
            default:
                return true;
        }
    }

    static String clip(final String t) {
        return clipBytes(t, PREVIEW_BYTES);
    }

    /**
     * The longest prefix of whole characters within the byte budget, as the Sessionizer clips, so a preview never
     * ends in a broken character.
     */
    private static String clipBytes(final String t, final int limit) {
        if (t == null) {
            return "";
        }
        final byte[] bytes = t.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= limit) {
            return t;
        }
        int cut = limit;
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) {
            cut--;
        }
        return new String(bytes, 0, cut, StandardCharsets.UTF_8);
    }

    static Map<String, Object> jsonToMap(final JsonObject json) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> e : json.entrySet()) {
            out.put(e.getKey(), jsonToValue(e.getValue()));
        }
        return out;
    }

    private static Object jsonToValue(final JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonObject()) {
            return jsonToMap(e.getAsJsonObject());
        }
        if (e.isJsonArray()) {
            final List<Object> list = new ArrayList<>();
            for (final JsonElement x : e.getAsJsonArray()) {
                list.add(jsonToValue(x));
            }
            return list;
        }
        if (e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        if (e.getAsJsonPrimitive().isNumber()) {
            // as written: Go prints the raw attrs, so 1.0 stays 1.0 and 1 stays 1
            final String literal = e.getAsString();
            if (INTEGER_LITERAL.matcher(literal).matches()) {
                try {
                    return Long.parseLong(literal);
                } catch (final NumberFormatException ignored) {
                    return new BigInteger(literal);
                }
            }
            return new BigDecimal(literal);
        }
        return e.getAsString();
    }

    @Nullable
    private static Long millisOrNull(@Nullable final String rfc3339) {
        // absent is null; present but unreadable is 0, as the Sessionizer's millisPtr
        return StringUtil.isEmpty(rfc3339) ? null : Long.valueOf(Times.millis(rfc3339));
    }

    @Nullable
    private static String string(final JsonObject json, final String key) {
        final JsonElement e = json.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    private static String nullToEmpty(@Nullable final String s) {
        return s == null ? "" : s;
    }

    private static String first12(@Nullable final String s) {
        return s == null ? "" : s.substring(0, Math.min(12, s.length()));
    }

    /**
     * One stored round: decoded, with what the store knows about it beyond its bytes, or the reason it does not
     * read, in the Sessionizer's words.
     */
    @Getter
    public static final class RoundInput {
        private final long number;
        private final SessionFlowRound round;
        private final String fileDigest;
        private final String error;

        public RoundInput(final SessionFlowRound round, final String fileDigest) {
            this.number = round.getHeader().getRound();
            this.round = round;
            this.fileDigest = fileDigest;
            this.error = null;
        }

        private RoundInput(final long number, final String error) {
            this.number = number;
            this.round = null;
            this.fileDigest = null;
            this.error = error;
        }

        /**
         * @param number the round's number as stored
         * @param error  why it does not read
         * @return a round the chain lists as a problem and never folds
         */
        public static RoundInput unreadable(final long number, final String error) {
            return new RoundInput(number, error);
        }
    }
}
