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
     * How many Session Data files one storage read fetches. Files are cut at 2 MiB, so a window is a few tens of
     * megabytes; the window times {@link #maxFileBytes} bounds what one read can answer with, and
     * {@link #maxResponseBytes} must cover it.
     */
    private int fileReadWindow = 16;
    /**
     * How many Session Flow rounds one storage read fetches. A round is cut at 2 MiB by the Sessionizer, and the
     * same bound as for files applies.
     */
    private int roundReadWindow = 16;
    /**
     * The most bytes one window read may answer with, applied to that read alone on a storage that caps a
     * response per call: BanyanDB's client holds every other read to 50 MB. 100 MiB by default, above sixteen
     * files at the 2 MiB cut with room for files landed whole; a root of larger files needs it raised or the
     * windows lowered, so that the window times {@link #maxFileBytes} stays under it.
     */
    private int maxResponseBytes = 100 * 1024 * 1024;
    /**
     * How long one conversation view request may take, in seconds, in place of the HTTP server's default of
     * ten: the floor is the storage read of every landed file plus the fold and the render, which is seconds
     * for a conversation of a hundred megabytes.
     */
    private int viewRequestTimeout = 120;
    /**
     * The most rounds one list query reads before folding to one row per conversation, and the ceiling of the
     * query's own limit argument.
     */
    private int maxListLimit = 10000;
    /**
     * The largest file stored, in bytes; a larger one is rejected at ingest and counted under the reason
     * <code>size</code>. Below BanyanDB's 16 MiB gRPC message limit, because one file over the limit fails the
     * storage write it travels in, and every record behind it in that write is lost with it. The Sessionizer cuts files and rounds at 2 MiB; a round from before that cut can be larger.
     */
    private int maxFileBytes = 15 * 1024 * 1024;
}
