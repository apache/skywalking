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

import lombok.*;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

/**
 * @author peng-yongsheng
 */
public class StorageModuleElasticsearchConfig extends ModuleConfig {

    @Setter @Getter private String nameSpace;
    @Setter @Getter private String clusterNodes;
    private int indexShardsNumber;
    private int indexReplicasNumber;
    private boolean highPerformanceMode;
    private int traceDataTTL = 90;
    private int minuteMetricDataTTL = 90;
    private int hourMetricDataTTL = 36;
    private int dayMetricDataTTL = 45;
    private int monthMetricDataTTL = 18;
    private int bulkActions = 2000;
    private int bulkSize = 20;
    private int flushInterval = 10;
    private int concurrentRequests = 2;
    private String user;
    private String password;

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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

    public int getTraceDataTTL() {
        return traceDataTTL;
    }

    void setTraceDataTTL(int traceDataTTL) {
        this.traceDataTTL = traceDataTTL == 0 ? 90 : traceDataTTL;
    }

    public int getMinuteMetricDataTTL() {
        return minuteMetricDataTTL;
    }

    void setMinuteMetricDataTTL(int minuteMetricDataTTL) {
        this.minuteMetricDataTTL = minuteMetricDataTTL == 0 ? 90 : minuteMetricDataTTL;
    }

    public int getHourMetricDataTTL() {
        return hourMetricDataTTL;
    }

    void setHourMetricDataTTL(int hourMetricDataTTL) {
        this.hourMetricDataTTL = hourMetricDataTTL == 0 ? 36 : hourMetricDataTTL;
    }

    public int getDayMetricDataTTL() {
        return dayMetricDataTTL;
    }

    void setDayMetricDataTTL(int dayMetricDataTTL) {
        this.dayMetricDataTTL = dayMetricDataTTL == 0 ? 45 : dayMetricDataTTL;
    }

    public int getMonthMetricDataTTL() {
        return monthMetricDataTTL;
    }

    void setMonthMetricDataTTL(int monthMetricDataTTL) {
        this.monthMetricDataTTL = monthMetricDataTTL == 0 ? 18 : monthMetricDataTTL;
    }

    public int getBulkActions() {
        return bulkActions;
    }

    public void setBulkActions(int bulkActions) {
        this.bulkActions = bulkActions == 0 ? 2000 : bulkActions;
    }

    public int getBulkSize() {
        return bulkSize;
    }

    public void setBulkSize(int bulkSize) {
        this.bulkSize = bulkSize == 0 ? 20 : bulkSize;
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(int flushInterval) {
        this.flushInterval = flushInterval == 0 ? 10 : flushInterval;
    }

    public int getConcurrentRequests() {
        return concurrentRequests;
    }

    public void setConcurrentRequests(int concurrentRequests) {
        this.concurrentRequests = concurrentRequests == 0 ? 2 : concurrentRequests;
    }
}
