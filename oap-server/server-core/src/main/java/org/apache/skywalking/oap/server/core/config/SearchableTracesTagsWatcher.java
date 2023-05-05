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

package org.apache.skywalking.oap.server.core.config;

import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class SearchableTracesTagsWatcher extends ConfigChangeWatcher {

    private AtomicReference<Set<String>> searchableTags;

    private final String initialSettingsString;

    private volatile String dynamicSettingsString;

    public SearchableTracesTagsWatcher(String config, ModuleProvider provider) {
        super(CoreModule.NAME, provider, "searchableTracesTags");
        searchableTags = new AtomicReference<>(new HashSet<>());
        initialSettingsString = config;

        activeSetting(config);
    }

    private void activeSetting(String config) {
        Set<String> tags = new HashSet<>();
        String[] settings = config.split(",");
        for (String setting : settings) {
            tags.add(setting);
        }

        searchableTags.set(tags);
    }

    public Set<String> getSearchableTags() {
        return searchableTags.get();
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            dynamicSettingsString = null;
            activeSetting(initialSettingsString);
        } else {
            dynamicSettingsString = value.getNewValue();
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return dynamicSettingsString;
    }
}