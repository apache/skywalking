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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;

/**
 * @author peng-yongsheng
 */
public class MemoryMetricEsUIDAO extends EsDAO implements IMemoryMetricUIDAO {

    public MemoryMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public Trend getHeapMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getMemoryTrend(instanceId, step, durationPoints, true);
    }

    @Override public Trend getNoHeapMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getMemoryTrend(instanceId, step, durationPoints, false);
    }

    private Trend getMemoryTrend(int instanceId, Step step, List<DurationPoint> durationPoints,
        boolean isHeap) {
        String tableName = TimePyramidTableNameBuilder.build(step, MemoryMetricTable.TABLE);
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + BooleanUtils.booleanToValue(isHeap);
            prepareMultiGet.add(tableName, MemoryMetricTable.TABLE_TYPE, id);
        });

        Trend trend = new Trend();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                long max = ((Number)response.getResponse().getSource().get(MemoryMetricTable.COLUMN_MAX)).longValue();
                long used = ((Number)response.getResponse().getSource().get(MemoryMetricTable.COLUMN_USED)).longValue();
                long times = ((Number)response.getResponse().getSource().get(MemoryMetricTable.COLUMN_TIMES)).longValue();

                trend.getMetrics().add((int)(used / times));

                if (max < 0) {
                    trend.getMaxMetrics().add((int)(used / times));
                } else {
                    trend.getMaxMetrics().add((int)(max / times));
                }
            } else {
                trend.getMetrics().add(0);
                trend.getMaxMetrics().add(0);
            }
        }

        return trend;
    }
}
