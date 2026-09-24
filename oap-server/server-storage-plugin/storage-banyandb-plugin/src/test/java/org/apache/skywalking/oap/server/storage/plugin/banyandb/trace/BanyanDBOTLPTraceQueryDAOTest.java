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

import java.io.IOException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BanyanDBOTLPTraceQueryDAOTest {
    /**
     * The catalog rows keep the minute their name was first seen, so a bounded read would hide every name older
     * than the window; every catalog must be read over all time.
     */
    @Test
    public void shouldReadTheNameCatalogsWithoutATimeRange() throws IOException {
        final CapturingDAO dao = new CapturingDAO();

        dao.getServiceNames();
        dao.getSpanNames("frontend");
        dao.getPeerServiceNames("frontend");

        assertEquals(3, dao.ranges.size());
        for (final TimestampRange range : dao.ranges) {
            assertNull(range);
        }
    }

    private static class CapturingDAO extends BanyanDBOTLPTraceQueryDAO {
        private final List<TimestampRange> ranges = new ArrayList<>();

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
            ranges.add(timestampRange);
            final MeasureQueryResponse response = mock(MeasureQueryResponse.class);
            when(response.getDataPoints()).thenReturn(Collections.emptyList());
            return response;
        }
    }
}
