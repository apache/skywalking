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
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IResponseTimeDistributionConfigService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IResponseTimeDistributionUIDAO;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.ValueType;
import org.apache.skywalking.apm.collector.storage.ui.overview.Thermodynamic;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistributionService {

    private final IResponseTimeDistributionConfigService responseTimeDistributionConfigService;
    private final IResponseTimeDistributionUIDAO responseTimeDistributionUIDAO;

    public ResponseTimeDistributionService(ModuleManager moduleManager) {
        this.responseTimeDistributionConfigService = moduleManager.find(ConfigurationModule.NAME).getService(IResponseTimeDistributionConfigService.class);
        this.responseTimeDistributionUIDAO = moduleManager.find(StorageModule.NAME).getService(IResponseTimeDistributionUIDAO.class);
    }

    public Thermodynamic getThermodynamic(Step step, long startTimeBucket, long endTimeBucket,
        ValueType type) throws ParseException {
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, startTimeBucket, endTimeBucket);

        List<IResponseTimeDistributionUIDAO.ResponseTimeStep> responseTimeSteps = new LinkedList<>();
        for (int i = 0; i < durationPoints.size(); i++) {
            for (int j = 0; j < responseTimeDistributionConfigService.getResponseTimeMaxStep(); j++) {
                responseTimeSteps.add(new IResponseTimeDistributionUIDAO.ResponseTimeStep(durationPoints.get(i).getPoint(), i, j));
            }
        }

        responseTimeDistributionUIDAO.loadMetrics(step, responseTimeSteps);

        Thermodynamic thermodynamic = new Thermodynamic();
        thermodynamic.setResponseTimeStep(responseTimeDistributionConfigService.getResponseTimeStep());
        responseTimeSteps.forEach(responseTimeStep -> {
            long calls = 0;
            switch (type) {
                case ALL:
                    calls = responseTimeStep.getCalls();
                    break;
                case RIGHT:
                    calls = responseTimeStep.getSuccessCalls();
                    break;
                case WRONG:
                    calls = responseTimeStep.getErrorCalls();
                    break;
            }

            List<Long> metric = trans(responseTimeStep.getyAxis(), responseTimeStep.getStep(), calls);
            thermodynamic.getNodes().add(metric);
        });

        return thermodynamic;
    }

    private List<Long> trans(long xAxis, long yAxis, long calls) {
        List<Long> metric = new LinkedList<>();
        metric.add(xAxis);
        metric.add(yAxis);
        metric.add(calls);
        return metric;
    }
}
