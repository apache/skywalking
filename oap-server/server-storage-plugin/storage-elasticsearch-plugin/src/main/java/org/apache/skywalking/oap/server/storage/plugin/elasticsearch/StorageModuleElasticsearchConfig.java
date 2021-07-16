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
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class StorageModuleElasticsearchConfig extends ModuleConfig {
    private String nameSpace;
    private String clusterNodes;
    String protocol = "http";
    /**
     * Connect timeout of ElasticSearch client.
     *
     * @since 8.7.0
     */
    private int connectTimeout = 500;
    /**
     * Socket timeout of ElasticSearch client.
     *
     * @since 8.7.0
     */
    private int socketTimeout = 30000;
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
     * @since 8.7.0 This setting affects all traces/logs/metrics/metadata flush policy.
     */
    private int bulkActions = 5000;
    /**
     * Period of flesh, no matter `bulkActions` reached or not.
     * INT(flushInterval * 2/3) would be used for index refresh period.
     * Unit is second.
     *
     * @since 8.7.0 increase to 15s from 10s
     * @since 8.7.0 use INT(flushInterval * 2/3) as ElasticSearch index refresh interval. Default is 10s.
     */
    private int flushInterval = 15;
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
    private int segmentQueryMaxSize = 200;
    private int profileTaskQueryMaxSize = 200;
    /**
     * The default analyzer for match query field. {@link org.apache.skywalking.oap.server.core.storage.annotation.Column.AnalyzerType#OAP_ANALYZER}
     *
     * @since 8.4.0
     */
    private String oapAnalyzer = "{\"analyzer\":{\"oap_analyzer\":{\"type\":\"stop\"}}}";
    /**
     * The log analyzer for match query field. {@link org.apache.skywalking.oap.server.core.storage.annotation.Column.AnalyzerType#OAP_LOG_ANALYZER}
     *
     * @since 8.4.0
     */
    private String oapLogAnalyzer = "{\"analyzer\":{\"oap_log_analyzer\":{\"type\":\"standard\"}}}";
    private String advanced;
}
