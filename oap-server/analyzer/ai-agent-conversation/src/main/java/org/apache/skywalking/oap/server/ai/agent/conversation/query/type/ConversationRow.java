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
    private long from;
    private long to;
}
