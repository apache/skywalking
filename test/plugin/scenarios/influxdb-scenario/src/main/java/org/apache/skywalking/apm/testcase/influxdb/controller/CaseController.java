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

package org.apache.skywalking.apm.testcase.influxdb.controller;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final String SUCCESS = "Success";

    @Value("${influxdb.url:http://127.0.0.1:8086}")
    private String serverURL;

    @RequestMapping("/influxdb-scenario")
    @ResponseBody
    public String testcase() throws Exception {
        final InfluxDB influxDB = InfluxDBFactory.connect(serverURL);
        influxDB.ping();
        // Create a database...
        String databaseName = "skywalking";
        influxDB.query(new Query("CREATE DATABASE " + databaseName));
        influxDB.setDatabase(databaseName);

        // ... and a retention policy, if necessary.
        String retentionPolicyName = "one_day_only";
        influxDB.query(new Query("CREATE RETENTION POLICY " + retentionPolicyName
            + " ON " + databaseName + " DURATION 1d REPLICATION 1 DEFAULT"));
        influxDB.setRetentionPolicy(retentionPolicyName);

        // Write points to InfluxDB.
        influxDB.write(Point.measurement("heartbeat")
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .tag("host", "127.0.0.1")
            .addField("device_name", "sensor x")
            .build());

        // Query your data using InfluxQL.
        QueryResult queryResult = influxDB.query(new Query("SELECT * FROM heartbeat"));

        System.out.println(queryResult);

        // Close it if your application is terminating or you are not using it anymore.
        influxDB.close();
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws Exception {
        try (InfluxDB influxDB = InfluxDBFactory.connect(serverURL)) {

        }
        return SUCCESS;
    }
}
