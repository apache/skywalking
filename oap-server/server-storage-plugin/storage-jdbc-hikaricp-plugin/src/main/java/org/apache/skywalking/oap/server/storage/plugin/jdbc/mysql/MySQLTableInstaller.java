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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntKeyLongValueHashMap;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TableInstaller;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ALARM;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_INVENTORY;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.NETWORK_ADDRESS;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SEGMENT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_INVENTORY;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INVENTORY;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_LOG;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_SEGMENT_SNAPSHOT;

/**
 * Extend H2TableInstaller but match MySQL SQL syntax.
 */
@Slf4j
public class MySQLTableInstaller extends H2TableInstaller {
    public MySQLTableInstaller(ModuleManager moduleManager) {
        super(moduleManager);
        /*
         * Override column because the default column names in core have syntax conflict with MySQL.
         */
        this.overrideColumnName("precision", "cal_precision");
        this.overrideColumnName("match", "match_num");
    }

    @Override
    protected void createTable(Client client, Model model) throws StorageException {
        super.createTable(client, model);
        JDBCHikariCPClient jdbcHikariCPClient = (JDBCHikariCPClient) client;
        this.createIndexes(jdbcHikariCPClient, model);
    }

    /**
     * Based on MySQL features, provide a specific data type mappings.
     */
    @Override
    protected String getColumnType(Model model, ColumnName name, Class<?> type) {
        if (Integer.class.equals(type) || int.class.equals(type)) {
            return "INT";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return "BIGINT";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return "DOUBLE";
        } else if (String.class.equals(type)) {
            if (SEGMENT == model.getScopeId() || PROFILE_TASK_SEGMENT_SNAPSHOT == model.getScopeId()) {
                if (name.getName().equals(SegmentRecord.TRACE_ID) || name.getName().equals(SegmentRecord.SEGMENT_ID))
                    return "VARCHAR(300)";
                if (name.getName().equals(SegmentRecord.DATA_BINARY)) {
                    return "MEDIUMTEXT";
                }
            }
            if (PROFILE_TASK_LOG == model.getScopeId() || PROFILE_TASK_SEGMENT_SNAPSHOT == model.getScopeId()) {
                if (name.getName().equals(ProfileTaskLogRecord.TASK_ID)) {
                    return "VARCHAR(300)";
                }
            }
            return "VARCHAR(2000)";
        } else if (IntKeyLongValueHashMap.class.equals(type)) {
            return "MEDIUMTEXT";
        } else if (byte[].class.equals(type)) {
            return "MEDIUMTEXT";
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }

    /**
     * Create indexes of all tables. Due to MySQL storage is suitable for middle size use case and also compatible with
     * TiDB users, Indexes are required for the UI query.
     *
     * Based on different Model, provide different index creation strategy.
     */
    protected void createIndexes(JDBCHikariCPClient client, Model model) throws StorageException {
        switch (model.getScopeId()) {
            case SERVICE_INVENTORY:
            case SERVICE_INSTANCE_INVENTORY:
            case NETWORK_ADDRESS:
            case ENDPOINT_INVENTORY:
                createInventoryIndexes(client, model);
                return;
            case SEGMENT:
                createSegmentIndexes(client, model);
                return;
            case ALARM:
                createAlarmIndexes(client, model);
                return;
            case PROFILE_TASK_LOG:
                createProfileLogIndexes(client, model);
                return;
            case PROFILE_TASK_SEGMENT_SNAPSHOT:
                createProfileThreadSnapshotIndexes(client, model);
                return;
            default:
                createIndexesForAllMetrics(client, model);
        }
    }

    private void createProfileThreadSnapshotIndexes(JDBCHikariCPClient client, Model model) throws StorageException {
        try (Connection connection = client.getConnection()) {
            // query by task id, sequence
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_TASK_ID_SEQUENCE ");
            tableIndexSQL.append("ON ")
                    .append(model.getName())
                    .append("(")
                    .append(ProfileThreadSnapshotRecord.TASK_ID)
                    .append(", ")
                    .append(ProfileThreadSnapshotRecord.SEQUENCE)
                    .append(")");
            createIndex(client, connection, model, tableIndexSQL);

            // query by segment id, sequence
            tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_SEGMENT_ID_SEQUENCE ");
            tableIndexSQL.append("ON ")
                    .append(model.getName())
                    .append("(")
                    .append(ProfileThreadSnapshotRecord.SEGMENT_ID)
                    .append(", ")
                    .append(ProfileThreadSnapshotRecord.SEQUENCE)
                    .append(")");
            createIndex(client, connection, model, tableIndexSQL);

            // query by segment id, dump time
            tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_SEGMENT_ID_DUMP_TIME ");
            tableIndexSQL.append("ON ")
                    .append(model.getName())
                    .append("(")
                    .append(ProfileThreadSnapshotRecord.SEGMENT_ID)
                    .append(", ")
                    .append(ProfileThreadSnapshotRecord.DUMP_TIME)
                    .append(")");
            createIndex(client, connection, model, tableIndexSQL);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void createProfileLogIndexes(JDBCHikariCPClient client, Model model) throws StorageException {
        try (Connection connection = client.getConnection()) {
            // query by task id
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_TASK_ID ");
            tableIndexSQL.append("ON ")
                    .append(model.getName())
                    .append("(")
                    .append(ProfileTaskLogRecord.TASK_ID)
                    .append(")");
            createIndex(client, connection, model, tableIndexSQL);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void createIndexesForAllMetrics(JDBCHikariCPClient client, Model model) throws StorageException {
        try (Connection connection = client.getConnection()) {
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_TIME_BUCKET ");
            tableIndexSQL.append("ON ")
                         .append(model.getName())
                         .append("(")
                         .append(SegmentRecord.TIME_BUCKET)
                         .append(")");
            createIndex(client, connection, model, tableIndexSQL);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void createAlarmIndexes(JDBCHikariCPClient client, Model model) throws StorageException {
        try (Connection connection = client.getConnection()) {
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_TIME_BUCKET ");
            tableIndexSQL.append("ON ")
                         .append(model.getName())
                         .append("(")
                         .append(SegmentRecord.TIME_BUCKET)
                         .append(")");
            createIndex(client, connection, model, tableIndexSQL);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void createSegmentIndexes(JDBCHikariCPClient client, Model model) throws StorageException {
        try (Connection connection = client.getConnection()) {
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_TRACE_ID ");
            tableIndexSQL.append("ON ").append(model.getName()).append("(").append(SegmentRecord.TRACE_ID).append(")");
            createIndex(client, connection, model, tableIndexSQL);

            tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_ENDPOINT_ID ");
            tableIndexSQL.append("ON ")
                         .append(model.getName())
                         .append("(")
                         .append(SegmentRecord.ENDPOINT_ID)
                         .append(")");
            createIndex(client, connection, model, tableIndexSQL);

            tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_LATENCY ");
            tableIndexSQL.append("ON ").append(model.getName()).append("(").append(SegmentRecord.LATENCY).append(")");
            createIndex(client, connection, model, tableIndexSQL);

            tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_TIME_BUCKET ");
            tableIndexSQL.append("ON ")
                         .append(model.getName())
                         .append("(")
                         .append(SegmentRecord.TIME_BUCKET)
                         .append(")");
            createIndex(client, connection, model, tableIndexSQL);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void createInventoryIndexes(JDBCHikariCPClient client, Model model) throws StorageException {
        try (Connection connection = client.getConnection()) {
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE UNIQUE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_SEQ ");
            tableIndexSQL.append("ON ").append(model.getName()).append("(").append(RegisterSource.SEQUENCE).append(")");
            createIndex(client, connection, model, tableIndexSQL);

            tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase()).append("_TIME ");
            tableIndexSQL.append("ON ")
                         .append(model.getName())
                         .append("(")
                         .append(RegisterSource.HEARTBEAT_TIME)
                         .append(", ")
                         .append(RegisterSource.REGISTER_TIME)
                         .append(")");
            createIndex(client, connection, model, tableIndexSQL);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    private void createIndex(JDBCHikariCPClient client, Connection connection, Model model,
                             SQLBuilder indexSQL) throws JDBCClientException {
        if (log.isDebugEnabled()) {
            log.debug("create index for table {}, sql: {} ", model.getName(), indexSQL.toStringInNewLine());
        }
        client.execute(connection, indexSQL.toString());
    }
}
