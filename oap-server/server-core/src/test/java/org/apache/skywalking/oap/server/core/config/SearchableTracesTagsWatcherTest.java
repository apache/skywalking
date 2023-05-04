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
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class SearchableTracesTagsWatcherTest {

    private ModuleProvider provider;
    private CoreModuleConfig moduleConfig;

    private SearchableTracesTagsWatcher searchableTracesTagsWatcher;

    @BeforeEach
    public void init() {
        provider = new CoreModuleProvider();
        moduleConfig = new CoreModuleConfig();

        searchableTracesTagsWatcher = new SearchableTracesTagsWatcher(moduleConfig.getSearchableTracesTags(), provider);
    }


    @Test
    public void testGetDefaultSearchableTags() {

        Assertions.assertEquals(searchableTracesTagsWatcher.getSearchableTags(),
                Arrays.stream(moduleConfig.getSearchableTracesTags().split(",")).collect(Collectors.toSet()));
    }

    @Test
    public void testNotify() {

        //add
        String addSearchableTracesTagsStr = moduleConfig.getSearchableTracesTags() + ",userId";
        ConfigChangeWatcher.ConfigChangeEvent addEvent =
                new ConfigChangeWatcher.ConfigChangeEvent(addSearchableTracesTagsStr,
                        ConfigChangeWatcher.EventType.ADD);

        searchableTracesTagsWatcher.notify(addEvent);

        Assertions.assertEquals(searchableTracesTagsWatcher.getSearchableTags(),
                Arrays.stream(addSearchableTracesTagsStr.split(",")).collect(Collectors.toSet()));


        //modify
        String modifySearchableTracesTagsStr = moduleConfig.getSearchableTracesTags() + ",userId,orderId";
        ConfigChangeWatcher.ConfigChangeEvent modifyEvent =
                new ConfigChangeWatcher.ConfigChangeEvent(modifySearchableTracesTagsStr,
                        ConfigChangeWatcher.EventType.MODIFY);

        searchableTracesTagsWatcher.notify(modifyEvent);

        Assertions.assertEquals(searchableTracesTagsWatcher.getSearchableTags(),
                Arrays.stream(modifySearchableTracesTagsStr.split(",")).collect(Collectors.toSet()));


        //delete
        ConfigChangeWatcher.ConfigChangeEvent deleteEvent =
                new ConfigChangeWatcher.ConfigChangeEvent(null,
                        ConfigChangeWatcher.EventType.DELETE);
        searchableTracesTagsWatcher.notify(deleteEvent);

        Assertions.assertEquals(searchableTracesTagsWatcher.getSearchableTags(),
                Arrays.stream(moduleConfig.getSearchableTracesTags().split(",")).collect(Collectors.toSet()));

    }

}