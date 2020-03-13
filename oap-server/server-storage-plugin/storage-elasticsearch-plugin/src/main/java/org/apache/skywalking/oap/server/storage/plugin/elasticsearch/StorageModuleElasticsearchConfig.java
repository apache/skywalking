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
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
public class StorageModuleElasticsearchConfig extends ModuleConfig {
    @Setter
    private String nameSpace;
    @Setter
    private String clusterNodes;
    @Getter
    @Setter
    String protocol = "http";
    @Setter
    private int indexShardsNumber = 2;
    @Setter
    private int indexReplicasNumber = 0;
    @Setter
    private int indexRefreshInterval = 2;
    @Setter
    private int bulkActions = 2000;
    @Setter
    private int flushInterval = 10;
    @Setter
    private int concurrentRequests = 2;
    @Setter
    private int syncBulkActions = 3;
    /**
     * @since 7.0.0 This could be managed inside {@link #secretsManagementFile}
     */
    @Setter
    private String user;
    /**
     * @since 7.0.0 This could be managed inside {@link #secretsManagementFile}
     */
    @Setter
    private String password;
    /**
     * Secrets management file includes the username, password, which are managed by 3rd party tool.
     */
    @Getter
    private String secretsManagementFile;
    @Getter
    @Setter
    private String trustStorePath;
    /**
     * @since 7.0.0 This could be managed inside {@link #secretsManagementFile}
     */
    @Getter
    @Setter
    private String trustStorePass;
    /**
     * If this is ON, downsampling indexes(hour and day precisions) merged into minute precision. In this case, only
     * {@link #minuteMetricsDataTTL} works for minute, hour and day.
     *
     * @since 7.0.0 This is an enhancement. Reduce 50% of index number(remove day/hour index requirements) but keep the
     * performance nearly same as before. Only one side-effect for 6.x storage is just day/hour indexes remain, users
     * need to remove them manually.
     */
    @Getter
    private boolean enablePackedDownsampling = true;
    /**
     * Since 6.4.0, the index of metrics and traces data in minute/hour/month precision are organized in days. ES
     * storage creates new indexes in every day.
     *
     * @since 7.0.0 dayStep represents how many days a single one index represents. Default is 1, meaning no difference
     * with previous versions. But if there isn't much traffic for single one day, user could set the step larger to
     * reduce the number of indexes, and keep the TTL longer.
     *
     * Same as {@link #enablePackedDownsampling} this config doesn't affects month level data. Because usually, no one
     * keeps the observability data in several months.
     */
    @Getter
    private int dayStep = 1;
    @Setter
    private int resultWindowMaxSize = 10000;
    @Setter
    private int metadataQueryMaxSize = 5000;
    @Setter
    private int segmentQueryMaxSize = 200;
    @Setter
    private int profileTaskQueryMaxSize = 200;
    @Setter
    private int recordDataTTL = 7;
    @Setter
    private int minuteMetricsDataTTL = 2;
    @Setter
    private int hourMetricsDataTTL = 2;
    @Setter
    private int dayMetricsDataTTL = 2;
    private int otherMetricsDataTTL = 0;
    @Setter
    private int monthMetricsDataTTL = 18;
    @Setter
    private String advanced;

    public int getMinuteMetricsDataTTL() {
        if (otherMetricsDataTTL > 0) {
            return otherMetricsDataTTL;
        }
        return minuteMetricsDataTTL;
    }

    public int getHourMetricsDataTTL() {
        if (otherMetricsDataTTL > 0) {
            return otherMetricsDataTTL;
        }
        return hourMetricsDataTTL;
    }

    public int getDayMetricsDataTTL() {
        if (otherMetricsDataTTL > 0) {
            return otherMetricsDataTTL;
        }
        return dayMetricsDataTTL;
    }
}
