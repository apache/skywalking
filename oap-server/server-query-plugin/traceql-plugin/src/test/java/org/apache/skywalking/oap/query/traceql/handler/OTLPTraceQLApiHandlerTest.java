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

package org.apache.skywalking.oap.query.traceql.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.skywalking.oap.query.traceql.TraceQLConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tag endpoints over the OTLP autocomplete index, whose keys OTLPSpanForward publishes with their scope prefix.
 */
class OTLPTraceQLApiHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TagAutoCompleteQueryService tagAutoCompleteQueryService;
    private OTLPTraceQLApiHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        tagAutoCompleteQueryService = mock(TagAutoCompleteQueryService.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(NamingControl.class)).thenReturn(mock(NamingControl.class));
        when(serviceHolder.getService(TagAutoCompleteQueryService.class)).thenReturn(tagAutoCompleteQueryService);
        when(tagAutoCompleteQueryService.queryTagAutocompleteKeys(eq(TagType.OTLP), any())).thenReturn(new LinkedHashSet<>(List.of(
            "resource.service.instance.id",
            "resource.deployment.environment",
            "span.otel.scope.name",
            "span.http.request.method"
        )));
        handler = new OTLPTraceQLApiHandler(moduleManager, new TraceQLConfig());
    }

    @Test
    void shouldListEachAutocompleteKeyUnderItsOwnScope() throws Exception {
        final JsonNode body = MAPPER.readTree(handler.searchTagsV2(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ).aggregate().join().contentUtf8());

        assertEquals(
            List.of("service.name", "service.instance.id", "remote.service", "deployment.environment"),
            tagsOf(body, "resource"));
        assertEquals(List.of("otel.scope.name", "http.request.method"), tagsOf(body, "span"));
    }

    @Test
    void shouldAnswerResourceAttributeValuesFromTheAutocompleteIndex() throws Exception {
        when(tagAutoCompleteQueryService.queryTagAutocompleteValues(
            eq(TagType.OTLP), eq("resource.deployment.environment"), any())).thenReturn(Set.of("prod"));

        final AggregatedHttpResponse response = handler.searchTagValues(
            "resource.deployment.environment", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty()
        ).aggregate().join();

        assertEquals(HttpStatus.OK, response.status());
        assertEquals(List.of("prod"), valuesOf(MAPPER.readTree(response.contentUtf8())));
    }

    @Test
    void shouldLookUpAnUnscopedKeyInBothScopes() throws Exception {
        when(tagAutoCompleteQueryService.queryTagAutocompleteValues(
            eq(TagType.OTLP), eq("span.deployment.environment"), any())).thenReturn(Set.of("canary"));
        when(tagAutoCompleteQueryService.queryTagAutocompleteValues(
            eq(TagType.OTLP), eq("resource.deployment.environment"), any())).thenReturn(Set.of("prod"));

        final JsonNode body = MAPPER.readTree(handler.searchTagValues(
            ".deployment.environment", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty()
        ).aggregate().join().contentUtf8());

        assertEquals(List.of("canary", "prod"), valuesOf(body));
    }

    private static List<String> tagsOf(final JsonNode body, final String scope) {
        final List<String> tags = new ArrayList<>();
        for (final JsonNode entry : body.get("scopes")) {
            if (scope.equals(entry.get("name").asText())) {
                entry.get("tags").forEach(tag -> tags.add(tag.asText()));
            }
        }
        return tags;
    }

    private static List<String> valuesOf(final JsonNode body) {
        final List<String> values = new ArrayList<>();
        body.get("tagValues").forEach(value -> values.add(value.get("value").asText()));
        return values;
    }
}
