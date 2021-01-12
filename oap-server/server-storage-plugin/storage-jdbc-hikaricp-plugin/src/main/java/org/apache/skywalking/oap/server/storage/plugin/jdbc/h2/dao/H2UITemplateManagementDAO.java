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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

@Slf4j
@RequiredArgsConstructor
public class H2UITemplateManagementDAO extends H2SQLExecutor implements UITemplateManagementDAO {
    private final JDBCHikariCPClient h2Client;

    @Override
    public List<DashboardConfiguration> getAllTemplates(final Boolean includingDisabled) throws IOException {
        final StringBuilder sql = new StringBuilder();
        final ArrayList<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(UITemplate.INDEX_NAME).append(" where 1=1 ");
        if (!includingDisabled) {
            sql.append(" and ").append(UITemplate.DISABLED).append("=?");
            condition.add(BooleanUtils.booleanToValue(includingDisabled));
        }

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]))) {
                final List<DashboardConfiguration> configs = new ArrayList<>();
                final UITemplate.Builder builder = new UITemplate.Builder();
                UITemplate uiTemplate = null;
                do {
                    uiTemplate = (UITemplate) toStorageData(resultSet, UITemplate.INDEX_NAME, builder);
                    if (uiTemplate != null) {
                        configs.add(new DashboardConfiguration().fromEntity(uiTemplate));
                    }
                } while (uiTemplate != null);
                return configs;
            }
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e);
        }
    }

    @Override
    public TemplateChangeStatus addTemplate(final DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();
        final SQLExecutor insertExecutor = getInsertExecutor(UITemplate.INDEX_NAME, uiTemplate, new UITemplate.Builder());
        try (Connection connection = h2Client.getConnection()) {
            insertExecutor.invoke(connection);
            return TemplateChangeStatus.builder().status(true).build();
        } catch (SQLException | JDBCClientException e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder().status(false).message("Can't add a new template").build();
        }
    }

    @Override
    public TemplateChangeStatus changeTemplate(final DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();
        return executeUpdate(uiTemplate);
    }

    @Override
    public TemplateChangeStatus disableTemplate(final String name) throws IOException {
        final UITemplate uiTemplate = (UITemplate) getByID(h2Client, UITemplate.INDEX_NAME, name, new UITemplate.Builder());
        if (uiTemplate == null) {
            return TemplateChangeStatus.builder().status(false).message("Can't find the template").build();
        }
        uiTemplate.setDisabled(BooleanUtils.TRUE);
        return executeUpdate(uiTemplate);
    }

    private TemplateChangeStatus executeUpdate(final UITemplate uiTemplate) throws IOException {
        final SQLExecutor updateExecutor = getUpdateExecutor(UITemplate.INDEX_NAME, uiTemplate, new UITemplate.Builder());
        try (Connection connection = h2Client.getConnection()) {
            updateExecutor.invoke(connection);
            return TemplateChangeStatus.builder().status(true).build();
        } catch (SQLException | JDBCClientException e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder().status(false).message("Can't add/update the template").build();
        }
    }
}
