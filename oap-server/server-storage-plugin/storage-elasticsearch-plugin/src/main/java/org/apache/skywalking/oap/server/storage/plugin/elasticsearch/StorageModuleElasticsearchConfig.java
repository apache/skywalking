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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class StorageModuleElasticsearchConfig extends ModuleConfig {
    private String namespace;
    private String clusterNodes;
    String protocol = "http";
    /**
     * Connect timeout of ElasticSearch client.
     *
     * @since 8.7.0
     */
    private int connectTimeout = 3000;
    /**
     * Socket timeout of ElasticSearch client.
     *
     * @since 8.7.0
     */
    private int socketTimeout = 30000;
    /**
     * @since 9.0.0 the response timeout of ElasticSearch client (Armeria under the hood), set to 0 to disable response
     * timeout.
     */
    private int responseTimeout = 15000;
    /**
     * @since 6.4.0, the index of metrics and traces data in minute/hour/month precision are organized in days. ES
     * storage creates new indexes in every day.
     *
     * @since 7.0.0 dayStep represents how many days a single one index represents. Default is 1, meaning no difference
     * with previous versions. But if there isn't much traffic for single one day, user could set the step larger to
     * reduce the number of indexes, and keep the TTL longer.
     */
    private int dayStep = 1;
    private int indexReplicasNumber = 0;
    private int indexShardsNumber = 1;
    /**
     * @since 9.3.0, Specify the settings for each index individually.
     * Use JSON format and the index name in the config should exclude the `${SW_NAMESPACE}` e.g.
     * {"metrics-all":{"number_of_shards":"3","number_of_replicas":"2"},"segment":{"number_of_shards":"6","number_of_replicas":"1"}}
     * If configured, this setting has the highest priority and overrides the generic settings.
     */
    private String specificIndexSettings;

    /**
     * @since 8.2.0, the record day step is for super size dataset record index rolling when the value of it is greater
     * than 0
     */
    private int superDatasetDayStep = -1;
    /**
     * @see SuperDataset
     * @since 8.2.0, the replicas number is for super size dataset record replicas number
     */
    private int superDatasetIndexReplicasNumber = 0;
    private int superDatasetIndexShardsFactor = 5;
    private int indexRefreshInterval = 2;

    /**
     * @since 8.7.0 The order of index template.
     */
    private int indexTemplateOrder = 0;

    /**
     * @since 8.7.0 This setting affects all traces/logs/metrics/metadata flush policy.
     */
    private int bulkActions = 5000;

    private int batchOfBytes = 1024 * 1024 * 5;
    /**
     * Period of flush, no matter `bulkActions` reached or not.
     * Unit is second.
     */
    private int flushInterval = 5;
    private int concurrentRequests = 2;
    /**
     * @since 7.0.0 This could be managed inside {@link #secretsManagementFile}
     */
    private String user;
    /**
     * @since 7.0.0 This could be managed inside {@link #secretsManagementFile}
     */
    private String password;
    /**
     * Secrets management file includes the username, password, which are managed by 3rd party tool.
     */
    private String secretsManagementFile;
    private String trustStorePath;
    /**
     * @since 7.0.0 This could be managed inside {@link #secretsManagementFile}
     */
    private String trustStorePass;
    private int resultWindowMaxSize = 10000;
    private int metadataQueryMaxSize = 5000;
    /**
     * @since 9.0.0 The batch size that is used to scroll on the large results,
     * if {@link #metadataQueryMaxSize} is larger than the maximum result window in
     * ElasticSearch server, this can be used to retrieve all results.
     */
    private int scrollingBatchSize = 5000;
    private int segmentQueryMaxSize = 200;
    private int profileTaskQueryMaxSize = 200;
    /**
     * The batch size that is used to scroll on the large eBPF profiling data result.
     * The profiling data contains full-stack symbol data, which could make ElasticSearch response large content.
     * {@link #scrollingBatchSize} would not be used in profiling data query.
     */
    private int profileDataQueryBatchSize = 100;
    /**
     * The default analyzer for match query field. {@link ElasticSearch.MatchQuery.AnalyzerType#OAP_ANALYZER}
     *
     * @since 8.4.0
     */
    private String oapAnalyzer = "{\"analyzer\":{\"oap_analyzer\":{\"type\":\"stop\"}}}";
    /**
     * The log analyzer for match query field. {@link ElasticSearch.MatchQuery.AnalyzerType#OAP_LOG_ANALYZER}
     *
     * @since 8.4.0
     */
    private String oapLogAnalyzer = "{\"analyzer\":{\"oap_log_analyzer\":{\"type\":\"standard\"}}}";
    private String advanced;

    /**
     * The number of threads for the underlying HTTP client to perform socket I/O.
     * If the value is <= 0, the number of available processors will be used.
     */
    private int numHttpClientThread;

    /**
     * If disabled, all metrics would be persistent in one physical index template, to reduce the number of physical indices.
     * If enabled, shard metrics indices into multi-physical indices, one index template per metric/meter aggregation function.
     *
     * @since 9.2.0
     */
    private boolean logicSharding = false;
}
