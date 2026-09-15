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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class AIAgentConversationConfig extends ModuleConfig {
    /**
     * The most rounds one conversation list reads, and the ceiling of the limit a caller may ask for. It
     * counts rounds, not conversations: the newest rounds in the window are folded to one row each, so a
     * busy conversation spends the budget of the quiet ones and a quiet one can fall off the list.
     */
    private int conversationListMaxLimit = 10000;
    /**
     * How long one conversation view request may take, in seconds, in place of the HTTP server's default of
     * ten. The whole chain is folded before the first byte is written, which is seconds for a conversation
     * of a hundred megabytes.
     */
    private int viewRequestTimeout = 120;
    /**
     * How many Session Data files, or Session Flow rounds, one storage query fetches. A batch size and not a
     * limit: a view reads every round and every file of its conversation, this many per query, so raising it
     * trades bytes in one response for round trips. It must stay within {@link #maxResponseBytes}.
     */
    private int readWindow = 16;
    /**
     * The most bytes one storage query may answer with. BanyanDB alone accepts it, as a per-call option
     * raising the 50 MB its client holds every other read to; Elasticsearch and JDBC ignore it and bound a
     * read by hits and by rows. A window of sixteen files at the Sessionizer's 2 MiB cut is a few tens of
     * megabytes, so only a root whose files land whole needs this raised.
     */
    private int maxResponseBytes = 100 * 1024 * 1024;
    /**
     * The largest file stored; a larger one is rejected at ingest and counted under the reason
     * <code>size</code>, because one file over the storage's message limit fails the bulk write it travels
     * in and every record behind it. 15 MiB, under BanyanDB's 16 MiB; lowering it is how a test proves the
     * rejection without pushing a file that size.
     */
    private int maxFileBytes = 15 * 1024 * 1024;
}
