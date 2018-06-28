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

package org.apache.skywalking.apm.collector.ui.service;

import java.text.ParseException;
import java.util.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.register.*;
import org.apache.skywalking.apm.collector.storage.ttl.ITTLConfigService;
import org.apache.skywalking.apm.collector.storage.ui.common.*;
import org.apache.skywalking.apm.collector.storage.ui.service.*;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceNameService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameService.class);

    private final ApplicationCacheService applicationCacheService;
    private final IServiceNameServiceUIDAO serviceNameServiceUIDAO;
    private final IServiceMetricUIDAO serviceMetricUIDAO;
    private final ServiceNameCacheService serviceNameCacheService;
    private final DateBetweenService dateBetweenService;
    private final ITTLConfigService configService;

    public ServiceNameService(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serviceNameServiceUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceNameServiceUIDAO.class);
        this.serviceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceMetricUIDAO.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
        this.configService = moduleManager.find(StorageModule.NAME).getService(ITTLConfigService.class);
        this.dateBetweenService = new DateBetweenService(moduleManager);
    }

    public int getCount() {
        return serviceNameServiceUIDAO.getCount(startTimeMillis());
    }

    public List<ServiceInfo> searchService(String keyword, int applicationId, int topN) {
        List<ServiceInfo> services = serviceNameServiceUIDAO.searchService(keyword, applicationId, startTimeMillis(), topN);
        services.forEach(service -> {
            Application application = applicationCacheService.getApplicationById(service.getApplicationId());
            if (Objects.nonNull(application)) {
                service.setApplicationName(application.getApplicationCode());
            } else {
                service.setApplicationName(Const.EMPTY_STRING);
            }
        });
        return services;
    }

    private long startTimeMillis() {
        int minuteMetricDataTTL = configService.minuteMetricDataTTL();
        return System.currentTimeMillis() - minuteMetricDataTTL * 60 * 60 * 100;
    }

    public ThroughputTrend getServiceThroughputTrend(int serviceId, Step step, long startTimeBucket,
        long endTimeBucket) throws ParseException {
        ThroughputTrend throughputTrend = new ThroughputTrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, startTimeBucket, endTimeBucket);
        List<Integer> throughputTrends = serviceMetricUIDAO.getServiceThroughputTrend(serviceId, step, durationPoints);
        throughputTrend.setTrendList(throughputTrends);
        return throughputTrend;
    }

    public ResponseTimeTrend getServiceResponseTimeTrend(int serviceId, Step step, long startTimeBucket,
        long endTimeBucket) throws ParseException {
        ResponseTimeTrend responseTimeTrend = new ResponseTimeTrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, startTimeBucket, endTimeBucket);
        responseTimeTrend.setTrendList(serviceMetricUIDAO.getServiceResponseTimeTrend(serviceId, step, durationPoints));
        return responseTimeTrend;
    }

    public SLATrend getServiceSLATrend(int serviceId, Step step, long startTimeBucket,
        long endTimeBucket) throws ParseException {
        SLATrend slaTrend = new SLATrend();
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, startTimeBucket, endTimeBucket);
        slaTrend.setTrendList(serviceMetricUIDAO.getServiceSLATrend(serviceId, step, durationPoints));
        return slaTrend;
    }

    public List<ServiceMetric> getSlowService(Step step, long startTimeBucket, long endTimeBucket,
        long startSecondTimeBucket, long endSecondTimeBucket, Integer topN) {
        List<ServiceMetric> slowServices = serviceMetricUIDAO.getSlowService(0, step, startTimeBucket, endTimeBucket, topN, MetricSource.Callee);
        slowServices.forEach(slowService -> {
            ServiceName serviceName = serviceNameCacheService.get(slowService.getService().getId());
            slowService.getService().setName(serviceName.getServiceName());
            slowService.getService().setApplicationId(serviceName.getApplicationId());
            slowService.getService().setApplicationName(applicationCacheService.getApplicationById(serviceName.getApplicationId()).getApplicationCode());
            try {
                slowService.setCpm((int)(slowService.getCalls() / dateBetweenService.minutesBetween(serviceName.getApplicationId(), startSecondTimeBucket, endSecondTimeBucket)));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
        });
        return slowServices;
    }
}
