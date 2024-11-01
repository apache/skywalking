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
     * Shards Number for measure/metrics.
     */
    private int metricsShardsNumber;
    /**
     * Shards Number for a normal record.
     */
    private int recordShardsNumber;
    /**
     * Shards Factor for a super dataset
     */
    private int superDatasetShardsFactor;
    /**
     * Default global segment interval for non-super-dataset models.
     * Unit is day.
     *
     * @since 9.4.0
     */
    private int segmentIntervalDays;
    /**
     * Default global segment interval for super-dataset models.
     * Unit is day.
     *
     * @since 9.4.0
     */
    private int superDatasetSegmentIntervalDays;
    /**
     * Specify the settings for each group individually. All groups created in BanyanDB can
     * be found with <a href="https://skywalking.apache.org/docs/skywalking-banyandb/next/crud/group/#list-operation">bydbctl</a>.
     * <p>
     * NOTE: setting intervals works for all groups except `measure-default`.
     * <p>
     * NOTE: available groups: `measure-default`, `measure-sampled`, `stream-default`
     * and `stream-*` with names of the super dataset as the suffix.
     *
     * @since 9.4.0
     */
    private String specificGroupSettings;

    /**
     * If the BanyanDB server is configured with TLS, config the TLS cert file path and open tls connection.
     */
    private String sslTrustCAPath = "";
    /**
     * Max size of {@link org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask} to be fetched
     * in a single request.
     */
    private int asyncProfilerTaskQueryMaxSize;

    public String[] getTargetArray() {
        return Iterables.toArray(Splitter.on(",").omitEmptyStrings().trimResults().split(this.targets), String.class);
    }
}
