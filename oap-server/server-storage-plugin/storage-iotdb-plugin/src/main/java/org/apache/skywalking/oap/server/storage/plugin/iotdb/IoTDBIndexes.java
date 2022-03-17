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

package org.apache.skywalking.oap.server.storage.plugin.iotdb;

public interface IoTDBIndexes {
    // Here is the indexes we choose and their order in storage path.
    String ID_IDX = "id";
    String ENTITY_ID_IDX = "entity_id";
    String LAYER_IDX = "layer";
    String SERVICE_ID_IDX = "service_id";
    String GROUP_IDX = "service_group";
    String TRACE_ID_IDX = "trace_id";
    String INSTANCE_ID_INX = "instance_id";
    String PROCESS_ID_INX = "process_id";
    String AGENT_ID_INX = "agent_id";

    static boolean isIndex(String key) {
        return key.equals(ID_IDX) || key.equals(ENTITY_ID_IDX) || key.equals(LAYER_IDX) ||
                key.equals(SERVICE_ID_IDX) || key.equals(GROUP_IDX) || key.equals(TRACE_ID_IDX) ||
                key.equals(INSTANCE_ID_INX) || key.equals(AGENT_ID_INX) || key.equals(PROCESS_ID_INX);
    }
}
