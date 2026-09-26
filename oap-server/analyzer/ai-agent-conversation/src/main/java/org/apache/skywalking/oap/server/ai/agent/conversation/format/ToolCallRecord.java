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

import java.util.Map;

/**
 * A record about one tool call, which names the call by its tool-use id: a workspace change record or a tool
 * execution record. The tool-use id is the only join between the record and the step of the call.
 */
public interface ToolCallRecord {
    /**
     * @return the record's own id
     */
    String getId();

    /**
     * @return the tool-use id of the call the record names; empty on a record that names none
     */
    String getTool();

    /**
     * @return when the observation ended, RFC 3339 as written
     */
    String getTime();

    /**
     * @return the record's fields as the Sessionizer prints them, keys in the order its struct lists them
     */
    Map<String, Object> fields();
}
