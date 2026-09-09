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

import java.util.Collections;
import org.apache.skywalking.oap.server.ai.agent.conversation.query.IConversationQueryService;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoneAIAgentConversationProviderTest {
    @Test
    public void testTheDisabledModuleStoresAndAnswersNothing() throws Exception {
        final NoneAIAgentConversationProvider provider = new NoneAIAgentConversationProvider();
        assertEquals("none", provider.name());
        assertEquals(AIAgentConversationModule.class, provider.module());
        assertNull(provider.newConfigCreator());
        assertEquals(0, provider.requiredModules().length);

        provider.prepare();

        // Without the models the storage creates neither table, and RecordStreamProcessor has no worker to
        // dispatch a file to.
        assertTrue(DisableRegister.INSTANCE.include(AIAgentSessionDataRecord.INDEX_NAME));
        assertTrue(DisableRegister.INSTANCE.include(AIAgentSessionFlowRecord.INDEX_NAME));

        final IConversationQueryService service = provider.getService(IConversationQueryService.class);
        assertNotNull(service);
        assertTrue(service.listConversations("1", null, null, null, new Duration(), null)
                          .getConversations()
                          .isEmpty());
        assertNotNull(service.listConversations("1", null, null, null, new Duration(), null).getErrorReason());
        assertNull(service.buildConversationView("1", null, "c"));
        assertTrue(service.getConversationRawFiles("1", null, "c", Collections.emptyList(), true)
                          .getFiles()
                          .isEmpty());
    }
}
