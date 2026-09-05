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

package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.server.ai.agent.conversation.AIAgentConversationModule;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.input.ConversationCondition;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.input.ConversationListCondition;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationList;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.type.ConversationRawFiles;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.InstanceCondition;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static org.apache.skywalking.oap.query.graphql.AsyncQueryUtils.queryAsync;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

/**
 * Resolvers of <code>ai-agent-conversation.graphqls</code>. The conversation view is not a GraphQL query; it is
 * the module's own HTTP route on the same server, see <code>ConversationViewHandler</code>.
 */
public class AIAgentConversationQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private IConversationQueryService queryService;

    public AIAgentConversationQuery(final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IConversationQueryService getQueryService() {
        if (queryService == null) {
            queryService = moduleManager.find(AIAgentConversationModule.NAME)
                                        .provider()
                                        .getService(IConversationQueryService.class);
        }
        return queryService;
    }

    public CompletableFuture<ConversationList> listConversations(final ConversationListCondition condition,
                                                                 final Duration duration,
                                                                 final boolean debug) {
        return queryAsync(() -> {
            final DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "ConversationListCondition: " + condition + ", Duration: " + duration, debug, false);
            TRACE_CONTEXT.set(traceContext);
            final DebuggingSpan span = traceContext.createSpan("Query AI agent conversations");
            try {
                final ConversationList list = getQueryService().listConversations(
                    condition.getService().getServiceId(),
                    instanceId(condition.getInstance()),
                    duration,
                    condition.getLimit()
                );
                if (debug) {
                    list.setDebuggingTrace(traceContext.getExecTrace());
                }
                return list;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public CompletableFuture<ConversationRawFiles> getConversationRawFiles(final ConversationCondition condition,
                                                                           final List<String> files,
                                                                           final boolean debug,
                                                                           final DataFetchingEnvironment env) {
        // The body is read from storage only when the client selected it; selecting it on every file is the
        // export path.
        final boolean includeBody = env != null && env.getSelectionSet() != null
            && env.getSelectionSet().contains("files/body");
        return queryAsync(() -> {
            final DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "ConversationCondition: " + condition + ", Files: " + files, debug, false);
            TRACE_CONTEXT.set(traceContext);
            final DebuggingSpan span = traceContext.createSpan("Query AI agent conversation raw files");
            try {
                final ConversationRawFiles raw = getQueryService().getConversationRawFiles(
                    condition.getService().getServiceId(),
                    instanceId(condition.getInstance()),
                    condition.getConversation(),
                    files,
                    includeBody
                );
                if (debug) {
                    raw.setDebuggingTrace(traceContext.getExecTrace());
                }
                return raw;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    private static String instanceId(final InstanceCondition instance) {
        return instance == null ? null : instance.getInstanceId();
    }
}
