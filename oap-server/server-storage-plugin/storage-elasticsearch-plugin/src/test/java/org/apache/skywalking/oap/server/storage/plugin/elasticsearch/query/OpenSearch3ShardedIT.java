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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport.Mode;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAOITBase;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.HistoryDeleteEsDAOITBase;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.otlp.OTLPTraceQueryEsDAOITBase;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin.ZipkinQueryEsDAOITBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The storage ITs on OpenSearch 3, with logic sharding and custom routing, on one server for every nested IT.
 */
@Tag("slow")
@Tag("elastic-it")
@Testcontainers
public class OpenSearch3ShardedIT {
    @Container
    private static final ElasticsearchContainer SERVER = ElasticSearchQueryITSupport.openSearch("3.0.0");
    private static final Mode MODE = Mode.LOGIC_SHARDING_AND_CUSTOM_ROUTING;

    @Nested
    class AIAgentConversations extends AIAgentConversationQueryITBase {
        AIAgentConversations() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Alarms extends AlarmQueryITBase {
        Alarms() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class BatchProcess extends BatchProcessEsDAOITBase {
        BatchProcess() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class BrowserLogs extends BrowserLogQueryITBase {
        BrowserLogs() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class EBPFProfiling extends EBPFProfilingQueryITBase {
        EBPFProfiling() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Events extends EventQueryITBase {
        Events() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class GenAIEvaluations extends GenAIEvaluationQueryITBase {
        GenAIEvaluations() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Hierarchy extends HierarchyQueryITBase {
        Hierarchy() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class HistoryDelete extends HistoryDeleteEsDAOITBase {
        HistoryDelete() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Logs extends LogQueryITBase {
        Logs() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class ManagementData extends ManagementDataITBase {
        ManagementData() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Metadata extends MetadataQueryITBase {
        Metadata() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Metrics extends MetricsQueryITBase {
        Metrics() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class MetricsPersistence extends MetricsPersistenceITBase {
        MetricsPersistence() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class OTLPTraces extends OTLPTraceQueryEsDAOITBase {
        OTLPTraces() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class ProfileThreadSnapshots extends ProfileThreadSnapshotQueryITBase {
        ProfileThreadSnapshots() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class ProfilingTasks extends ProfilingTaskQueryITBase {
        ProfilingTasks() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class SpanAttachedEvents extends SpanAttachedEventQueryITBase {
        SpanAttachedEvents() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Topology extends TopologyQueryITBase {
        Topology() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Traces extends TraceQueryEsDAOITBase {
        Traces() {
            super(SERVER, MODE);
        }
    }

    @Nested
    class Zipkin extends ZipkinQueryEsDAOITBase {
        Zipkin() {
            super(SERVER, MODE);
        }
    }
}
