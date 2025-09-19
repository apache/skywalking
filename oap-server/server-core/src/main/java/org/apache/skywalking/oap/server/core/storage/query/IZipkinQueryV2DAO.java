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
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import zipkin2.storage.QueryRequest;

public interface IZipkinQueryV2DAO extends IZipkinQueryDAO {
    /**
     * @param duration nullable unless for BanyanDB query from cold stage
     */
    List<SpanWrapper> getTraceV2(final String traceId, @Nullable final Duration duration) throws IOException;

    /**
     * @param duration nullable unless for BanyanDB query from cold stage
     */
    List<List<SpanWrapper>> getTracesV2(final Set<String> traceIds, @Nullable final Duration duration) throws IOException;

    List<List<SpanWrapper>> getTracesV2(final QueryRequest request, final Duration duration) throws IOException;
}
