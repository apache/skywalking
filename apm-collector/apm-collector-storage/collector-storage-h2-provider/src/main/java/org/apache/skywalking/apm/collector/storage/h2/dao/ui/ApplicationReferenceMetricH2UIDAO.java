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

import java.util.List;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationReferenceMetricH2UIDAO extends H2DAO implements IApplicationReferenceMetricUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationReferenceMetricH2UIDAO.class);
    private static final String APPLICATION_REFERENCE_SQL = "select {8}, {9}, sum({0}) as {0}, sum({1}) as {1}, sum({2}) as {2}, " +
        "sum({3}) as {3}, sum({4}) as {4}, sum({5}) as {5} from {6} where {7} >= ? and {7} <= ? group by {8}, {9} limit 100";

    public ApplicationReferenceMetricH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public List<ApplicationReferenceMetric> getReferences(Step step,
        long startTimeBucket, long endTimeBucket, MetricSource metricSource, Integer... applicationIds) {
        return null;
    }
}
