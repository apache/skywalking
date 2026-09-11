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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.agent.conversation.AIAgentConversationConfig;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.FileNames;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Times;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationFileFormat;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRawFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRawFiles;
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
 * the BanyanDB client's inbound cap. The fold, the chain check and the view are built once per head digest and
 * cached.
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
            limit == null || limit <= 0 ? DEFAULT_LIST_LIMIT : limit, config.getMaxListLimit());
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
                                                     final String conversation) throws IOException {
        final List<AIAgentSessionFlowRecord> heads =
            dao().queryRoundsDebuggable(serviceId, serviceInstanceId, conversation, null, 1, false);
        if (heads.isEmpty()) {
            return null;
        }
        final Chain chain = readChain(serviceId, serviceInstanceId, conversation);
        return new ConversationViewBuilder(chain.fold, chain.roundInputs, chain.files, chain.problems).build();
    }

    @Override
    public ConversationRawFiles getConversationRawFiles(final String serviceId,
                                                        @Nullable final String serviceInstanceId,
                                                        final String conversation,
                                                        @Nullable final List<String> files,
                                                        final boolean includeBody) throws IOException {
        final Set<FileNames.Parsed> wanted = new LinkedHashSet<>();
        if (files != null) {
            for (final String id : files) {
                final FileNames.Parsed p = FileNames.parse(id);
                if (p != null) {
                    wanted.add(p);
                }
            }
        }
        final List<AIAgentSessionFlowRecord> rounds = readRounds(serviceId, serviceInstanceId, conversation);
        final ConversationRawFiles out = new ConversationRawFiles();
        if (rounds.isEmpty()) {
            out.setErrorReason("no round of conversation " + conversation + " is stored for this service");
            return out;
        }
        final AIAgentSessionFlowRecord headRow = rounds.get(rounds.size() - 1);
        // the newest round that reads names the session and the range; one that does not read is still exported
        // below, as stored, and must not block the export of what does
        SessionFlowRound.Header head = null;
        for (int i = rounds.size() - 1; i >= 0 && head == null; i--) {
            try {
                head = SessionFlowRound.parse(rounds.get(i).getBody()).getHeader();
            } catch (final RuntimeException e) {
                log.debug("round {} of conversation {} does not read: {}", rounds.get(i).getRound(), conversation,
                          e.getMessage());
            }
        }
        // the caller's sender, or every sender of the service: a Sessionizer renamed between pushes leaves a
        // conversation's files under two instances, and a read must see both
        final String instance = StringUtil.isNotEmpty(serviceInstanceId) ? serviceInstanceId : null;
        final long from = head == null ? 0 : Times.millis(head.getSessionFromTime());
        final long to = rangeEnd(head == null ? null : head.getSessionThroughTime(), headRow.getTimestamp());

        // Session Data files first, then the rounds, each list in its own order.
        final Set<String> sessions = new LinkedHashSet<>();
        if (head != null && StringUtil.isNotEmpty(head.getSession())) {
            sessions.add(head.getSession());
        }
        for (final FileNames.Parsed p : wanted) {
            if (p.isDataFile()) {
                sessions.add(p.getSession());
            }
        }
        for (final String session : sessions) {
            final Set<Long> seqs = new HashSet<>();
            boolean all = files == null;
            for (final FileNames.Parsed p : wanted) {
                if (p.isDataFile() && session.equals(p.getSession())) {
                    seqs.add(p.getSeq());
                }
            }
            if (files != null && seqs.isEmpty()) {
                continue;
            }
            final long throughSeq = all ? (head == null ? 0 : head.getThroughSeq())
                : seqs.stream().mapToLong(Long::longValue).max().orElse(0);
            final long fromSeq = all ? 1 : seqs.stream().mapToLong(Long::longValue).min().orElse(1);
            final Set<Long> seen = new HashSet<>();
            for (final AIAgentSessionDataRecord f : readFiles(serviceId, instance, session, from, to, fromSeq, throughSeq)) {
                if (!all && !seqs.contains(f.getSeq()) || !seen.add(f.getSeq())) {
                    continue;
                }
                final SessionDataFile parsed = SessionDataFile.parse(f.getBody());
                final ConversationRawFile raw = new ConversationRawFile();
                raw.setId(FileNames.dataFile(parsed.getHeader()));
                raw.setFormat(ConversationFileFormat.SD);
                raw.setSession(session);
                raw.setSeq((int) f.getSeq());
                raw.setDigest(f.getDigest());
                raw.setBytes(f.getBody().length);
                raw.setTimestamp(f.getTimestamp());
                if (includeBody) {
                    raw.setBody(new String(f.getBody(), StandardCharsets.UTF_8));
                }
                out.getFiles().add(raw);
            }
        }
        for (final AIAgentSessionFlowRecord r : rounds) {
            if (files != null) {
                boolean named = false;
                for (final FileNames.Parsed p : wanted) {
                    if (!p.isDataFile() && p.getRound() == r.getRound()) {
                        named = true;
                        break;
                    }
                }
                if (!named) {
                    continue;
                }
            }
            String commit;
            try {
                commit = SessionFlowRound.parse(r.getBody()).getCommitDigest();
            } catch (final RuntimeException e) {
                // a round that does not read has no commit digest to be named by; the digest of its file names it
                commit = r.getDigest();
            }
            final ConversationRawFile raw = new ConversationRawFile();
            raw.setId(FileNames.roundFile(conversation, r.getRound(), commit));
            raw.setFormat(ConversationFileFormat.SF);
            raw.setRound((int) r.getRound());
            raw.setDigest(r.getDigest());
            raw.setBytes(r.getBody().length);
            raw.setTimestamp(r.getTimestamp());
            if (includeBody) {
                raw.setBody(new String(r.getBody(), StandardCharsets.UTF_8));
            }
            out.getFiles().add(raw);
        }
        return out;
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
                                                      final String conversation) throws IOException {
        final long headRound = dao().queryHeadRoundDebuggable(serviceId, instance, conversation);
        if (headRound == 0) {
            return new ArrayList<>();
        }
        final Map<Long, AIAgentSessionFlowRecord> byRound = new TreeMap<>();
        final int window = config.getRoundReadWindow();
        for (long start = 1; start <= headRound; start += window) {
            final long end = Math.min(headRound, start + window - 1);
            for (final AIAgentSessionFlowRecord r : dao().queryRoundsByNumberDebuggable(
                serviceId, instance, conversation, start, end, config.getMaxResponseBytes())) {
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
                            final String conversation) throws IOException {
        final Chain chain = new Chain();
        final List<AIAgentSessionFlowRecord> rounds = readRounds(serviceId, serviceInstanceId, conversation);
        final String instance = StringUtil.isNotEmpty(serviceInstanceId) ? serviceInstanceId : null;
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
            for (final AIAgentSessionDataRecord f : readFiles(serviceId, instance, session, from, to, 1, throughSeq)) {
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
                                                     final long fromSeq, final long throughSeq) throws IOException {
        final List<AIAgentSessionDataRecord> out = new ArrayList<>();
        final int window = config.getFileReadWindow();
        for (long start = fromSeq; start <= throughSeq; start += window) {
            final long end = Math.min(throughSeq, start + window - 1);
            out.addAll(dao().queryFilesDebuggable(
                serviceId, instance, session, from, to, start, end, config.getMaxResponseBytes()));
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
