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

package org.apache.skywalking.apm.testcase.postgresql.controller;

import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/postgresql-scenario/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    @Autowired
    PostgresqlConfig postgresqlConfig;

    @GetMapping("/healthcheck")
    public String healthcheck() throws Exception {
        SQLExecutor sqlExecute = null;
        try {
            sqlExecute = new SQLExecutor(postgresqlConfig);
            sqlExecute.checkPG(ConstSql.TEST_SQL);
        } catch (SQLException e) {
            LOGGER.error("Failed to execute sql.", e);
            throw e;
        } finally {
            if (sqlExecute != null) {
                try {
                    sqlExecute.closeConnection();
                } catch (SQLException e) {
                    LOGGER.error("Failed to close connection.", e);
                }
            }
        }
        return "Success";
    }

    @GetMapping("/postgres")
    public String postgres() throws SQLException {
        LOGGER.info("Begin to start execute sql");
        SQLExecutor sqlExecute = null;
        try {
            sqlExecute = new SQLExecutor(postgresqlConfig);
            sqlExecute.createTable(ConstSql.CREATE_TABLE_SQL);
            sqlExecute.insertData(ConstSql.INSERT_DATA_SQL, "1", "1");
            sqlExecute.dropTable(ConstSql.DROP_TABLE_SQL);
        } catch (SQLException e) {
            LOGGER.error("Failed to execute sql.", e);
            throw e;
        } finally {
            if (sqlExecute != null) {
                try {
                    sqlExecute.closeConnection();
                } catch (SQLException e) {
                    LOGGER.error("Failed to close connection.", e);
                }
            }
        }
        return "Success";
    }
}

