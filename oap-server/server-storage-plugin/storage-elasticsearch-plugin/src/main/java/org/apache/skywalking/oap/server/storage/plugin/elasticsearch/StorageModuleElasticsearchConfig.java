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

/**
 * @author peng-yongsheng
 */
@Getter
public class StorageModuleElasticsearchConfig extends ModuleConfig {
    @Setter private String nameSpace;
    @Setter private String clusterNodes;
    @Getter @Setter String protocol = "http";
    @Setter private int indexShardsNumber = 2;
    @Setter private int indexReplicasNumber = 0;
    @Setter private int indexRefreshInterval = 2;
    @Setter private int bulkActions = 2000;
    @Setter private int flushInterval = 10;
    @Setter private int concurrentRequests = 2;
    @Setter private int syncBulkActions = 3;
    @Setter private String user;
    @Setter private String password;
    @Getter @Setter String trustStorePath;
    @Getter @Setter String trustStorePass;
    @Setter private int metadataQueryMaxSize = 5000;
    @Setter private int segmentQueryMaxSize = 200;
    @Setter private int recordDataTTL = 7;
    @Setter private int minuteMetricsDataTTL = 2;
    @Setter private int hourMetricsDataTTL = 2;
    @Setter private int dayMetricsDataTTL = 2;
    private int otherMetricsDataTTL = 0;
    @Setter private int monthMetricsDataTTL = 18;

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
