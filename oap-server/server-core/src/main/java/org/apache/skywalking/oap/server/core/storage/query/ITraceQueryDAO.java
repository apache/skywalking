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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.library.module.Service;

public interface ITraceQueryDAO extends Service {

    TraceBrief queryBasicTraces(long startSecondTB,
                                long endSecondTB,
                                long minDuration,
                                long maxDuration,
                                String endpointName,
                                String serviceId,
                                String serviceInstanceId,
                                String endpointId,
                                String traceId,
                                int limit,
                                int from,
                                TraceState traceState,
                                QueryOrder queryOrder,
                                final List<Tag> tags) throws IOException;

    List<SegmentRecord> queryByTraceId(String traceId) throws IOException;

    /**
     * This method gives more flexible for 3rd trace without segment concept, which can't search data through {@link #queryByTraceId(String)}
     */
    List<Span> doFlexibleTraceQuery(String traceId) throws IOException;
}
