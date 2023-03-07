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
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JDBCUITemplateManagementDAO extends JDBCSQLExecutor implements UITemplateManagementDAO {
    private final JDBCClient h2Client;
    private final TableHelper tableHelper;

    @Override
    @SneakyThrows
    public DashboardConfiguration getTemplate(final String id) {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        final var tables = tableHelper.getTablesForRead(UITemplate.INDEX_NAME);

        for (String table : tables) {
            final StringBuilder sql = new StringBuilder();
            final ArrayList<Object> condition = new ArrayList<>(1);
            sql.append("select * from ").append(table).append(" where ")
               .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?")
               .append(" and id=? LIMIT 1 ");
            condition.add(UITemplate.INDEX_NAME);
            condition.add(id);

            final var result = h2Client.executeQuery(sql.toString(), resultSet -> {
                final UITemplate.Builder builder = new UITemplate.Builder();
                UITemplate uiTemplate = (UITemplate) toStorageData(resultSet, UITemplate.INDEX_NAME, builder);
                if (uiTemplate != null) {
                    return new DashboardConfiguration().fromEntity(uiTemplate);
                }
                return null;
            }, condition.toArray(new Object[0]));
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @Override
    @SneakyThrows
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) {
        final var tables = tableHelper.getTablesForRead(UITemplate.INDEX_NAME);
        final var configs = new ArrayList<DashboardConfiguration>();

        for (String table : tables) {
            final StringBuilder sql = new StringBuilder();
            final ArrayList<Object> condition = new ArrayList<>(1);
            sql.append("select * from ").append(table).append(" where ")
               .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
            condition.add(UITemplate.INDEX_NAME);
            if (!includingDisabled) {
                sql.append(" and ").append(UITemplate.DISABLED).append("=?");
                condition.add(BooleanUtils.booleanToValue(includingDisabled));
            }

            h2Client.executeQuery(sql.toString(), resultSet -> {
                final UITemplate.Builder builder = new UITemplate.Builder();
                UITemplate uiTemplate = null;
                do {
                    uiTemplate = (UITemplate) toStorageData(resultSet, UITemplate.INDEX_NAME, builder);
                    if (uiTemplate != null) {
                        configs.add(new DashboardConfiguration().fromEntity(uiTemplate));
                    }
                }
                while (uiTemplate != null);
                return null;
            }, condition.toArray(new Object[0]));
        }

        return configs;
    }

    @Override
    public TemplateChangeStatus addTemplate(final DashboardSetting setting) throws IOException {
        final var uiTemplate = setting.toEntity();
        final var model = TableMetaInfo.get(UITemplate.INDEX_NAME);
        final SQLExecutor insertExecutor = getInsertExecutor(
            model, uiTemplate, 0, new UITemplate.Builder(), new HashMapConverter.ToStorage(), null);
        try (Connection connection = h2Client.getConnection()) {
            insertExecutor.invoke(connection);
            return TemplateChangeStatus.builder().status(true).id(setting.getId()).build();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder()
                                       .status(false)
                                       .id(setting.getId())
                                       .message("Can't add a new template")
                                       .build();
        }
    }

    @Override
    public TemplateChangeStatus changeTemplate(final DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();
        return executeUpdate(uiTemplate);
    }

    @Override
    public TemplateChangeStatus disableTemplate(final String id) throws IOException {
        final UITemplate uiTemplate = (UITemplate) getByID(
            h2Client, UITemplate.INDEX_NAME, id, new UITemplate.Builder());
        if (uiTemplate == null) {
            return TemplateChangeStatus.builder().status(false).id(id).message("Can't find the template").build();
        }
        uiTemplate.setDisabled(BooleanUtils.TRUE);
        return executeUpdate(uiTemplate);
    }

    private TemplateChangeStatus executeUpdate(final UITemplate uiTemplate) throws IOException {
        final var model = TableMetaInfo.get(UITemplate.INDEX_NAME);
        final var updateExecutor = getUpdateExecutor(
            model, uiTemplate, 0, new UITemplate.Builder(), null);
        try (Connection connection = h2Client.getConnection()) {
            updateExecutor.invoke(connection);
            return TemplateChangeStatus.builder().status(true).id(uiTemplate.getTemplateId()).build();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder()
                                       .status(false)
                                       .id(uiTemplate.getTemplateId())
                                       .message("Can't add/update the template")
                                       .build();
        }
    }
}
