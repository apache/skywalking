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

package org.apache.skywalking.oap.server.core.storage;

import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;

/**
 * The following algorithms represent different Table Sharding strategies.
 * Cooperate with tableShardingColumn and dataSourceShardingColumn which are defined in {@link SQLDatabase.Sharding}
 *
 * The DataSource sharding strategy are same:
 * If we have {dataSourceList = ds_0...ds_n} and {dataSourceShardingColumn.hashcode() % dataSourceList.size() = 2}
 * then the route target is ds_2
 *
 * The sharding number of the tables is according to TTL, One table per day:
 * {tableName = table_timeSeries}, {timeSeries = currentDate - TTL +1 ... currentDate + 1}
 * For example: if TTL=3, currentDate = 20220907, the sharding tables are:
 * Table_20220905
 * Table_20220906
 * Table_20220907
 * Table_20220908
 */
public enum ShardingAlgorithm {
    /**
     * Wouldn't sharding Table nor DataSource, keep single Table.
     */
    NO_SHARDING,

    /**
     * Use the time_bucket inside the ID column to sharding by day.
     * The precision of time_bucket could be second, minute, hour and day in the same table.
     *
     * For example, the single table `service_metrics`:
     * ┌────────────────────────┬────────────┐
     * │         id             │    value   │
     * ├────────────────────────┼────────────┤
     * │ 20220905_Service_A     │   300      │
     * ├────────────────────────┼────────────┤
     * │ 2022090512_Service_A   │   200      │
     * ├────────────────────────┼────────────┤
     * │ 202209051211_Service_A │   100      │
     * ├────────────────────────┼────────────┤
     * │ 20220906_Service_A     │   500      │
     * ├────────────────────────┼────────────┤
     * │ 2022090612_Service_A   │   300      │
     * ├────────────────────────┼────────────┤
     * │ 202209061211_Service_A │   100      │
     * └────────────────────────┴────────────┘
     *
     * The sharding tables will be:
     * `service_metrics_20220905`
     * ┌────────────────────────┬────────────┐
     * │         id             │    value   │
     * ├────────────────────────┼────────────┤
     * │ 20220905_Service_A     │   300      │
     * ├────────────────────────┼────────────┤
     * │ 2022090512_Service_A   │   200      │
     * ├────────────────────────┼────────────┤
     * │ 202209051211_Service_A │   100      │
     * └────────────────────────┴────────────┘
     * and `service_metrics_20220906`
     * ┌────────────────────────┬────────────┐
     * │         id             │    value   │
     * ├────────────────────────┼────────────┤
     * │ 20220906_Service_A     │   500      │
     * ├────────────────────────┼────────────┤
     * │ 2022090612_Service_A   │   300      │
     * ├────────────────────────┼────────────┤
     * │ 202209061211_Service_A │   100      │
     * └────────────────────────┴────────────┘
     *
     */
    TIME_RELATIVE_ID_SHARDING_ALGORITHM,

    /**
     * Use the time_bucket column to sharding by day.
     * The precision of time_bucket should be `second`.
     *
     * For example, the single table `service_records`:
     * ┌──────────────┬───────────────┬─────────┐
     * │   Service    │  time_bucket  │  value  │
     * ├──────────────┼───────────────┼─────────┤
     * │   Service_A  │20220905121130 │ 300     │
     * ├──────────────┼───────────────┼─────────┤
     * │   Service_A  │20220906181233 │ 200     │
     * └──────────────┴───────────────┴─────────┘
     * The sharding tables will be:
     * `service_records_20220905`
     * ┌──────────────┬───────────────┬─────────┐
     * │   Service    │  time_bucket  │  value  │
     * ├──────────────┼───────────────┼─────────┤
     * │   Service_A  │20220905121130 │ 300     │
     * └──────────────┴───────────────┴─────────┘
     * and `service_records_20220906`
     * ┌──────────────┬───────────────┬─────────┐
     * │   Service    │  time_bucket  │  value  │
     * ├──────────────┼───────────────┼─────────┤
     * │   Service_A  │20220906181233 │ 200     │
     * └──────────────┴───────────────┴─────────┘
     */
    TIME_SEC_RANGE_SHARDING_ALGORITHM,

    /**
     * Use the time_bucket column to sharding by day.
     * The precision of time_bucket should be `minute`.
     *
     * For example, the single table `endpoint_traffic`:
     * ┌──────────────┬───────────────┐
     * │   Endpoint   │  time_bucket  │
     * ├──────────────┼───────────────┤
     * │   Endpoint_A │202209051211   │
     * ├──────────────┼───────────────┤
     * │   Endpoint_B │202209061812   │
     * └──────────────┴───────────────┘
     * The sharding tables will be:
     * `endpoint_traffic_20220905`
     * ┌──────────────┬───────────────┐
     * │   Endpoint   │  time_bucket  │
     * ├──────────────┼───────────────┤
     * │   Endpoint_A │202209051211   │
     * └──────────────┴───────────────┘
     * and `endpoint_traffic_20220906`
     * ┌──────────────┬───────────────┐
     * │   Endpoint   │  time_bucket  │
     * ├──────────────┼───────────────┤
     * │   Endpoint_B │202209061812   │
     * └──────────────┴───────────────┘
     */
    TIME_MIN_RANGE_SHARDING_ALGORITHM,

    /**
     * Use the time_bucket column to sharding by day.
     * The precision of time_bucket could be `second, minute, hour and day` in the same table.
     * For example, the single table `service_relation`:
     * ┌────────────────┬────────────────┐
     * │   relation     │   time_bucket  │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 20220905       │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 2022090512     │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 202209051211   │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 20220906       │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 2022090612     │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 202209061211   │
     * └────────────────┴────────────────┘
     * The sharding tables will be:
     * `service_relation_20220905`
     * ┌────────────────┬────────────────┐
     * │   relation     │   time_bucket  │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 20220905       │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 2022090512     │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 202209051211   │
     * └────────────────┴────────────────┘
     * and `endpoint_traffic_20220906`
     * ┌────────────────┬────────────────┐
     * │   relation     │   time_bucket  │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 20220906       │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 2022090612     │
     * ├────────────────┼────────────────┤
     * │   Service_A_B  │ 202209061211   │
     * └────────────────┴────────────────┘
     */
    TIME_BUCKET_SHARDING_ALGORITHM
}
