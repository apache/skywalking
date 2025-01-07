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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class BanyanDBStorageConfig extends ModuleConfig {
    /**
     * A comma-separated list of BanyanDB targets.
     *
     * @since 9.7.0
     */
    private String targets = "127.0.0.1:17912";
    /**
     * The maximum size of write entities in a single batch write call.
     */
    private int maxBulkSize = 5000;
    /**
     * Period of flush interval. In the timeunit of seconds.
     */
    private int flushInterval = 15;
    /**
     * Timeout of flush. In the timeunit of seconds.
     */
    private int flushTimeout = 10;
    /**
     * Concurrent consumer threads for batch writing.
     */
    private int concurrentWriteThreads = 2;
    /**
     * Max size of {@link org.apache.skywalking.oap.server.core.query.type.ProfileTask} to be fetched
     * in a single request.
     */
    private int profileTaskQueryMaxSize;

    /**
     * If the BanyanDB server is configured with TLS, config the TLS cert file path and open tls connection.
     */
    private String sslTrustCAPath = "";
    /**
     * Max size of {@link org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask} to be fetched
     * in a single request.
     */
    private int asyncProfilerTaskQueryMaxSize;

    private int resultWindowMaxSize = 10000;
    private int metadataQueryMaxSize = 5000;
    private int segmentQueryMaxSize = 200;
    private int profileDataQueryBatchSize = 100;

    // ----------------------------------------
    // The configuration of the groups.
    // since 10.2.0
    // ----------------------------------------
    // The group settings of record.
    // `gr` is the short name of the group settings of record.
    //
    // The "normal"(`gr...`) section defines settings for datasets not specified in "super".
    // Each dataset will be grouped under a single group named "normal".
    // "super"(`grSuper...`) is a special dataset designed to store trace or log data that is too large for normal datasets.
    // # Each super dataset will be a separate group in BanyanDB, following the settings defined in the "super" section.
    // ----------------------------------------
    // The group settings of metrics.
    // `gm` is the short name of the group settings of metrics.
    //
    // OAP stores metrics based its granularity.
    // Valid values are "day", "hour", and "minute". That means metrics will be stored in the three separate groups.
    // Non-"minute" are governed by the "core.downsampling" setting.
    // For example, if "core.downsampling" is set to "hour", the "hour" will be used, while "day" are ignored.

    private int grNormalShardNum = 1;
    private int grNormalSIDays = 1;
    private int grNormalTTLDays = 3;
    private int grSuperShardNum = 2;
    private int grSuperSIDays = 1;
    private int grSuperTTLDays = 3;
    private int gmMinuteShardNum = 2;
    private int gmMinuteSIDays = 1;
    private int gmMinuteTTLDays = 7;
    private int gmHourShardNum = 1;
    private int gmHourSIDays = 1;
    private int gmHourTTLDays = 15;
    private int gmDayShardNum = 1;
    private int gmDaySIDays = 1;
    private int gmDayTTLDays = 30;
    private int gmIndexShardNum = 1;
    private int gmIndexSIDays = 1;
    private int gmIndexTTLDays = 30;

    public String[] getTargetArray() {
        return Iterables.toArray(Splitter.on(",").omitEmptyStrings().trimResults().split(this.targets), String.class);
    }
}
