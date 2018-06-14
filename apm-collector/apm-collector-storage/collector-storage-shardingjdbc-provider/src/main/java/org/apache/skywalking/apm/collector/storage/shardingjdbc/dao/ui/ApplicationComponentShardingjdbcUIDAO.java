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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ApplicationComponentShardingjdbcUIDAO extends ShardingjdbcDAO implements IApplicationComponentUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationComponentShardingjdbcUIDAO.class);
    private static final String APPLICATION_COMPONENT_SQL = "select {0}, {1} from {2} where {3} >= ? and {3} <= ? group by {0}, {1} limit 100";

    public ApplicationComponentShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public List<ApplicationComponent> load(Step step, long startTimeBucket, long endTimeBucket) {
        ShardingjdbcClient client = getClient();

        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationComponentTable.TABLE);

        List<ApplicationComponent> applicationComponents = new LinkedList<>();
        String sql = SqlBuilder.buildSql(APPLICATION_COMPONENT_SQL, ApplicationComponentTable.COMPONENT_ID.getName(), ApplicationComponentTable.APPLICATION_ID.getName(),
            tableName, ApplicationComponentTable.TIME_BUCKET.getName());

        Object[] params = new Object[] {startTimeBucket, endTimeBucket};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                int applicationId = rs.getInt(ApplicationComponentTable.APPLICATION_ID.getName());
                int componentId = rs.getInt(ApplicationComponentTable.COMPONENT_ID.getName());

                ApplicationComponent applicationComponent = new ApplicationComponent();
                applicationComponent.setComponentId(componentId);
                applicationComponent.setApplicationId(applicationId);
                applicationComponents.add(applicationComponent);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return applicationComponents;
    }
}
