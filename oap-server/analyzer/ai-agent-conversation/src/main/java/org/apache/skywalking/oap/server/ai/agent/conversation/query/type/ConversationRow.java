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

package org.apache.skywalking.oap.server.ai.agent.conversation.query.type;

import lombok.Data;

/**
 * One row per conversation on the list page, from the newest round's attributes.
 */
@Data
public class ConversationRow {
    private String conversation;
    private String serviceInstanceId;
    private String serviceInstanceName;
    private String title;
    private int round;
    private int talks;
    private int steps;
    private int streams;
    private int segments;
    private int unresolved;
    // Distinct change records the Sessionizer had captured as of the head round, one per tool call a producer watched; null when the round did not carry the count.
    private Integer changes;
    // What those records' diffs add and remove; null when the round did not carry them.
    private Integer linesAdded;
    private Integer linesRemoved;
    // Provider calls, child agents started, and shell commands run through the runtime's Bash tool; null when the round did not carry them.
    private Integer llmCalls;
    private Integer subagents;
    private Integer bashRuns;
    private long from;
    private long to;
}
