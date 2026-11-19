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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.IAIAgentConversationQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads AI agent conversations the way the conversation query service does on a JDBC storage, through
 * {@link JDBCAIAgentConversationQueryDAO}: the latest rounds of a service, a sender or a conversation, with or
 * without their bodies; the highest round of a conversation; the rounds of a round-number window, every copy of a
 * round two senders pushed included; and the files of a session in a seq window and a timestamp range. A subclass
 * names the server.
 *
 * <p>The rounds and the files are written on both sides of midnight, so a read crosses two day tables, and the highest
 * round of the conversation is its oldest row. Each read has a row of another service, sender, conversation or session
 * that matches it otherwise, and rows just outside the window. A round pushed by more senders than a window makes room
 * for shows the window's limit. Rounds of their own lie on and just outside the bounds of a duration: on its first
 * millisecond and the one before, on its last millisecond, and just after its end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCAIAgentConversationQueryITBase {
    private static final long MINUTE = 60_000L;
    private static final Duration AROUND_MIDNIGHT = minutes(MIDNIGHT - 5 * MINUTE, MIDNIGHT + 4 * MINUTE);
    private static final Duration BOUNDED = minutes(MIDNIGHT + 10 * MINUTE, MIDNIGHT + 11 * MINUTE);

    private static final String SERVICE = "ai-agent";
    private static final String OTHER_SERVICE = "other-agent";
    private static final String CONVERSATION = "conversation-read";
    private static final String SESSION = "session-read";
    /** More senders than the window of one round makes room for. */
    private static final int COPIES = IAIAgentConversationQueryDAO.MAX_COPIES + 1;

    /** Round 5 was pushed first, so the highest round is not the newest row. */
    private static final List<Round> ROUNDS = Arrays.asList(
        new Round(SERVICE, "instance-0", CONVERSATION, 5, MIDNIGHT - 150_000L),
        new Round(SERVICE, "instance-0", CONVERSATION, 1, MIDNIGHT - 110_000L),
        new Round(SERVICE, "instance-0", CONVERSATION, 2, MIDNIGHT - 70_000L),
        new Round(SERVICE, "instance-0", CONVERSATION, 3, MIDNIGHT - 30_000L),
        new Round(SERVICE, "instance-1", CONVERSATION, 3, MIDNIGHT + 30_000L),
        new Round(SERVICE, "instance-0", CONVERSATION, 4, MIDNIGHT + 70_000L).counted(),
        new Round(SERVICE, "instance-0", "conversation-other", 9, MIDNIGHT + 110_000L),
        new Round(OTHER_SERVICE, "instance-0", CONVERSATION, 7, MIDNIGHT + 150_000L),
        new Round(SERVICE, "instance-0", "conversation-bounds", 1, BOUNDED.getStartTimestamp() - 1),
        new Round(SERVICE, "instance-0", "conversation-bounds", 2, BOUNDED.getStartTimestamp()),
        new Round(SERVICE, "instance-0", "conversation-bounds", 3, BOUNDED.getEndTimestamp() - 1),
        new Round(SERVICE, "instance-0", "conversation-bounds", 4, BOUNDED.getEndTimestamp() + 1)
    );
    /** Seq 3 is pushed by a second sender as well, a second later. */
    private static final List<SessionFile> FILES = Arrays.asList(
        new SessionFile(SERVICE, "instance-0", SESSION, 1),
        new SessionFile(SERVICE, "instance-0", SESSION, 2),
        new SessionFile(SERVICE, "instance-0", SESSION, 3),
        new SessionFile(SERVICE, "instance-1", SESSION, 3),
        new SessionFile(SERVICE, "instance-0", SESSION, 4),
        new SessionFile(SERVICE, "instance-0", SESSION, 5),
        new SessionFile(SERVICE, "instance-0", SESSION, 6),
        new SessionFile(SERVICE, "instance-0", "session-other", 3),
        new SessionFile(OTHER_SERVICE, "instance-0", SESSION, 3)
    );

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCAIAgentConversationQueryDAO dao;

    JDBCAIAgentConversationQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeConversations() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final List<AIAgentSessionFlowRecord> rounds = ROUNDS.stream().map(Round::record).collect(Collectors.toList());
        final List<AIAgentSessionDataRecord> files = FILES.stream().map(SessionFile::record)
                                                          .collect(Collectors.toList());
        for (int i = 0; i < COPIES; i++) {
            rounds.add(new Round(SERVICE, "copy-" + i, "conversation-copies", 1, MIDNIGHT + 40 * MINUTE).record());
            files.add(new SessionFile(SERVICE, "copy-" + i, "session-copies", 1).record());
        }
        final Model roundModel = support.install(AIAgentSessionFlowRecord.class,
                                                 DefaultScopeDefine.AI_AGENT_SESSION_FLOW,
                                                 AIAgentSessionFlowRecord.INDEX_NAME, DownSampling.Second);
        final Model fileModel = support.install(AIAgentSessionDataRecord.class,
                                                DefaultScopeDefine.AI_AGENT_SESSION_DATA,
                                                AIAgentSessionDataRecord.INDEX_NAME, DownSampling.Second);
        support.writeRecords(roundModel, new AIAgentSessionFlowRecord.Builder(), rounds);
        support.writeRecords(fileModel, new AIAgentSessionDataRecord.Builder(), files);
        dao = new JDBCAIAgentConversationQueryDAO(support.getClient(), support.getTableHelper());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheLatestRoundsOfAConversationNewestFirst() {
        assertEquals(Arrays.asList("4@instance-0", "3@instance-1", "3@instance-0", "2@instance-0", "1@instance-0",
                                   "5@instance-0"),
                     rounds(dao.queryRounds(serviceId(SERVICE), null, CONVERSATION, AROUND_MIDNIGHT, 10, false)));
        assertEquals(Arrays.asList("4@instance-0", "3@instance-1", "3@instance-0"),
                     rounds(dao.queryRounds(serviceId(SERVICE), null, CONVERSATION, AROUND_MIDNIGHT, 3, false)));
    }

    @Test
    public void readsTheRoundsOfASenderOfEveryConversationAndOfAService() {
        assertEquals(Collections.singletonList("3@instance-1"), rounds(dao.queryRounds(
            serviceId(SERVICE), instanceId(SERVICE, "instance-1"), CONVERSATION, AROUND_MIDNIGHT, 10, false)));
        assertEquals(Arrays.asList("9@instance-0", "4@instance-0", "3@instance-1", "3@instance-0", "2@instance-0",
                                   "1@instance-0", "5@instance-0"),
                     rounds(dao.queryRounds(serviceId(SERVICE), null, null, AROUND_MIDNIGHT, 10, false)));
        assertEquals(Collections.singletonList("7@instance-0"), rounds(dao.queryRounds(
            serviceId(OTHER_SERVICE), null, CONVERSATION, AROUND_MIDNIGHT, 10, false)));
    }

    @Test
    public void readsTheRoundsOfEveryDayWithoutADuration() {
        assertEquals(Arrays.asList("4@instance-0", "3@instance-1", "3@instance-0", "2@instance-0", "1@instance-0",
                                   "5@instance-0"),
                     rounds(dao.queryRounds(serviceId(SERVICE), null, CONVERSATION, null, 10, false)));
    }

    @Test
    public void readsEachRoundWholeWithOrWithoutItsBody() {
        final List<String> withBody = new ArrayList<>();
        final List<String> withoutBody = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            withBody.add(render(ROUNDS.get(i).record()));
            final AIAgentSessionFlowRecord noBody = ROUNDS.get(i).record();
            noBody.setBody(null);
            withoutBody.add(render(noBody));
        }
        assertEquals(withBody, dao.queryRounds(serviceId(SERVICE), null, CONVERSATION, AROUND_MIDNIGHT, 10, true)
                                  .stream().map(JDBCAIAgentConversationQueryITBase::render)
                                  .collect(Collectors.toList()));
        assertEquals(withoutBody, dao.queryRounds(serviceId(SERVICE), null, CONVERSATION, AROUND_MIDNIGHT, 10, false)
                                     .stream().map(JDBCAIAgentConversationQueryITBase::render)
                                     .collect(Collectors.toList()));
    }

    @Test
    public void keepsTheRoundsWithinTheDuration() {
        assertEquals(Arrays.asList("3@instance-0", "2@instance-0"), rounds(dao.queryRounds(
            serviceId(SERVICE), null, "conversation-bounds", BOUNDED, 10, false)));
    }

    @Test
    public void readsTheHighestRoundOfAConversation() {
        assertEquals(5, dao.queryHeadRound(serviceId(SERVICE), null, CONVERSATION, false));
        assertEquals(3, dao.queryHeadRound(serviceId(SERVICE), instanceId(SERVICE, "instance-1"), CONVERSATION, false));
        assertEquals(9, dao.queryHeadRound(serviceId(SERVICE), null, "conversation-other", false));
        assertEquals(7, dao.queryHeadRound(serviceId(OTHER_SERVICE), null, CONVERSATION, false));
        assertEquals(0, dao.queryHeadRound(serviceId(SERVICE), null, "conversation-missing", false));
    }

    /**
     * Copies of one round come in no order.
     */
    @Test
    public void readsEveryCopyOfTheRoundsOfAWindowInRoundOrder() {
        final List<AIAgentSessionFlowRecord> window = dao.queryRoundsByNumber(
            serviceId(SERVICE), null, CONVERSATION, 2, 4, Integer.MAX_VALUE, false);
        assertEquals(Arrays.asList(2L, 3L, 3L, 4L), window.stream().map(AIAgentSessionFlowRecord::getRound)
                                                         .collect(Collectors.toList()));
        assertEquals(Arrays.asList(render(ROUNDS.get(2).record()), render(ROUNDS.get(3).record()),
                                   render(ROUNDS.get(4).record()), render(ROUNDS.get(5).record())),
                     window.stream().sorted(Comparator.comparing(AIAgentSessionFlowRecord::getRound)
                                                      .thenComparing(AIAgentSessionFlowRecord::getServiceInstanceId))
                           .map(JDBCAIAgentConversationQueryITBase::render).collect(Collectors.toList()));
        assertEquals(Arrays.asList("2@instance-0", "3@instance-0", "4@instance-0"), rounds(dao.queryRoundsByNumber(
            serviceId(SERVICE), instanceId(SERVICE, "instance-0"), CONVERSATION, 2, 4, Integer.MAX_VALUE, false)));
        assertEquals(Collections.singletonList("5@instance-0"), rounds(dao.queryRoundsByNumber(
            serviceId(SERVICE), null, CONVERSATION, 5, 5, Integer.MAX_VALUE, false)));
        assertEquals(Collections.emptyList(), rounds(dao.queryRoundsByNumber(
            serviceId(SERVICE), null, CONVERSATION, 6, 8, Integer.MAX_VALUE, false)));
    }

    @Test
    public void readsAtMostTheRowsTheWindowMakesRoomFor() {
        final List<AIAgentSessionFlowRecord> rounds = dao.queryRoundsByNumber(
            serviceId(SERVICE), null, "conversation-copies", 1, 1, Integer.MAX_VALUE, false);
        assertEquals(Collections.nCopies(IAIAgentConversationQueryDAO.MAX_COPIES, 1L), rounds.stream()
            .map(AIAgentSessionFlowRecord::getRound).collect(Collectors.toList()));
        final List<AIAgentSessionDataRecord> files = dao.queryFiles(
            serviceId(SERVICE), null, "session-copies", time(1) - 1, time(1) + 1, 1, 1, Integer.MAX_VALUE, false);
        assertEquals(Collections.nCopies(IAIAgentConversationQueryDAO.MAX_COPIES, 1L), files.stream()
            .map(AIAgentSessionDataRecord::getSeq).collect(Collectors.toList()));
    }

    /**
     * Copies of one seq come in no order.
     */
    @Test
    public void readsTheFilesOfASeqWindowBySeq() {
        final List<AIAgentSessionDataRecord> files = dao.queryFiles(
            serviceId(SERVICE), null, SESSION, time(1), time(6) + 1000L, 2, 4, Integer.MAX_VALUE, false);
        assertEquals(Arrays.asList(2L, 3L, 3L, 4L), files.stream().map(AIAgentSessionDataRecord::getSeq)
                                                        .collect(Collectors.toList()));
        assertEquals(Arrays.asList(render(FILES.get(1).record()), render(FILES.get(2).record()),
                                   render(FILES.get(3).record()), render(FILES.get(4).record())),
                     files.stream().sorted(Comparator.comparing(AIAgentSessionDataRecord::getSeq)
                                                     .thenComparing(AIAgentSessionDataRecord::getServiceInstanceId))
                          .map(JDBCAIAgentConversationQueryITBase::render).collect(Collectors.toList()));
        assertEquals(Collections.singletonList("3@instance-1"), files(dao.queryFiles(
            serviceId(SERVICE), instanceId(SERVICE, "instance-1"), SESSION, time(1), time(6) + 1000L, 1, 6,
            Integer.MAX_VALUE, false)));
    }

    /**
     * Both ends of the timestamp range are inclusive.
     */
    @Test
    public void readsTheFilesOnAndWithinTheTimestampRange() {
        assertEquals(Arrays.asList("2@instance-0", "3@instance-0", "3@instance-1", "4@instance-0", "5@instance-0"),
                     files(dao.queryFiles(serviceId(SERVICE), null, SESSION, time(2), time(5), 1, 6,
                                          Integer.MAX_VALUE, false)));
        assertEquals(Arrays.asList("3@instance-0", "3@instance-1", "4@instance-0"),
                     files(dao.queryFiles(serviceId(SERVICE), null, SESSION, time(2) + 1, time(5) - 1, 1, 6,
                                          Integer.MAX_VALUE, false)));
    }

    @Test
    public void readsTheFilesOfEveryDaySinceTheEpoch() {
        assertEquals(Arrays.asList("1@instance-0", "2@instance-0", "3@instance-0", "3@instance-1", "4@instance-0",
                                   "5@instance-0", "6@instance-0"),
                     files(dao.queryFiles(serviceId(SERVICE), null, SESSION, 0, time(6) + 1000L, 1, 6,
                                          Integer.MAX_VALUE, false)));
    }

    private static List<String> rounds(final List<AIAgentSessionFlowRecord> rounds) {
        return rounds.stream()
                     .map(round -> round.getRound() + "@" + instanceName(round.getServiceInstanceId()))
                     .collect(Collectors.toList());
    }

    /** Copies of one seq sorted by their sender. */
    private static List<String> files(final List<AIAgentSessionDataRecord> files) {
        return files.stream()
                    .sorted(Comparator.comparing(AIAgentSessionDataRecord::getSeq)
                                      .thenComparing(AIAgentSessionDataRecord::getServiceInstanceId))
                    .map(file -> file.getSeq() + "@" + instanceName(file.getServiceInstanceId()))
                    .collect(Collectors.toList());
    }

    private static String render(final AIAgentSessionFlowRecord round) {
        return String.join("|", round.getServiceId(), round.getServiceInstanceId(), round.getConversation(),
                           String.valueOf(round.getRound()), String.valueOf(round.getSessionFromTime()),
                           round.getTitle(), String.valueOf(round.getTalks()), String.valueOf(round.getSteps()),
                           String.valueOf(round.getStreams()), String.valueOf(round.getSegments()),
                           String.valueOf(round.getUnresolved()), String.valueOf(round.getChanges()),
                           String.valueOf(round.getLinesAdded()), String.valueOf(round.getLinesRemoved()),
                           String.valueOf(round.getLlmCalls()), String.valueOf(round.getSubagents()),
                           String.valueOf(round.getBashRuns()), round.getDigest(),
                           String.valueOf(round.getTimestamp()), String.valueOf(round.getTimeBucket()),
                           body(round.getBody()));
    }

    private static String render(final AIAgentSessionDataRecord file) {
        return String.join("|", file.getServiceId(), file.getServiceInstanceId(), file.getSession(),
                           String.valueOf(file.getSeq()), file.getDigest(), String.valueOf(file.getTimestamp()),
                           String.valueOf(file.getTimeBucket()), body(file.getBody()));
    }

    private static String body(final byte[] body) {
        return body == null ? "no body" : new String(body, StandardCharsets.UTF_8);
    }

    private static String instanceName(final String instanceId) {
        return IDManager.ServiceInstanceID.analysisId(instanceId).getName();
    }

    /** The time of a session's file of the seq: seq 3 at midnight, a minute apart. */
    private static long time(final long seq) {
        return MIDNIGHT + (seq - 3) * MINUTE;
    }

    private static Duration minutes(final long start, final long end) {
        return JDBCQueryITSupport.duration(start, end, Step.MINUTE);
    }

    private static String serviceId(final String service) {
        return IDManager.ServiceID.buildId(service, true);
    }

    private static String instanceId(final String service, final String instance) {
        return IDManager.ServiceInstanceID.buildId(serviceId(service), instance);
    }

    /**
     * A conversation round as the Sessionizer pushes it; only round 4 carries the optional counts.
     */
    private static final class Round {
        private final String service;
        private final String instance;
        private final String conversation;
        private final long round;
        private final long time;
        private boolean counted;

        Round(final String service, final String instance, final String conversation, final long round,
              final long time) {
            this.service = service;
            this.instance = instance;
            this.conversation = conversation;
            this.round = round;
            this.time = time;
        }

        Round counted() {
            counted = true;
            return this;
        }

        AIAgentSessionFlowRecord record() {
            final AIAgentSessionFlowRecord record = new AIAgentSessionFlowRecord();
            record.setServiceId(serviceId(service));
            record.setServiceInstanceId(instanceId(service, instance));
            record.setConversation(conversation);
            record.setRound(round);
            record.setSessionFromTime(MIDNIGHT - 10 * MINUTE);
            record.setTitle("title of " + conversation);
            record.setTalks(round);
            record.setSteps(round * 2);
            record.setStreams(round * 3);
            record.setSegments(round * 4);
            record.setUnresolved(round % 2);
            if (counted) {
                record.setChanges(2L);
                record.setLinesAdded(10L);
                record.setLinesRemoved(3L);
                record.setLlmCalls(7L);
                record.setSubagents(1L);
                record.setBashRuns(4L);
            }
            record.setDigest("digest-" + conversation + "-" + round);
            record.setTimestamp(time);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
            record.setBody(("round " + round + " of " + conversation + " by " + instance)
                               .getBytes(StandardCharsets.UTF_8));
            return record;
        }
    }

    /**
     * A session file as the Sessionizer pushes it, at the time of its seq; a second sender pushes a second later.
     */
    private static final class SessionFile {
        private final String service;
        private final String instance;
        private final String session;
        private final long seq;

        SessionFile(final String service, final String instance, final String session, final long seq) {
            this.service = service;
            this.instance = instance;
            this.session = session;
            this.seq = seq;
        }

        AIAgentSessionDataRecord record() {
            final long timestamp = "instance-1".equals(instance) ? time(seq) + 1000L : time(seq);
            final AIAgentSessionDataRecord record = new AIAgentSessionDataRecord();
            record.setServiceId(serviceId(service));
            record.setServiceInstanceId(instanceId(service, instance));
            record.setSession(session);
            record.setSeq(seq);
            record.setDigest("file-" + session + "-" + seq);
            record.setTimestamp(timestamp);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
            record.setBody(("file " + seq + " of " + session + " by " + instance).getBytes(StandardCharsets.UTF_8));
            return record;
        }
    }
}
