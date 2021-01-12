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
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * ConfigTable contains all config.
 */
@ToString
public class ConfigTable {
    @Getter
    private List<ConfigItem> items = new ArrayList<>();

    public void add(ConfigItem item) {
        items.add(item);
    }

    @Getter
    @Setter
    @ToString
    public static class ConfigItem {
        private String name;
        private String value;

        public ConfigItem(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
