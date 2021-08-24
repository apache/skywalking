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

package org.apache.skywalking.oap.server.configuration.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * ConfigTable contains all WatchType.GROUP config.
 */
@ToString
public class GroupConfigTable {
    @Getter
    private List<GroupConfigItems> groupItems = new ArrayList<>();

    public void addGroupConfigItems(GroupConfigItems items) {
        groupItems.add(items);
    }

    @Getter
    @Setter
    @ToString
    public static class GroupConfigItems {
        private String name;
        private Map<String, ConfigTable.ConfigItem> items = new ConcurrentHashMap<>();

        public GroupConfigItems(final String name) {
            this.name = name;
        }

        public void add(ConfigTable.ConfigItem item) {
            items.put(item.getName(), item);
        }
    }
}
