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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class BatchShardingjdbcDAO extends ShardingjdbcDAO implements IBatchDAO {

    private static final Logger logger = LoggerFactory.getLogger(BatchShardingjdbcDAO.class);

    public BatchShardingjdbcDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override
    public void batchPersistence(List<?> batchCollection) {
        if (batchCollection != null && batchCollection.size() > 0) {
            logger.debug("the batch collection size is {}", batchCollection.size());
            Connection conn;
            final Map<String, PreparedStatement> batchSqls = new HashMap<>();
            try {
                conn = getClient().getConnection();
                conn.setAutoCommit(true);
                PreparedStatement ps;
                for (Object entity : batchCollection) {
                    ShardingjdbcSqlEntity e = getShardingjdbcSqlEntity(entity);
                    String sql = e.getSql();
                    if (batchSqls.containsKey(sql)) {
                        ps = batchSqls.get(sql);
                    } else {
                        ps = conn.prepareStatement(sql);
                        batchSqls.put(sql, ps);
                    }

                    Object[] params = e.getParams();
                    if (params != null) {
                        logger.debug("the sql is {}, params size is {}, params: {}", e.getSql(), params.length, params);
                        for (int i = 0; i < params.length; i++) {
                            ps.setObject(i + 1, params[i]);
                        }
                    }
                    ps.addBatch();
                }

                for (String k : batchSqls.keySet()) {
                    batchSqls.get(k).executeBatch();
                }
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
            }
            batchSqls.clear();
        }
    }

    private ShardingjdbcSqlEntity getShardingjdbcSqlEntity(Object entity) {
        if (entity instanceof ShardingjdbcSqlEntity) {
            return (ShardingjdbcSqlEntity)entity;
        }
        return null;
    }
}
