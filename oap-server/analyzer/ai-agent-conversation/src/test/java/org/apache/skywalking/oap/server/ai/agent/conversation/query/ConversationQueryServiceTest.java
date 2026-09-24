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
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IAIAgentConversationQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The reads of the query service over a storage that answers from memory: the head is what the storage names
 * as the highest round, whatever page the list would read, and the files read by name serve every named round,
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

    private static final List<Long> ALL_SEQS = Arrays.asList(1L, 2L, 3L, 4L);

    private static List<ConversationFile> read(final ConversationQueryService service, final String conversation,
                                               final List<Long> seqs, final boolean coldStage) throws Exception {
        final List<ConversationFile> out = new ArrayList<>();
        assertTrue(service.readConversationFiles(SERVICE, "sender", conversation, Fixtures.SESSION, seqs, coldStage, () -> true, out::add));
        return out;
    }

    /**
     * Two stored rounds and a list page of one: the head is still round 2, because it is read as the highest
     * round stored and not off the page, so the files are read over round 2's range.
     */
    @Test
    public void theHeadIsTheHighestRoundStoredNotTheNewestOfAListPage() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final String conversation = first.getHeader().getConversation();
        final byte[] second = Fixtures.emptyRound(first, 2, first.getCommitDigest(), 4, 4, first.getHeader().getParser());
        // a third round, so the chain spans two round windows and the stage is proven on both
        final byte[] third = Fixtures.emptyRound(
            first, 3, SessionFlowRound.parse(second).getCommitDigest(), 5, 5, first.getHeader().getParser());
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        when(dao.queryHeadRoundDebuggable(eq(SERVICE), any(), eq(conversation), eq(false))).thenReturn(2L);
        when(dao.queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), anyLong(), anyLong(), anyInt(), eq(false)))
            .thenAnswer(inv -> roundsIn(Arrays.asList(
                storedRound(conversation, 1, Fixtures.bytes(Fixtures.ROUND_FILE)), storedRound(conversation, 2, second)),
                inv.getArgument(3), inv.getArgument(4)));
        when(dao.queryFilesDebuggable(anyString(), any(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), eq(false)))
            .thenReturn(storedFiles());
        final AIAgentConversationConfig config = new AIAgentConversationConfig();
        config.setConversationListMaxLimit(1);

        final List<ConversationFile> out = read(service(dao, config), conversation, Arrays.asList(4L, 2L, 3L, 1L), false);

        final List<Long> seqs = new ArrayList<>();
        for (final ConversationFile f : out) {
            seqs.add(f.getSeq());
        }
        // in seq order, each named from its own header
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L), seqs);
        assertEquals(Fixtures.SESSION + "/streams/main/" + Fixtures.DATA_FILES[0], out.get(0).getId());
        verify(dao).queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), eq(2L), eq(2L), anyInt(), eq(false));
        verify(dao, never()).queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), eq(1L), eq(1L), anyInt(), eq(false));
        assertEquals(Fixtures.SESSION + "/streams/" + Fixtures.CHILD_STREAM + "/" + Fixtures.DATA_FILES[2], out.get(2).getId());
        verify(dao, never()).queryRoundsDebuggable(anyString(), any(), any(), any(), anyInt(), eq(false));
    }

    /**
     * The newest stored round is not a round at all, though ingest could not tell: the files are read over the range
     * of the readable round before it.
     */
    @Test
    public void anUnreadableNewestRoundDoesNotBlockTheRead() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final String conversation = first.getHeader().getConversation();
        final byte[] truncated = "{\"t\":\"header\",\"schema\":\"sf/1\"".getBytes(StandardCharsets.UTF_8);
        final AIAgentSessionFlowRecord broken = storedRound(conversation, 2, truncated);
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        when(dao.queryHeadRoundDebuggable(eq(SERVICE), any(), eq(conversation), eq(false))).thenReturn(2L);
        when(dao.queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), anyLong(), anyLong(), anyInt(), eq(false)))
            .thenAnswer(inv -> roundsIn(Arrays.asList(storedRound(conversation, 1, Fixtures.bytes(Fixtures.ROUND_FILE)), broken),
                                        inv.getArgument(3), inv.getArgument(4)));
        when(dao.queryFilesDebuggable(anyString(), any(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), eq(false)))
            .thenReturn(storedFiles());
        final ConversationQueryService service = service(dao, new AIAgentConversationConfig());

        final List<ConversationFile> all = read(service, conversation, ALL_SEQS, false);
        assertEquals(Fixtures.DATA_FILES.length, all.size());
        assertEquals(new String(Fixtures.bytes(Fixtures.DATA_FILES[0]), StandardCharsets.UTF_8),
                     new String(all.get(0).getBody(), StandardCharsets.UTF_8));
        // the files are read over the readable round's range, ended by its own row's time and not the broken one's
        final SessionFlowRound.Header h = first.getHeader();
        final long from = org.apache.skywalking.oap.server.ai.agent.conversation.format.Times.millis(h.getSessionFromTime());
        final long through = Math.max(
            org.apache.skywalking.oap.server.ai.agent.conversation.format.Times.millis(h.getSessionThroughTime()), SENT_AT + 1);
        verify(dao).queryFilesDebuggable(eq(SERVICE), eq("sender"), eq(Fixtures.SESSION), eq(from), eq(through),
                                         eq(1L), eq(4L), anyInt(), eq(false));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    public void everyViewAndExportWindowUsesTheRequestedStage(final boolean export, final boolean coldStage) throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final String conversation = first.getHeader().getConversation();
        final byte[] second = Fixtures.emptyRound(first, 2, first.getCommitDigest(), 4, 4, first.getHeader().getParser());
        // a third round, so the chain spans two round windows and the stage is proven on both
        final byte[] third = Fixtures.emptyRound(
            first, 3, SessionFlowRound.parse(second).getCommitDigest(), 5, 5, first.getHeader().getParser());
        final List<AIAgentSessionDataRecord> files = storedFiles();
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        final AIAgentConversationConfig config = new AIAgentConversationConfig();
        config.setReadWindow(2);
        final String sender = export ? "sender" : null;
        when(dao.queryHeadRoundDebuggable(SERVICE, sender, conversation, coldStage)).thenReturn(3L);
        when(dao.queryRoundsByNumberDebuggable(SERVICE, sender, conversation, 1, 2, config.getMaxResponseBytes(), coldStage))
            .thenReturn(Arrays.asList(storedRound(conversation, 1, Fixtures.bytes(Fixtures.ROUND_FILE)),
                                      storedRound(conversation, 2, second)));
        when(dao.queryRoundsByNumberDebuggable(SERVICE, sender, conversation, 3, 3, config.getMaxResponseBytes(), coldStage))
            .thenReturn(Collections.singletonList(storedRound(conversation, 3, third)));
        when(dao.queryFilesDebuggable(eq(SERVICE), any(), eq(Fixtures.SESSION), anyLong(), anyLong(),
                                     eq(1L), eq(2L), eq(config.getMaxResponseBytes()), eq(coldStage)))
            .thenReturn(files.subList(0, 2));
        when(dao.queryFilesDebuggable(eq(SERVICE), any(), eq(Fixtures.SESSION), anyLong(), anyLong(),
                                     eq(3L), eq(4L), eq(config.getMaxResponseBytes()), eq(coldStage)))
            .thenReturn(files.subList(2, 4));
        final ConversationQueryService service = service(dao, config);

        if (export) {
            assertEquals(files.size(), read(service, conversation, ALL_SEQS, coldStage).size());
        } else {
            assertNotNull(service.buildConversationView(SERVICE, null, conversation, coldStage, () -> true));
        }

        verify(dao).queryHeadRoundDebuggable(SERVICE, sender, conversation, coldStage);
        // a file read takes its range from the head round alone; the view reads every round, a window at a time
        if (export) {
            verify(dao).queryRoundsByNumberDebuggable(SERVICE, sender, conversation, 3, 3, config.getMaxResponseBytes(), coldStage);
        } else {
            // both windows of the chain carry the stage, not only the first
            verify(dao).queryRoundsByNumberDebuggable(SERVICE, sender, conversation, 1, 2, config.getMaxResponseBytes(), coldStage);
            verify(dao).queryRoundsByNumberDebuggable(SERVICE, sender, conversation, 3, 3, config.getMaxResponseBytes(), coldStage);
        }
        verify(dao).queryFilesDebuggable(eq(SERVICE), any(), eq(Fixtures.SESSION), anyLong(), anyLong(),
                                        eq(1L), eq(2L), eq(config.getMaxResponseBytes()), eq(coldStage));
        verify(dao).queryFilesDebuggable(eq(SERVICE), any(), eq(Fixtures.SESSION), anyLong(), anyLong(),
                                        eq(3L), eq(4L), eq(config.getMaxResponseBytes()), eq(coldStage));
        if (!export) {
            // the view reads every file the chain names, and the third round names one more
            verify(dao).queryFilesDebuggable(eq(SERVICE), any(), eq(Fixtures.SESSION), anyLong(), anyLong(),
                                            eq(5L), eq(5L), eq(config.getMaxResponseBytes()), eq(coldStage));
        }
        verifyNoMoreInteractions(dao);
    }

    /**
     * Names scattered over a session are read one run of consecutive seqs at a time, never the stretch between
     * them, and each file once, in seq order, whatever order the storage answers in.
     */
    @Test
    public void namedFilesAreReadInRunsOfConsecutiveSeqs() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final String conversation = first.getHeader().getConversation();
        final List<AIAgentSessionDataRecord> files = storedFiles();
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        when(dao.queryHeadRoundDebuggable(eq(SERVICE), any(), eq(conversation), eq(false))).thenReturn(1L);
        when(dao.queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), anyLong(), anyLong(), anyInt(), eq(false)))
            .thenReturn(Collections.singletonList(storedRound(conversation, 1, Fixtures.bytes(Fixtures.ROUND_FILE))));
        when(dao.queryFilesDebuggable(eq(SERVICE), eq("sender"), eq(Fixtures.SESSION), anyLong(), anyLong(), eq(1L), eq(2L), anyInt(), eq(false)))
            .thenReturn(Arrays.asList(files.get(1), files.get(0), files.get(1)));
        when(dao.queryFilesDebuggable(eq(SERVICE), eq("sender"), eq(Fixtures.SESSION), anyLong(), anyLong(), eq(4L), eq(4L), anyInt(), eq(false)))
            .thenReturn(Collections.singletonList(files.get(3)));
        final List<ConversationFile> out = read(service(dao, new AIAgentConversationConfig()), conversation,
                                                Arrays.asList(4L, 2L, 1L, 2L), false);

        final List<Long> seqs = new ArrayList<>();
        for (final ConversationFile f : out) {
            seqs.add(f.getSeq());
        }
        assertEquals(Arrays.asList(1L, 2L, 4L), seqs);
        assertEquals(files.get(3).getDigest(), out.get(2).getDigest());
        verify(dao).queryFilesDebuggable(eq(SERVICE), eq("sender"), eq(Fixtures.SESSION), anyLong(), anyLong(), eq(1L), eq(2L), anyInt(), eq(false));
        verify(dao).queryFilesDebuggable(eq(SERVICE), eq("sender"), eq(Fixtures.SESSION), anyLong(), anyLong(), eq(4L), eq(4L), anyInt(), eq(false));
        verify(dao, never()).queryFilesDebuggable(anyString(), any(), anyString(), anyLong(), anyLong(), eq(3L), anyLong(), anyInt(), eq(false));
    }

    @Test
    public void aConversationTheSenderDoesNotStoreServesNoFile() throws Exception {
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        final List<ConversationFile> out = new ArrayList<>();
        assertFalse(service(dao, new AIAgentConversationConfig()).readConversationFiles(
            SERVICE, "sender", Fixtures.SESSION, Fixtures.SESSION, ALL_SEQS, false, () -> true, out::add));
        assertTrue(out.isEmpty());
    }

    /**
     * Each storage window is handed to the sink before the next window is read, so a response holds one window of
     * bodies at a time.
     */
    @Test
    public void eachWindowReachesTheSinkBeforeTheNextIsRead() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final String conversation = first.getHeader().getConversation();
        final List<AIAgentSessionDataRecord> files = storedFiles();
        final List<String> events = new ArrayList<>();
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);
        final AIAgentConversationConfig config = new AIAgentConversationConfig();
        config.setReadWindow(2);
        when(dao.queryHeadRoundDebuggable(eq(SERVICE), any(), eq(conversation), eq(false))).thenReturn(1L);
        when(dao.queryRoundsByNumberDebuggable(eq(SERVICE), any(), eq(conversation), anyLong(), anyLong(), anyInt(), eq(false)))
            .thenReturn(Collections.singletonList(storedRound(conversation, 1, Fixtures.bytes(Fixtures.ROUND_FILE))));
        when(dao.queryFilesDebuggable(eq(SERVICE), any(), eq(Fixtures.SESSION), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), eq(false)))
            .thenAnswer(inv -> {
                final long from = inv.getArgument(5);
                final long through = inv.getArgument(6);
                events.add("read " + from + ".." + through);
                return files.subList((int) from - 1, (int) through);
            });

        assertTrue(service(dao, config).readConversationFiles(SERVICE, "sender", conversation, Fixtures.SESSION, ALL_SEQS, false, () -> true,
                                                              f -> events.add("file " + f.getSeq())));

        assertEquals(Arrays.asList("read 1..2", "file 1", "file 2", "read 3..4", "file 3", "file 4"), events);
    }

    /**
     * Windows and runs never overflow, even at the largest seq a request can name.
     */
    @Test
    public void windowsAndRunsHoldAtTheLargestNumbers() {
        final List<long[]> windows = new ArrayList<>();
        ConversationQueryService.windows(Long.MAX_VALUE - 20, Long.MAX_VALUE, 16).forEach(windows::add);
        assertEquals(2, windows.size());
        assertEquals(Long.MAX_VALUE - 4, windows.get(1)[0]);
        assertEquals(Long.MAX_VALUE, windows.get(1)[1]);
        assertTrue(ConversationQueryService.windows(7, 7, 16).iterator().hasNext());
        assertFalse(ConversationQueryService.windows(8, 7, 16).iterator().hasNext());
        // a window a round claims up to the largest number is produced one window at a time, never as a list
        final java.util.Iterator<long[]> huge = ConversationQueryService.windows(1, Long.MAX_VALUE, 16).iterator();
        assertEquals(1L, huge.next()[0]);
        assertEquals(17L, huge.next()[0]);
        final List<long[]> runs = ConversationQueryService.runs(Arrays.asList(1L, 2L, 4L, Long.MAX_VALUE - 1, Long.MAX_VALUE));
        assertEquals(3, runs.size());
        assertEquals(Long.MAX_VALUE, runs.get(2)[1]);
    }

    private static List<AIAgentSessionFlowRecord> roundsIn(final List<AIAgentSessionFlowRecord> stored, final long from,
                                                           final long through) {
        final List<AIAgentSessionFlowRecord> out = new ArrayList<>();
        for (final AIAgentSessionFlowRecord r : stored) {
            if (r.getRound() >= from && r.getRound() <= through) {
                out.add(r);
            }
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void aMissingConversationDoesNotFallBackToAnotherStage(final boolean coldStage) throws Exception {
        final IAIAgentConversationQueryDAO dao = mock(IAIAgentConversationQueryDAO.class);

        assertNull(service(dao, new AIAgentConversationConfig()).buildConversationView(
            SERVICE, null, Fixtures.SESSION, coldStage, () -> true));

        verify(dao).queryHeadRoundDebuggable(SERVICE, null, Fixtures.SESSION, coldStage);
        verifyNoMoreInteractions(dao);
    }
}
