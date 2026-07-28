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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BanyanDB applies its own default LIMIT to any query that carries none — 100 rows for measures — and
 * applies it after GROUP BY, so an over-long result set is silently truncated rather than rejected. These
 * tests pin the invariant that OAP never relies on that default: every query leaves with an explicit LIMIT.
 * The bound value and its placeholder ordering are covered by {@link ConditionsTest}.
 */
public class QueryLimitTest {
    private static final int RESULT_WINDOW_MAX_SIZE = 10000;

    private final List<String> emitted = new ArrayList<>();
    private BanyanDBStorageClient client;
    private ProbeDAO dao;

    /**
     * Exposes the protected measure query helper — the single funnel every measure DAO goes through.
     */
    private static class ProbeDAO extends AbstractBanyanDBDAO {
        ProbeDAO(final BanyanDBStorageClient client) {
            super(client);
        }

        void query(final MetadataRegistry.Schema schema, final Conditions where) throws IOException {
            queryDebuggable(false, schema, ImmutableSet.of("entity_id"), Collections.emptySet(),
                            new TimestampRange(0, 1), where);
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        client = mock(BanyanDBStorageClient.class);
        when(client.getResultWindowMaxSize()).thenReturn(RESULT_WINDOW_MAX_SIZE);
        // queryMeasure is varargs — the matcher has to target the array type, not a single element.
        when(client.queryMeasure(anyString(), any(Serializable[].class))).thenAnswer(invocation -> {
            emitted.add(invocation.getArgument(0));
            return mock(MeasureQueryResponse.class);
        });
        dao = new ProbeDAO(client);
    }

    private static MetadataRegistry.Schema schema() {
        return MetadataRegistry.Schema.builder()
                                      .metadata(new MetadataRegistry.SchemaMetadata(
                                          "sw", "measure-default", "service_cpm",
                                          MetadataRegistry.Kind.MEASURE, DownSampling.Minute, null))
                                      .build();
    }

    @Test
    public void measureQueryWithoutAnExplicitLimitFallsBackToTheResultWindow() throws IOException {
        dao.query(schema(), Conditions.create().eq("entity_id", "svc"));

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0).endsWith(" LIMIT ?"),
                   "a query with no caller limit must still carry one, was: " + emitted.get(0));
        verify(client, times(1)).getResultWindowMaxSize();
    }

    @Test
    public void measureQueryWithNoConditionsAtAllStillCarriesALimit() throws IOException {
        // The whole-range topology reads pass an empty condition set; they were the worst hit by the
        // server-side default because they return one row per relation.
        dao.query(schema(), Conditions.create());

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0).endsWith(" LIMIT ?"),
                   "an unconditional query must still carry a limit, was: " + emitted.get(0));
    }

    @Test
    public void measureQueryKeepsAnExplicitLimit() throws IOException {
        dao.query(schema(), Conditions.create().eq("entity_id", "svc").limit(240));

        assertEquals(1, countOccurrences(emitted.get(0), "LIMIT"),
                     "the caller's limit must not be doubled, was: " + emitted.get(0));
    }

    @Test
    public void groupedQueryPutsTheFallbackLimitAfterGroupBy() throws IOException {
        dao.query(schema(), Conditions.create().eq("entity_id", "svc").groupBy("entity_id"));

        assertTrue(emitted.get(0).endsWith(" GROUP BY entity_id LIMIT ?"),
                   "LIMIT must follow GROUP BY, was: " + emitted.get(0));
    }

    private static int countOccurrences(final String text, final String token) {
        int count = 0;
        int idx = text.indexOf(token);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(token, idx + token.length());
        }
        return count;
    }
}
