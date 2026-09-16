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

package org.apache.skywalking.oap.server.ai.agent.conversation.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.agent.conversation.AIAgentConversationConfig;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.FileNames;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Times;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRow;
import org.apache.skywalking.oap.server.ai.agent.conversation.view.ConversationViewBuilder;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IAIAgentConversationQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * The read side. A conversation is read in two storage passes and never a read per file: its rounds by
 * <code>conversation</code> over the whole retention window, then, for each session the head round names, the
 * files by series id over the range the head round carries, in seq windows that keep one storage response under
 * the BanyanDB client's inbound cap. The fold, the chain check and the view are built on every call, using
 * only the storage stages the caller selected.
 */
@Slf4j
public class ConversationQueryService implements IConversationQueryService {
    private static final int DEFAULT_LIST_LIMIT = 1000;

    private final ModuleManager moduleManager;
    private final AIAgentConversationConfig config;
    private IAIAgentConversationQueryDAO dao;

    public ConversationQueryService(final ModuleManager moduleManager, final AIAgentConversationConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;
    }

    private IAIAgentConversationQueryDAO dao() {
        if (dao == null) {
            dao = moduleManager.find(StorageModule.NAME).provider().getService(IAIAgentConversationQueryDAO.class);
        }
        return dao;
    }

    @Override
    public ConversationList listConversations(final String serviceId,
                                              @Nullable final String serviceInstanceId,
                                              @Nullable final String conversation,
                                              @Nullable final String title,
                                              final Duration duration,
                                              @Nullable final Integer limit) throws IOException {
        final int rounds = Math.min(
            limit == null || limit <= 0 ? DEFAULT_LIST_LIMIT : limit, config.getConversationListMaxLimit());
        final List<AIAgentSessionFlowRecord> newestFirst = dao().queryRoundsDebuggable(
            serviceId, serviceInstanceId, StringUtil.isEmpty(conversation) ? null : conversation.trim(),
            duration, rounds, false);
        final Map<String, ConversationRow> rows = new LinkedHashMap<>();
        for (final AIAgentSessionFlowRecord r : newestFirst) {
            final ConversationRow existing = rows.get(r.getConversation());
            if (existing != null && existing.getRound() >= r.getRound()) {
                continue;
            }
            rows.put(r.getConversation(), row(r));
        }
        // The title is the newest round's, so the match runs on the folded row: a round budget spent on
        // a conversation stays spent whether or not its title matches.
        final String needle = StringUtil.isEmpty(title) ? null : title.trim().toLowerCase(Locale.ROOT);
        final ConversationList list = new ConversationList();
        final List<ConversationRow> out = new ArrayList<>(rows.size());
        for (final ConversationRow row : rows.values()) {
            if (needle != null && !needle.isEmpty()
                && (row.getTitle() == null || !row.getTitle().toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            out.add(row);
        }
        list.setConversations(out);
        return list;
    }

    private static ConversationRow row(final AIAgentSessionFlowRecord r) {
        final ConversationRow row = new ConversationRow();
        row.setConversation(r.getConversation());
        row.setServiceInstanceId(r.getServiceInstanceId());
        row.setServiceInstanceName(instanceName(r.getServiceInstanceId()));
        row.setTitle(r.getTitle());
        row.setRound((int) r.getRound());
        row.setTalks((int) r.getTalks());
        row.setSteps((int) r.getSteps());
        row.setStreams((int) r.getStreams());
        row.setSegments((int) r.getSegments());
        row.setUnresolved((int) r.getUnresolved());
        row.setChanges(r.getChanges() == null ? null : r.getChanges().intValue());
        row.setLinesAdded(r.getLinesAdded() == null ? null : r.getLinesAdded().intValue());
        row.setLinesRemoved(r.getLinesRemoved() == null ? null : r.getLinesRemoved().intValue());
        row.setLlmCalls(r.getLlmCalls() == null ? null : r.getLlmCalls().intValue());
        row.setSubagents(r.getSubagents() == null ? null : r.getSubagents().intValue());
        row.setBashRuns(r.getBashRuns() == null ? null : r.getBashRuns().intValue());
        row.setFrom(r.getSessionFromTime());
        row.setTo(r.getTimestamp());
        return row;
    }

    private static String instanceName(final String instanceId) {
        if (StringUtil.isEmpty(instanceId)) {
            return "";
        }
        try {
            return IDManager.ServiceInstanceID.analysisId(instanceId).getName();
        } catch (final RuntimeException e) {
            return instanceId;
        }
    }

    @Override
    @Nullable
    public Map<String, Object> buildConversationView(final String serviceId,
                                                     @Nullable final String serviceInstanceId,
                                                     final String conversation, final boolean coldStage,
                                                     final BooleanSupplier alive) throws IOException {
        final Chain chain = readChain(serviceId, serviceInstanceId, conversation, coldStage, alive);
        if (chain.roundInputs.isEmpty()) {
            return null;
        }
        return new ConversationViewBuilder(chain.fold, chain.roundInputs, chain.files, chain.problems).build();
    }

    @Override
    public boolean readConversationFiles(final String serviceId, final String serviceInstanceId,
                                         final String conversation, final String session,
                                         final Collection<Long> seqs, final boolean coldStage,
                                         final BooleanSupplier alive, final FileSink sink) throws IOException {
        final long headRound = dao().queryHeadRoundDebuggable(serviceId, serviceInstanceId, conversation, coldStage);
        if (headRound == 0) {
            return false;
        }
        if (seqs.isEmpty()) {
            return true;
        }
        final long[] range = fileRange(serviceId, serviceInstanceId, conversation, headRound, alive, coldStage);
        final int window = config.getReadWindow();
        for (final long[] run : runs(new TreeSet<>(seqs))) {
            long last = -1;
            for (final long[] w : windows(run[0], run[1], window)) {
                if (!alive.getAsBoolean()) {
                    throw new IOException("the caller of session " + session + " is gone");
                }
                // one window read, sorted and handed on before the next is read, so no more than one window of
                // bodies is held
                final List<AIAgentSessionDataRecord> read = new ArrayList<>(dao().queryFilesDebuggable(
                    serviceId, serviceInstanceId, session, range[0], range[1], w[0], w[1],
                    config.getMaxResponseBytes(), coldStage));
                read.sort(Comparator.comparingLong(AIAgentSessionDataRecord::getSeq));
                // A sequence normally has one file. It has more where the same sequence was stored with
                // different bytes, and then the first is served and the count says the others are there:
                // a reader that showed one copy as the whole truth would be wrong without knowing it.
                final Map<Long, Integer> copies = new HashMap<>();
                for (final AIAgentSessionDataRecord f : read) {
                    if (f.getSeq() >= w[0] && f.getSeq() <= w[1]) {
                        copies.merge(f.getSeq(), 1, Integer::sum);
                    }
                }
                for (final AIAgentSessionDataRecord f : read) {
                    if (f.getSeq() < w[0] || f.getSeq() > w[1] || f.getSeq() == last) {
                        continue;
                    }
                    last = f.getSeq();
                    sink.accept(new ConversationFile(
                        dataFileId(f.getBody(), session, f.getSeq()), f.getSeq(), f.getDigest(), f.getBody(),
                        copies.getOrDefault(f.getSeq(), 1)));
                }
            }
        }
        return true;
    }

    /**
     * The time range the conversation's files are stamped in: the newest intact round's, from its session's first
     * activity to its last or its own row's time, whichever is later, read one round at a time down from the head, so
     * only the rounds above it are read. The view takes its range from the last round it folds, which is this round
     * unless the fold refused it. A conversation with no intact round is read over everything up to the head row's own
     * time, and so is one whose head round and the fifteen below it are all unreadable.
     *
     * @return the first and the last millisecond
     */
    private long[] fileRange(final String serviceId, final String serviceInstanceId, final String conversation,
                             final long headRound, final BooleanSupplier alive, final boolean coldStage)
        throws IOException {
        long headRowTimestamp = 0;
        // One round at a time, down from the head, which answers in a single read for every conversation
        // whose head round is intact - and that is all of them until one is damaged. A window would read
        // sixteen round bodies to find one, and a round is cut at 2 MiB, so the read a healthy conversation
        // pays would grow by that much for nothing.
        //
        // The walk stops after ROUNDS_SEARCHED rounds. Its purpose is to find any round that carries the
        // session's time range; if that many consecutive rounds from the head are unreadable, the chain is
        // damaged far past what one more read would fix, and the head row's own time is the answer. Walking
        // to round 1 instead cost one storage read per round - a hundred thousand of them on a long chain,
        // for one request, and they all ran on after the caller had gone.
        final long floor = Math.max(1, headRound - ROUNDS_SEARCHED + 1);
        for (long round = headRound; round >= floor; round--) {
            if (!alive.getAsBoolean()) {
                throw new IOException("the caller of conversation " + conversation + " is gone");
            }
            for (final AIAgentSessionFlowRecord r : dao().queryRoundsByNumberDebuggable(
                serviceId, serviceInstanceId, conversation, round, round, config.getMaxResponseBytes(), coldStage)) {
                if (r.getRound() != round) {
                    continue;
                }
                if (headRowTimestamp == 0) {
                    // the head row's own time bounds the range only when no round is intact
                    headRowTimestamp = r.getTimestamp();
                }
                final SessionFlowRound parsed;
                try {
                    parsed = SessionFlowRound.parse(r.getBody());
                } catch (final RuntimeException e) {
                    continue;
                }
                if (parsed.isIntact()) {
                    final SessionFlowRound.Header h = parsed.getHeader();
                    // the intact round's own row, as the view takes its range from the last round it folds
                    return new long[] {
                        Times.millis(h.getSessionFromTime()), rangeEnd(h.getSessionThroughTime(), r.getTimestamp())};
                }
            }
        }
        return new long[] {0, headRowTimestamp};
    }

    /**
     * How many rounds down from the head a file read looks for one that carries the session's time range.
     * One is enough unless the head is damaged; past this many the chain is broken, not merely dented.
     */
    private static final int ROUNDS_SEARCHED = 16;

    /**
     * @return the file's name from its own header line, or one built from its session and seq when the header does
     * not read
     */
    private static String dataFileId(final byte[] body, final String session, final long seq) {
        try {
            return FileNames.dataFile(SessionDataFile.header(body));
        } catch (final RuntimeException e) {
            return session + "/unknown-" + String.format(Locale.ROOT, "%06d", seq) + ".sd";
        }
    }

    /**
     * @param numbers ascending numbers
     * @return each run of consecutive numbers as its first and last
     */
    static List<long[]> runs(final Iterable<Long> numbers) {
        final List<long[]> out = new ArrayList<>();
        long[] run = null;
        for (final long n : numbers) {
            if (run != null && run[1] != Long.MAX_VALUE && n == run[1] + 1) {
                run[1] = n;
                continue;
            }
            run = new long[] {n, n};
            out.add(run);
        }
        return out;
    }

    /**
     * @return the windows of at most <code>size</code> numbers that cover first through last, one at a time as they
     * are iterated, so no bound overflows and no list of them is built, even for a window a round claims up to the
     * largest number
     */
    static Iterable<long[]> windows(final long first, final long last, final int size) {
        return () -> new Iterator<long[]>() {
            private long start = first;
            private boolean done = first > last;

            @Override
            public boolean hasNext() {
                return !done;
            }

            @Override
            public long[] next() {
                if (done) {
                    throw new NoSuchElementException();
                }
                final long end = last - start < size - 1L ? last : start + size - 1L;
                final long[] w = {start, end};
                if (end >= last) {
                    done = true;
                } else {
                    start = end + 1;
                }
                return w;
            }
        };
    }

    // ---------------------------------------------------------------- the two-pass read

    private static final class Chain {
        final ConversationFold fold = new ConversationFold();
        /** Every stored round in number order, readable or not, for the document's listing. */
        final List<ConversationViewBuilder.RoundInput> roundInputs = new ArrayList<>();
        final Map<Long, SessionDataFile> files = new TreeMap<>();
        /** What stopped the fold short of the chain's last round, in words, as the Sessionizer's FoldPartial. */
        final List<String> problems = new ArrayList<>();
    }

    /**
     * Every round of a conversation, in round order, read window by window up to the head: a round is up to
     * 2 MiB and a long conversation has hundreds, so one read of them all would not fit a storage response.
     * The head is the highest round number stored, read directly and fixed first, so a round landing during the
     * read is left for the next call; it is not the newest row by time, because the Sessionizer can write a
     * later round that carries no later activity, over metadata or older records. A round stored more than once, by two
     * senders or by a redelivery, is kept once, the first copy.
     */
    private List<AIAgentSessionFlowRecord> readRounds(final String serviceId, @Nullable final String instance,
                                                      final String conversation, final boolean coldStage,
                                                      final BooleanSupplier alive) throws IOException {
        final long headRound = dao().queryHeadRoundDebuggable(serviceId, instance, conversation, coldStage);
        if (headRound == 0) {
            return new ArrayList<>();
        }
        final Map<Long, AIAgentSessionFlowRecord> byRound = new TreeMap<>();
        final int window = config.getReadWindow();
        for (long start = 1; start <= headRound; start += window) {
            if (!alive.getAsBoolean()) {
                throw new IOException("the caller of conversation " + conversation + " is gone");
            }
            final long end = Math.min(headRound, start + window - 1);
            for (final AIAgentSessionFlowRecord r : dao().queryRoundsByNumberDebuggable(
                serviceId, instance, conversation, start, end, config.getMaxResponseBytes(), coldStage)) {
                byRound.putIfAbsent(r.getRound(), r);
            }
        }
        return new ArrayList<>(byRound.values());
    }

    /**
     * The fold covers every stored round that reads and folds. A gap, a round that does not read, or one the fold
     * refuses is skipped and the chain resumes at the next stored round, so the document shows as much as landed;
     * what was skipped is carried in words, and the view builder names the gaps among the rounds it lists.
     * Every stored round is listed, readable or not.
     */
    private Chain readChain(final String serviceId, @Nullable final String serviceInstanceId,
                            final String conversation, final boolean coldStage, final BooleanSupplier alive)
        throws IOException {
        final Chain chain = new Chain();
        final List<AIAgentSessionFlowRecord> rounds = readRounds(serviceId, serviceInstanceId, conversation, coldStage, alive);
        long throughSeq = 0;
        AIAgentSessionFlowRecord headRow = null;
        for (final AIAgentSessionFlowRecord r : rounds) {
            SessionFlowRound parsed = null;
            String unreadable = null;
            try {
                parsed = SessionFlowRound.parse(r.getBody());
                if (!parsed.isIntact()) {
                    unreadable = "sessionflow: digest mismatch: computed " + first12(parsed.getComputedDigest())
                        + ", round claims " + first12(parsed.getCommitDigest());
                }
            } catch (final RuntimeException e) {
                unreadable = "sessionflow: " + e.getMessage();
            }
            if (unreadable != null) {
                chain.roundInputs.add(ConversationViewBuilder.RoundInput.unreadable(r.getRound(), unreadable));
                chain.problems.add("round " + r.getRound() + " does not read: " + unreadable);
                continue;
            }
            chain.roundInputs.add(new ConversationViewBuilder.RoundInput(parsed, r.getDigest()));
            throughSeq = Math.max(throughSeq, parsed.getHeader().getThroughSeq());
            final String refused = parsed.getHeader().getRound() == chain.fold.getRound() + 1
                ? chain.fold.apply(parsed) : chain.fold.applyAfterGap(parsed);
            if (refused != null) {
                chain.problems.add("round " + r.getRound() + " does not fold: " + refused);
                continue;
            }
            headRow = r;
        }
        if (headRow == null) {
            return chain;
        }
        // every landed file of the conversation's sessions, as far as any listed round reaches
        final long from = Times.millis(chain.fold.getSessionFromTime());
        final long to = rangeEnd(chain.fold.getSessionThroughTime(), headRow.getTimestamp());
        log.debug("files of conversation {} read over {}..{} for seq 1..{}, from the range of round {}",
                  conversation, from, to, throughSeq, chain.fold.getRound());
        final Set<String> sessions = new LinkedHashSet<>();
        if (StringUtil.isNotEmpty(chain.fold.getSession())) {
            sessions.add(chain.fold.getSession());
        }
        for (final SessionFlowRound.Node n : chain.fold.nodesOfKind("session")) {
            final String id = n.getId();
            sessions.add(id.startsWith("session/") ? id.substring("session/".length()) : id);
        }
        for (final String session : sessions) {
            for (final AIAgentSessionDataRecord f : readFiles(serviceId, serviceInstanceId, session, from, to, 1, throughSeq, coldStage, alive)) {
                if (chain.files.containsKey(f.getSeq())) {
                    // the same file under two senders; the chain check judges the copy that was kept
                    continue;
                }
                try {
                    chain.files.put(f.getSeq(), SessionDataFile.parse(f.getBody()));
                } catch (final RuntimeException e) {
                    // as the Sessionizer's reader, a file that does not decode contributes no records and is
                    // named by the chain check as missing
                    chain.problems.add("file seq " + f.getSeq() + " of session " + session + " cannot be decoded: "
                                           + e.getMessage());
                }
            }
        }
        return chain;
    }

    private static String first12(@Nullable final String s) {
        return s == null ? "" : s.substring(0, Math.min(12, s.length()));
    }

    private List<AIAgentSessionDataRecord> readFiles(final String serviceId, @Nullable final String instance,
                                                     final String session, final long from, final long to,
                                                     final long fromSeq, final long throughSeq,
                                                     final boolean coldStage, final BooleanSupplier alive)
        throws IOException {
        final List<AIAgentSessionDataRecord> out = new ArrayList<>();
        if (fromSeq > throughSeq) {
            return out;
        }
        for (final long[] w : windows(fromSeq, throughSeq, config.getReadWindow())) {
            if (!alive.getAsBoolean()) {
                throw new IOException("the caller of session " + session + " is gone");
            }
            out.addAll(dao().queryFilesDebuggable(
                serviceId, instance, session, from, to, w[0], w[1], config.getMaxResponseBytes(), coldStage));
        }
        return out;
    }

    /**
     * The end of the files read: the conversation's last activity as of the head round, or the head row's own
     * timestamp when the header carries none. A file is stamped at or before that moment.
     */
    private static long rangeEnd(@Nullable final String sessionThroughTime, final long headRowTimestamp) {
        final long through = Times.millis(sessionThroughTime);
        return Math.max(through, headRowTimestamp);
    }
}
