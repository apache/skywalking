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

public interface InfluxTTLCalculator {

    /**
     * To calculate the name of the latest time-bucket that we need to delete.
     *
     * @param dataTTLConfig
     * @return the name of the latest time-bucket
     */
    String timeBucketBefore(DataTTLConfig dataTTLConfig);

    /**
     * To calculate the timestamp of the latest
     *
     * @param dataTTLConfig
     * @return timestamp
     */
    long timestampBefore(DataTTLConfig dataTTLConfig);
}
