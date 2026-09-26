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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionFormatsTest {
    @Test
    public void dataFilesDecodeAndTheirDigestsAreTheSessionizersDigests() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.dataFiles();
        assertEquals(4, files.size());
        final SessionDataFile main = files.get(1L);
        assertEquals("sd/1", main.getHeader().getSchema());
        assertEquals("transcript", main.getHeader().getKind());
        assertEquals("main", main.getHeader().getStream());
        assertEquals(16, main.getRecords().size());
        assertEquals(16, main.getDeclaredRecords());
        assertEquals(18, main.getLines());
        assertEquals(5976, main.getBytes());
        // the digests asz conversation -json printed for the same files
        assertEquals("667c3a32480190268901b2cf83f19087bed9bf44416dd900d0eb16cbab800580", main.getFileDigest());
        assertEquals("b7fc207afdc51b9c48e42af8524ea7a68e5e5f3b4c76b7d37dd765e2fe8145c5", files.get(2L).getFileDigest());
        assertEquals("fa5e9bb561d0bc6168382498e805829b2820141b57117db50f4894d474c3e486", files.get(3L).getFileDigest());
        assertEquals("62f019483e4d0c0d4d0f5f296bec63fbfae663479a2a78d83b45481bd8df7185", files.get(4L).getFileDigest());
        // row 2 is the person's input
        assertEquals("run the build", main.record(2).text());
        assertEquals(1767225600000L, main.record(2).getTime());
        assertNull(main.record(0));
        assertNull(main.record(17));
        // the file's record time range, as the example's files[0]
        assertEquals(1767225600000L, main.getFromTime());
        assertEquals(1767225611100L, main.getThroughTime());
        // the plugin's changes file is an ordinary landed file: its one record is timed, and it took the seq between
        // the transcript files that landed around it
        assertEquals("changes", files.get(2L).getHeader().getKind());
        assertEquals("main", files.get(2L).getHeader().getStream());
        assertEquals(1, files.get(2L).getRecords().size());
        assertEquals(1767225602000L, files.get(2L).getFromTime());
        assertEquals(1767225602000L, files.get(2L).getThroughTime());
        // a meta file carries no timed record
        assertEquals(0L, files.get(4L).getFromTime());
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
        // the plugin's changes file landed between the transcripts, so the round's window covers it too
        assertEquals(4, r.getHeader().getThroughSeq());
        assertEquals(45, r.getLines());
        assertEquals(43, r.getNodes().size() + r.getRelations().size() + r.getUnresolved().size());
        assertEquals("befb6026a5776f481ed79bb59cc7ff0fbabd36db8e4d174156230ac3051d723d", r.getCommitDigest());
        assertEquals(Times.millis("2026-01-01T00:00:00Z"), Times.millis(r.getHeader().getSessionFromTime()));
    }

    @Test
    public void inputDigestChainsTheFileDigests() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.dataFiles();
        final List<String> added = new ArrayList<>();
        for (final SessionDataFile f : files.values()) {
            added.add(f.getFileDigest());
        }
        assertEquals("ede3d4ca2b72ada8850407ebd46585d9306fd9448a767d170465b7847d833b9f", Digests.chainInputDigest("", added));
        assertEquals(Fixtures.round().getHeader().getInputDigest(), Digests.chainInputDigest("", added));
    }

    @Test
    public void tamperedRoundIsNotIntact() throws Exception {
        final byte[] body = Fixtures.bytes(Fixtures.ROUND_FILE);
        final String text = new String(body, StandardCharsets.UTF_8).replace("\"trigger\":\"external\"", "\"trigger\":\"exterior\"");
        final SessionFlowRound r = SessionFlowRound.parse(text.getBytes(StandardCharsets.UTF_8));
        assertFalse(r.isIntact());
    }

    /**
     * A file's stamp writes the year as the Sessionizer does, four digits of the year itself: year zero is 0000, not the
     * first year of an era.
     */
    @Test
    public void aFilesStampWritesYearZeroAsTheSessionizerDoes() {
        final SessionDataFile f = SessionDataFile.parse(("{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"at\":\"0000-01-01T00:00:00Z\","
            + "\"kind\":\"transcript\",\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\",\"src\":\"x\",\"session\":\"s\","
            + "\"stream\":\"main\"}\n").getBytes(StandardCharsets.UTF_8));
        assertEquals("s/streams/main/transcript-00000101T000000.000000000Z-000001.sd", FileNames.dataFile(f.getHeader()));
    }

    @Test
    public void fileNamesFollowTheStorageRootLayout() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.dataFiles();
        assertEquals(Fixtures.SESSION + "/streams/main/" + Fixtures.DATA_FILES[0],
                     FileNames.dataFile(files.get(1L).getHeader()));
        assertEquals(Fixtures.SESSION + "/streams/main/" + Fixtures.DATA_FILES[1],
                     FileNames.dataFile(files.get(2L).getHeader()));
        assertEquals(Fixtures.SESSION + "/streams/" + Fixtures.CHILD_STREAM + "/" + Fixtures.DATA_FILES[2],
                     FileNames.dataFile(files.get(3L).getHeader()));
        assertEquals(Fixtures.SESSION + "/streams/" + Fixtures.CHILD_STREAM + "/" + Fixtures.DATA_FILES[3],
                     FileNames.dataFile(files.get(4L).getHeader()));
        final SessionFlowRound r = Fixtures.round();
        assertEquals("_conversations/" + Fixtures.SESSION + "/rounds/" + Fixtures.ROUND_FILE,
                     FileNames.roundFile(r.getHeader().getConversation(), 1, r.getCommitDigest()));

        // the provider bodies of a session share one directory, beside its streams
        final Map<Long, SessionDataFile> provider = Fixtures.providerBodiesDataFiles();
        assertEquals(Fixtures.PROVIDER_BODIES_SESSION + "/provider_body/" + Fixtures.PROVIDER_BODIES_DATA_FILES[3],
                     FileNames.dataFile(provider.get(4L).getHeader()));
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

    /**
     * A call's provider bodies are references like any other. The round is refused when its bodies do not read as a
     * list of <code>{role, ref}</code>: a role other than request or response, a seq or row below one, a value of
     * another type, or a record past the round's range. A body's own block is kept.
     */
    @Test
    public void aRoundWhoseProviderBodiesDoNotReadIsRefused() throws Exception {
        final String round = new String(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.PROVIDER_BODIES_ROUND_FILE), StandardCharsets.UTF_8);
        final String from = "\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":4,\"row\":1}},";
        assertTrue(round.contains(from));
        final Object[][] cases = {
            {from, true},
            {"\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":4,\"row\":1,\"block\":2}},", true},
            {"\"provider_bodies\":[{\"role\":\"prompt\",\"ref\":{\"seq\":4,\"row\":1}},", false},
            {"\"provider_bodies\":[{\"role\":7,\"ref\":{\"seq\":4,\"row\":1}},", false},
            {"\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":0,\"row\":1}},", false},
            {"\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":4,\"row\":0}},", false},
            {"\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":-4,\"row\":1}},", false},
            {"\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":\"4\",\"row\":1}},", false},
            {"\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":4.0,\"row\":1}},", false},
            {"\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":5,\"row\":1}},", false},
            {"\"provider_bodies\":[null,", false},
        };
        for (final Object[] c : cases) {
            final byte[] changed = round.replace(from, (String) c[0]).getBytes(StandardCharsets.UTF_8);
            if ((Boolean) c[1]) {
                SessionFlowRound.parse(changed);
            } else {
                assertThrows(IllegalArgumentException.class, () -> SessionFlowRound.parse(changed), (String) c[0]);
            }
        }
        // the block is kept, past 32 bits too
        final String wide = "\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":4,\"row\":1,\"block\":2147483648}},";
        assertEquals(Long.valueOf(2147483648L), call(round.replace(from, wide)).getProviderBodies().get(0).getRef().getBlock());
        assertEquals(2147483648L, call(round.replace(from, wide)).getProviderBodies().get(0).getRef().toMap().get("block"));
        // the attribute as a whole: null is no bodies, anything else that is not a list is refused
        SessionFlowRound.parse(round.replaceAll("\"provider_bodies\":\\[[^\\]]*\\],?", "\"provider_bodies\":null,")
                                    .getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> SessionFlowRound.parse(
            round.replaceAll("\"provider_bodies\":\\[[^\\]]*\\],?", "\"provider_bodies\":\"x\",").getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * No frame the Sessionizer writes nests more than a few levels. A frame nested deeper than the reader takes is
     * refused, so writing the document never exhausts the stack; a frame at the limit still reads.
     */
    @Test
    public void aFrameNestedTooDeepIsRefused() throws Exception {
        final String round = new String(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.PROVIDER_BODIES_ROUND_FILE), StandardCharsets.UTF_8);
        final String from = "\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":4,\"row\":1}},";
        // the frame, its attrs and this key are three levels, so these reach the limit and one past it
        final String deep = "\"x\":" + "[".repeat(253) + "0" + "]".repeat(253) + "," + from;
        final String deeper = "\"x\":" + "[".repeat(254) + "0" + "]".repeat(254) + "," + from;
        SessionFlowRound.parse(round.replace(from, deep).getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
            () -> SessionFlowRound.parse(round.replace(from, deeper).getBytes(StandardCharsets.UTF_8)));
    }

    private static SessionFlowRound.Node call(final String round) {
        return SessionFlowRound.parse(round.getBytes(StandardCharsets.UTF_8)).getNodes()
            .stream().filter(n -> "call/s2-call-fdae022ac306".equals(n.getId())).findFirst().orElseThrow();
    }

    /**
     * Record times are RFC 3339, with Z or an offset, and compare as the instants they name, whatever the length of
     * their fractions or the offset they are written in. A time that does not parse sorts after every one that does,
     * and two such times are equal, so the caller decides between them by where each record was read.
     */
    @Test
    public void recordTimesCompareAsInstants() {
        assertEquals(1767225600123456789L, Times.nanos("2026-01-01T00:00:00.123456789Z"));
        assertEquals(1767196800000000000L, Times.nanos("2026-01-01T00:00:00+08:00"));
        assertEquals(0L, Times.nanos("2026-01-01 00:00:00Z"));
        // a year of four digits, as RFC 3339 has it: one past 9999 is not a time, and is never converted
        assertEquals(0L, Times.millis("+999999999-12-31T23:59:59Z"));
        assertEquals(253402300799000L, Times.millis("9999-12-31T23:59:59Z"));
        assertTrue(Times.compare("2026-01-01T00:00:00.11Z", "2026-01-01T00:00:00.1Z") > 0);
        assertTrue(Times.compare("2026-01-01T00:00:00.5Z", "2026-01-01T00:00:01Z") < 0);
        assertEquals(0, Times.compare("2026-01-01T00:00:01Z", "2026-01-01T00:00:01.0Z"));
        assertEquals(0, Times.compare("2026-01-01T08:00:01+08:00", "2026-01-01T00:00:01Z"));
        assertTrue(Times.compare("not a time", "2026-01-01T00:00:01Z") > 0);
        assertEquals(0, Times.compare("not a time", "another"));
    }
}
