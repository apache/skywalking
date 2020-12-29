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

import org.apache.skywalking.apm.testcase.influxdb.executor.InfluxDBExecutor;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final String SUCCESS = "Success";
    private static final String ERROR = "Error";

    @Value("${influxdb.url:http://127.0.0.1:8086}")
    private String serverURL;

    @RequestMapping("/influxdb-scenario")
    @ResponseBody
    public String testcase() {
        InfluxDBExecutor executor = new InfluxDBExecutor(serverURL);
        // createDatabase
        String db = "skywalking";
        executor.createDatabase(db);
        // createRetentionPolicy
        String rp = "one_day";
        executor.createRetentionPolicyWithOneDay(db, rp);
        Point point = Point.measurement("heartbeat")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .tag("host", "127.0.0.1")
                .addField("device_name", "sensor x")
                .build();
        // write
        executor.write(db, rp, point);
        // query
        executor.query(db, "SELECT * FROM heartbeat");
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        InfluxDBExecutor executor = new InfluxDBExecutor(serverURL);
        Pong pong = executor.ping();
        return pong.getVersion() != null ? SUCCESS : ERROR;
    }
}
