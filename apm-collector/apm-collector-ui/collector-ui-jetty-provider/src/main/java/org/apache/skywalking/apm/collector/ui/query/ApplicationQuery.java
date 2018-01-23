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
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceInfo;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.graphql.utils.DurationUtils;
import org.apache.skywalking.apm.collector.ui.service.ApplicationService;

/**
 * @author peng-yongsheng
 */
public class ApplicationQuery implements Query {

    private final ModuleManager moduleManager;
    private ApplicationService applicationService;

    public ApplicationQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ApplicationService getApplicationService() {
        if (ObjectUtils.isEmpty(applicationService)) {
            this.applicationService = new ApplicationService(moduleManager);
        }
        return applicationService;
    }

    public List<Application> getAllApplication(Duration duration) throws ParseException {
        long start = 0;
        long end = 0;

        switch (duration.getStep()) {
            case MONTH:
                start = DurationUtils.INSTANCE.monthDurationToSecondTimeBucket(duration.getStart());
                end = DurationUtils.INSTANCE.monthDurationToSecondTimeBucket(duration.getEnd());
                break;
            case DAY:
                start = DurationUtils.INSTANCE.dayDurationToSecondTimeBucket(duration.getStart());
                end = DurationUtils.INSTANCE.dayDurationToSecondTimeBucket(duration.getEnd());
                break;
            case HOUR:
                start = DurationUtils.INSTANCE.hourDurationToSecondTimeBucket(duration.getStart());
                end = DurationUtils.INSTANCE.hourDurationToSecondTimeBucket(duration.getEnd());
                break;
            case MINUTE:
                start = DurationUtils.INSTANCE.minuteDurationToSecondTimeBucket(duration.getStart());
                end = DurationUtils.INSTANCE.minuteDurationToSecondTimeBucket(duration.getEnd());
                break;
            case SECOND:
                start = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
                end = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
                break;
        }

        return getApplicationService().getApplications(start, end);
    }

    public Topology getApplicationTopology(int applicationId, Duration duration) {
        return null;
    }

    public List<ServiceInfo> getSlowService(int applicationId, Duration duration, Integer top) {
        return null;
    }

    public List<AppServerInfo> getServerThroughput(int applicationId, Duration duration, Integer top) {
        return null;
    }
}
