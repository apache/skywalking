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

package org.apache.skywalking.oap.server.ai.agent.conversation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Times;
import org.apache.skywalking.oap.server.ai.agent.conversation.ingest.ConversationFileBuilder;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversationFileBuilderTest {
    private static final long SENT_AT = 1788503495903L;

    /** Captures what the builder would hand to the record stream. */
    private static final class Capturing extends ConversationFileBuilder {
        final List<Record> dispatched = new ArrayList<>();

        @Override
        protected void dispatch(final Record record) {
            dispatched.add(record);
        }
    }

    @BeforeAll
    public static void namingControl() {
        ConversationFileBuilder.setNamingControl(new NamingControl(70, 70, 150, new EndpointNameGrouping()));
    }

    private static LogData.Builder input(final byte[] body, final String fileName) {
        return LogData.newBuilder()
                      .setBody(LogDataBody.newBuilder().setText(
                          TextLog.newBuilder().setText(new String(body, StandardCharsets.UTF_8))))
                      .setTags(LogTags.newBuilder().addData(
                          KeyStringValuePair.newBuilder().setKey("asz.file").setValue(fileName)));
    }

    private static LogMetadata metadata() {
        return LogMetadata.builder()
                          .service("Claude Code")
                          .serviceInstance("1748f643-fc41-4507-bc27-86fcc011c4a4")
                          .layer(Layer.AI_AGENT.name())
                          .timestamp(SENT_AT)
                          .build();
    }

    @Test
    public void aVerifiedDataFileBecomesASessionDataRow() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.DATA_FILES[0]);
        final Capturing b = new Capturing();
        b.setFormat("sd");
        b.setDigest(Digests.sha256Hex(body));
        b.setLines(18L);
        b.setSession(Fixtures.SESSION);
        b.setSeq(1L);
        b.setThroughTime("2026-01-01T00:00:11.100Z");
        b.init(metadata(), input(body, "x/streams/main/transcript-…-000001.sd"), null);
        b.complete(null);

        assertEquals(1, b.dispatched.size());
        final AIAgentSessionDataRecord row = (AIAgentSessionDataRecord) b.dispatched.get(0);
        assertEquals(IDManager.ServiceID.buildId("Claude Code", true), row.getServiceId());
        assertEquals(IDManager.ServiceInstanceID.buildId(row.getServiceId(), "1748f643-fc41-4507-bc27-86fcc011c4a4"),
                     row.getServiceInstanceId());
        assertEquals(Fixtures.SESSION, row.getSession());
        assertEquals(1L, row.getSeq());
        assertEquals(Digests.sha256Hex(body), row.getDigest());
        assertEquals(Times.millis("2026-01-01T00:00:11.100Z"), row.getTimestamp());
        assertArrayEquals(body, row.getBody());
        assertEquals(
            AIAgentSessionDataRecord.ownerHash(row.getServiceId(), row.getServiceInstanceId()) + "_" + Digests.sha256Hex(body),
            row.id().build());
    }

    @Test
    public void aFileWithoutARecordTimeIsStampedWithTheRecordsTime() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.DATA_FILES[2]);
        final Capturing b = new Capturing();
        b.setFormat("sd");
        b.setDigest(Digests.sha256Hex(body));
        b.setLines(3L);
        b.setSession(Fixtures.SESSION);
        b.setSeq(3L);
        b.init(metadata(), input(body, "meta"), null);
        b.complete(null);
        assertEquals(SENT_AT, ((AIAgentSessionDataRecord) b.dispatched.get(0)).getTimestamp());
    }

    @Test
    public void aVerifiedRoundBecomesASessionFlowRowStampedWithTheConversationsLastActivity() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.ROUND_FILE);
        final Capturing b = new Capturing();
        b.setFormat("sf");
        b.setDigest(Digests.sha256Hex(body));
        b.setLines(45L);
        b.setConversation(Fixtures.SESSION);
        b.setRound(1L);
        b.setSessionFromTime("2026-01-01T00:00:00Z");
        b.setSessionThroughTime("2026-01-01T00:00:11.1Z");
        b.setTitle("build and check");
        b.setTalks(3L);
        b.setSteps(21L);
        b.setStreams(3L);
        b.setSegments(1L);
        b.setUnresolved(2L);
        b.init(metadata(), input(body, "_conversations/x/rounds/r000001-3ad0dcd4cd53.sf"), null);
        b.complete(null);

        final AIAgentSessionFlowRecord row = (AIAgentSessionFlowRecord) b.dispatched.get(0);
        assertEquals(Fixtures.SESSION, row.getConversation());
        assertEquals(1L, row.getRound());
        assertEquals("build and check", row.getTitle());
        assertEquals(21L, row.getSteps());
        assertEquals(Times.millis("2026-01-01T00:00:00Z"), row.getSessionFromTime());
        assertEquals(Times.millis("2026-01-01T00:00:11.1Z"), row.getTimestamp());
        assertArrayEquals(body, row.getBody());
    }

    @Test
    public void aDigestMismatchIsNeverStored() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.DATA_FILES[0]);
        final Capturing b = new Capturing();
        b.setFormat("sd");
        b.setDigest("0000000000000000000000000000000000000000000000000000000000000000");
        b.setLines(18L);
        b.setSession(Fixtures.SESSION);
        b.setSeq(1L);
        b.init(metadata(), input(body, "f"), null);
        b.complete(null);
        assertTrue(b.dispatched.isEmpty());
    }

    @Test
    public void aLineCountMismatchIsNeverStored() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.DATA_FILES[0]);
        final Capturing b = new Capturing();
        b.setFormat("sd");
        b.setDigest(Digests.sha256Hex(body));
        b.setLines(17L);
        b.setSession(Fixtures.SESSION);
        b.setSeq(1L);
        b.init(metadata(), input(body, "f"), null);
        b.complete(null);
        assertTrue(b.dispatched.isEmpty());
    }

    @Test
    public void aRecordWithoutItsKeysIsNeverStored() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.ROUND_FILE);
        final Capturing b = new Capturing();
        b.setFormat("sf");
        b.setDigest(Digests.sha256Hex(body));
        b.setLines(45L);
        b.init(metadata(), input(body, "f"), null);
        b.complete(null);
        assertTrue(b.dispatched.isEmpty());
    }
}
