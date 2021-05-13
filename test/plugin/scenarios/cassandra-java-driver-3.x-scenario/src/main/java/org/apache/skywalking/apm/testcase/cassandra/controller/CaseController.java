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

package org.apache.skywalking.apm.testcase.cassandra.controller;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
public class CaseController {

    @Value("${cassandra.host}")
    private String host;

    @Value("${cassandra.port}")
    private int port;

    private static final String TEST_EXIST_SQL = "SELECT now() FROM system.local";
    private static final String CREATE_KEYSPACE_SQL = "CREATE KEYSPACE IF NOT EXISTS demo WITH replication = " + "{'class': 'SimpleStrategy', 'replication_factor': 1}";
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS demo.test(id TEXT PRIMARY KEY, value TEXT)";
    private static final String INSERT_DATA_SQL = "INSERT INTO demo.test(id, value) VALUES(?,?)";
    private static final String SELECT_DATA_SQL = "SELECT * FROM demo.test WHERE id = ?";
    private static final String DELETE_DATA_SQL = "DELETE FROM demo.test WHERE id = ?";
    private static final String DROP_TABLE_SQL = "DROP TABLE IF EXISTS demo.test";
    private static final String DROP_KEYSPACE = "DROP KEYSPACE IF EXISTS demo";

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);
    private static final String SUCCESS = "Success";

    @RequestMapping("/cassandra")
    @ResponseBody
    public String testcase() {
        LOGGER.info("cassandra contact points: {}:{}", host, port);

        Cluster cluster = null;
        Session session = null;
        try {
            cluster = Cluster.builder().addContactPoint(host).withPort(port).withoutJMXReporting().build();
            session = cluster.connect();
            LOGGER.info("cassandra connection open");

            execute(session);
            executeAsync(session);

            return SUCCESS;
        } finally {
            if (session != null) {
                session.close();
            }
            if (cluster != null) {
                cluster.close();
            }
            LOGGER.info("cassandra connection close");
        }
    }

    private void execute(Session session) {
        LOGGER.info("execute in sync");

        ResultSet createKeyspaceDataResultSet = session.execute(CREATE_KEYSPACE_SQL);
        LOGGER.info("CREATE KEYSPACE result: " + createKeyspaceDataResultSet.toString());

        ResultSet createTableDataResultSet = session.execute(CREATE_TABLE_SQL);
        LOGGER.info("CREATE TABLE result: " + createTableDataResultSet.toString());

        PreparedStatement insertDataPreparedStatement = session.prepare(INSERT_DATA_SQL);
        ResultSet insertDataResultSet = session.execute(insertDataPreparedStatement.bind("101", "foobar"));
        LOGGER.info("INSERT result: " + insertDataResultSet.toString());

        PreparedStatement selectDataPreparedStatement = session.prepare(SELECT_DATA_SQL);
        ResultSet resultSet = session.execute(selectDataPreparedStatement.bind("101"));
        Row row = resultSet.one();
        LOGGER.info("SELECT result: id: {}, value: {}", row.getString("id"), row.getString("value"));

        PreparedStatement deleteDataPreparedStatement = session.prepare(DELETE_DATA_SQL);
        ResultSet deleteDataResultSet = session.execute(deleteDataPreparedStatement.bind("101"));
        LOGGER.info("DELETE result: " + deleteDataResultSet.toString());

        ResultSet dropTableDataResultSet = session.execute(DROP_TABLE_SQL);
        LOGGER.info("DROP TABLE result: " + dropTableDataResultSet.toString());

        ResultSet dropKeyspaceDataResultSet = session.execute(DROP_KEYSPACE);
        LOGGER.info("DROP KEYSPACE result: " + dropKeyspaceDataResultSet.toString());
    }

    private void executeAsync(Session session) {
        LOGGER.info("execute in async");

        ResultSetFuture createKeyspaceDataResultSetFuture = session.executeAsync(CREATE_KEYSPACE_SQL);
        ResultSet createKeyspaceDataResultSet = createKeyspaceDataResultSetFuture.getUninterruptibly();
        LOGGER.info("CREATE KEYSPACE result: " + createKeyspaceDataResultSet.toString());

        ResultSetFuture createTableDataResultSetFuture = session.executeAsync(CREATE_TABLE_SQL);
        ResultSet createTableDataResultSet = createTableDataResultSetFuture.getUninterruptibly();
        LOGGER.info("CREATE TABLE result: " + createTableDataResultSet.toString());

        PreparedStatement insertDataPreparedStatement = session.prepare(INSERT_DATA_SQL);
        ResultSetFuture insertDataResultSetFuture = session.executeAsync(insertDataPreparedStatement.bind("101", "foobar"));
        ResultSet insertDataResultSet = insertDataResultSetFuture.getUninterruptibly();
        LOGGER.info("INSERT result: " + insertDataResultSet.toString());

        PreparedStatement selectDataPreparedStatement = session.prepare(SELECT_DATA_SQL);
        ResultSetFuture resultSetFuture = session.executeAsync(selectDataPreparedStatement.bind("101"));
        ResultSet resultSet = resultSetFuture.getUninterruptibly();
        Row row = resultSet.one();
        LOGGER.info("SELECT result: id: {}, value: {}", row.getString("id"), row.getString("value"));

        PreparedStatement deleteDataPreparedStatement = session.prepare(DELETE_DATA_SQL);
        ResultSetFuture deleteDataResultSetFuture = session.executeAsync(deleteDataPreparedStatement.bind("101"));
        ResultSet deleteDataResultSet = deleteDataResultSetFuture.getUninterruptibly();
        LOGGER.info("DELETE result: " + deleteDataResultSet.toString());

        ResultSetFuture dropTableDataResultSetFuture = session.executeAsync(DROP_TABLE_SQL);
        ResultSet dropTableDataResultSet = dropTableDataResultSetFuture.getUninterruptibly();
        LOGGER.info("DROP TABLE result: " + dropTableDataResultSet.toString());

        ResultSetFuture dropKeyspaceDataResultSetFuture = session.executeAsync(DROP_KEYSPACE);
        ResultSet dropKeyspaceDataResultSet = dropKeyspaceDataResultSetFuture.getUninterruptibly();
        LOGGER.info("DROP KEYSPACE result: " + dropKeyspaceDataResultSet.toString());
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        Cluster cluster = null;
        Session session = null;
        try {
            cluster = Cluster.builder().addContactPoint(host).withPort(port).withoutJMXReporting().build();
            session = cluster.connect();
            LOGGER.info("cassandra connection open");

            PreparedStatement testPreparedStatement = session.prepare(TEST_EXIST_SQL);
            LOGGER.info("Test result:" + testPreparedStatement.toString());
        } finally {
            if (session != null) {
                session.close();
            }
            if (cluster != null) {
                cluster.close();
            }
            LOGGER.info("cassandra connection close");
        }
        return SUCCESS;
    }
}
