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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PhysicalIndices {

    /**
     * The relations of the logic table and the physical table.
     */
    private static final Map<String, String> RELATIONS = new ConcurrentHashMap<>();

    public static String getPhysicalTableName(String logicName) {
        return Optional.of(RELATIONS.get(logicName)).orElse(logicName);
    }

    public static void registerRelation(String logicName, String physicalName) {
        RELATIONS.put(logicName, physicalName);
    }
}
