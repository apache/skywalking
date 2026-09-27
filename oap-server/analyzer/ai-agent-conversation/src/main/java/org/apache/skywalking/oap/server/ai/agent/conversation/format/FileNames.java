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
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * A landed file's name follows the storage-root layout from its header line, so the name is never stored and is
 * derived on read. A query chooses a file by what the storage holds, its session and its seq, not by its name.
 *
 * <pre>
 * &lt;session&gt;/streams/&lt;stream&gt;/transcript-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/streams/&lt;stream&gt;/meta-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/streams/&lt;stream&gt;/changes-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/streams/&lt;stream&gt;/execution-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/runs/&lt;run&gt;/journal-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/runs/&lt;run&gt;/manifest-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/runs/&lt;run&gt;/script-&lt;stamp&gt;-&lt;seq&gt;.sd
 * &lt;session&gt;/provider_body/provider_body-&lt;stamp&gt;-&lt;seq&gt;.sd
 * _conversations/&lt;conversation&gt;/rounds/r&lt;round&gt;-&lt;digest12&gt;.sf
 * </pre>
 */
public final class FileNames {
    private FileNames() {
    }

    /**
     * @param header the header line of a Session Data file
     * @return the file's relative path in the storage root
     */
    public static String dataFile(final SessionDataFile.Header header) {
        final Place place = place(header);
        final String stamp = Times.fileStamp(header.getAt());
        return header.getSession() + "/" + place.directory + "/" + place.prefix + "-" + (stamp == null ? "unknown" : stamp)
            + "-" + String.format(Locale.ROOT, "%06d", header.getSeq()) + ".sd";
    }

    /**
     * @param header the header line of a Session Data file
     * @return the directory the file lands in under its session: <code>streams/&lt;stream&gt;</code> for a stream's
     * records, <code>runs/&lt;run&gt;</code> for a workflow run's, and <code>provider_body</code> for the session's
     * provider bodies
     */
    public static String directory(final SessionDataFile.Header header) {
        return place(header).directory;
    }

    /**
     * @param header the header line of a Session Data file
     * @return the lane the file's records are in: its stream's or its workflow run's directory, or empty for the
     * session's provider bodies, which belong to no stream or run
     */
    public static String lane(final SessionDataFile.Header header) {
        return "provider_body".equals(header.getKind()) ? "" : place(header).directory;
    }

    /** Where a file of one kind lands: the prefix of its name and its directory under the session. */
    private static final class Place {
        final String prefix;
        final String directory;

        Place(final String prefix, final String directory) {
            this.prefix = prefix;
            this.directory = directory;
        }
    }

    private static Place place(final SessionDataFile.Header header) {
        switch (header.getKind() == null ? "" : header.getKind()) {
            case "transcript":
                return new Place("transcript", "streams/" + header.getStream());
            case "agent_meta":
                return new Place("meta", "streams/" + header.getStream());
            case "changes":
                // the plugin's workspace change records, beside the transcript of the stream the tool ran under
                return new Place("changes", "streams/" + header.getStream());
            case "execution":
                // what the plugin saw each tool call do after the model asked for it, beside the transcript of the
                // stream the call ran under
                return new Place("execution", "streams/" + header.getStream());
            case "journal":
                return new Place("journal", "runs/" + header.getBatch());
            case "workflow_manifest":
                return new Place("manifest", "runs/" + header.getBatch());
            case "workflow_script":
                return new Place("script", "runs/" + header.getBatch());
            case "provider_body":
                // the bodies a runtime exchanged with its model provider, one directory for the session: a body is
                // evidence beside a call of any stream, and a later body refers to earlier ones of every stream
                return new Place("provider_body", "provider_body");
            default:
                return new Place(header.getKind() == null ? "file" : header.getKind(),
                                 StringUtil.isNotEmpty(header.getStream()) ? "streams/" + header.getStream()
                                     : "runs/" + header.getBatch());
        }
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
}
