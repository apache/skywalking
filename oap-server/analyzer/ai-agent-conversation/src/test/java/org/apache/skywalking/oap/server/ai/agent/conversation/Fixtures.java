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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;

/**
 * The Sessionizer's fixture scenario <code>tests/scenarios/fixture.yaml</code>, built with
 * <code>asz scenario build --format sd --at 2026-01-01T00:00:00Z</code> and parsed: three Session Data files and one
 * round, byte for byte as the Sessionizer wrote them, so every digest is real, and the <code>asz.view</code>
 * document <code>asz conversation -json</code> printed for them, which is the document the OAP must equal.
 */
public final class Fixtures {
    public static final String SESSION = "00000001-0000-4000-8000-000000000001";
    public static final String[] DATA_FILES = {
        "transcript-20260101T000000.000000000Z-000001.sd",
        "transcript-20260101T000000.000000000Z-000002.sd",
        "meta-20260101T000000.000000000Z-000003.sd",
    };
    public static final String CHILD_STREAM = "a0a10ef0666c4dc7e";
    public static final String ROUND_FILE = "r000001-3ad0dcd4cd53.sf";
    public static final String VIEW_EXAMPLE_JSON = "asz-view-example.json";

    private Fixtures() {
    }

    public static byte[] bytes(final String name) throws IOException {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IOException("no fixture " + name);
            }
            return in.readAllBytes();
        }
    }

    public static Map<Long, SessionDataFile> dataFiles() throws IOException {
        final Map<Long, SessionDataFile> out = new TreeMap<>();
        for (final String name : DATA_FILES) {
            final SessionDataFile f = SessionDataFile.parse(bytes(name));
            out.put(f.getHeader().getSeq(), f);
        }
        return out;
    }

    public static SessionFlowRound round() throws IOException {
        return SessionFlowRound.parse(bytes(ROUND_FILE));
    }

    /**
     * A round with no entities of its own, signed like a real one: it continues the fixture's conversation, session
     * and policy under the parser given, names the previous digest given, or none for round 1, and claims the seq
     * window given. For chain tests that need a round the fixture set does not hold.
     *
     * @param like       the round whose conversation, session and policy are kept
     * @param round      the round number
     * @param previous   the previous round's commit digest, or null for round 1
     * @param fromSeq    the first seq of the window
     * @param throughSeq the last seq of the window
     * @param parser     the parser version the header names
     * @return the round's bytes
     */
    public static byte[] emptyRound(final SessionFlowRound like, final long round, final String previous,
                                    final long fromSeq, final long throughSeq, final String parser) {
        final SessionFlowRound.Header h = like.getHeader();
        final String header = "{\"t\":\"header\",\"schema\":\"sf/1\",\"conversation\":\"" + h.getConversation()
            + "\",\"session\":\"" + h.getSession() + "\",\"round\":" + round
            + (previous == null ? "" : ",\"previous\":\"" + previous + "\"")
            + ",\"from_seq\":" + fromSeq + ",\"through_seq\":" + throughSeq
            + ",\"input_digest\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\""
            + ",\"parser\":\"" + parser + "\",\"policy\":\"" + h.getPolicy() + "\"}";
        final String commit = "{\"t\":\"commit\",\"digest\":\""
            + Digests.sha256Hex((header + "\n").getBytes(StandardCharsets.UTF_8)) + "\"}";
        return (header + "\n" + commit + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
