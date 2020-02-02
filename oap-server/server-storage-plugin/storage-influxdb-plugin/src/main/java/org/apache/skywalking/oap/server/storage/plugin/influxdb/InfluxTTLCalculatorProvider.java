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
package org.apache.skywalking.oap.server.storage.plugin.influxdb;

import org.apache.skywalking.oap.server.core.DataTTLConfig;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.joda.time.DateTime;

public class InfluxTTLCalculatorProvider {
    private final ModuleDefineHolder moduleDefineHolder;

    public InfluxTTLCalculatorProvider(ModuleDefineHolder moduleDefineHolder) {
        this.moduleDefineHolder = moduleDefineHolder;
    }

    public InfluxTTLCalculator metricsCalculator(Downsampling downsampling) {
        switch (downsampling) {
            case Month:
                return MONTH_TTL_CALCULATOR;
            case Hour:
                return HOUR_TTL_CALCULATOR;
            case Minute:
                return MINUTE_TTL_CALCULATOR;
            default:
                return DAY_TTL_CALCULATOR;
        }
    }

    public InfluxTTLCalculator recordCalculator() {
        return RECORD_TTL_CALCULATOR;
    }

    private static final InfluxTTLCalculator MONTH_TTL_CALCULATOR = new InfluxTTLCalculator() {
        @Override public String timeBucketBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusHours(dataTTLConfig.getMonthMetricsDataTTL()).toString("yyyyMM");
        }

        @Override public long timestampBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusHours(dataTTLConfig.getMonthMetricsDataTTL()).getMillis();
        }
    };

    private static final InfluxTTLCalculator DAY_TTL_CALCULATOR = new InfluxTTLCalculator() {
        @Override public String timeBucketBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusDays(dataTTLConfig.getDayMetricsDataTTL()).toString("yyyyMMdd");
        }

        @Override public long timestampBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusDays(dataTTLConfig.getDayMetricsDataTTL()).getMillis();
        }
    };

    private static final InfluxTTLCalculator HOUR_TTL_CALCULATOR = new InfluxTTLCalculator() {
        @Override public String timeBucketBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusHours(dataTTLConfig.getHourMetricsDataTTL()).toString("yyyyMMddHH");
        }

        @Override public long timestampBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusHours(dataTTLConfig.getHourMetricsDataTTL()).getMillis();
        }
    };

    private static final InfluxTTLCalculator MINUTE_TTL_CALCULATOR = new InfluxTTLCalculator() {
        @Override public String timeBucketBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusMinutes(dataTTLConfig.getMinuteMetricsDataTTL()).toString("yyyyMMddHHmm");
        }

        @Override public long timestampBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusMinutes(dataTTLConfig.getMinuteMetricsDataTTL()).getMillis();
        }
    };

    /**
     * It works for RECORD. It is stored in InfluxDB divided 2-dim, time-bucket(day) and entity_id. For higher disk
     * utilization, In future, I think it need to be configurable. Maybe it is HOUR/half-DAY.
     */
    private static final InfluxTTLCalculator RECORD_TTL_CALCULATOR = new InfluxTTLCalculator() {
        @Override public String timeBucketBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusDays(dataTTLConfig.getDayMetricsDataTTL()).toString("yyyyMMdd");
        }

        @Override public long timestampBefore(DataTTLConfig dataTTLConfig) {
            return new DateTime().minusDays(dataTTLConfig.getDayMetricsDataTTL()).getMillis();
        }
    };
}
