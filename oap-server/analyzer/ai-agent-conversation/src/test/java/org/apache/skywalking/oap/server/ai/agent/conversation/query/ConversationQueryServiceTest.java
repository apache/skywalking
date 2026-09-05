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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.ai.agent.conversation.AIAgentConversationConfig;
import org.apache.skywalking.oap.server.ai.agent.conversation.Fixtures;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.FileNames;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationFileFormat;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRawFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRawFiles;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IAIAgentConversationQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The reads of the query service over a storage that answers from memory: the head is what the storage names
 * as the highest round, whatever page the list would read, and the raw-file export serves every stored round,
 * readable or not. The service reads through the interface's tracing wrappers, so those are what the mock answers.
 */
public class ConversationQueryServiceTest {
    private static final String SERVICE = "Q2xhdWRlIENvZGU=.1";
    private static final long SENT_AT = 1757100000000L;

    private static ConversationQueryService service(final IAIAgentConversationQueryDAO dao,
                                                    final AIAgentConversationConfig config) {
        final ModuleManager manager = mock(ModuleManager.class);
        final ModuleProviderHolder storage = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(manager.find(StorageModule.NAME)).thenReturn(storage);
        when(storage.provider()).thenReturn(services);
        when(services.getService(IAIAgentConversationQueryDAO.class)).thenReturn(dao);
        return new ConversationQueryService(manager, config);
    }

    private static AIAgentSessionFlowRecord storedRound(final String conversation, final long round, final byte[] body) {
        final AIAgentSessionFlowRecord r = new AIAgentSessionFlowRecord();
        r.setServiceId(SERVICE);
        r.setConversation(conversation);
        r.setRound(round);
        r.setBody(body);
        r.setDigest(Digests.sha256Hex(body));
        r.setTimestamp(SENT_AT + round);
        return r;
    }

    private static List<AIAgentSessionDataRecord> storedFiles() throws Exception {
        final List<AIAgentSessionDataRecord> files = new ArrayList<>();
        for (int i = 0; i < Fixtures.DATA_FILES.length; i++) {
            final byte[] body = Fixtures.bytes(Fixtures.DATA_FILES[i]);
            final AIAgentSessionDataRecord f = new AIAgentSessionDataRecord();
            f.setServiceId(SERVICE);
            f.setSession(Fixtures.SESSION);
            f.setSeq(i + 1);
            f.setBody(body);
            f.setDigest(Digests.sha256Hex(body));
            f.setTimestamp(SENT_AT);
            files.add(f);
        }
        return files;
    }

    /**
     * Two stored rounds and a list page of one: the head is still round 2, because it is read as the highest
     * round stored and not off the page, so the export names both rounds.
     */
    @Test
    public void theHeadIsTheHighestRoundStoredNotTheNewestOfAListPage() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final String conversation = first.getHeader().getConversation();
        final byte[] second = Fixtures.emptyRound(first, 2, first.getCommitDigest(), 4, 4, first.getHeader().getParser());
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        when(dao.queryHeadRoundDebuggable(eq(SERVICE), any(), eq(conversation))).thenReturn(2L);
        when(dao.queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), anyLong(), anyLong(), anyInt()))
            .thenReturn(Arrays.asList(
                storedRound(conversation, 1, Fixtures.bytes(Fixtures.ROUND_FILE)), storedRound(conversation, 2, second)));
        when(dao.queryFilesDebuggable(anyString(), any(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt()))
            .thenReturn(storedFiles());
        final AIAgentConversationConfig config = new AIAgentConversationConfig();
        config.setMaxListLimit(1);

        final ConversationRawFiles out = service(dao, config)
            .getConversationRawFiles(SERVICE, null, conversation, null, false);

        assertNull(out.getErrorReason());
        final List<Long> rounds = new ArrayList<>();
        for (final ConversationRawFile f : out.getFiles()) {
            if (f.getFormat() == ConversationFileFormat.SF) {
                rounds.add((long) f.getRound());
            }
        }
        assertEquals(Arrays.asList(1L, 2L), rounds);
        assertEquals(Fixtures.DATA_FILES.length + 2, out.getFiles().size());
        verify(dao, never()).queryRoundsDebuggable(anyString(), any(), any(), any(), anyInt(), eq(false));
    }

    /**
     * The newest stored round is not a round at all, though ingest could not tell: the export still lists it, named
     * by the digest of its file, serves the readable round before it, and honours a selection of that round alone.
     */
    @Test
    public void anUnreadableNewestRoundDoesNotBlockTheExport() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final String conversation = first.getHeader().getConversation();
        final byte[] truncated = "{\"t\":\"header\",\"schema\":\"sf/1\"".getBytes(StandardCharsets.UTF_8);
        final AIAgentSessionFlowRecord broken = storedRound(conversation, 2, truncated);
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        when(dao.queryHeadRoundDebuggable(eq(SERVICE), any(), eq(conversation))).thenReturn(2L);
        when(dao.queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), anyLong(), anyLong(), anyInt()))
            .thenReturn(Arrays.asList(storedRound(conversation, 1, Fixtures.bytes(Fixtures.ROUND_FILE)), broken));
        when(dao.queryFilesDebuggable(anyString(), any(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt()))
            .thenReturn(storedFiles());
        final ConversationQueryService service = service(dao, new AIAgentConversationConfig());

        final String firstId = FileNames.roundFile(conversation, 1, first.getCommitDigest());
        final ConversationRawFiles selected = service.getConversationRawFiles(
            SERVICE, null, conversation, Collections.singletonList(firstId), true);
        assertNull(selected.getErrorReason());
        assertEquals(1, selected.getFiles().size());
        assertEquals(firstId, selected.getFiles().get(0).getId());
        assertEquals(new String(Fixtures.bytes(Fixtures.ROUND_FILE), StandardCharsets.UTF_8), selected.getFiles().get(0).getBody());

        final ConversationRawFiles all = service.getConversationRawFiles(SERVICE, null, conversation, null, false);
        assertNull(all.getErrorReason());
        assertEquals(Fixtures.DATA_FILES.length + 2, all.getFiles().size());
        final ConversationRawFile last = all.getFiles().get(all.getFiles().size() - 1);
        assertEquals(2, last.getRound());
        assertTrue(last.getId().endsWith(broken.getDigest().substring(0, 12) + ".sf"), last.getId());
    }
}
