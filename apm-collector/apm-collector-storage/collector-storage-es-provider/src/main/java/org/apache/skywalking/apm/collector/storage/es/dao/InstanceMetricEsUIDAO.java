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

package org.apache.skywalking.apm.collector.storage.es.dao;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricEsUIDAO extends EsDAO implements IInstanceMetricUIDAO {

    public InstanceMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<Integer> getServerTPSTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);

        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
            prepareMultiGet.add(tableName, InstanceMetricTable.TABLE_TYPE, id);
        });

        List<Integer> throughputTrend = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();

        for (int i = 0; i < multiGetResponse.getResponses().length; i++) {
            MultiGetItemResponse response = multiGetResponse.getResponses()[i];
            if (response.getResponse().isExists()) {
                long callTimes = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue();
                throughputTrend.add((int)(callTimes / durationPoints.get(i).getSecondsBetween()));
            } else {
                throughputTrend.add(0);
            }
        }
        return throughputTrend;
    }

    @Override public List<Integer> getResponseTimeTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();
        String tableName = TimePyramidTableNameBuilder.build(step, InstanceMetricTable.TABLE);

        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + MetricSource.Callee.getValue();
            prepareMultiGet.add(tableName, InstanceMetricTable.TABLE_TYPE, id);
        });

        List<Integer> responseTimeTrends = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                long callTimes = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_CALLS)).longValue();
                long errorCallTimes = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS)).longValue();
                long durationSum = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM)).longValue();
                long errorDurationSum = ((Number)response.getResponse().getSource().get(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM)).longValue();
                responseTimeTrends.add((int)((durationSum - errorDurationSum) / (callTimes - errorCallTimes)));
            } else {
                responseTimeTrends.add(0);
            }
        }
        return responseTimeTrends;
    }
}
