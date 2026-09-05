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
     * How many Session Data files one storage read fetches. Files are cut at 2 MiB, and the BanyanDB client caps
     * one response at 50 MB, so the window keeps a single response well under the cap.
     */
    private int fileReadWindow = 16;
    /**
     * How many Session Flow rounds one storage read fetches. A round is cut at 2 MiB by the Sessionizer, so the
     * window keeps a single response well under the BanyanDB client's cap and Elasticsearch's result window.
     */
    private int roundReadWindow = 16;
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
}
