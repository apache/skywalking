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

package org.apache.skywalking.oap.server.ai.agent.conversation.query;

import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.core.query.input.Duration;

/**
 * Answers every query of a disabled module with nothing, so that the GraphQL query module, which requires the
 * {@link org.apache.skywalking.oap.server.ai.agent.conversation.AIAgentConversationModule}, still boots and its
 * conversation list answers instead of failing. The module registers no HTTP route, so the document and the files
 * are never asked for; asked directly, they are not there.
 */
public class NoneConversationQueryService implements IConversationQueryService {
    private static final String DISABLED =
        "The ai-agent-conversation module is disabled, its selector is none.";

    @Override
    public ConversationList listConversations(final String serviceId,
                                              @Nullable final String serviceInstanceId,
                                              @Nullable final String conversation,
                                              @Nullable final String title,
                                              final Duration duration,
                                              @Nullable final Integer limit) {
        final ConversationList list = new ConversationList();
        list.setErrorReason(DISABLED);
        return list;
    }

    @Nullable
    @Override
    public Map<String, Object> buildConversationView(final String serviceId,
                                                     @Nullable final String serviceInstanceId,
                                                     final String conversation, final boolean coldStage,
                                                     final BooleanSupplier alive) {
        return null;
    }

    @Override
    public boolean readConversationFiles(final String serviceId, final String serviceInstanceId,
                                         final String conversation, final String session,
                                         final Collection<Long> seqs, final boolean coldStage,
                                         final BooleanSupplier alive, final FileSink sink) {
        return false;
    }
}
