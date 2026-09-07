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

import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Conversations of long-lived AI agents, landed by the AI Sessionizer as Session Data (<code>.sd</code>) and
 * Session Flow (<code>.sf</code>) files over OTLP logs under the <code>AI_AGENT</code> layer.
 *
 * <p>Ingest is a LAL output builder, {@code ConversationFile}, that the bundled <code>lal/ai-agent.yaml</code>
 * rule names; it verifies each file and stores it verbatim. The read side folds a conversation's rounds and
 * resolves every reference into the landed records, answering with one <code>asz.view</code> document.
 */
public class AIAgentConversationModule extends ModuleDefine {
    public static final String NAME = "ai-agent-conversation";

    public AIAgentConversationModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            IConversationQueryService.class
        };
    }
}
