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
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.collector.storage.ui.overview.AlarmTrend;
import org.apache.skywalking.apm.collector.storage.ui.overview.ApplicationTPS;
import org.apache.skywalking.apm.collector.storage.ui.overview.ClusterBrief;
import org.apache.skywalking.apm.collector.storage.ui.overview.ConjecturalAppBrief;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.service.AlarmService;
import org.apache.skywalking.apm.collector.ui.service.ApplicationService;
import org.apache.skywalking.apm.collector.ui.service.ClusterTopologyService;
import org.apache.skywalking.apm.collector.ui.service.NetworkAddressService;
import org.apache.skywalking.apm.collector.ui.service.ServiceNameService;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;

/**
 * @author peng-yongsheng
 */
public class OverViewLayerQuery implements Query {

    private final ModuleManager moduleManager;
    private ClusterTopologyService clusterTopologyService;
    private ApplicationService applicationService;
    private NetworkAddressService networkAddressService;
    private ServiceNameService serviceNameService;
    private AlarmService alarmService;

    public OverViewLayerQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ClusterTopologyService getClusterTopologyService() {
        if (ObjectUtils.isEmpty(clusterTopologyService)) {
            this.clusterTopologyService = new ClusterTopologyService(moduleManager);
        }
        return clusterTopologyService;
    }

    private ApplicationService getApplicationService() {
        if (ObjectUtils.isEmpty(applicationService)) {
            this.applicationService = new ApplicationService(moduleManager);
        }
        return applicationService;
    }

    private NetworkAddressService getNetworkAddressService() {
        if (ObjectUtils.isEmpty(networkAddressService)) {
            this.networkAddressService = new NetworkAddressService(moduleManager);
        }
        return networkAddressService;
    }

    private ServiceNameService getServiceNameService() {
        if (ObjectUtils.isEmpty(serviceNameService)) {
            this.serviceNameService = new ServiceNameService(moduleManager);
        }
        return serviceNameService;
    }

    private AlarmService getAlarmService() {
        if (ObjectUtils.isEmpty(alarmService)) {
            this.alarmService = new AlarmService(moduleManager);
        }
        return alarmService;
    }

    public Topology getClusterTopology(Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getClusterTopologyService().getClusterTopology(duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket);
    }

    public ClusterBrief getClusterBrief(Duration duration) throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        ClusterBrief clusterBrief = new ClusterBrief();
        clusterBrief.setNumOfApplication(getApplicationService().getApplications(startSecondTimeBucket, endSecondTimeBucket).size());
        clusterBrief.setNumOfDatabase(getNetworkAddressService().getNumOfDatabase());
        clusterBrief.setNumOfCache(getNetworkAddressService().getNumOfCache());
        clusterBrief.setNumOfMQ(getNetworkAddressService().getNumOfMQ());
        clusterBrief.setNumOfService(getServiceNameService().getCount());
        return clusterBrief;
    }

    public AlarmTrend getAlarmTrend(Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getAlarmService().getApplicationAlarmTrend(duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket);
    }

    public ConjecturalAppBrief getConjecturalApps(Duration duration) throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getApplicationService().getConjecturalApps(duration.getStep(), startSecondTimeBucket, endSecondTimeBucket);
    }

    public List<ServiceMetric> getTopNSlowService(Duration duration, int topN) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());

        return getServiceNameService().getSlowService(duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket, topN);
    }

    public List<ApplicationTPS> getTopNApplicationThroughput(Duration duration,
        int topN) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getApplicationService().getTopNApplicationThroughput(duration.getStep(), startTimeBucket, endTimeBucket, topN);
    }
}
