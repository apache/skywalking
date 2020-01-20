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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.Thermodynamic;
import org.apache.skywalking.oap.server.core.query.sql.KeyValues;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnIds;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class MetricQueryService implements Service {

    private static final Logger logger = LoggerFactory.getLogger(MetricQueryService.class);

    private final ModuleManager moduleManager;
    private IMetricsQueryDAO metricQueryDAO;

    public MetricQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IMetricsQueryDAO getMetricQueryDAO() {
        if (metricQueryDAO == null) {
            metricQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetricsQueryDAO.class);
        }
        return metricQueryDAO;
    }

    public IntValues getValues(final String metricsName, final List<String> ids, final Downsampling downsampling,
        final long startTB,
        final long endTB) throws IOException {
        if (CollectionUtils.isEmpty(ids)) {
            /*
             * Don't support query values w/o ID. but UI still did this(as bug),
             * we return an empty list, and a debug level log,
             * rather than an exception, which always being considered as a serious error from new users.
             */
            logger.debug("query metrics[{}] w/o IDs", metricsName);
            return new IntValues();
        }

        Where where = new Where();
        KeyValues intKeyValues = new KeyValues();
        intKeyValues.setKey(Metrics.ENTITY_ID);
        where.getKeyValues().add(intKeyValues);
        ids.forEach(intKeyValues.getValues()::add);

        return getMetricQueryDAO().getValues(metricsName, downsampling, startTB, endTB, where, ValueColumnIds.INSTANCE.getValueCName(metricsName), ValueColumnIds.INSTANCE.getValueFunction(metricsName));
    }

    public IntValues getLinearIntValues(final String indName, final String id, final Downsampling downsampling,
        final long startTB,
        final long endTB) throws IOException {
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(downsampling, startTB, endTB);
        List<String> ids = new ArrayList<>();
        if (StringUtil.isEmpty(id)) {
            durationPoints.forEach(durationPoint -> ids.add(String.valueOf(durationPoint.getPoint())));
        } else {
            durationPoints.forEach(durationPoint -> ids.add(durationPoint.getPoint() + Const.ID_SPLIT + id));
        }

        return getMetricQueryDAO().getLinearIntValues(indName, downsampling, ids, ValueColumnIds.INSTANCE.getValueCName(indName));
    }

    public List<IntValues> getMultipleLinearIntValues(final String indName, final String id, final int numOfLinear,
        final Downsampling downsampling,
        final long startTB,
        final long endTB) throws IOException {
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(downsampling, startTB, endTB);
        List<String> ids = new ArrayList<>();
        if (StringUtil.isEmpty(id)) {
            durationPoints.forEach(durationPoint -> ids.add(String.valueOf(durationPoint.getPoint())));
        } else {
            durationPoints.forEach(durationPoint -> ids.add(durationPoint.getPoint() + Const.ID_SPLIT + id));
        }

        IntValues[] multipleLinearIntValues = getMetricQueryDAO().getMultipleLinearIntValues(indName, downsampling, ids, numOfLinear, ValueColumnIds.INSTANCE.getValueCName(indName));

        List<IntValues> response = new ArrayList<>(numOfLinear);
        Collections.addAll(response, multipleLinearIntValues);
        return response;
    }

    public Thermodynamic getThermodynamic(final String indName, final String id, final Downsampling downsampling,
        final long startTB,
        final long endTB) throws IOException {
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(downsampling, startTB, endTB);
        List<String> ids = new ArrayList<>();
        durationPoints.forEach(durationPoint -> {
            if (id == null) {
                ids.add(String.valueOf(durationPoint.getPoint()));
            } else {
                ids.add(durationPoint.getPoint() + Const.ID_SPLIT + id);
            }
        });

        return getMetricQueryDAO().getThermodynamic(indName, downsampling, ids, ValueColumnIds.INSTANCE.getValueCName(indName));
    }
}
