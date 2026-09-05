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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRawFiles;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * The two GraphQL operations of <code>ai-agent-conversation.graphqls</code> and the document behind the
 * conversation view route.
 */
public interface IConversationQueryService extends Service {
    /**
     * One row per conversation, from the newest round's attributes; nothing decoded.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender, or null
     * @param duration          the window
     * @param limit             at most this many rounds read, newest first, before folding; null for the default
     * @return the rows, newest first
     * @throws IOException on a storage failure
     */
    ConversationList listConversations(String serviceId, @Nullable String serviceInstanceId, Duration duration,
                                       @Nullable Integer limit) throws IOException;

    /**
     * The whole conversation, once, as one <code>asz.view</code> document, built on every call.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender, or null
     * @param conversation      the conversation
     * @return the document as ordered maps, or null when the service stores no round of the conversation
     * @throws IOException on a storage failure
     */
    @Nullable
    Map<String, Object> buildConversationView(String serviceId, @Nullable String serviceInstanceId,
                                              String conversation) throws IOException;

    /**
     * Every landed file and round of a conversation as stored, or only the named ones.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender, or null
     * @param conversation      the conversation
     * @param files             only these file ids, or null for every file
     * @param includeBody       whether the caller selected the body field
     * @return the files
     * @throws IOException on a storage failure
     */
    ConversationRawFiles getConversationRawFiles(String serviceId, @Nullable String serviceInstanceId,
                                                 String conversation, @Nullable List<String> files,
                                                 boolean includeBody) throws IOException;
}
