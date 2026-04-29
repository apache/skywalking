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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

/**
 * JDBC read + delete for the {@link RuntimeRule} management table. Reuses the same
 * {@code JDBCTableInstaller.TABLE_COLUMN} multi-entity pattern that every other management
 * DAO in this plugin uses, so a single physical table can host records for multiple
 * management models without schema churn.
 */
@Slf4j
@RequiredArgsConstructor
public class JDBCRuntimeRuleManagementDAO extends JDBCSQLExecutor implements RuntimeRuleManagementDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public List<RuntimeRuleFile> getAll() {
        final List<String> tables = tableHelper.getTablesWithinTTL(RuntimeRule.INDEX_NAME);
        final List<RuntimeRuleFile> files = new ArrayList<>();

        for (final String table : tables) {
            final StringBuilder sql = new StringBuilder();
            sql.append("select ")
               .append(RuntimeRule.CATALOG).append(", ")
               .append(RuntimeRule.NAME).append(", ")
               .append(RuntimeRule.CONTENT).append(", ")
               .append(RuntimeRule.STATUS).append(", ")
               .append(RuntimeRule.UPDATE_TIME)
               .append(" from ").append(table)
               .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
            jdbcClient.executeQuery(sql.toString(), resultSet -> {
                while (resultSet.next()) {
                    files.add(new RuntimeRuleFile(
                        resultSet.getString(RuntimeRule.CATALOG),
                        resultSet.getString(RuntimeRule.NAME),
                        resultSet.getString(RuntimeRule.CONTENT),
                        resultSet.getString(RuntimeRule.STATUS),
                        resultSet.getLong(RuntimeRule.UPDATE_TIME)
                    ));
                }
                return null;
            }, RuntimeRule.INDEX_NAME);
        }
        return files;
    }

    @Override
    public void save(final RuntimeRule rule) throws IOException {
        final Model model = TableMetaInfo.get(RuntimeRule.INDEX_NAME);
        final RuntimeRule.Builder builder = new RuntimeRule.Builder();
        // The shared {@link JDBCSQLExecutor#getByID} is unusable for ManagementData like
        // RuntimeRule: it always passes the lookup id through {@code TableHelper.generateId(
        // String, String)} which prefixes with the model name, but the INSERT path uses
        // {@code TableHelper.generateId(Model, String)} which returns the RAW id for
        // non-record / non-function-metric types. RuntimeRule is non-record + non-metric, so
        // its row is stored with the raw composite id while getByID looks up "runtimerule_<id>"
        // and never finds it. Without this workaround the second save() always falls through
        // to INSERT and trips the primary-key constraint, breaking every /addOrUpdate update
        // and every /inactivate after the first persist.
        final String storedId = TableHelper.generateId(model, rule.id().build());
        try (Connection connection = jdbcClient.getConnection()) {
            final boolean exists = rowExists(connection, storedId);
            final SQLExecutor executor;
            if (exists) {
                executor = getUpdateExecutor(model, rule, 0, builder, null);
            } else {
                executor = getInsertExecutor(
                    model, rule, 0, builder, new HashMapConverter.ToStorage(), null);
            }
            executor.invoke(connection);
        } catch (final SQLException e) {
            throw new IOException("failed to save runtime rule "
                + rule.getCatalog() + ":" + rule.getName(), e);
        }
    }

    /**
     * Probe every TTL-shadow table for a row with the given stored id. Direct SELECT on the
     * id column rather than the shared getByID helper for the prefix-mismatch reason
     * documented in {@link #save(RuntimeRule)}.
     */
    private boolean rowExists(final Connection connection, final String storedId) throws SQLException {
        final List<String> tables = tableHelper.getTablesWithinTTL(RuntimeRule.INDEX_NAME);
        for (final String table : tables) {
            final String sql = "SELECT 1 FROM " + table
                + " WHERE " + JDBCTableInstaller.ID_COLUMN + " = ? LIMIT 1";
            try (final var stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, storedId);
                try (final var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void delete(final String catalog, final String name) throws IOException {
        final List<String> tables = tableHelper.getTablesWithinTTL(RuntimeRule.INDEX_NAME);
        for (final String table : tables) {
            final String sql = "delete from " + table
                + " where " + JDBCTableInstaller.TABLE_COLUMN + " = ?"
                + " and " + RuntimeRule.CATALOG + " = ?"
                + " and " + RuntimeRule.NAME + " = ?";
            try {
                jdbcClient.executeUpdate(sql, RuntimeRule.INDEX_NAME, catalog, name);
            } catch (final SQLException e) {
                throw new IOException("failed to delete runtime rule " + catalog + ":" + name, e);
            }
        }
    }
}
