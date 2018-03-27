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

import java.text.ParseException;
import java.util.List;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.service.ApplicationService;
import org.apache.skywalking.apm.collector.ui.service.ApplicationTopologyService;
import org.apache.skywalking.apm.collector.ui.service.ServerService;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;

/**
 * @author peng-yongsheng
 */
public class ApplicationQuery implements Query {

    private final ModuleManager moduleManager;
    private ApplicationService applicationService;
    private ApplicationTopologyService applicationTopologyService;
    private ServerService serverService;

    public ApplicationQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ApplicationService getApplicationService() {
        if (ObjectUtils.isEmpty(applicationService)) {
            this.applicationService = new ApplicationService(moduleManager);
        }
        return applicationService;
    }

    private ApplicationTopologyService getApplicationTopologyService() {
        if (ObjectUtils.isEmpty(applicationTopologyService)) {
            this.applicationTopologyService = new ApplicationTopologyService(moduleManager);
        }
        return applicationTopologyService;
    }

    private ServerService getServerService() {
        if (ObjectUtils.isEmpty(serverService)) {
            this.serverService = new ServerService(moduleManager);
        }
        return serverService;
    }

    public List<Application> getAllApplication(Duration duration) throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getApplicationService().getApplications(startSecondTimeBucket, endSecondTimeBucket);
    }

    public Topology getApplicationTopology(int applicationId, Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getApplicationTopologyService().getApplicationTopology(duration.getStep(), applicationId, startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket);
    }

    public List<ServiceMetric> getSlowService(int applicationId, Duration duration,
        Integer topN) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getApplicationService().getSlowService(applicationId, duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket, topN);
    }

    public List<AppServerInfo> getServerThroughput(int applicationId, Duration duration,
        Integer topN) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getServerService().getServerThroughput(applicationId, duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket, topN);
    }
}
