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
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IResponseTimeDistributionUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistributionTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistributionEsUIDAO extends EsDAO implements IResponseTimeDistributionUIDAO {

    public ResponseTimeDistributionEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public void loadMetrics(Step step, List<ResponseTimeStep> responseTimeSteps) {
        String tableName = TimePyramidTableNameBuilder.build(step, ResponseTimeDistributionTable.TABLE);

        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet(responseTimeSteps, new ElasticSearchClient.MultiGetRowHandler<ResponseTimeStep>() {
            @Override
            public void accept(ResponseTimeStep responseTimeStep) {
                String id = String.valueOf(responseTimeStep.getDurationPoint()) + Const.ID_SPLIT + String.valueOf(responseTimeStep.getStep());
                this.add(tableName, ResponseTimeDistributionTable.TABLE_TYPE, id);
            }
        });

        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (int i = 0; i < multiGetResponse.getResponses().length; i++) {
            MultiGetItemResponse response = multiGetResponse.getResponses()[i];
            if (response.getResponse().isExists()) {
                long calls = ((Number)response.getResponse().getSource().get(ResponseTimeDistributionTable.CALLS.getName())).longValue();
                long errorCalls = ((Number)response.getResponse().getSource().get(ResponseTimeDistributionTable.ERROR_CALLS.getName())).longValue();
                long successCalls = ((Number)response.getResponse().getSource().get(ResponseTimeDistributionTable.SUCCESS_CALLS.getName())).longValue();

                responseTimeSteps.get(i).setCalls(calls);
                responseTimeSteps.get(i).setErrorCalls(errorCalls);
                responseTimeSteps.get(i).setSuccessCalls(successCalls);
            } else {
                responseTimeSteps.get(i).setCalls(0);
                responseTimeSteps.get(i).setErrorCalls(0);
                responseTimeSteps.get(i).setSuccessCalls(0);
            }
        }
    }
}
