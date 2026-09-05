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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

/**
 * The three reads of an AI agent conversation: the rounds of a sender or of one conversation, and the files of
 * one session in a seq window. Every storage implements it once against what it does natively.
 */
public interface IAIAgentConversationQueryDAO extends Service {
    default List<AIAgentSessionFlowRecord> queryRoundsDebuggable(String serviceId,
                                                                 @Nullable String serviceInstanceId,
                                                                 @Nullable String conversation,
                                                                 @Nullable Duration duration,
                                                                 int limit,
                                                                 boolean includeBody) throws IOException {
        final DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryRounds");
                span.setMsg("ServiceId: " + serviceId + ", ServiceInstanceId: " + serviceInstanceId
                                + ", Conversation: " + conversation + ", Duration: " + duration
                                + ", Limit: " + limit + ", IncludeBody: " + includeBody);
            }
            return queryRounds(serviceId, serviceInstanceId, conversation, duration, limit, includeBody);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<AIAgentSessionFlowRecord> queryRoundsByNumberDebuggable(String serviceId,
                                                                         @Nullable String serviceInstanceId,
                                                                         String conversation,
                                                                         long fromRound,
                                                                         long throughRound) throws IOException {
        final DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryRoundsByNumber");
                span.setMsg("ServiceId: " + serviceId + ", ServiceInstanceId: " + serviceInstanceId
                                + ", Conversation: " + conversation + ", Round: " + fromRound + ".." + throughRound);
            }
            return queryRoundsByNumber(serviceId, serviceInstanceId, conversation, fromRound, throughRound);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<AIAgentSessionDataRecord> queryFilesDebuggable(String serviceId,
                                                                @Nullable String serviceInstanceId,
                                                                String session,
                                                                long fromTimestamp,
                                                                long toTimestamp,
                                                                long fromSeq,
                                                                long throughSeq) throws IOException {
        final DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryFiles");
                span.setMsg("ServiceId: " + serviceId + ", ServiceInstanceId: " + serviceInstanceId
                                + ", Session: " + session + ", From: " + fromTimestamp + ", To: " + toTimestamp
                                + ", Seq: " + fromSeq + ".." + throughSeq);
            }
            return queryFiles(serviceId, serviceInstanceId, session, fromTimestamp, toTimestamp, fromSeq, throughSeq);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * Rounds newest first.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender, or null for every sender of the service
     * @param conversation      one conversation, or null for every conversation
     * @param duration          the time window, or null for the whole retention window
     * @param limit             at most this many rows
     * @param includeBody       whether to read the body column; the list page does not
     * @return the rounds, newest first
     * @throws IOException on a storage failure
     */
    List<AIAgentSessionFlowRecord> queryRounds(String serviceId,
                                               @Nullable String serviceInstanceId,
                                               @Nullable String conversation,
                                               @Nullable Duration duration,
                                               int limit,
                                               boolean includeBody) throws IOException;

    /**
     * The rounds of one conversation whose numbers lie in a window, bodies included, in round order, over every
     * retained stage. A round is up to 2 MiB, so the caller reads a long chain window by window.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender, or null for every sender of the service
     * @param conversation      the conversation
     * @param fromRound         the first round number of the window
     * @param throughRound      the last round number of the window
     * @return the rounds of the window, in round order
     * @throws IOException on a storage failure
     */
    List<AIAgentSessionFlowRecord> queryRoundsByNumber(String serviceId, @Nullable String serviceInstanceId,
                                                       String conversation, long fromRound,
                                                       long throughRound) throws IOException;

    /**
     * The files of one session whose seq is within the window and whose timestamp is within the range, with
     * their bodies, seq ascending.
     *
     * @param serviceId         the service
     * @param serviceInstanceId the sender, or null for every sender of the service
     * @param session           the session the files belong to
     * @param fromTimestamp     inclusive start of the timestamp range, milliseconds
     * @param toTimestamp       inclusive end of the timestamp range, milliseconds
     * @param fromSeq           inclusive first seq
     * @param throughSeq        inclusive last seq
     * @return the files, seq ascending
     * @throws IOException on a storage failure
     */
    List<AIAgentSessionDataRecord> queryFiles(String serviceId,
                                              @Nullable String serviceInstanceId,
                                              String session,
                                              long fromTimestamp,
                                              long toTimestamp,
                                              long fromSeq,
                                              long throughSeq) throws IOException;
}
