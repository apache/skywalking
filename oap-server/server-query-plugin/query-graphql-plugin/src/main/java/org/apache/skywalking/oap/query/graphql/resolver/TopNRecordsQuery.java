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
import org.apache.skywalking.oap.query.graphql.type.TopNRecordsCondition;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.query.type.TopNRecord;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @since 8.0.0 This query is replaced by {@link MetricsQuery}, all queries have been delegated to there.
 */
@Deprecated
public class TopNRecordsQuery implements GraphQLQueryResolver {
    private MetricsQuery query;

    public TopNRecordsQuery(ModuleManager moduleManager) {
        query = new MetricsQuery(moduleManager);
    }

    public List<TopNRecord> getTopNRecords(TopNRecordsCondition condition) throws IOException {
        TopNCondition topNCondition = new TopNCondition();
        topNCondition.setName(condition.getMetricName());
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
            condition.getServiceId());
        topNCondition.setParentService(serviceIDDefinition.getName());
        topNCondition.setNormal(serviceIDDefinition.isReal());
        // Scope is not required in topN record query.
        // topNCondition.setScope();
        topNCondition.setOrder(condition.getOrder());
        topNCondition.setTopN(condition.getTopN());

        final List<SelectedRecord> selectedRecords = query.readSampledRecords(topNCondition, condition.getDuration());
        List<TopNRecord> list = new ArrayList<>(selectedRecords.size());
        selectedRecords.forEach(record -> {
            TopNRecord top = new TopNRecord();
            top.setStatement(record.getName());
            top.setTraceId(record.getRefId());
            top.setLatency(Long.parseLong(record.getValue()));
            list.add(top);
        });
        return list;
    }
}
