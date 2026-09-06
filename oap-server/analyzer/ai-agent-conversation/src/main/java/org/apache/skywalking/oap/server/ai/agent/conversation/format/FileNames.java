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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * A landed file's name follows the storage-root layout from its header line, so the name is never stored and is
 * derived on read, and a name given to a query is parsed back into what it encodes: a session and a seq, or a
 * round.
 *
 * <pre>
 * &lt;session&gt;/streams/&lt;stream&gt;/transcript-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/streams/&lt;stream&gt;/meta-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/runs/&lt;run&gt;/journal-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/runs/&lt;run&gt;/manifest-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/runs/&lt;run&gt;/script-&lt;stamp&gt;-&lt;seq&gt;.sd
 * _conversations/&lt;conversation&gt;/rounds/r&lt;round&gt;-&lt;digest12&gt;.sf
 * </pre>
 */
public final class FileNames {
    private static final Pattern DATA_FILE =
        Pattern.compile("^(?<session>[^/]+)/(streams|runs)/[^/]+/[a-z]+-[^/-]+-(?<seq>\\d{6,})\\.sd$");
    private static final Pattern ROUND_FILE =
        Pattern.compile("^_conversations/(?<conversation>[^/]+)/rounds/r(?<round>\\d{6,})-[0-9a-f]+\\.sf$");

    private FileNames() {
    }

    /**
     * @param header the header line of a Session Data file
     * @return the file's relative path in the storage root
     */
    public static String dataFile(final SessionDataFile.Header header) {
        final String prefix;
        final String dir;
        switch (header.getKind() == null ? "" : header.getKind()) {
            case "transcript":
                prefix = "transcript";
                dir = "streams/" + header.getStream();
                break;
            case "agent_meta":
                prefix = "meta";
                dir = "streams/" + header.getStream();
                break;
            case "journal":
                prefix = "journal";
                dir = "runs/" + header.getBatch();
                break;
            case "workflow_manifest":
                prefix = "manifest";
                dir = "runs/" + header.getBatch();
                break;
            case "workflow_script":
                prefix = "script";
                dir = "runs/" + header.getBatch();
                break;
            default:
                prefix = header.getKind() == null ? "file" : header.getKind();
                dir = StringUtil.isNotEmpty(header.getStream())
                    ? "streams/" + header.getStream()
                    : "runs/" + header.getBatch();
                break;
        }
        final String stamp = Times.fileStamp(header.getAt());
        return header.getSession() + "/" + dir + "/" + prefix + "-" + (stamp == null ? "unknown" : stamp)
            + "-" + String.format(Locale.ROOT, "%06d", header.getSeq()) + ".sd";
    }

    /**
     * @param conversation the conversation
     * @param round        the round number
     * @param commitDigest the round's commit digest
     * @return the round file's relative path in the storage root
     */
    public static String roundFile(final String conversation, final long round, final String commitDigest) {
        final String digest12 = commitDigest == null ? "" : commitDigest.substring(0, Math.min(12, commitDigest.length()));
        return "_conversations/" + conversation + "/rounds/r" + String.format(Locale.ROOT, "%06d", round)
            + "-" + digest12 + ".sf";
    }

    /**
     * @param id a file id as returned by the raw-files query
     * @return what the id names, or null when it is not a landed file or round name
     */
    @Nullable
    public static Parsed parse(final String id) {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        final Matcher data = DATA_FILE.matcher(id);
        if (data.matches()) {
            return new Parsed(data.group("session"), Long.parseLong(data.group("seq")), null, -1);
        }
        final Matcher round = ROUND_FILE.matcher(id);
        if (round.matches()) {
            return new Parsed(null, -1, round.group("conversation"), Long.parseLong(round.group("round")));
        }
        return null;
    }

    @Getter
    @RequiredArgsConstructor
    public static final class Parsed {
        private final String session;
        private final long seq;
        private final String conversation;
        private final long round;

        public boolean isDataFile() {
            return session != null;
        }
    }
}
