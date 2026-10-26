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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.Conditions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The service catalog read narrows by layer in the query itself.
 */
public class BanyanDBOTLPServiceCatalogTest {
    @Test
    public void withoutALayerEveryRowIsRead() throws Exception {
        final CapturingDAO dao = new CapturingDAO();

        dao.getServices(null);

        assertFalse(dao.queries.get(0).contains("otlp_service_layer"), dao.queries.get(0));
    }

    @Test
    public void aLayerIsAConditionAheadOfTheLimit() throws Exception {
        final CapturingDAO dao = new CapturingDAO();

        dao.getServices("OTLP_GENERAL");

        final String ql = dao.queries.get(0);
        assertTrue(ql.contains("otlp_service_layer = ?"), ql);
        assertTrue(ql.indexOf("otlp_service_layer = ?") < ql.indexOf("LIMIT"), ql);
    }

    private static class CapturingDAO extends BanyanDBOTLPTraceQueryDAO {
        private final List<String> queries = new ArrayList<>();

        CapturingDAO() {
            super(mock(BanyanDBStorageClient.class));
        }

        @Override
        protected MeasureQueryResponse queryDebuggable(final boolean isColdStage,
                                                       final MetadataRegistry.Schema schema,
                                                       final Set<String> tags,
                                                       final Set<String> fields,
                                                       final TimestampRange timestampRange,
                                                       final Conditions where) {
            queries.add(where.buildQl());
            final MeasureQueryResponse response = mock(MeasureQueryResponse.class);
            when(response.getDataPoints()).thenReturn(Collections.emptyList());
            return response;
        }
    }
}
