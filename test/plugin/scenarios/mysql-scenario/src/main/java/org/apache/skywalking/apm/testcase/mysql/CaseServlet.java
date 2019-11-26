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

package org.apache.skywalking.apm.testcase.mysql;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CaseServlet extends HttpServlet {
    private static Logger logger = LogManager.getLogger(CaseServlet.class);
    private static final String CREATE_TABLE_SQL = "CREATE TABLE test_007(\n" +
        "id VARCHAR(1) PRIMARY KEY, \n" +
        "value VARCHAR(1) NOT NULL)";
    private static final String INSERT_DATA_SQL = "INSERT INTO test_007(id, value) VALUES(?,?)";
    private static final String QUERY_DATA_SQL = "SELECT id, value FROM test_007 WHERE id=?";
    private static final String DELETE_DATA_SQL = "DELETE FROM test_007 WHERE id=?";
    private static final String DROP_TABLE_SQL = "DROP table test_007";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        SQLExecutor sqlExecute = null;
        try {
            sqlExecute = new SQLExecutor();
            sqlExecute.createTable(CREATE_TABLE_SQL);
            sqlExecute.insertData(INSERT_DATA_SQL, "1", "1");
            sqlExecute.dropTable(DROP_TABLE_SQL);
            PrintWriter printWriter = resp.getWriter();
            printWriter.write("success");
            printWriter.flush();
            printWriter.close();
        } catch (SQLException e) {
            logger.error("Failed to execute sql.", e);
        } finally {
            if (sqlExecute != null) {
                try {
                    sqlExecute.closeConnection();
                } catch (SQLException e) {
                    logger.error("Failed to close connection.", e);
                }
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
