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

package org.apache.skywalking.apm.collector.storage.es;

import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClientConfig;

/**
 * @author peng-yongsheng
 */
class StorageModuleEsConfig extends ElasticSearchClientConfig {

    private int indexShardsNumber;
    private int indexReplicasNumber;
    private boolean highPerformanceMode;
    private int traceDataTTL;
    private int minuteMetricDataTTL;
    private int hourMetricDataTTL;
    private int dayMetricDataTTL;
    private int monthMetricDataTTL;

    int getIndexShardsNumber() {
        return indexShardsNumber;
    }

    void setIndexShardsNumber(int indexShardsNumber) {
        this.indexShardsNumber = indexShardsNumber;
    }

    int getIndexReplicasNumber() {
        return indexReplicasNumber;
    }

    void setIndexReplicasNumber(int indexReplicasNumber) {
        this.indexReplicasNumber = indexReplicasNumber;
    }

    boolean isHighPerformanceMode() {
        return highPerformanceMode;
    }

    void setHighPerformanceMode(boolean highPerformanceMode) {
        this.highPerformanceMode = highPerformanceMode;
    }

    int getTraceDataTTL() {
        return traceDataTTL;
    }

    void setTraceDataTTL(int traceDataTTL) {
        this.traceDataTTL = traceDataTTL;
    }

    int getMinuteMetricDataTTL() {
        return minuteMetricDataTTL;
    }

    void setMinuteMetricDataTTL(int minuteMetricDataTTL) {
        this.minuteMetricDataTTL = minuteMetricDataTTL;
    }

    int getHourMetricDataTTL() {
        return hourMetricDataTTL;
    }

    void setHourMetricDataTTL(int hourMetricDataTTL) {
        this.hourMetricDataTTL = hourMetricDataTTL;
    }

    int getDayMetricDataTTL() {
        return dayMetricDataTTL;
    }

    void setDayMetricDataTTL(int dayMetricDataTTL) {
        this.dayMetricDataTTL = dayMetricDataTTL;
    }

    int getMonthMetricDataTTL() {
        return monthMetricDataTTL;
    }

    void setMonthMetricDataTTL(int monthMetricDataTTL) {
        this.monthMetricDataTTL = monthMetricDataTTL;
    }
}
