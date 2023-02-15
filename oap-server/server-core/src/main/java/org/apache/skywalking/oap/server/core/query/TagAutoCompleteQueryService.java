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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.Set;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

public class TagAutoCompleteQueryService implements Service {
    private final ModuleManager moduleManager;
    private final CoreModuleConfig config;
    private ITagAutoCompleteQueryDAO tagAutoCompleteQueryDAO;

    public TagAutoCompleteQueryService(ModuleManager moduleManager, CoreModuleConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;
    }

    private ITagAutoCompleteQueryDAO getTagAutoCompleteQueryDAO() {
        if (tagAutoCompleteQueryDAO == null) {
            this.tagAutoCompleteQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ITagAutoCompleteQueryDAO.class);
        }
        return tagAutoCompleteQueryDAO;
    }

    public Set<String> queryTagAutocompleteKeys(final TagType tagType,
                                                final Duration duration) throws IOException {
        return getTagAutoCompleteQueryDAO().queryTagAutocompleteKeys(tagType, config.getAutocompleteTagKeysQueryMaxSize(), duration);
    }

    public Set<String> queryTagAutocompleteValues(final TagType tagType,
                                                  final String tagKey,
                                                  final Duration duration) throws IOException {
        return getTagAutoCompleteQueryDAO().queryTagAutocompleteValues(
            tagType, tagKey, config.getAutocompleteTagValuesQueryMaxSize(), duration);
    }
}
