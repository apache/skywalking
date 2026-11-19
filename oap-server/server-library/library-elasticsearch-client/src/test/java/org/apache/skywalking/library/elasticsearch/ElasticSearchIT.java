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

package org.apache.skywalking.library.elasticsearch;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The client's requests on the server versions OAP supports, one nested class per version, each starting its server
 * once for all its tests. The client picks its request format by version, and every format runs here: Elasticsearch 6
 * with typed documents (6.8.23, the last 6.x release), 7.0-7.7 with legacy index templates (7.7.1, the last of them),
 * 7.8 on with composable index templates (7.8.0, where they start, and 7.17.29, the last 7.x release), 8 and 9 with
 * typeless single-document updates, and OpenSearch 1, 2 and 3.
 */
@Tag("slow")
public class ElasticSearchIT {
    private static final String ELASTICSEARCH = "ElasticSearch";
    private static final String OPENSEARCH = "OpenSearch";

    @Nested
    @DisplayName("Elasticsearch 6.8.23")
    class Elasticsearch6823 extends ElasticSearchClientITBase {
        Elasticsearch6823() {
            super(new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss").withTag("6.8.23")),
                  ELASTICSEARCH, "6.8");
        }
    }

    @Nested
    @DisplayName("Elasticsearch 7.7.1")
    class Elasticsearch771 extends ElasticSearchClientITBase {
        Elasticsearch771() {
            super(new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss").withTag("7.7.1")),
                  ELASTICSEARCH, "7.7");
        }
    }

    @Nested
    @DisplayName("Elasticsearch 7.8.0")
    class Elasticsearch780 extends ElasticSearchClientITBase {
        Elasticsearch780() {
            super(new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss").withTag("7.8.0")),
                  ELASTICSEARCH, "7.8");
        }
    }

    @Nested
    @DisplayName("Elasticsearch 7.17.29")
    class Elasticsearch71729 extends ElasticSearchClientITBase {
        Elasticsearch71729() {
            super(new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch").withTag("7.17.29")),
                  ELASTICSEARCH, "7.17");
        }
    }

    @Nested
    @DisplayName("Elasticsearch 8.19.25")
    class Elasticsearch81925 extends ElasticSearchClientITBase {
        Elasticsearch81925() {
            super(elasticsearch("8.19.25"), ELASTICSEARCH, "8.19");
        }
    }

    @Nested
    @DisplayName("Elasticsearch 9.3.0")
    class Elasticsearch930 extends ElasticSearchClientITBase {
        Elasticsearch930() {
            super(elasticsearch("9.3.0"), ELASTICSEARCH, "9.3");
        }
    }

    @Nested
    @DisplayName("OpenSearch 1.3.20")
    class OpenSearch1320 extends ElasticSearchClientITBase {
        OpenSearch1320() {
            super(openSearch("1.3.20").withEnv("plugins.security.disabled", "true")
                      // The image starts the Performance Analyzer agent's JVM together with the launcher's. A JVM
                      // that starts beside another can print an hsperfdata lock warning to stdout, which the
                      // launcher reads as part of its JVM options, and OpenSearch then fails with "main class
                      // Cannot" (opensearch-project/opensearch-build#3821).
                      .withEnv("DISABLE_PERFORMANCE_ANALYZER_AGENT_CLI", "true"),
                  OPENSEARCH, "1.3");
        }
    }

    @Nested
    @DisplayName("OpenSearch 2.8.0")
    class OpenSearch280 extends ElasticSearchClientITBase {
        OpenSearch280() {
            super(openSearch("2.8.0").withEnv("plugins.security.disabled", "true")
                                     .withEnv("DISABLE_PERFORMANCE_ANALYZER_AGENT_CLI", "true"),
                  OPENSEARCH, "2.8");
        }
    }

    @Nested
    @DisplayName("OpenSearch 3.0.0")
    class OpenSearch300 extends ElasticSearchClientITBase {
        OpenSearch300() {
            super(openSearch("3.0.0")
                      // From 2.12 on, disabling the security plugin alone is not enough: the image still installs
                      // the demo security config, which refuses to run without an initial admin password. With the
                      // plugin removed, its plugins.security.disabled setting is unknown and stops the server.
                      .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                      .withEnv("DISABLE_PERFORMANCE_ANALYZER_AGENT_CLI", "true"),
                  OPENSEARCH, "3.0");
        }
    }

    private static ElasticsearchContainer elasticsearch(final String version) {
        return new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch").withTag(version))
            .withEnv("xpack.security.enabled", "false");
    }

    private static ElasticsearchContainer openSearch(final String version) {
        return new ElasticsearchContainer(
            DockerImageName.parse("opensearchproject/opensearch").withTag(version)
                           .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch-oss"))
            .withStartupTimeout(Duration.ofMinutes(5));
    }
}
