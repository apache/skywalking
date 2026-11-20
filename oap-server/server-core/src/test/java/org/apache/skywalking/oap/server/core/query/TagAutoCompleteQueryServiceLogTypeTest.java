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

import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The log tag autocomplete reads one log source, or both in one storage query, passing the storage the tag types'
 * names.
 */
class TagAutoCompleteQueryServiceLogTypeTest {
    private final Duration duration = new Duration();
    private ITagAutoCompleteQueryDAO dao;
    private TagAutoCompleteQueryService service;

    @BeforeEach
    void setUp() throws Exception {
        dao = mock(ITagAutoCompleteQueryDAO.class);
        when(dao.queryTagAutocompleteKeys(anyList(), anyInt(), any())).thenReturn(Set.of());
        when(dao.queryTagAutocompleteValues(anyList(), anyString(), anyInt(), any())).thenReturn(Set.of());
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(ITagAutoCompleteQueryDAO.class)).thenReturn(dao);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        when(provider.provider()).thenReturn(services);
        final ModuleManager manager = mock(ModuleManager.class);
        when(manager.find(StorageModule.NAME)).thenReturn(provider);
        final CoreModuleConfig config = new CoreModuleConfig();
        config.setAutocompleteTagKeysQueryMaxSize(20);
        config.setAutocompleteTagValuesQueryMaxSize(30);
        service = new TagAutoCompleteQueryService(manager, config);
    }

    @Test
    void withoutATypeBothLogSourcesAreReadInOneQuery() throws Exception {
        assertEquals(List.of(TagType.LOG, TagType.OTLP_LOG), TagAutoCompleteQueryService.logTagTypes(null));

        service.queryTagAutocompleteKeys(TagAutoCompleteQueryService.logTagTypes(null), duration);
        service.queryTagAutocompleteValues(TagAutoCompleteQueryService.logTagTypes(null), "level", duration);

        verify(dao, times(1)).queryTagAutocompleteKeys(List.of("LOG", "OTLP_LOG"), 20, duration);
        verify(dao, times(1)).queryTagAutocompleteValues(List.of("LOG", "OTLP_LOG"), "level", 30, duration);
    }

    @Test
    void aTypeReadsThatSourceOnly() throws Exception {
        service.queryTagAutocompleteKeys(TagAutoCompleteQueryService.logTagTypes(TagType.OTLP_LOG), duration);

        verify(dao).queryTagAutocompleteKeys(eq(List.of("OTLP_LOG")), eq(20), any());
    }

    @Test
    void noTagTypeReadsNothing() throws Exception {
        assertEquals(Set.of(), service.queryTagAutocompleteKeys(List.of(), duration));
        assertEquals(Set.of(), service.queryTagAutocompleteValues(List.of(), "level", duration));

        verify(dao, never()).queryTagAutocompleteKeys(anyList(), anyInt(), any());
        verify(dao, never()).queryTagAutocompleteValues(anyList(), anyString(), anyInt(), any());
    }

    @Test
    void aSingleTypeQueryIsAListOfOne() throws Exception {
        service.queryTagAutocompleteValues(TagType.TRACE, "http.method", duration);

        verify(dao).queryTagAutocompleteValues(List.of("TRACE"), "http.method", 30, duration);
    }
}
