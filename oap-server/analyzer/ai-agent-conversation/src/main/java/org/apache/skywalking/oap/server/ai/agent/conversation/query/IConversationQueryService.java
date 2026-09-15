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
import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * The GraphQL list of <code>ai-agent-conversation.graphqls</code>, and what the two HTTP routes serve: the
 * conversation's <code>asz.view</code> document, and its chosen stored files.
 */
public interface IConversationQueryService extends Service {
    /**
     * One row per conversation, from the newest round's attributes; nothing decoded.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender, or null
     * @param conversation      one conversation by id, or null for every conversation of the service
     * @param title             text the row's title must contain, case-insensitively, or null
     * @param duration          the window
     * @param limit             at most this many rounds read, newest first, before folding; null for the default
     * @return the rows, newest first
     * @throws IOException on a storage failure
     */
    ConversationList listConversations(String serviceId, @Nullable String serviceInstanceId,
                                       @Nullable String conversation, @Nullable String title, Duration duration,
                                       @Nullable Integer limit) throws IOException;

    /**
     * The whole conversation, once, as one <code>asz.view</code> document, built on every call.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender
     * @param conversation      the conversation
     * @param coldStage         whether the caller explicitly selected BanyanDB's cold stage
     * @return the document as ordered maps, or null when the sender stores no round of the conversation
     * @throws IOException on a storage failure
     */
    @Nullable
    Map<String, Object> buildConversationView(String serviceId, @Nullable String serviceInstanceId,
                                              String conversation, boolean coldStage, BooleanSupplier alive) throws IOException;

    /**
     * The chosen Session Data files of a conversation's session, as stored, handed to the sink one by one as they
     * are read, one storage window at a time. A file is chosen by its landed seq, which the Sessionizer assigns once
     * per file in a session. The files come in seq order, which is the order a reader of provider bodies must add
     * them in. A seq no stored file answers is left out.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender
     * @param conversation      the conversation, whose rounds give the time range the files are read over
     * @param session           the session the seqs belong to
     * @param seqs              the landed seqs
     * @param coldStage         whether the caller explicitly selected BanyanDB's cold stage
     * @param sink              takes each file as it is read
     * @return false when the sender stores no round of the conversation, before the sink is called
     * @throws IOException on a storage failure, or when the sink fails
     */
    boolean readConversationFiles(String serviceId, String serviceInstanceId, String conversation, String session,
                                  Collection<Long> seqs, boolean coldStage, BooleanSupplier alive, FileSink sink) throws IOException;

    /**
     * Takes the stored files of {@link #readConversationFiles} as they are read.
     */
    interface FileSink {
        /**
         * @param file one stored file
         * @throws IOException when the file cannot be passed on, such as when the client went away
         */
        void accept(ConversationFile file) throws IOException;
    }
}
