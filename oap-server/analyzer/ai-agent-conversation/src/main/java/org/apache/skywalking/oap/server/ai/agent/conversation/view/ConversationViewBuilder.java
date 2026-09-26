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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.ChangesRecord;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.ExecutionRecord;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.FileNames;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.CodePointOrder;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Ref;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Times;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.ToolCallRecord;
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
    static final String STATE_VERIFIED = "verified";
    static final String STATE_INCOMPLETE = "incomplete";
    static final String STATE_MISMATCH = "mismatch";
    /** The shapes the view reads out of records and attributes. */
    private static final Schema JOURNAL_ROW = new Schema()
        .field("type", Schema.Kind.STRING)
        .field("result", Schema.Kind.OBJECT, new Schema()
            .field("surface", Schema.Kind.STRING)
            .field("summary", Schema.Kind.STRING)
            .field("verdict", Schema.Kind.STRING)
            .field("refuted_claims", Schema.Kind.OBJECTS, new Schema()
                .field("claim", Schema.Kind.STRING)));
    private static final Schema QUEUED_COMMAND = new Schema()
        .field("type", Schema.Kind.STRING)
        .field("prompt", Schema.Kind.OBJECTS, new Schema()
            .field("text", Schema.Kind.STRING));
    private static final Schema DURATION = new Schema()
        .field("durationMs", Schema.Kind.INTEGER);
    private static final Schema SESSION_ATTRS = new Schema()
        .field("provider_bodies_landed", Schema.Kind.INTEGER);
    private static final Schema USAGE_AT = new Schema()
        .field("usage_at", Schema.Kind.NULLABLE_OBJECT, SessionFlowRound.REF);

    private final ConversationFold fold;
    private final List<RoundInput> rounds;
    /** By seq, so every walk over the files is in landed order. */
    private final TreeMap<Long, SessionDataFile> files;
    private final List<String> problems;
    /** Every timed record's moment in nanoseconds, the precision the Sessionizer computes intervals with. */
    private final Map<Ref, Long> at = new HashMap<>();
    /**
     * Each file's lane by seq: the directory it lands in, its stream's or its workflow run's. A position orders records
     * only inside one lane.
     */
    private final Map<Long, String> lanes = new HashMap<>();
    /** Each step's workspace change ids, in the order the document lists the records; filled by {@link #workspaceChanges}. */
    private final Map<String, List<String>> changesByStep = new HashMap<>();
    /** Each step's tool execution record ids, in the order the document lists the records; filled by {@link #toolExecutions}. */
    private final Map<String, List<String>> executionsByStep = new HashMap<>();
    /** Each call step's joined provider bodies, its request then its response; filled by {@link #joinProviderBodies}. */
    private final Map<String, List<Map<String, Object>>> providerBodiesByStep = new HashMap<>();

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
        this.files = new TreeMap<>(files);
        this.problems = problems;
        for (final SessionDataFile f : files.values()) {
            lanes.put(f.getHeader().getSeq(), FileNames.lane(f.getHeader()));
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
        // before the nodes are rendered: each step lists the ids of its records
        final List<Map<String, Object>> workspaceChanges = workspaceChanges();
        final List<Map<String, Object>> toolExecutions = toolExecutions();
        final int capturedPrompts = joinProviderBodies();

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
        summary.put("changes", workspaceChanges.size());
        summary.put("provider_bodies", landedProviderBodies());
        summary.put("captured_prompts", capturedPrompts);
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
        doc.put("workspace_changes", workspaceChanges);
        doc.put("tool_executions", toolExecutions);
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
        others.sort(CodePointOrder.ORDER);
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
     * listed. The fold's own problems lead, and a fold that skipped anything is incomplete at best.
     *
     * <p>Absent rounds are named once, as the range before the round that follows them, and the chain restarts
     * there: that round is listed unverified, the ones after it verify against it. Absent files are named once
     * per run of seqs, so a conversation whose files did not land is one line, not one per file.
     */
    private Chain chain() {
        final Chain chain = new Chain();
        String prevDigest = "";
        String prevInput = "";
        long prevThrough = 0;
        long prevRound = 0;
        final List<long[]> missingFiles = new ArrayList<>();
        for (final RoundInput in : rounds) {
            if (in.error != null) {
                chain.mismatch("round " + in.number + ": " + in.error);
                continue;
            }
            final SessionFlowRound r = in.round;
            final SessionFlowRound.Header h = r.getHeader();
            boolean ok = true;
            if (h.getRound() != prevRound + 1) {
                // absent rounds: this one cannot link to what is not there, so the chain restarts at it
                final long from = prevRound + 1;
                final long through = h.getRound() - 1;
                chain.incomplete((from == through ? "round " + from + " is" : "rounds " + from + "-" + through + " are")
                                     + " missing before round " + h.getRound());
                ok = false;
                prevThrough = h.getFromSeq() - 1;
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
            // A round names a range of sequences, and a round that names an impossible one - or a chain
            // whose files are all gone - would otherwise cost one entry per absent sequence before they
            // are coalesced, and the counter itself would wrap at the end of the range.
            long missingFrom = 0;
            long missingTo = 0;
            for (long seq = h.getFromSeq(); seq <= h.getThroughSeq() && seq >= h.getFromSeq(); seq++) {
                final SessionDataFile f = files.get(seq);
                if (f == null) {
                    if (missingFrom == 0) {
                        missingFrom = seq;
                    } else if (seq != missingTo + 1) {
                        missingFiles.add(new long[] {h.getRound(), missingFrom, missingTo});
                        missingFrom = seq;
                    }
                    missingTo = seq;
                    ok = false;
                    continue;
                }
                added.add(f.getFileDigest());
            }
            if (missingFrom != 0) {
                missingFiles.add(new long[] {h.getRound(), missingFrom, missingTo});
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
        for (final String problem : missingFileProblems(missingFiles)) {
            chain.incomplete(problem);
        }
        if (!problems.isEmpty()) {
            chain.problems.addAll(0, problems);
            if (STATE_VERIFIED.equals(chain.state)) {
                chain.state = STATE_INCOMPLETE;
            }
        }
        return chain;
    }

    /**
     * @param missing one entry per run of absent seqs a round names, as the round and the run's first and last
     *                seq, in chain order
     * @return one problem per run of consecutive seqs, joined across rounds where they meet, worded as one file
     * when the run is one
     */
    private static List<String> missingFileProblems(final List<long[]> missing) {
        final List<String> out = new ArrayList<>();
        int i = 0;
        while (i < missing.size()) {
            int j = i;
            while (j + 1 < missing.size() && missing.get(j + 1)[1] == missing.get(j)[2] + 1) {
                j++;
            }
            final long firstRound = missing.get(i)[0];
            final long lastRound = missing.get(j)[0];
            final long firstSeq = missing.get(i)[1];
            final long lastSeq = missing.get(j)[2];
            out.add((firstRound == lastRound ? "round " + firstRound : "rounds " + firstRound + "-" + lastRound)
                        + (firstSeq == lastSeq ? ": landed file seq " + firstSeq + " is missing"
                            : ": landed files seq " + firstSeq + "-" + lastSeq + " are missing"));
            i = j + 1;
        }
        return out;
    }

    private List<Map<String, Object>> files() {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final SessionDataFile f : files.values()) {
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
        /** When the talk began, in nanoseconds, which is what talks are ordered by. */
        long began;
        boolean child;
        String segment = "";
        String reply = "";
        List<Ref> labelAt = Collections.emptyList();
        Ref replyAt;
        SessionFlowRound.Node node;
    }

    private static final class Overview {
        String title = "";
        Map<String, Integer> kinds;
        Map<String, Integer> relationTypes;
        Map<String, Integer> quality;
        List<TalkRow> talks;
        List<Map<String, Object>> streams;
        List<Map<String, Object>> segments;
    }

    private Overview overview() {
        final Overview o = new Overview();
        o.kinds = new TreeMap<>(CodePointOrder.ORDER);
        for (final SessionFlowRound.Node n : fold.getNodes().values()) {
            o.kinds.merge(nullToEmpty(n.getKind()), 1, Integer::sum);
            if ("session".equals(n.getKind())) {
                o.title = nullToEmpty(n.attr("title"));
            }
        }
        o.relationTypes = new TreeMap<>(CodePointOrder.ORDER);
        o.quality = new TreeMap<>(CodePointOrder.ORDER);
        for (final SessionFlowRound.Relation r : fold.getRelations().values()) {
            o.relationTypes.merge(nullToEmpty(r.getType()), 1, Integer::sum);
            o.quality.merge(nullToEmpty(r.getQuality()), 1, Integer::sum);
        }

        // a child agent's talk, and only that: an auxiliary stream is the same agent carrying a different prompt, not
        // a child, as the Sessionizer reads it
        final Set<String> childStreams = new HashSet<>();
        for (final SessionFlowRound.Node st : streamNodes()) {
            if ("child".equals(st.attr("role"))) {
                childStreams.add(st.getStream());
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
            row.child = childStreams.contains(t.getStream());
            final long[] span = span(t);
            row.from = span[0];
            row.to = span[1];
            row.began = beganAt(t);
            row.segment = inSegment.getOrDefault(t.getId(), "");
            walkCounts(t.getId(), row);
            row.labelAt = labelRefs(t);
            row.replyAt = replyRef(t);
            talks.add(row);
        }
        // Across streams the order is time, not landed position: a position orders records within one stream only,
        // and a child's file can land before its parent's. Every timed talk comes before every untimed one, timed
        // talks by time, and the sort is stable, so position decides among equals, as the Sessionizer orders them.
        talks.sort(Comparator.comparing((TalkRow t) -> t.began == 0).thenComparingLong(t -> t.began));
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
        // each stream's origins in the order their steps happened, so a stream with several candidate origins lists
        // them in one order on every read, the Sessionizer's; each stream's on their own, since one order over every
        // stream's origins is not the same: an untimed step holds back the rest of its lane
        final Map<String, List<SessionFlowRound.Relation>> startsOf = new HashMap<>();
        for (final SessionFlowRound.Relation r : fold.getRelations().values()) {
            // an origin whose step is in no stream is not listed, so it takes no part in the order either
            final SessionFlowRound.Node step = fold.node(r.getFrom());
            if ("starts".equals(r.getType()) && step != null && StringUtil.isNotEmpty(step.getStream())) {
                startsOf.computeIfAbsent(r.getTo(), k -> new ArrayList<>()).add(r);
            }
        }
        final Function<SessionFlowRound.Relation, Order.Point> stepPoint = r -> pointOf(fold.node(r.getFrom()).getRef());
        for (final Map.Entry<String, List<SessionFlowRound.Relation>> stream : startsOf.entrySet()) {
            for (final SessionFlowRound.Relation r : Order.inOrder(stream.getValue(), stepPoint, RELATION_TIE)) {
                final SessionFlowRound.Node n = fold.node(r.getFrom());
                parent.put(stream.getKey(), n.getStream());
                final Map<String, Object> origin = new LinkedHashMap<>();
                origin.put("step", r.getFrom());
                origin.put("stream", n.getStream());
                origin.put("talk", talkOf(r.getFrom()));
                origin.put("quality", nullToEmpty(r.getQuality()));
                openedBy.computeIfAbsent(stream.getKey(), x -> new ArrayList<>()).add(origin);
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
            m.put("records", (long) st.attrNumber("records"));
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
        for (final SessionDataFile f : files.values()) {
            if (StringUtil.isEmpty(f.getHeader().getBatch())) {
                continue;
            }
            for (final SessionDataFile.Record rec : f.getRecords()) {
                final String child = rec.child();
                if (StringUtil.isEmpty(child) || names.containsKey(child)) {
                    continue;
                }
                final String name = resultName(rec);
                if (StringUtil.isNotEmpty(name)) {
                    names.put(child, name);
                }
            }
        }
        return names;
    }

    /**
     * @param rec a record of a run's journal
     * @return the name the record's first result row gives the child it started: its surface, else its summary, else
     * its verdict and the first claim it refuted; empty when that row gives none; null when no candidate of the record
     * is a result row
     */
    @Nullable
    static String resultName(final SessionDataFile.Record rec) {
        for (final String raw : candidates(rec)) {
            final Map<String, Object> row = JOURNAL_ROW.read(Schema.parse(raw));
            if (row == null || !"result".equals(row.get("type"))) {
                continue;
            }
            final Map<?, ?> result = (Map<?, ?>) row.get("result");
            String name = (String) result.get("surface");
            if (name.isEmpty()) {
                name = (String) result.get("summary");
            }
            if (name.isEmpty() && !((String) result.get("verdict")).isEmpty()) {
                name = (String) result.get("verdict");
                final List<?> refuted = (List<?>) result.get("refuted_claims");
                final String claim = refuted == null || refuted.isEmpty() ? "" : (String) ((Map<?, ?>) refuted.get(0)).get("claim");
                if (!claim.isEmpty()) {
                    name += " · " + claim;
                }
            }
            return shortName(name);
        }
        return null;
    }

    /**
     * A name cut to what a label holds: its words joined by one space, clipped to 160 bytes, and marked when clipped.
     */
    private static String shortName(final String s) {
        final String joined = String.join(" ", fields(s));
        final String clipped = clipBytes(joined, 160);
        return clipped.length() < joined.length() ? clipped + "…" : joined;
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
        final JsonElement attrs = withoutProviderBodies(n.getRawAttrs());
        if (attrs != null) {
            // as the Sessionizer prints the raw attrs: an empty object stays {}, an explicit null stays null
            out.put("attrs", jsonToValue(attrs));
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
        final List<String> changes = changesByStep.get(n.getId());
        if (changes != null && !changes.isEmpty()) {
            out.put("changes", new ArrayList<>(changes));
        }
        final List<String> executions = executionsByStep.get(n.getId());
        if (executions != null && !executions.isEmpty()) {
            out.put("executions", new ArrayList<>(executions));
        }
        final List<Map<String, Object>> bodies = providerBodiesByStep.get(n.getId());
        if (bodies != null && !bodies.isEmpty()) {
            out.put("provider_bodies", bodies);
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
        // every relation touching the node, in the order the relations happened, as the Sessionizer lists them
        final List<Map<String, Object>> touching = new ArrayList<>();
        for (final SessionFlowRound.Relation r : fold.relationsFrom(n.getId())) {
            touching.add(edge(r, r.getTo(), "out"));
        }
        for (final SessionFlowRound.Relation r : fold.relationsTo(n.getId())) {
            touching.add(edge(r, r.getFrom(), "in"));
        }
        final List<Map<String, Object>> edges = Order.inOrder(
            touching, e -> relationPoint(fold.getRelations().get((String) e.get("id"))),
            Comparator.comparing((Map<String, Object> e) -> (String) e.get("id"), CodePointOrder.ORDER)
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
            // the whole parent chain, as the Sessionizer walks it; a malformed fold whose parents loop stops where it
            // comes back round
            final Set<String> walked = new HashSet<>();
            for (SessionFlowRound.Node cur = n; cur != null && walked.add(cur.getId());
                 cur = StringUtil.isEmpty(cur.getParent()) ? null : fold.node(cur.getParent())) {
                if ("talk".equals(cur.getKind())) {
                    covered = true;
                    break;
                }
                if ("run".equals(cur.getKind()) || isStep(cur.getKind())) {
                    top = cur;
                }
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
                             final SessionDataFile.Record rec, @Nullable final Long block) {
        final SessionDataFile.Part p = partAt(rec, block);
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
        if (!"llm.call".equals(n.getKind())) {
            return null;
        }
        // a reference with nothing in it names no record
        final Map<String, Object> attrs = USAGE_AT.read(n.getRawAttrs());
        if (attrs == null || attrs.get("usage_at") == null) {
            return null;
        }
        final SessionDataFile.Record at = record(Ref.of((Map<?, ?>) attrs.get("usage_at")));
        return at == null ? null : at.usage();
    }

    private static void fillResult(final Map<String, Object> tool, final SessionDataFile.Record rec,
                                   @Nullable final Long block) {
        SessionDataFile.Part p = null;
        if (block != null && block < rec.getParts().size()) {
            p = rec.getParts().get(block.intValue());
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
        final long durationMs = reportedDuration(rec);
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

    /**
     * @param rec a turn duration record
     * @return the milliseconds the runtime reported for the turn, from the first candidate that reports any; 0 when
     * none does
     */
    static long reportedDuration(final SessionDataFile.Record rec) {
        for (final String raw : candidates(rec)) {
            final Map<String, Object> probe = DURATION.read(Schema.parse(raw));
            if (probe != null && (Long) probe.get("durationMs") != 0) {
                return (Long) probe.get("durationMs");
            }
        }
        return 0;
    }

    /**
     * @return every relation of the fold, in the order they happened, each at the earliest record that supports it
     */
    private List<Map<String, Object>> relations() {
        final List<Map<String, Object>> out = new ArrayList<>();
        final List<SessionFlowRound.Relation> all = new ArrayList<>(fold.getRelations().values());
        for (final SessionFlowRound.Relation r : Order.inOrder(all, this::relationPoint, RELATION_TIE)) {
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
        sorted.sort(Comparator.comparing(SessionFlowRound.Unresolved::getId, CodePointOrder.ORDER));
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

    // ---------------------------------------------------------------- workspace changes and tool executions

    /**
     * Every workspace change record the session's files carry, each joined to its step. Two places hold them: a
     * result record of an editing tool carries the runtime's own patch as a data part beside the raw result, and
     * a <code>changes</code> file the plugin's adapter landed holds one record per line. Fills
     * {@link #changesByStep} on the way, which {@link #step} reads, so this runs before the nodes are rendered.
     *
     * @return the entries in the order the document lists them: by the instant each names, the runtime's record
     * before the plugin's for one call, then by where each was read
     */
    private List<Map<String, Object>> workspaceChanges() {
        final Map<String, String> stepOf = stepsByToolUse();
        final List<Joined<ChangesRecord>> entries = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final BiConsumer<Ref, String> collect = (ref, data) -> {
            final ChangesRecord r = ChangesRecord.decode(data);
            // the id is the tool-use id, so two producers observing one call share it; who captured the record
            // tells them apart, and a line landed twice by an interrupted pass is the same on both counts
            if (r != null && seen.add(r.getCapturedBy() + "|" + r.getId())) {
                entries.add(new Joined<>(r, stepOf.get(r.getTool()), ref));
            }
        };
        // the runtime's own patches, on the result records a tool step reads after its call
        for (final SessionFlowRound.Node n : fold.getNodes().values()) {
            if (!"tool".equals(n.getKind())) {
                continue;
            }
            for (int i = 1; i < n.getRefs().size(); i++) {
                final Ref ref = n.getRefs().get(i);
                forEachDataPart(ref.getSeq(), record(ref), collect);
            }
        }
        // the plugin's records, from the files its adapter landed
        for (final SessionDataFile f : filesOfKind("changes")) {
            for (final SessionDataFile.Record rec : f.getRecords()) {
                forEachDataPart(f.getHeader().getSeq(), rec, collect);
            }
        }
        entries.sort((a, b) -> {
            final int byTime = Times.compare(a.record.getTime(), b.record.getTime());
            if (byTime != 0) {
                return byTime;
            }
            // the runtime's record is listed first, and a viewer showing one prefers it
            final int byProducer = Boolean.compare(!ChangesRecord.CAPTURED_BY_CLAUDE_CODE.equals(a.record.getCapturedBy()),
                                                   !ChangesRecord.CAPTURED_BY_CLAUDE_CODE.equals(b.record.getCapturedBy()));
            return byProducer != 0 ? byProducer : Order.comparePositions(pointOf(a.ref), pointOf(b.ref));
        });
        return listed(entries, changesByStep);
    }

    /**
     * Every tool execution record the session's <code>execution</code> files carry, each joined to the step of the
     * call it observed: what an observer around the call saw it do, such as which MCP server ran it, how it ended and
     * how long it took. One call can have several records, one per observation, so a step lists them all. Fills
     * {@link #executionsByStep} on the way, which {@link #step} reads, so this runs before the nodes are rendered.
     *
     * @return the entries in the order the document lists them: by the instant each names, then by where each was read
     */
    private List<Map<String, Object>> toolExecutions() {
        final Map<String, String> stepOf = stepsByToolUse();
        final List<Joined<ExecutionRecord>> entries = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final BiConsumer<Ref, String> collect = (ref, data) -> {
            final ExecutionRecord r = ExecutionRecord.decode(data);
            // kept once by its own id: the same line landed twice, by a hook handed its event again or by an
            // interrupted pass, is one record
            if (r != null && seen.add(r.getId())) {
                entries.add(new Joined<>(r, stepOf.get(r.getTool()), ref));
            }
        };
        for (final SessionDataFile f : filesOfKind("execution")) {
            for (final SessionDataFile.Record rec : f.getRecords()) {
                forEachDataPart(f.getHeader().getSeq(), rec, collect);
            }
        }
        entries.sort((a, b) -> {
            final int byTime = Times.compare(a.record.getTime(), b.record.getTime());
            return byTime != 0 ? byTime : Order.comparePositions(pointOf(a.ref), pointOf(b.ref));
        });
        return listed(entries, executionsByStep);
    }

    /**
     * A record about a tool call, the step of that call, and where the record was read. The join is the tool-use id
     * the record names, which the step's call part carries; nothing is matched by name or by time. A record whose
     * call is not a step of the document is kept with no step.
     */
    private static final class Joined<R extends ToolCallRecord> {
        final R record;
        final String step;
        final Ref ref;

        Joined(final R record, @Nullable final String step, final Ref ref) {
            this.record = record;
            this.step = nullToEmpty(step);
            this.ref = ref;
        }
    }

    /**
     * @param byStep filled with each step's record ids, in the order of the entries
     * @return each entry as the document lists it: its step, its reference, then the record's own fields
     */
    private static <R extends ToolCallRecord> List<Map<String, Object>> listed(
        final List<Joined<R>> entries, final Map<String, List<String>> byStep) {
        final List<Map<String, Object>> out = new ArrayList<>(entries.size());
        for (final Joined<R> e : entries) {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("step", e.step);
            m.put("ref", e.ref.toMap());
            m.putAll(e.record.fields());
            out.add(m);
            if (!e.step.isEmpty()) {
                byStep.computeIfAbsent(e.step, k -> new ArrayList<>()).add(e.record.getId());
            }
        }
        return out;
    }

    /** Hands each data part of a record to a consumer, with the reference that names the part. */
    private static void forEachDataPart(final long seq, @Nullable final SessionDataFile.Record rec,
                                        final BiConsumer<Ref, String> consumer) {
        if (rec == null) {
            return;
        }
        for (int b = 0; b < rec.getParts().size(); b++) {
            final SessionDataFile.Part p = rec.getParts().get(b);
            if ("data".equals(p.getKind())) {
                consumer.accept(new Ref(seq, rec.getRow(), (long) b), p.data());
            }
        }
    }

    private List<SessionDataFile> filesOfKind(final String kind) {
        final List<SessionDataFile> out = new ArrayList<>();
        for (final SessionDataFile f : files.values()) {
            if (kind.equals(f.getHeader().getKind())) {
                out.add(f);
            }
        }
        return out;
    }

    /**
     * Which step carries which tool-use id, from the call part each tool or agent step points at. A change record and
     * an execution record name their call by that id, and it is their only join.
     */
    private Map<String, String> stepsByToolUse() {
        final Map<String, String> stepOf = new HashMap<>();
        for (final SessionFlowRound.Node n : fold.getNodes().values()) {
            if (!"tool".equals(n.getKind()) && !"agent.call".equals(n.getKind()) || n.getRef() == null) {
                continue;
            }
            final SessionDataFile.Record rec = record(n.getRef());
            final SessionDataFile.Part p = rec == null ? null : partAt(rec, n.getRef().getBlock());
            if (p != null && "call".equals(p.getKind()) && StringUtil.isNotEmpty(p.getId())) {
                stepOf.put(p.getId(), n.getId());
            }
        }
        return stepOf;
    }

    // ---------------------------------------------------------------- provider bodies

    /**
     * A node's attributes without the provider bodies a call carries. The document gives them a field of their own,
     * <code>provider_bodies</code> on the step, so leaving them in the attributes as well would say the same thing
     * twice. Every other attribute travels as the round wrote it. When the bodies are taken out, what is left is
     * written with its keys in code point order, and nothing is left when nothing else was there.
     */
    @Nullable
    private static JsonElement withoutProviderBodies(@Nullable final JsonElement raw) {
        if (raw == null || !raw.isJsonObject() || !raw.getAsJsonObject().has(SessionFlowRound.PROVIDER_BODIES_ATTR)) {
            return raw;
        }
        final TreeMap<String, JsonElement> sorted = new TreeMap<>(CodePointOrder.ORDER);
        for (final Map.Entry<String, JsonElement> e : raw.getAsJsonObject().entrySet()) {
            if (!SessionFlowRound.PROVIDER_BODIES_ATTR.equals(e.getKey())) {
                sorted.put(e.getKey(), e.getValue());
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }
        final JsonObject out = new JsonObject();
        sorted.forEach(out::add);
        return out;
    }

    /**
     * Each call's provider bodies, as the round carries them. A body is the request or the response a runtime
     * exchanged with its model provider. The document names where each joined body landed and never carries the
     * body; a reader loads the files up to that seq and rebuilds it. Fills {@link #providerBodiesByStep} on the way,
     * which {@link #step} reads, so this runs before the nodes are rendered.
     *
     * <p>The join is not made here. The Sessionizer makes it once, when it parses the round, and the round carries it
     * on each <code>llm.call</code>: a body joins by identifiers only its manifest holds, and a reader that made the
     * join itself would open every landed body, the largest files a session holds, to read one line of each. A
     * round whose bodies do not read is refused when it is read, so what reaches here is the shape or nothing.
     *
     * @return how many requests are joined, which is how many calls have their prompt captured
     */
    private int joinProviderBodies() {
        int requests = 0;
        for (final SessionFlowRound.Node n : fold.getNodes().values()) {
            if (!"llm.call".equals(n.getKind())) {
                continue;
            }
            for (final SessionFlowRound.ProviderBody y : n.getProviderBodies()) {
                final Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", y.getRole());
                m.put("ref", y.getRef().toMap());
                providerBodiesByStep.computeIfAbsent(n.getId(), k -> new ArrayList<>()).add(m);
                if (SessionFlowRound.ROLE_REQUEST.equals(y.getRole())) {
                    requests++;
                }
            }
        }
        return requests;
    }

    /**
     * @return how many bodies the session holds as of the folded chain, joined or not, as its session node states it;
     * 0 when the node does not, or states it as something other than a whole number
     */
    private long landedProviderBodies() {
        final SessionFlowRound.Node session = fold.node(sessionNodeId());
        final Map<String, Object> attrs = session == null ? null : SESSION_ATTRS.read(session.getRawAttrs());
        return attrs == null ? 0 : (Long) attrs.get("provider_bodies_landed");
    }

    /**
     * @param rec   a record
     * @param block the part a reference names, or null
     * @return the part named, or the only part when none is named, or null
     */
    @Nullable
    private static SessionDataFile.Part partAt(final SessionDataFile.Record rec, @Nullable final Long block) {
        if (block != null && block < rec.getParts().size()) {
            return rec.getParts().get(block.intValue());
        }
        return rec.getParts().size() == 1 ? rec.getParts().get(0) : null;
    }

    // ---------------------------------------------------------------- records and times

    /** Relations one record supports are listed by id, as nothing else tells them apart. */
    private static final Comparator<SessionFlowRound.Relation> RELATION_TIE =
        Comparator.comparing(SessionFlowRound.Relation::getId, CodePointOrder.ORDER);

    /** Where a reference sits: its file's lane, its position, and its record's time. */
    private Order.Point pointOf(@Nullable final Ref r) {
        if (r == null || r.getSeq() == 0) {
            return Order.Point.NOWHERE;
        }
        return new Order.Point(lanes.getOrDefault(r.getSeq(), ""), r.getSeq(), r.getRow(),
                               r.getBlock() == null ? -1 : r.getBlock(), nanosAt(r));
    }

    /** Where a relation happened: at the earliest record that supports it. */
    private Order.Point relationPoint(@Nullable final SessionFlowRound.Relation r) {
        if (r == null) {
            return Order.Point.NOWHERE;
        }
        final List<Order.Point> points = new ArrayList<>();
        for (final Ref e : r.getEvidence()) {
            points.add(pointOf(e));
        }
        return Order.earliest(points);
    }

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

    /**
     * The earliest record time under a node, in nanoseconds: talks are ordered at the precision the Sessionizer
     * keeps, so two that began in the same millisecond keep their order. 0 when nothing under it is timed.
     */
    private long beganAt(final SessionFlowRound.Node x) {
        long lo = nanosAt(x.getRef());
        for (final SessionFlowRound.Node k : fold.children(x.getId())) {
            final long t = beganAt(k);
            if (t != 0 && (lo == 0 || t < lo)) {
                lo = t;
            }
        }
        return lo;
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
            final String prompt = queuedPrompt(raw);
            if (prompt != null) {
                return prompt;
            }
        }
        return "";
    }

    /**
     * @param raw one candidate of a record
     * @return the texts of the prompt a queued command envelope carries, one per line and trimmed, or null when the
     * candidate is not one or carries no text
     */
    @Nullable
    private static String queuedPrompt(final String raw) {
        final Map<String, Object> envelope = QUEUED_COMMAND.read(Schema.parse(raw));
        if (envelope == null || !"queued_command".equals(envelope.get("type")) || envelope.get("prompt") == null) {
            return null;
        }
        final List<String> out = new ArrayList<>();
        for (final Object p : (List<?>) envelope.get("prompt")) {
            final String text = (String) ((Map<?, ?>) p).get("text");
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out.isEmpty() ? null : trimSpace(String.join("\n", out));
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
