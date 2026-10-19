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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A history.change card stands at its call's first record and comes just before that call: at one position it is
 * ordered by the call it names, and before it, as the Sessionizer orders it (pkg/sessionflow, Before).
 */
public class CardOrderTest {
    private static SessionFlowRound.Node node(final String id, final String kind) {
        final JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("kind", kind);
        final JsonObject ref = new JsonObject();
        ref.addProperty("seq", 1);
        ref.addProperty("row", 1);
        json.add("ref", ref);
        return new SessionFlowRound.Node(json, Collections.emptyList());
    }

    @Test
    public void aCardComesJustBeforeItsCallWhateverItIsGivenAfter() {
        final List<SessionFlowRound.Node> all = Arrays.asList(
            node("call/a", "llm.call"), node("call/b", "llm.call"),
            node("history/b", "history.change"), node("input/1/1", "message.external"));
        for (int i = 0; i < all.size(); i++) {
            final List<SessionFlowRound.Node> in = new ArrayList<>(all.subList(i, all.size()));
            in.addAll(all.subList(0, i));
            final List<String> ids = new ArrayList<>();
            for (final SessionFlowRound.Node n : ConversationFold.inOrder(in)) {
                ids.add(n.getId());
            }
            assertEquals(Arrays.asList("call/a", "history/b", "call/b", "input/1/1"), ids);
        }
    }
}
