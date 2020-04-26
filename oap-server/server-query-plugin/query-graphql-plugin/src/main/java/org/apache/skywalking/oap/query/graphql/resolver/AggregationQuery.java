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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.TopNEntity;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @since 8.0.0 This query is replaced by {@link MetricsQuery}, all queries have been delegated to there.
 */
@Deprecated
public class AggregationQuery implements GraphQLQueryResolver {
    private MetricsQuery query;

    public AggregationQuery(ModuleManager moduleManager) {
        query = new MetricsQuery(moduleManager);
    }

    public List<TopNEntity> getServiceTopN(final String name, final int topN, final Duration duration,
                                           final Order order) throws IOException {
        TopNCondition condition = new TopNCondition();
        condition.setName(name);
        condition.setScope(Scope.Service);
        condition.setOrder(order);
        condition.setTopN(topN);
        List<TopNEntity> list = new ArrayList<>();
        query.sortMetrics(condition, duration).forEach(selectedRecord -> {
            TopNEntity entity = new TopNEntity(selectedRecord);
            list.add(entity);
        });
        return list;
    }

    public List<TopNEntity> getAllServiceInstanceTopN(final String name, final int topN, final Duration duration,
                                                      final Order order) throws IOException {
        TopNCondition condition = new TopNCondition();
        condition.setName(name);
        condition.setScope(Scope.ServiceInstance);
        condition.setOrder(order);
        condition.setTopN(topN);
        List<TopNEntity> list = new ArrayList<>();
        query.sortMetrics(condition, duration).forEach(selectedRecord -> {
            TopNEntity entity = new TopNEntity(selectedRecord);
            list.add(entity);
        });
        return list;
    }

    public List<TopNEntity> getServiceInstanceTopN(final String serviceId, final String name, final int topN,
                                                   final Duration duration, final Order order) throws IOException {
        TopNCondition condition = new TopNCondition();
        condition.setName(name);
        condition.setScope(Scope.ServiceInstance);
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(serviceId);
        condition.setParentService(serviceIDDefinition.getName());
        condition.setNormal(true);
        condition.setOrder(order);
        condition.setTopN(topN);
        List<TopNEntity> list = new ArrayList<>();
        query.sortMetrics(condition, duration).forEach(selectedRecord -> {
            TopNEntity entity = new TopNEntity(selectedRecord);
            list.add(entity);
        });
        return list;
    }

    public List<TopNEntity> getAllEndpointTopN(final String name, final int topN, final Duration duration,
                                               final Order order) throws IOException {
        TopNCondition condition = new TopNCondition();
        condition.setName(name);
        condition.setScope(Scope.Endpoint);
        condition.setOrder(order);
        condition.setTopN(topN);
        List<TopNEntity> list = new ArrayList<>();
        query.sortMetrics(condition, duration).forEach(selectedRecord -> {
            TopNEntity entity = new TopNEntity(selectedRecord);
            list.add(entity);
        });
        return list;
    }

    public List<TopNEntity> getEndpointTopN(final String serviceId, final String name, final int topN,
                                            final Duration duration, final Order order) throws IOException {
        TopNCondition condition = new TopNCondition();
        condition.setName(name);
        condition.setScope(Scope.Endpoint);
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(serviceId);
        condition.setParentService(serviceIDDefinition.getName());
        condition.setNormal(true);
        condition.setOrder(order);
        condition.setTopN(topN);
        List<TopNEntity> list = new ArrayList<>();
        query.sortMetrics(condition, duration).forEach(selectedRecord -> {
            TopNEntity entity = new TopNEntity(selectedRecord);
            list.add(entity);
        });
        return list;
    }
}
