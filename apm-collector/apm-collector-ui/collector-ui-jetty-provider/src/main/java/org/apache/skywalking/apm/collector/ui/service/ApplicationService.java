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
import java.util.List;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IComponentLibraryCatalogService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.overview.ApplicationTPS;
import org.apache.skywalking.apm.collector.storage.ui.overview.ConjecturalApp;
import org.apache.skywalking.apm.collector.storage.ui.overview.ConjecturalAppBrief;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);

    private final IInstanceUIDAO instanceDAO;
    private final IServiceMetricUIDAO serviceMetricUIDAO;
    private final IApplicationMetricUIDAO applicationMetricUIDAO;
    private final INetworkAddressUIDAO networkAddressUIDAO;
    private final ApplicationCacheService applicationCacheService;
    private final ServiceNameCacheService serviceNameCacheService;
    private final SecondBetweenService secondBetweenService;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public ApplicationService(ModuleManager moduleManager) {
        this.instanceDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceUIDAO.class);
        this.serviceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceMetricUIDAO.class);
        this.applicationMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMetricUIDAO.class);
        this.networkAddressUIDAO = moduleManager.find(StorageModule.NAME).getService(INetworkAddressUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
        this.componentLibraryCatalogService = moduleManager.find(ConfigurationModule.NAME).getService(IComponentLibraryCatalogService.class);
        this.secondBetweenService = new SecondBetweenService(moduleManager);
    }

    public List<Application> getApplications(long startSecondTimeBucket, long endSecondTimeBucket,
        int... applicationIds) {
        List<Application> applications = instanceDAO.getApplications(startSecondTimeBucket, endSecondTimeBucket, applicationIds);

        for (int i = applications.size() - 1; i >= 0; i--) {
            Application application = applications.get(i);
            if (application.getId() == Const.NONE_APPLICATION_ID) {
                applications.remove(i);
            }
        }

        applications.forEach(application -> {
            String applicationCode = applicationCacheService.getApplicationById(application.getId()).getApplicationCode();
            application.setName(applicationCode);
        });
        return applications;
    }

    public List<ServiceMetric> getSlowService(int applicationId, Step step, long startTimeBucket, long endTimeBucket,
        long startSecondTimeBucket, long endSecondTimeBucket, Integer topN) throws ParseException {
        List<ServiceMetric> slowServices = serviceMetricUIDAO.getSlowService(applicationId, step, startTimeBucket, endTimeBucket, topN, MetricSource.Callee);
        slowServices.forEach(slowService -> {
            ServiceName serviceName = serviceNameCacheService.get(slowService.getId());

            try {
                slowService.setCallsPerSec((int)(slowService.getCalls() / secondBetweenService.calculate(serviceName.getApplicationId(), startSecondTimeBucket, endSecondTimeBucket)));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            slowService.setName(serviceName.getServiceName());
        });
        return slowServices;
    }

    public List<ApplicationTPS> getTopNApplicationThroughput(Step step, long startTimeBucket, long endTimeBucket,
        int topN) throws ParseException {
        int secondsBetween = DurationUtils.INSTANCE.secondsBetween(step, startTimeBucket, endTimeBucket);
        List<ApplicationTPS> applicationThroughput = applicationMetricUIDAO.getTopNApplicationThroughput(step, startTimeBucket, endTimeBucket, secondsBetween, topN, MetricSource.Callee);
        applicationThroughput.forEach(applicationTPS -> {
            String applicationCode = applicationCacheService.getApplicationById(applicationTPS.getApplicationId()).getApplicationCode();
            applicationTPS.setApplicationCode(applicationCode);
        });
        return applicationThroughput;
    }

    public ConjecturalAppBrief getConjecturalApps(Step step, long startSecondTimeBucket,
        long endSecondTimeBucket) throws ParseException {
        List<ConjecturalApp> conjecturalApps = networkAddressUIDAO.getConjecturalApps();
        conjecturalApps.forEach(conjecturalApp -> {
            String serverType = componentLibraryCatalogService.getServerName(conjecturalApp.getId());
            conjecturalApp.setName(serverType);
        });

        ConjecturalAppBrief conjecturalAppBrief = new ConjecturalAppBrief();
        conjecturalAppBrief.setApps(conjecturalApps);
        return conjecturalAppBrief;
    }

}
