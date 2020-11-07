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

package org.apache.skywalking.apm.testcase.shardingsphere.controller;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.skywalking.apm.testcase.shardingsphere.service.api.service.CommonService;
import org.apache.skywalking.apm.testcase.shardingsphere.service.config.ShardingDatabasesAndTablesConfigurationPrecise;
import org.apache.skywalking.apm.testcase.shardingsphere.service.repository.jdbc.JDBCOrderItemRepositoryImpl;
import org.apache.skywalking.apm.testcase.shardingsphere.service.repository.jdbc.JDBCOrderRepositoryImpl;
import org.apache.skywalking.apm.testcase.shardingsphere.service.repository.service.RawPojoService;
import org.apache.skywalking.apm.testcase.shardingsphere.service.utility.config.DataSourceUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {
    
    private CommonService commonService = null;

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws SQLException {
        DataSourceUtil.createDataSource("");
        DataSourceUtil.createSchema("demo_ds_0");
        DataSourceUtil.createSchema("demo_ds_1");
        DataSourceUtil.createDataSource("demo_ds_0");
        DataSourceUtil.createDataSource("demo_ds_1");
        DataSource dataSource = new ShardingDatabasesAndTablesConfigurationPrecise().createDataSource();
        commonService = new RawPojoService(new JDBCOrderRepositoryImpl(dataSource), new JDBCOrderItemRepositoryImpl(dataSource));
        commonService.initEnvironment();
        return "Success";
    }

    @RequestMapping("/execute")
    @ResponseBody
    public String execute() {
        commonService.processSuccess(false);
        return "Success";
    }
}
