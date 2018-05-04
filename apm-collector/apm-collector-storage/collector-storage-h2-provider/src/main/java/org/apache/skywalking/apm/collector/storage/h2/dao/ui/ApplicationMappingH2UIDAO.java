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

package org.apache.skywalking.apm.collector.storage.h2.dao.ui;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationMappingH2UIDAO extends H2DAO implements IApplicationMappingUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingH2UIDAO.class);
    private static final String APPLICATION_MAPPING_SQL = "select {0}, {1} from {2} where {3} >= ? and {3} <= ? group by {0}, {1} limit 100";

    public ApplicationMappingH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public List<ApplicationMapping> load(Step step, long startTimeBucket, long endTimeBucket) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationMappingTable.TABLE);

        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(APPLICATION_MAPPING_SQL, ApplicationMappingTable.APPLICATION_ID.getName(),
            ApplicationMappingTable.MAPPING_APPLICATION_ID.getName(), tableName, ApplicationMappingTable.TIME_BUCKET.getName());

        List<ApplicationMapping> applicationMappings = new LinkedList<>();
        Object[] params = new Object[] {startTimeBucket, endTimeBucket};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int applicationId = rs.getInt(ApplicationMappingTable.APPLICATION_ID.getName());
                int addressId = rs.getInt(ApplicationMappingTable.MAPPING_APPLICATION_ID.getName());

                ApplicationMapping applicationMapping = new ApplicationMapping();
                applicationMapping.setApplicationId(applicationId);
                applicationMapping.setMappingApplicationId(addressId);
                applicationMappings.add(applicationMapping);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        logger.debug("node mapping data: {}", applicationMappings.toString());
        return applicationMappings;
    }
}
