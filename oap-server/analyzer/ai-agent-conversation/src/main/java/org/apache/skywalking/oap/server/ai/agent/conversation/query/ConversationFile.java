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
 */

package org.apache.skywalking.oap.server.ai.agent.conversation.query;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * One stored Session Data file of a conversation's session, with its bytes as they are served: as stored, or
 * with what the reader withholds masked inside them.
 */
@Getter
public final class ConversationFile {
    /** The file's relative path in the Sessionizer's storage root. */
    private final String id;
    /** The file's landed seq, unique within its session. */
    private final long seq;
    /** The sha256 of the body as stored, which the document's file list names. */
    private final String digest;
    private final byte[] body;
    /**
     * How many files this read saw under that sequence. One, unless the same sequence was stored with
     * different bytes, which takes two roots of one session pushed by one sender. The file served is the
     * first; a reader is told the others are there so it can say so rather than show one copy as the truth.
     *
     * <p>It counts what the read returned, not what the storage holds: Elasticsearch caps a search by hits
     * and BanyanDB by its result window, so a sequence stored more times than a window carries counts as
     * what came back. It says "more than one", never "exactly this many".
     */
    private final int copies;
    /** The names withheld from the body, sorted; empty when the body is the stored one. */
    private final List<String> withheld;
    /** The sha256 of the body as served, when something was withheld from it; null otherwise. */
    @Nullable
    private final String servedDigest;

    public ConversationFile(final String id, final long seq, final String digest, final byte[] body, final int copies) {
        this(id, seq, digest, body, copies, Collections.emptyList(), null);
    }

    public ConversationFile(final String id, final long seq, final String digest, final byte[] body, final int copies,
                            final List<String> withheld, @Nullable final String servedDigest) {
        this.id = id;
        this.seq = seq;
        this.digest = digest;
        this.body = body;
        this.copies = copies;
        this.withheld = withheld;
        this.servedDigest = servedDigest;
    }
}
