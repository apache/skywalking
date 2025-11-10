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

package org.apache.skywalking.oap.server.core.alarm;

import org.apache.skywalking.oap.server.library.module.Service;

public interface AlarmStatusWatcherService extends Service {
    /**
     * Get all alarm rules in JSON format
     * @return JSON String of all alarm rules
     */
    String getAlarmRules();

    /**
     * Get a specific alarm rule details by its id in JSON format
     * @param ruleId id of the alarm rule
     * @return JSON String of the specified alarm rule
     */
    String getAlarmRuleById(String ruleId);

    /**
     * Get the context information of a specific alarm rule for a given entity
     * @param ruleId id of the alarm rule
     * @param entityName Name of the entity
     * @return Context information in JSON String format
     */
    String getAlarmRuleContext(String ruleId, String entityName);
}
