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
import org.apache.skywalking.oap.server.ai.agent.conversation.query.NoneConversationQueryService;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * The module doing nothing: no model is created in the storage, no file is stored, no query is answered and the
 * conversation view route is not registered. The module cannot be removed with the <code>-</code> selector,
 * because the GraphQL query module requires it, so this provider is how the feature is turned off.
 */
public class NoneAIAgentConversationProvider extends ModuleProvider {
    @Override
    public String name() {
        return "none";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AIAgentConversationModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return null;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        // The core module scans @Stream in its start(), after every provider's prepare(), so the two models are
        // disabled here: their tables are never created, nor the BanyanDB group they are the only members of.
        // A record with no worker is dropped by RecordStreamProcessor, so the LAL rule stores nothing either;
        // drop `ai-agent` from SW_LOG_LAL_FILES to skip verifying the files it still parses.
        DisableRegister.INSTANCE.add(AIAgentSessionDataRecord.INDEX_NAME);
        DisableRegister.INSTANCE.add(AIAgentSessionFlowRecord.INDEX_NAME);
        registerServiceImplementation(IConversationQueryService.class, new NoneConversationQueryService());
    }

    @Override
    public void start() throws ServiceNotProvidedException {
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
