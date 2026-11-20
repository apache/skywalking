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

package org.apache.skywalking.oap.server.core.source;

import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A log indexes the searchable tag keys its receiver gave, and core's {@code searchableLogsTags} when it gave none.
 */
class LogBuilderSearchableTagsTest {
    /**
     * Written with a space and a trailing comma, which the keys are trimmed of.
     */
    private static final String CORE_KEYS = "level, http.status_code,";

    private static ModuleManager MANAGER;

    @BeforeAll
    static void setUp() {
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableLogsTags()).thenReturn(CORE_KEYS);
        final NamingControl namingControl = mock(NamingControl.class);
        when(namingControl.formatServiceName(anyString())).thenAnswer(inv -> inv.getArgument(0));
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(services.getService(ConfigService.class)).thenReturn(configService);
        when(services.getService(NamingControl.class)).thenReturn(namingControl);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        when(provider.provider()).thenReturn(services);
        MANAGER = mock(ModuleManager.class);
        when(MANAGER.find(CoreModule.NAME)).thenReturn(provider);
    }

    @Test
    void theReceiversKeysReplaceCoresList() {
        final LogMetadata metadata = metadata();
        metadata.setSearchableTagKeys(Set.of("severity", "http.response.status_code"));

        final Set<String> searchable = searchableTagsOf(metadata);

        assertEquals(Set.of("severity=SEVERITY_NUMBER_ERROR", "http.response.status_code=502"), searchable,
                     "level and http.status_code are core's keys, which these logs do not index");
    }

    @Test
    void aLogWithoutReceiverKeysIndexesCoresList() {
        final Set<String> searchable = searchableTagsOf(metadata());

        assertEquals(Set.of("level=ERROR", "http.status_code=500"), searchable);
    }

    @Test
    void anEmptyReceiverListIndexesNothing() {
        final LogMetadata metadata = metadata();
        metadata.setSearchableTagKeys(Set.of());

        assertTrue(searchableTagsOf(metadata).isEmpty());
    }

    @Test
    void theAutocompleteIsWrittenUnderTheReceiversTagType() {
        final LogMetadata otlp = metadata();
        otlp.setTagType(TagType.OTLP_LOG);

        assertEquals(Set.of("OTLP_LOG"), autocompleteTypesOf(otlp));
        assertEquals(Set.of("LOG"), autocompleteTypesOf(metadata()), "a log whose receiver gave no type");
    }

    private static Set<String> autocompleteTypesOf(final LogMetadata metadata) {
        final SourceReceiver receiver = mock(SourceReceiver.class);
        final LogBuilder builder = new LogBuilder();
        builder.init(metadata, LogData.newBuilder().setService("checkout")
                                      .setTags(LogTags.newBuilder().addData(tag("level", "ERROR"))).build(), MANAGER);
        builder.complete(receiver);

        final ArgumentCaptor<ISource> sources = ArgumentCaptor.forClass(ISource.class);
        verify(receiver, atLeastOnce()).receive(sources.capture());
        return sources.getAllValues().stream()
                      .filter(TagAutocomplete.class::isInstance)
                      .map(source -> ((TagAutocomplete) source).getTagType().name())
                      .collect(Collectors.toSet());
    }

    private static LogMetadata metadata() {
        return LogMetadata.builder().service("checkout").timestamp(1_700_000_000_000L).build();
    }

    private static Set<String> searchableTagsOf(final LogMetadata metadata) {
        final LogData input = LogData.newBuilder()
                                     .setService("checkout")
                                     .setTags(LogTags.newBuilder()
                                                     .addData(tag("level", "ERROR"))
                                                     .addData(tag("http.status_code", "500"))
                                                     .addData(tag("severity", "SEVERITY_NUMBER_ERROR"))
                                                     .addData(tag("http.response.status_code", "502")))
                                     .build();
        final LogBuilder builder = new LogBuilder();
        builder.init(metadata, input, MANAGER);
        return builder.toLog().getTags().stream().map(Object::toString).collect(Collectors.toSet());
    }

    private static KeyStringValuePair tag(final String key, final String value) {
        return KeyStringValuePair.newBuilder().setKey(key).setValue(value).build();
    }
}
