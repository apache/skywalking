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

package org.apache.skywalking.apm.testcase.mariadb.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.mariadb.SQLExecutor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";

    private static final String CREATE_TABLE_SQL = "CREATE TABLE test_table(\n" + "id VARCHAR(1) PRIMARY KEY, \n" + "value VARCHAR(10) NOT NULL)";
    private static final String INSERT_DATA_SQL = "INSERT INTO test_table(id, value) VALUES(?,?)";
    private static final String QUERY_DATA_SQL = "SELECT id, value FROM test_table WHERE id = ?";
    private static final String DELETE_DATA_SQL = "DELETE FROM test_table WHERE id=?";
    private static final String DROP_TABLE_SQL = "DROP table test_table";

    @RequestMapping("/mariadb-scenario")
    @ResponseBody
    public String testcase() throws Exception {
        try (SQLExecutor sqlExecute = new SQLExecutor()) {
            sqlExecute.execute(CREATE_TABLE_SQL);
            sqlExecute.insertData(INSERT_DATA_SQL, "1", "value");
            sqlExecute.queryData(QUERY_DATA_SQL, "1");
            sqlExecute.execute(DROP_TABLE_SQL);
        } catch (Exception ex) {
            LOGGER.error("Failed to execute sql.", ex);
            throw ex;
        }
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws Exception {
        return SUCCESS;
    }

}
