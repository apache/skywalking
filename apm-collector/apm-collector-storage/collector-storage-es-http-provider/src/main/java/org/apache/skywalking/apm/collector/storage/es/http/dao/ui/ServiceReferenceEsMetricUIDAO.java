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

package org.apache.skywalking.apm.collector.storage.es.http.dao.ui;

import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;

/**
 * @author cyberdak
 */
public class ServiceReferenceEsMetricUIDAO extends EsHttpDAO implements IServiceReferenceMetricUIDAO {

    public ServiceReferenceEsMetricUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    //TODO
    @Override
    public List<ServiceReferenceMetric> getFrontServices(Step step, long startTimeBucket,
            long endTimeBucket, MetricSource metricSource, int behindServiceId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ServiceReferenceMetric> getBehindServices(Step step, long startTimeBucket,
            long endTimeBucket, MetricSource metricSource, int frontServiceId) {
        // TODO Auto-generated method stub
        return null;
    }

}
