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
package org.apache.skywalking.oap.server.storage.plugin.influxdb.base;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxTTLCalculatorProvider;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxTTLCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryDeleteDAO implements IHistoryDeleteDAO {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final InfluxTTLCalculatorProvider calculatorProvider;
    private final ModuleDefineHolder moduleDefineHolder;
    private final InfluxClient client;

    public HistoryDeleteDAO(ModuleDefineHolder moduleDefineHolder, InfluxClient client, InfluxTTLCalculatorProvider calculatorProvider) {
        this.moduleDefineHolder = moduleDefineHolder;
        this.calculatorProvider = calculatorProvider;
        this.client = client;
    }

    @Override public void deleteHistory(Model model, String timeBucketColumnName) throws IOException {
        ConfigService configService = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ConfigService.class);

        InfluxTTLCalculator ttlCalculator;
        if (model.isRecord()) {
            ttlCalculator = calculatorProvider.recordCalculator();
        } else {
            ttlCalculator = calculatorProvider.metricsCalculator(model.getDownsampling());
        }

        if (model.isCapableOfTimeSeries()) {
            // drop the whole series
            client.dropSeries(model.getName(), ttlCalculator.timeBucketBefore(configService.getDataTTLConfig()));
        } else {
            long timeBefore = ttlCalculator.timestampBefore(configService.getDataTTLConfig());
            client.queryForDelete("DELETE FROM " + model.getName() + " WHERE time <= " + timeBefore + "ms");
        }
    }
}
