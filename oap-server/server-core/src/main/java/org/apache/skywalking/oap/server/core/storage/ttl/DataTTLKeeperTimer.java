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

package org.apache.skywalking.oap.server.core.storage.ttl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.DataTTL;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.config.DownsamplingConfigService;
import org.apache.skywalking.oap.server.core.storage.Downsampling;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.IModelGetter;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public enum DataTTLKeeperTimer {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(DataTTLKeeperTimer.class);

    private ModuleManager moduleManager;
    private ClusterNodesQuery clusterNodesQuery;
    @Setter private DataTTL dataTTL;

    public void start(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.clusterNodesQuery = moduleManager.find(ClusterModule.NAME).provider().getService(ClusterNodesQuery.class);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new RunnableWithExceptionProtection(this::delete,
                t -> logger.error("Remove data in background failure.", t)), 1, 5, TimeUnit.MINUTES);
    }

    private void delete() {
        List<RemoteInstance> remoteInstances = clusterNodesQuery.queryRemoteNodes();
        if (CollectionUtils.isNotEmpty(remoteInstances) && !remoteInstances.get(0).getAddress().isSelf()) {
            logger.info("The selected first getAddress is {}. Skip.", remoteInstances.get(0).toString());
            return;
        }

        TimeBuckets timeBuckets = convertTimeBucket(new DateTime());
        logger.info("Beginning to remove expired metrics from the storage.");
        logger.info("Metrics in minute dimension before {}, are going to be removed.", timeBuckets.minuteTimeBucketBefore);
        logger.info("Metrics in hour dimension before {}, are going to be removed.", timeBuckets.hourTimeBucketBefore);
        logger.info("Metrics in day dimension before {}, are going to be removed.", timeBuckets.dayTimeBucketBefore);
        logger.info("Metrics in month dimension before {}, are going to be removed.", timeBuckets.monthTimeBucketBefore);

        IModelGetter modelGetter = moduleManager.find(CoreModule.NAME).provider().getService(IModelGetter.class);
        DownsamplingConfigService downsamplingConfigService = moduleManager.find(CoreModule.NAME).provider().getService(DownsamplingConfigService.class);
        List<Model> models = modelGetter.getModels();
        models.forEach(model -> {
            if (model.isIndicator()) {
                execute(model, model.getName(), timeBuckets.minuteTimeBucketBefore, Indicator.TIME_BUCKET);

                if (downsamplingConfigService.shouldToHour()) {
                    execute(model, model.getName() + Const.ID_SPLIT + Downsampling.Hour.getName(), timeBuckets.hourTimeBucketBefore, Indicator.TIME_BUCKET);
                }
                if (downsamplingConfigService.shouldToDay()) {
                    execute(model, model.getName() + Const.ID_SPLIT + Downsampling.Day.getName(), timeBuckets.dayTimeBucketBefore, Indicator.TIME_BUCKET);
                }
                if (downsamplingConfigService.shouldToMonth()) {
                    execute(model, model.getName() + Const.ID_SPLIT + Downsampling.Month.getName(), timeBuckets.monthTimeBucketBefore, Indicator.TIME_BUCKET);
                }
            } else {
                execute(model, model.getName(), timeBuckets.recordDataTTL, Record.TIME_BUCKET);
            }
        });
    }

    TimeBuckets convertTimeBucket(DateTime currentTime) {
        TimeBuckets timeBuckets = new TimeBuckets();

        timeBuckets.recordDataTTL = Long.valueOf(currentTime.plusMinutes(0 - dataTTL.getRecordDataTTL()).toString("yyyyMMddHHmmss"));
        timeBuckets.minuteTimeBucketBefore = Long.valueOf(currentTime.plusMinutes(0 - dataTTL.getMinuteMetricsDataTTL()).toString("yyyyMMddHHmm"));
        timeBuckets.hourTimeBucketBefore = Long.valueOf(currentTime.plusHours(0 - dataTTL.getHourMetricsDataTTL()).toString("yyyyMMddHH"));
        timeBuckets.dayTimeBucketBefore = Long.valueOf(currentTime.plusDays(0 - dataTTL.getDayMetricsDataTTL()).toString("yyyyMMdd"));
        timeBuckets.monthTimeBucketBefore = Long.valueOf(currentTime.plusMonths(0 - dataTTL.getMonthMetricsDataTTL()).toString("yyyyMM"));

        return timeBuckets;
    }

    private void execute(Model model, String modelName, long timeBucketBefore, String timeBucketColumnName) {
        try {
            if (model.isDeleteHistory()) {
                moduleManager.find(StorageModule.NAME).provider().getService(IHistoryDeleteDAO.class).deleteHistory(modelName, timeBucketColumnName, timeBucketBefore);
            }
        } catch (IOException e) {
            logger.warn("History of {} delete failure, time bucket {}", modelName, timeBucketBefore);
            logger.error(e.getMessage(), e);
        }
    }

    class TimeBuckets {
        private long recordDataTTL;
        private long minuteTimeBucketBefore;
        private long hourTimeBucketBefore;
        private long dayTimeBucketBefore;
        private long monthTimeBucketBefore;
    }
}