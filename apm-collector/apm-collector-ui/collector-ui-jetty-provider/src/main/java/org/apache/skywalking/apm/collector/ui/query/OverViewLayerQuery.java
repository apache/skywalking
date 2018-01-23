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

package org.apache.skywalking.apm.collector.ui.query;

import java.util.List;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.collector.storage.ui.overview.AlarmTrend;
import org.apache.skywalking.apm.collector.storage.ui.overview.ClusterBrief;
import org.apache.skywalking.apm.collector.storage.ui.overview.ConjecturalAppBrief;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceInfo;
import org.apache.skywalking.apm.collector.ui.graphql.Query;

/**
 * @author peng-yongsheng
 */
public class OverViewLayerQuery implements Query {

    public OverViewLayerQuery(ModuleManager moduleManager) {
    }

    public Topology getClusterTopology(Duration duration) {
        return null;
    }

    public ClusterBrief getClusterBrief(Duration duration) {
        return null;
    }

    public AlarmTrend getAlarmTrend(Duration duration) {
        return null;
    }

    public ConjecturalAppBrief getConjecturalApps(Duration duration) {
        return null;
    }

    public List<ServiceInfo> getTopNSlowService(Duration duration, int topN) {
        return null;
    }

    public List<AppServerInfo> getTopNServerThroughput(Duration duration, int topN) {
        return null;
    }
}
