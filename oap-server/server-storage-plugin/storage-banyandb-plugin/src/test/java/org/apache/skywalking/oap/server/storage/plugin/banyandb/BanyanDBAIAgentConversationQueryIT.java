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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAIAgentConversationQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads AI agent conversations the way the conversation query service does, on the real session-flow and
 * session-data streams: the latest rounds of a conversation with or without their bodies, the highest round (which
 * need not be the newest row), a round range with every copy of a round two senders pushed, and a session's files
 * by sequence.
 */
@Testcontainers
public class BanyanDBAIAgentConversationQueryIT {
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("ai-agent-it", true);
    private static final String CONVERSATION = "conversation-read";
    private static final String SESSION = "session-read";

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBAIAgentConversationQueryDAO QUERY_DAO;
    private static long FIRST;

    @BeforeAll
    public static void writeConversations() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        FIRST = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        final List<AIAgentSessionFlowRecord> rounds = new ArrayList<>();
        // Round 5 was pushed first, so the highest round is not the newest row.
        rounds.add(round(CONVERSATION, 5, "instance-0", 0));
        rounds.add(round(CONVERSATION, 1, "instance-0", 1));
        rounds.add(round(CONVERSATION, 2, "instance-0", 2));
        rounds.add(round(CONVERSATION, 3, "instance-0", 3));
        // Round 3 pushed by a second sender as well.
        rounds.add(round(CONVERSATION, 3, "instance-1", 4));
        rounds.add(round(CONVERSATION, 4, "instance-0", 5));
        rounds.add(round("conversation-other", 9, "instance-0", 6));
        SUPPORT.write(SUPPORT.install(AIAgentSessionFlowRecord.class, DefaultScopeDefine.AI_AGENT_SESSION_FLOW,
                                      AIAgentSessionFlowRecord.INDEX_NAME), new AIAgentSessionFlowRecord.Builder(), rounds);
        final List<AIAgentSessionDataRecord> files = new ArrayList<>();
        for (int seq = 1; seq <= 6; seq++) {
            files.add(file(SESSION, seq));
        }
        files.add(file("session-other", 3));
        SUPPORT.write(SUPPORT.install(AIAgentSessionDataRecord.class, DefaultScopeDefine.AI_AGENT_SESSION_DATA,
                                      AIAgentSessionDataRecord.INDEX_NAME), new AIAgentSessionDataRecord.Builder(), files);
        QUERY_DAO = new BanyanDBAIAgentConversationQueryDAO(SUPPORT.getClient());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(6, QUERY_DAO.queryRounds(SERVICE_ID, null, CONVERSATION, null, 10, false).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheLatestRoundsOfAConversation() throws IOException {
        final List<AIAgentSessionFlowRecord> latest = QUERY_DAO.queryRounds(SERVICE_ID, null, CONVERSATION, null, 3, false);
        assertEquals(Arrays.asList("4|instance-0", "3|instance-1", "3|instance-0"), latest.stream()
            .map(r -> r.getRound() + "|" + IDManager.ServiceInstanceID.analysisId(r.getServiceInstanceId()).getName())
            .collect(Collectors.toList()));
        assertNull(latest.get(0).getBody());
        assertEquals("round 4", new String(QUERY_DAO.queryRounds(SERVICE_ID, null, CONVERSATION, null, 1, true)
                                                     .get(0).getBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void readsTheHighestRound() throws IOException {
        assertEquals(5, QUERY_DAO.queryHeadRound(SERVICE_ID, null, CONVERSATION, false));
        assertEquals(0, QUERY_DAO.queryHeadRound(SERVICE_ID, null, "conversation-missing", false));
    }

    @Test
    public void readsEveryCopyOfTheRoundsInARange() throws IOException {
        assertEquals(Arrays.asList("2|round 2", "3|round 3", "3|round 3", "4|round 4"),
                     QUERY_DAO.queryRoundsByNumber(SERVICE_ID, null, CONVERSATION, 2, 4, Integer.MAX_VALUE, false).stream()
                              .map(r -> r.getRound() + "|" + new String(r.getBody(), StandardCharsets.UTF_8))
                              .collect(Collectors.toList()));
    }

    @Test
    public void readsTheFilesOfASessionBySequence() throws IOException {
        assertEquals(Arrays.asList("2|file 2", "3|file 3", "4|file 4"),
                     QUERY_DAO.queryFiles(SERVICE_ID, null, SESSION, FIRST, FIRST + 10_000L, 2, 4, Integer.MAX_VALUE, false)
                              .stream().map(f -> f.getSeq() + "|" + new String(f.getBody(), StandardCharsets.UTF_8))
                              .collect(Collectors.toList()));
    }

    private static AIAgentSessionFlowRecord round(String conversation, long round, String instance, int second) {
        final long timestamp = FIRST + second * 1000L;
        final AIAgentSessionFlowRecord record = new AIAgentSessionFlowRecord();
        record.setServiceId(SERVICE_ID);
        record.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(SERVICE_ID, instance));
        record.setConversation(conversation);
        record.setRound(round);
        record.setSessionFromTime(FIRST);
        record.setTitle("title " + round);
        record.setTalks(round);
        record.setSteps(round * 2);
        record.setDigest("digest-" + round);
        record.setTimestamp(timestamp);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        record.setBody(("round " + round).getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private static AIAgentSessionDataRecord file(String session, long seq) {
        final long timestamp = FIRST + seq * 1000L;
        final AIAgentSessionDataRecord record = new AIAgentSessionDataRecord();
        record.setServiceId(SERVICE_ID);
        record.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance-0"));
        record.setSession(session);
        record.setSeq(seq);
        record.setDigest("digest-" + seq);
        record.setTimestamp(timestamp);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        record.setBody(("file " + seq).getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
