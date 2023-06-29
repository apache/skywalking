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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenu;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;

@Slf4j
@RequiredArgsConstructor
public class JDBCUIMenuManagementDAO extends JDBCSQLExecutor implements UIMenuManagementDAO {
    private final JDBCClient h2Client;
    private final TableHelper tableHelper;

    @SneakyThrows
    @Override
    public UIMenu getMenu(String id) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(UIMenu.INDEX_NAME);

        for (String table : tables) {
            final StringBuilder sql = new StringBuilder();
            final ArrayList<Object> condition = new ArrayList<>(1);
            sql.append("select * from ").append(table).append(" where ")
                .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?")
                .append(" and id=? LIMIT 1 ");
            condition.add(UIMenu.INDEX_NAME);
            condition.add(id);

            final var result = h2Client.executeQuery(sql.toString(), resultSet -> {
                final UIMenu.Builder builder = new UIMenu.Builder();
                return (UIMenu) toStorageData(resultSet, UIMenu.INDEX_NAME, builder);
            }, condition.toArray(new Object[0]));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @SneakyThrows
    @Override
    public void saveMenu(UIMenu menu) throws IOException {
        final var model = TableMetaInfo.get(UIMenu.INDEX_NAME);
        final SQLExecutor insertExecutor = getInsertExecutor(
            model, menu, 0, new UIMenu.Builder(), new HashMapConverter.ToStorage(), null);
        try (Connection connection = h2Client.getConnection()) {
            insertExecutor.invoke(connection);
        }
    }
}
