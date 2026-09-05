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
import java.util.Map;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.FileNames;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Times;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionFormatsTest {
    @Test
    public void dataFilesDecodeAndTheirDigestsAreTheSessionizersDigests() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.dataFiles();
        assertEquals(3, files.size());
        final SessionDataFile main = files.get(1L);
        assertEquals("sd/1", main.getHeader().getSchema());
        assertEquals("transcript", main.getHeader().getKind());
        assertEquals("main", main.getHeader().getStream());
        assertEquals(16, main.getRecords().size());
        assertEquals(16, main.getDeclaredRecords());
        assertEquals(18, main.getLines());
        assertEquals(5868, main.getBytes());
        // the digests asz conversation -json printed for the same files
        assertEquals("944ed3d1209c76afb98f0622eb9239f95adb79fd0d0262d634c2f20bc041b1f7", main.getFileDigest());
        assertEquals("39b8a446c3a87d2b8da5210bba7f66ee2fd04caccea1fa0a4efdc3345b7ff7ca", files.get(2L).getFileDigest());
        assertEquals("de085eda31cdf99231fb212cb8fefbbcf95bd4f88c007c70446ecc652db3b005", files.get(3L).getFileDigest());
        // row 2 is the person's input
        assertEquals("run the build", main.record(2).text());
        assertEquals(1767225600000L, main.record(2).getTime());
        assertNull(main.record(0));
        assertNull(main.record(17));
        // the file's record time range, as the example's files[0]
        assertEquals(1767225600000L, main.getFromTime());
        assertEquals(1767225611100L, main.getThroughTime());
        // a meta file carries no timed record
        assertEquals(0L, files.get(3L).getFromTime());
    }

    @Test
    public void closingLineDigestCoversHeaderAndRecords() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.DATA_FILES[0]);
        final String text = new String(body, StandardCharsets.UTF_8);
        final int lastLine = text.lastIndexOf('\n', text.length() - 2);
        final String covered = text.substring(0, lastLine + 1);
        final SessionDataFile f = SessionDataFile.parse(body);
        assertEquals(f.getDeclaredDigest(), Digests.sha256Hex(covered.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void roundDecodesAndItsCommitDigestVerifies() throws Exception {
        final SessionFlowRound r = Fixtures.round();
        assertTrue(r.isIntact());
        assertEquals(1, r.getHeader().getRound());
        assertEquals(Fixtures.SESSION, r.getHeader().getConversation());
        assertEquals(1, r.getHeader().getFromSeq());
        assertEquals(3, r.getHeader().getThroughSeq());
        assertEquals(45, r.getLines());
        assertEquals(43, r.getNodes().size() + r.getRelations().size() + r.getUnresolved().size());
        assertEquals("3ad0dcd4cd53fa06c502a2649b8788bfbe0a382bbc72d7f1f0fe68d9a18e96e2", r.getCommitDigest());
        assertEquals(Times.millis("2026-01-01T00:00:00Z"), Times.millis(r.getHeader().getSessionFromTime()));
    }

    @Test
    public void inputDigestChainsTheFileDigests() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.dataFiles();
        final List<String> added = new ArrayList<>();
        for (final SessionDataFile f : files.values()) {
            added.add(f.getFileDigest());
        }
        assertEquals("6872d48ef5d3e736d0bd9f5bc03844653fb304b4b98b9ef6a27342478d950c1b", Digests.chainInputDigest("", added));
        assertEquals(Fixtures.round().getHeader().getInputDigest(), Digests.chainInputDigest("", added));
    }

    @Test
    public void tamperedRoundIsNotIntact() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.ROUND_FILE);
        final String text = new String(body, StandardCharsets.UTF_8).replace("\"trigger\":\"external\"", "\"trigger\":\"exterior\"");
        final SessionFlowRound r = SessionFlowRound.parse(text.getBytes(StandardCharsets.UTF_8));
        assertFalse(r.isIntact());
    }

    @Test
    public void fileNamesFollowTheStorageRootLayout() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.dataFiles();
        assertEquals(Fixtures.SESSION + "/streams/main/" + Fixtures.DATA_FILES[0],
                     FileNames.dataFile(files.get(1L).getHeader()));
        assertEquals(Fixtures.SESSION + "/streams/" + Fixtures.CHILD_STREAM + "/" + Fixtures.DATA_FILES[1],
                     FileNames.dataFile(files.get(2L).getHeader()));
        assertEquals(Fixtures.SESSION + "/streams/" + Fixtures.CHILD_STREAM + "/" + Fixtures.DATA_FILES[2],
                     FileNames.dataFile(files.get(3L).getHeader()));
        final SessionFlowRound r = Fixtures.round();
        assertEquals("_conversations/" + Fixtures.SESSION + "/rounds/" + Fixtures.ROUND_FILE,
                     FileNames.roundFile(r.getHeader().getConversation(), 1, r.getCommitDigest()));

        final FileNames.Parsed data = FileNames.parse(Fixtures.SESSION + "/streams/main/" + Fixtures.DATA_FILES[0]);
        assertNotNull(data);
        assertTrue(data.isDataFile());
        assertEquals(Fixtures.SESSION, data.getSession());
        assertEquals(1, data.getSeq());
        final FileNames.Parsed round = FileNames.parse("_conversations/" + Fixtures.SESSION + "/rounds/" + Fixtures.ROUND_FILE);
        assertNotNull(round);
        assertFalse(round.isDataFile());
        assertEquals(1, round.getRound());
        assertNull(FileNames.parse("not/a/file"));
    }

    @Test
    public void lineCountIsTheNewlineCount() throws Exception {
        assertEquals(18, Digests.countLines(Fixtures.bytes(Fixtures.DATA_FILES[0])));
        assertEquals(45, Digests.countLines(Fixtures.bytes(Fixtures.ROUND_FILE)));
    }

    /**
     * A part's data is rendered as the Sessionizer wrote it: escapes, key order and spacing kept, the way Go
     * prints a raw message, so the document equals the Sessionizer's byte for byte.
     */
    @Test
    public void partDataIsTheRawTextTheSessionizerWrote() {
        final String data = "{\"z\": 1, \"a\":\"caf\\u00e9 \\/ <b>\", \"n\":1.50, \"list\":[ 1,2 ]}";
        final String line = "{\"id\":\"r1\",\"time\":\"2026-01-01T00:00:00Z\",\"parts\":["
            + "{\"k\":\"text\",\"text\":\"hi \\\"there\\\"\"},"
            + "{\"k\":\"call\",\"name\":\"Bash\",\"data\":" + data + "},"
            + "{\"k\":\"result\",\"data\":null},"
            + "{\"k\":\"result\",\"data\":\"plain\"}]}";
        final byte[] file = ("{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"kind\":\"transcript\",\"session\":\"s\",\"stream\":\"main\","
            + "\"src\":\"x\",\"dialect\":\"mock/1\"}\n"
            + line + "\n{\"t\":\"end\",\"records\":1,\"digest\":\"0\"}\n").getBytes(StandardCharsets.UTF_8);
        final SessionDataFile parsed = SessionDataFile.parse(file);
        final List<SessionDataFile.Part> parts = parsed.getRecords().get(0).getParts();
        assertEquals(4, parts.size());
        assertNull(parts.get(0).data());
        assertEquals(data, parts.get(1).data());
        // a literal null is the text "null", as Go keeps it in a raw message and prints it
        assertEquals("null", parts.get(2).data());
        assertEquals("\"plain\"", parts.get(3).data());
    }
}
