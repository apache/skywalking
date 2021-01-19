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

package org.apache.skywalking.apm.agent.core.conf.dynamic;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class AgentConfigChangeWatcher {
    private final String itemName;

    public AgentConfigChangeWatcher(String itemName) {
        this.itemName = itemName;
    }

    /**
     * Notify the watcher, the new value received.
     *
     * @param value of new.
     */
    public abstract void notify(ConfigChangeEvent value);

    /**
     * @return current value of current config.
     */
    public abstract String value();

    @Override
    public String toString() {
        return "AgentConfigChangeWatcher{" +
            "itemName='" + itemName + '\'' +
            '}';
    }

    @Setter(AccessLevel.PACKAGE)
    @Getter
    public static class ConfigChangeEvent {
        private String newValue;
        private EventType eventType;

        public ConfigChangeEvent(String newValue, EventType eventType) {
            this.newValue = newValue;
            this.eventType = eventType;
        }
    }

    public enum EventType {
        ADD, MODIFY, DELETE
    }
}
