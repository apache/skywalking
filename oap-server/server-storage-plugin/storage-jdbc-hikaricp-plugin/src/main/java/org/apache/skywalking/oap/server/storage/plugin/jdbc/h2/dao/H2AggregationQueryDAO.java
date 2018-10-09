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
import java.util.List;
import org.apache.skywalking.oap.server.core.query.entity.Order;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.query.entity.TopNEntity;
import org.apache.skywalking.oap.server.core.storage.TimePyramidTableNameBuilder;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

/**
 * @author wusheng
 */
public class H2AggregationQueryDAO implements IAggregationQueryDAO {
    private JDBCHikariCPClient h2Client;

    public H2AggregationQueryDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<TopNEntity> getServiceTopN(String name, int topN, Step step, long startTB,
        long endTB, Order order) throws IOException {
//        String tableName = TimePyramidTableNameBuilder.build(step, name);
//        StringBuilder sql = new StringBuilder();
//        sql.append("select ")

        return null;
    }

    @Override public List<TopNEntity> getAllServiceInstanceTopN(String name, int topN, Step step,
        long startTB, long endTB, Order order) throws IOException {
        return null;
    }

    @Override public List<TopNEntity> getServiceInstanceTopN(int serviceId, String name, int topN,
        Step step, long startTB, long endTB, Order order) throws IOException {
        return null;
    }

    @Override
    public List<TopNEntity> getAllEndpointTopN(String name, int topN, Step step, long startTB,
        long endTB, Order order) throws IOException {
        return null;
    }

    @Override
    public List<TopNEntity> getEndpointTopN(int serviceId, String name, int topN, Step step,
        long startTB, long endTB, Order order) throws IOException {
        return null;
    }
}
