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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.process;

import java.util.HashMap;

/**
 * ProcessNetworkRelationIDs indicates the IDs with the priority for process network relation.
 *
 * @since 9.4.0
 */
public enum ProcessNetworkRelationIDs {
    HTTPS(129, 4),
    HTTP(49, 3),
    TCP_TLS(130, 2),
    TCP(110, 1);

    /**
     * ID from component-libraries.yml definition.
     */
    private Integer id;
    /**
     * The higher the atomic number, the higher the priority
     */
    private Integer priority;

    ProcessNetworkRelationIDs(final int id, final int priority) {
        this.id = id;
        this.priority = priority;
    }

    /**
     * @return true if componentA has higher priority
     */
    public static boolean compare(int componentA, int componentB) {
        final Integer priorityA = (Integer) ID_2_PRIORITY.getOrDefault(componentA, 0);
        final Integer priorityB = (Integer) ID_2_PRIORITY.getOrDefault(componentB, 0);
        return priorityA.compareTo(priorityB) > 0;
    }

    private static HashMap ID_2_PRIORITY = new HashMap<Integer, Integer>(4);

    static {
        initMapping(TCP);
        initMapping(TCP_TLS);
        initMapping(HTTP);
        initMapping(HTTPS);
    }

    private static void initMapping(ProcessNetworkRelationIDs componentId) {
        ID_2_PRIORITY.put(componentId.id, componentId.priority);
    }
}
