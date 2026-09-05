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

import com.linecorp.armeria.common.HttpMethod;
import java.time.Duration;
import java.util.Collections;
import org.apache.skywalking.oap.server.ai.agent.conversation.ingest.ConversationFileBuilder;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.ConversationQueryService;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.http.ConversationViewHandler;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

public class AIAgentConversationProvider extends ModuleProvider {
    private AIAgentConversationConfig config = new AIAgentConversationConfig();
    private ConversationQueryService queryService;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AIAgentConversationModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<AIAgentConversationConfig>() {
            @Override
            public Class<AIAgentConversationConfig> type() {
                return AIAgentConversationConfig.class;
            }

            @Override
            public void onInitialized(final AIAgentConversationConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        if (config.getFileReadWindow() <= 0) {
            throw new ModuleStartException("fileReadWindow should be greater than 0");
        }
        if (config.getRoundReadWindow() <= 0) {
            throw new ModuleStartException("roundReadWindow should be greater than 0");
        }
        if (config.getMaxListLimit() <= 0) {
            throw new ModuleStartException("maxListLimit should be greater than 0");
        }
        if (config.getViewRequestTimeout() <= 0) {
            throw new ModuleStartException("viewRequestTimeout should be greater than 0");
        }
        queryService = new ConversationQueryService(getManager(), config);
        registerServiceImplementation(IConversationQueryService.class, queryService);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        getManager().find(CoreModule.NAME)
                    .provider()
                    .getService(HTTPHandlerRegister.class)
                    .addHandler(
                        new ConversationViewHandler(queryService, Duration.ofSeconds(config.getViewRequestTimeout())),
                        Collections.singletonList(HttpMethod.GET));
        final MetricsCreator metricsCreator = getManager().find(TelemetryModule.NAME)
                                                          .provider()
                                                          .getService(MetricsCreator.class);
        ConversationFileBuilder.setMetrics(
            metricsCreator.createCounter(
                "ai_agent_conversation_files_accepted", "AI agent conversation files verified and stored",
                new MetricsTag.Keys("format"), new MetricsTag.Values("sd")),
            metricsCreator.createCounter(
                "ai_agent_conversation_files_accepted", "AI agent conversation files verified and stored",
                new MetricsTag.Keys("format"), new MetricsTag.Values("sf")),
            metricsCreator.createCounter(
                "ai_agent_conversation_files_rejected", "AI agent conversation files rejected at ingest",
                new MetricsTag.Keys("reason"), new MetricsTag.Values("digest")),
            metricsCreator.createCounter(
                "ai_agent_conversation_files_rejected", "AI agent conversation files rejected at ingest",
                new MetricsTag.Keys("reason"), new MetricsTag.Values("lines")),
            metricsCreator.createCounter(
                "ai_agent_conversation_files_rejected", "AI agent conversation files rejected at ingest",
                new MetricsTag.Keys("reason"), new MetricsTag.Values("attributes"))
        );
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            StorageModule.NAME,
            TelemetryModule.NAME
        };
    }
}
