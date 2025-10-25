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
 */

package org.apache.skywalking.library.elasticsearch.bulk;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearch;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

@Slf4j
@RequiredArgsConstructor
public final class BulkProcessorBuilder {
    private int bulkActions = -1;
    private Duration flushInterval;
    private int concurrentRequests = 2;
    private int batchOfBytes;
    private HistogramMetrics bulkMetrics;

    public BulkProcessorBuilder bulkActions(int bulkActions) {
        checkArgument(bulkActions > 0, "bulkActions must be positive");
        this.bulkActions = bulkActions;
        return this;
    }

    public BulkProcessorBuilder batchOfBytes(int batchOfBytes) {
        checkArgument(batchOfBytes > 0, "batchOfBytes must be positive");
        this.batchOfBytes = batchOfBytes;
        return this;
    }

    public BulkProcessorBuilder flushInterval(Duration flushInterval) {
        this.flushInterval = requireNonNull(flushInterval, "flushInterval");
        return this;
    }

    public BulkProcessorBuilder concurrentRequests(int concurrentRequests) {
        checkArgument(concurrentRequests >= 0, "concurrentRequests must be >= 0");
        this.concurrentRequests = concurrentRequests;
        return this;
    }

    public BulkProcessorBuilder bulkMetrics(HistogramMetrics bulkMetrics) {
        checkArgument(bulkMetrics != null, "bulkMetrics must not be null");
        this.bulkMetrics = bulkMetrics;
        return this;
    }

    public BulkProcessor build(AtomicReference<ElasticSearch> es) {
        return new BulkProcessor(
            es, bulkActions, flushInterval, concurrentRequests, batchOfBytes, bulkMetrics);
    }
}
